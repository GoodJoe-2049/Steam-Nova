package steam.nova.app.features.chat.media

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject
import steam.nova.app.features.chat.network.SteamChatAccount
import steam.nova.app.features.chat.network.SteamChatFailure
import steam.nova.app.features.chat.network.SteamChatHttp
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Account-scoped media cache. Downloads never occupy the chat/CM request workers. */
internal class SteamChatMediaTransfer(private val context: Context) {
    private class Entry(val account: String, val url: HttpUrl, val base: File) {
        @Volatile var metadata = JSONObject()
        @Volatile var state = "idle"
        @Volatile var received = 0L
        @Volatile var total = 0L
        @Volatile var error = ""
        @Volatile var call: Call? = null
        @Volatile var cancelled = false
        val file get() = File(base.path + if (metadata.optString("mime").startsWith("video/")) ".mp4" else ".image")
        val poster get() = File(base.path + ".jpg")
        fun snapshot(): JSONObject =
            JSONObject(metadata.toString()).put("state", if (file.isFile && file.length() > 0) "ready" else state)
                .put("received", received).put("size", total).put("error", error)
                .put("uri", if (file.isFile) Uri.fromFile(file).toString() else "")
                .put("poster", if (poster.isFile) Uri.fromFile(poster).toString() else "")
    }

