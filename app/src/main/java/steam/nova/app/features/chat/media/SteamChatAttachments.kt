package steam.nova.app.features.chat.media

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import android.graphics.BitmapFactory
import android.net.Uri
import steam.nova.app.features.chat.data.SteamChatRepository
import steam.nova.app.features.chat.network.SteamChatHttp
import steam.nova.app.features.chat.network.SteamChatFailure
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import org.json.JSONObject
import java.util.UUID

/** Only URIs returned by our own document picker may enter this upload path. */
internal class SteamChatAttachments(private val context: Context) {
    private data class Selected(val account: String, val uri: Uri, val name: String, val mime: String)
    private val selected = linkedMapOf<String, Selected>()
    private val uploads = ConcurrentHashMap<String, Int>()

    fun select(account: String, uri: Uri): JSONObject {
        require(uri.scheme == "content")
        val mime = context.contentResolver.getType(uri).orEmpty()
        require(mime in SUPPORTED)
        var name = "attachment"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0).orEmpty().replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(120)
                size = cursor.getLong(1)
            }
        }
        if (size > MAX_BYTES) throw SteamChatFailure("ATTACHMENT_TOO_LARGE")
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (mime.startsWith("image/")) {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth in 1..20000 && options.outHeight in 1..20000)
        }
        val key = UUID.randomUUID().toString()
        synchronized(selected) { selected.clear(); selected[key] = Selected(account, uri, name, mime) }
        return JSONObject().put("selection", key).put("uri", uri.toString()).put("name", name).put("mime", mime)
            .put("size", size).put("image", mime.startsWith("image/"))
            .put("width", options.outWidth.coerceAtLeast(0)).put("height", options.outHeight.coerceAtLeast(0))
    }

    fun progress(key: String): JSONObject = JSONObject().put("percent", uploads[key] ?: 0)

    fun clear() = synchronized(selected) { selected.clear(); uploads.clear() }

    fun upload(repository: SteamChatRepository, args: JSONObject): JSONObject {
        repository.checkAccount()
        val key = args.getString("selection")
        val item = synchronized(selected) { selected[key] } ?: throw SteamChatFailure("IMAGE_EXPIRED")
        require(item.account == repository.account.id)
        uploads[key] = 0
        val bytes = context.contentResolver.openInputStream(item.uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) throw SteamChatFailure("ATTACHMENT_TOO_LARGE")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw SteamChatFailure("IMAGE_EXPIRED")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val mime = item.mime
        if (mime.startsWith("image/")) {
            require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000 && bounds.outMimeType in SUPPORTED)
        }
        require(mime in SUPPORTED)
        if (bytes.isEmpty()) throw SteamChatFailure("ATTACHMENT_REJECTED")
        val result = SteamChatAttachmentUpload(repository).send(
            bytes = bytes, filename = item.name, mime = mime,
            width = bounds.outWidth.coerceAtLeast(0), height = bounds.outHeight.coerceAtLeast(0),
            target = SteamChatRepository.Target.parse(args), spoiler = args.optBoolean("spoiler"),
            progress = { uploads[key] = it },
        )
        synchronized(selected) { selected.remove(key) }
        return result
    }

    fun uploadAvatar(repository: SteamChatRepository, args: JSONObject): JSONObject {
        repository.checkAccount()
        val item = synchronized(selected) { selected[args.getString("selection")] } ?: throw SteamChatFailure("IMAGE_EXPIRED")
        require(item.account == repository.account.id && item.mime.startsWith("image/"))
        val bytes = context.contentResolver.openInputStream(item.uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                if (output.size() + count > 5 * 1024 * 1024) throw SteamChatFailure("AVATAR_TOO_LARGE")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw SteamChatFailure("IMAGE_EXPIRED")
        val session = UUID.randomUUID().toString().replace("-", "")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("sessionid", session)
            .addFormDataPart("avatar", item.name, bytes.toRequestBody(item.mime.toMediaType())).build()
        val result = SteamChatHttp.json(Request.Builder().url("https://steamcommunity.com/chat/avatarfileupload/")
            .header("Cookie", "${SteamChatHttp.cookie(repository.account)}; sessionid=$session")
            .header("Origin", "https://steamcommunity.com").header("Referer", "https://steamcommunity.com/chat/")
            .post(body).build())
        repository.checkAccount()
        val sha = result.getString("sha").trim(); require(sha.matches(Regex("[a-fA-F0-9]{40}")))
        return JSONObject().put("sha", sha)
    }

    fun save(args: JSONObject): JSONObject {
        val url = args.getString("url").toHttpUrl()
        require(url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty())
        require(listOf("steamusercontent.com", "steamcontent.com", "steamstatic.com", "steamcommunity.com").any {
            url.host == it || url.host.endsWith(".$it")
        })
        mediaClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw SteamChatFailure("DOWNLOAD_FAILED")
            val body = response.body
            require(body.contentLength() <= MAX_BYTES)
            val mime = body.contentType()?.toString()?.substringBefore(';') ?: "application/octet-stream"
            val image = mime.startsWith("image/")
            if (Build.VERSION.SDK_INT < 29 && context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw SteamChatFailure("SAVE_PERMISSION_REQUIRED")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Steam-${System.currentTimeMillis()}." + when(mime) {
                    "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/gif" -> "gif"; "image/webp" -> "webp"; "image/avif" -> "avif"
                    "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/mpeg" -> "mpeg"; "video/ogg" -> "ogv"; "application/zip" -> "zip"; else -> "bin"
                })
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, if (image) Environment.DIRECTORY_PICTURES + "/Steam" else Environment.DIRECTORY_DOWNLOADS + "/Steam")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else if (Build.VERSION.SDK_INT >= 29) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw SteamChatFailure("DOWNLOAD_FAILED")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) {
                            val count = input.read(buffer); if (count < 0) break
                            total += count; require(total <= MAX_BYTES); output.write(buffer, 0, count)
                        }
                    }
                } ?: throw SteamChatFailure("DOWNLOAD_FAILED")
                if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
            return JSONObject().put("saved", true)
        }
    }

    companion object {
        private val mediaClient = SteamChatHttp.client.newBuilder().writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        private const val MAX_BYTES = 30 * 1024 * 1024
        private val SUPPORTED = setOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/avif", "video/mp4", "video/webm",
            "video/mpeg", "video/ogg", "application/zip", "application/x-zip-compressed")
    }

}