    private val entries = ConcurrentHashMap<String, Entry>()
    private val downloads = Executors.newFixedThreadPool(2) { task -> Thread(task, "SteamNovaMediaDownload").apply { isDaemon = true } }
    private val client = SteamChatHttp.client.newBuilder().readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).build()

    fun execute(account: String, args: JSONObject): JSONObject {
        val entry = entry(account, args.getString("url"))
        return when (args.getString("operation")) {
            "mediaInfo" -> inspect(entry, args.optString("kind") == "video")
            "mediaStatus" -> entry.snapshot()
            "mediaDownload" -> {
                synchronized(entry) {
                    if (!entry.file.isFile && entry.state != "downloading") {
                        entry.cancelled = false; entry.error = ""; entry.received = 0; entry.state = "downloading"
                        downloads.execute { runCatching { download(entry) } }
                    }
                }
                entry.snapshot()
            }
            "mediaCancel" -> {
                entry.cancelled = true; entry.call?.cancel(); entry.snapshot()
            }
            "mediaExport", "mediaShare" -> {
                ensureDownloaded(entry)
                val uri = if (args.getString("operation") == "mediaShare") {
                    val shared = shareUri(entry)
                    checkAccount(entry)
                    val intent = Intent(Intent.ACTION_SEND).setType(entry.metadata.optString("mime", "application/octet-stream"))
                        .putExtra(Intent.EXTRA_STREAM, shared).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.clipData = ClipData.newRawUri("Steam", shared)
                    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    shared
                } else export(entry, args.optString("destination", "gallery"))
                JSONObject().put("saved", true).put("uri", uri.toString())
            }
            else -> throw SteamChatFailure("INVALID_OPERATION")
        }
    }

    fun clear(accountToKeep: String? = null) {
        entries.values.filter { it.account != accountToKeep }.forEach { it.cancelled = true; it.call?.cancel() }
        entries.entries.removeAll { it.value.account != accountToKeep }
    }

    private fun entry(account: String, raw: String): Entry {
        SteamChatAccount.current(account)
        val url = raw.toHttpUrl()
        require(url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty())
        require(listOf("steamusercontent.com", "steamcontent.com", "steamstatic.com", "steamcommunity.com", "akamaihd.net").any {
            url.host == it || url.host.endsWith(".$it")
        })
        val key = account + ":" + MessageDigest.getInstance("SHA-256").digest(url.toString().toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        return entries.computeIfAbsent(key) {
            val directory = File(context.cacheDir, "steamnova-chat-media/$account").apply { mkdirs() }
            // Old completed files may be evicted, but never an active partial download.
            val active = entries.values.map { it.base.name }.toSet()
            directory.listFiles()?.filter { it.name.substringBefore('.') !in active && System.currentTimeMillis() - it.lastModified() > TimeUnit.DAYS.toMillis(7) && it.extension != "part" }
                ?.forEach { it.delete() }
            Entry(account, url, File(directory, key.substringAfter(':'))).also { e ->
                runCatching { e.metadata = JSONObject(File(e.base.path + ".json").readText()) }
                e.total = e.metadata.optLong("size")
            }
        }
    }

    private fun checkAccount(entry: Entry) {
        SteamChatAccount.current(entry.account)
        if (entry.cancelled) throw SteamChatFailure("MEDIA_CANCELLED")
    }

    private fun inspect(entry: Entry, video: Boolean): JSONObject = synchronized(entry) {
        checkAccount(entry)
        if (entry.metadata.optBoolean("inspected")) return@synchronized entry.snapshot()
        client.newCall(Request.Builder().url(entry.url).head().build()).execute().use { response ->
            if (!response.isSuccessful) throw SteamChatFailure("MEDIA_FAILED")
            entry.total = response.header("Content-Length")?.toLongOrNull() ?: 0
            if (entry.total > MAX_BYTES) throw SteamChatFailure("ATTACHMENT_TOO_LARGE")
            val mime = response.header("Content-Type").orEmpty().substringBefore(';')
            require(mime.startsWith("image/") || mime.startsWith("video/"))
            entry.metadata = JSONObject(entry.metadata.toString()).put("mime", mime).put("size", entry.total)
        }
        if (video || entry.metadata.optString("mime").startsWith("video/")) {
            // Steam video BBCode has no thumbnail or duration. Decode a frame using bounded range reads.
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    if (entry.file.isFile) retriever.setDataSource(entry.file.path)
                    else RangeSource(entry).use { source -> retriever.setDataSource(source); metadata(entry, retriever) }
                    if (entry.file.isFile) metadata(entry, retriever)
                } finally { retriever.release() }
            }.onFailure { entry.metadata = JSONObject(entry.metadata.toString()).put("previewUnavailable", true) }
        }
        checkAccount(entry)
        entry.metadata = JSONObject(entry.metadata.toString()).put("inspected", true)
        File(entry.base.path + ".json").writeText(entry.metadata.toString())
        entry.snapshot()
    }

    private fun metadata(entry: Entry, retriever: MediaMetadataRetriever) {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        entry.metadata = JSONObject(entry.metadata.toString()).put("width", if (rotation % 180 == 0) width else height)
            .put("height", if (rotation % 180 == 0) height else width)
            .put("duration", (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) / 1000.0)
        val scale = 640.0 / maxOf(width, height, 1)
        val frame = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            (width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
            else retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        frame?.let { bitmap ->
            entry.poster.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bitmap.recycle()
        }
    }

    private inner class RangeSource(private val entry: Entry) : MediaDataSource() {
        private val blocks = linkedMapOf<Long, ByteArray>()
        private var readBytes = 0L
        private val deadline = SystemClock.elapsedRealtime() + 20_000
        override fun getSize() = entry.total.takeIf { it > 0 } ?: -1L
        override fun close() { blocks.clear() }
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (position !in 0 until (entry.total.takeIf { it > 0 } ?: Long.MAX_VALUE)) return -1
            checkAccount(entry)
            if (SystemClock.elapsedRealtime() > deadline || readBytes > 4 * 1024 * 1024) throw IOException("Preview budget exceeded")
            val start = position / BLOCK * BLOCK
            val block = blocks[start] ?: client.newCall(Request.Builder().url(entry.url)
                .header("Range", "bytes=$start-${start + BLOCK - 1}").header("Accept-Encoding", "identity").build()).execute().use { response ->
                if (response.code != 206 && !(response.code == 200 && start == 0L)) throw IOException("Range unavailable")
                if (response.code == 206 && !response.header("Content-Range").orEmpty().startsWith("bytes $start-")) throw IOException("Invalid range")
                val data = response.body.byteStream().readBytesLimited(BLOCK.toInt())
                readBytes += data.size
                blocks[start] = data
                if (blocks.size > 16) blocks.remove(blocks.keys.first())
                data
            }
            val index = (position - start).toInt()
            val count = minOf(size, block.size - index)
            if (count <= 0) return -1
            block.copyInto(buffer, offset, index, index + count)
            return count
        }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (result.size() < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - result.size()))
            if (count < 0) break
            result.write(buffer, 0, count)
        }
        return result.toByteArray()
    }

    private fun download(entry: Entry) {
        val partial = File(entry.base.path + ".part")
        try {
            checkAccount(entry)
            val call = client.newCall(Request.Builder().url(entry.url).header("Accept-Encoding", "identity").build())
            entry.call = call
            call.execute().use { response ->
                if (!response.isSuccessful) throw SteamChatFailure("MEDIA_FAILED")
                val mime = response.body.contentType()?.toString().orEmpty().substringBefore(';')
                require(mime.startsWith("video/") || mime.startsWith("image/"))
                entry.metadata = JSONObject(entry.metadata.toString()).put("mime", mime)
                entry.total = response.body.contentLength().coerceAtLeast(0)
                if (entry.total > MAX_BYTES) throw SteamChatFailure("ATTACHMENT_TOO_LARGE")
                response.body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkAccount(entry)
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (entry.received + count > MAX_BYTES) throw SteamChatFailure("ATTACHMENT_TOO_LARGE")
                        output.write(buffer, 0, count); entry.received += count
                    }
                } }
                if (entry.received == 0L || entry.total > 0 && entry.received != entry.total) throw SteamChatFailure("MEDIA_FAILED")
                checkAccount(entry)
                if (!partial.renameTo(entry.file)) throw SteamChatFailure("MEDIA_FAILED")
                entry.total = entry.received
                entry.metadata = JSONObject(entry.metadata.toString()).put("size", entry.total)
                if (mime.startsWith("video/")) runCatching {
                    val retriever = MediaMetadataRetriever()
                    try { retriever.setDataSource(entry.file.path); metadata(entry, retriever) } finally { retriever.release() }
                }
                File(entry.base.path + ".json").writeText(entry.metadata.toString())
                entry.state = "ready"
            }
        } catch (error: Exception) {
            partial.delete()
            entry.state = if (entry.cancelled) "idle" else "failed"
            entry.error = if (entry.cancelled) "" else (error as? SteamChatFailure)?.code ?: "MEDIA_FAILED"
            throw error
        } finally { entry.call = null }
    }

    private fun ensureDownloaded(entry: Entry) {
        if (entry.file.isFile) return
        synchronized(entry) {
            if (entry.state == "downloading") throw SteamChatFailure("MEDIA_DOWNLOADING")
            entry.cancelled = false; entry.received = 0; entry.state = "downloading"
        }
        download(entry)
    }

    @Suppress("DEPRECATION")
    private fun shareUri(entry: Entry): Uri {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
        val type = runCatching { Class.forName("androidx.core.content.FileProvider", false, context.classLoader) }.getOrNull()
        if (type != null) for (provider in info.providers.orEmpty()) {
            if (provider.metaData?.containsKey("android.support.FILE_PROVIDER_PATHS") != true) continue
            for (authority in provider.authority.orEmpty().split(';')) {
                val uri = runCatching { type.getMethod("getUriForFile", Context::class.java, String::class.java, File::class.java)
                    .invoke(null, context, authority, entry.file) as Uri }.getOrNull()
                if (uri != null) return uri
            }
        }
        // Hosts without a cache FileProvider can share a real MediaStore file with a URI grant.
        return export(entry, "downloads")
    }

    @Suppress("DEPRECATION")
    private fun export(entry: Entry, destination: String): Uri {
        checkAccount(entry)
        require(destination in setOf("gallery", "downloads"))
        val mime = entry.metadata.optString("mime")
        val video = mime.startsWith("video/")
        if (Build.VERSION.SDK_INT < 29 && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw SteamChatFailure("SAVE_PERMISSION_REQUIRED")
        }
        val extension = when (mime) { "video/webm" -> "webm"; "video/mp4" -> "mp4"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; "image/avif" -> "avif"; else -> if (video) "mp4" else "jpg" }
        val name = "Steam-${System.currentTimeMillis()}.$extension"
        val folder = when { destination == "downloads" -> Environment.DIRECTORY_DOWNLOADS; video -> Environment.DIRECTORY_MOVIES; else -> Environment.DIRECTORY_PICTURES }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Steam")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(folder), "Steam")
                if (!directory.isDirectory && !directory.mkdirs()) throw SteamChatFailure("MEDIA_FAILED")
                put(MediaStore.MediaColumns.DATA, File(directory, name).path)
            }
        }
        val collection = when {
            destination == "downloads" && Build.VERSION.SDK_INT >= 29 -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
            destination == "downloads" -> MediaStore.Files.getContentUri("external")
            video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw SteamChatFailure("MEDIA_FAILED")
        try {
            resolver.openOutputStream(uri)?.use { output -> entry.file.inputStream().use { it.copyTo(output) } }
                ?: throw SteamChatFailure("MEDIA_FAILED")
            checkAccount(entry)
            if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
        return uri
    }

    companion object {
        private const val MAX_BYTES = 30L * 1024 * 1024
        private const val BLOCK = 64L * 1024
    }
}
