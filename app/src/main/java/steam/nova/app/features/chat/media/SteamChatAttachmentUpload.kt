package steam.nova.app.features.chat.media

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import steam.nova.app.features.chat.data.SteamChatRepository
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.network.SteamChatFailure
import steam.nova.app.features.chat.network.SteamChatHttp
import steam.nova.app.features.chat.protocol.SteamChatProtocol as P
import steam.nova.app.features.steamguard.data.SteamSessionData
import java.io.IOException
import java.net.URLEncoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Steam Chat's begin -> cloud PUT -> commit protocol, also used by Monica Steam. */
internal class SteamChatAttachmentUpload(private val repository: SteamChatRepository) {
    private val sessionId = randomHex().take(24)
    private var accessToken = repository.account.token
    private var refreshAttempted = false

    fun send(
        bytes: ByteArray,
        filename: String,
        mime: String,
        width: Int,
        height: Int,
        target: SteamChatRepository.Target,
        spoiler: Boolean,
        progress: (Int) -> Unit,
    ): JSONObject {
        repository.checkAccount()
        val common = linkedMapOf(
            "sessionid" to sessionId, "l" to "english",
            "file_name" to "${System.nanoTime()}_${filename.ifBlank { "attachment" }}",
            // The web client uses a random 40-hex upload identifier, not a content digest.
            "file_sha" to randomHex() + randomHex().take(8), "file_type" to mime,
            "file_image_width" to width.toString(), "file_image_height" to height.toString(),
        )
        val expiry = claims(accessToken)?.optLong("exp", 0) ?: 0
        if (expiry <= System.currentTimeMillis() / 1000 + 300) {
            try {
                refreshSession()
            } catch (error: Exception) {
                // Like Monica, still try the current credential if proactive renewal is unavailable.
                repository.checkAccount()
                if (error is SteamChatFailure && error.code == "RATE_LIMITED") throw error
            }
        }
        val beginFields = common + ("file_size" to bytes.size.toString())
        val begin = try {
            post("begin", beginFields)
        } catch (error: SteamChatFailure) {
            // No message has been sent at this stage. Never retry the final commit automatically.
            if (refreshAttempted || error.code !in setOf("AUTH_EXPIRED", "ATTACHMENT_SESSION_REJECTED")) throw error
            try {
                refreshSession()
            } catch (refreshError: Exception) {
                repository.checkAccount()
                if (refreshError is SteamChatFailure && refreshError.code == "RATE_LIMITED") throw refreshError
                throw error
            }
            post("begin", beginFields)
        }
        val reservation = begin.optJSONObject("result") ?: throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
        val ugcId = reservation.optString("ugcid")
        val timestamp = begin.optString("timestamp")
        val hmac = begin.optString("hmac")
        if (ugcId.isBlank() || timestamp.toLongOrNull()?.let { it > 0 } != true || hmac.isBlank()) {
            throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
        }
        val recipient = target.peer?.let { mapOf("friend_steamid" to it) }
            ?: mapOf("chat_group_id" to requireNotNull(target.group), "chat_id" to requireNotNull(target.room))
        val commitFields = common + recipient + mapOf(
            "ugcid" to ugcId, "timestamp" to timestamp, "hmac" to hmac,
            "spoiler" to if (spoiler) "1" else "0",
        )
        try {
            put(reservation, bytes, mime, progress)
            repository.checkAccount()
        } catch (error: Exception) {
            // Release only a failed cloud reservation. A commit may already have delivered a message.
            runCatching { post("cancel", commitFields + ("success" to "0")) }
            throw error
        }
        val committed = try {
            post("commit", commitFields + ("success" to "1"))
        } catch (error: SteamChatFailure) {
            if (error.code in setOf("ATTACHMENT_UPLOAD_FAILED", "ATTACHMENT_RESPONSE_INVALID")) {
                throw SteamChatFailure("ATTACHMENT_UNCONFIRMED")
            }
            throw error
        } catch (_: IOException) {
            throw SteamChatFailure("ATTACHMENT_UNCONFIRMED")
        }
        // Commit sends the attachment itself; do not also call SendMessage with the URL.
        val result = committed.optJSONObject("result") ?: throw SteamChatFailure("ATTACHMENT_UNCONFIRMED")
        if (successCode(result) == null) throw SteamChatFailure("ATTACHMENT_UNCONFIRMED")
        requireSuccess(result, "commit.result", 200)
        val url = result.optJSONObject("details")?.optString("url")?.toHttpUrlOrNull()
            ?: throw SteamChatFailure("ATTACHMENT_UNCONFIRMED")
        if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw SteamChatFailure("ATTACHMENT_UNCONFIRMED")
        }
        repository.checkAccount()
        progress(100)
        return JSONObject().put("url", url.toString())
    }

    private fun post(stage: String, fields: Map<String, String>): JSONObject = SteamChatDiagnostics.trace("upload.$stage") {
        repository.checkAccount()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (name, value) -> body.addFormDataPart(name, value) }
        val form = body.build()
        val requestBody = if (stage == "commit") object : RequestBody() {
            override fun contentType() = form.contentType()
            override fun contentLength() = form.contentLength()
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
        } else form
        val path = if (stage == "begin") "beginfileupload/?l=english" else "commitfileupload/"
        val secure = URLEncoder.encode("${repository.account.id}||$accessToken", "UTF-8").replace("+", "%20")
        val request = Request.Builder().url("https://steamcommunity.com/chat/$path")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Steam Chat")
            .header("Accept", "application/json, text/plain, */*")
            .header("Origin", "https://steamcommunity.com")
            .header("Referer", "https://steamcommunity.com/chat/")
            .header("Cookie", "steamLoginSecure=$secure; sessionid=$sessionId")
            .post(requestBody).build()
        client.newCall(request).execute().use { response ->
            SteamChatDiagnostics.info("upload.$stage.http", "status=${response.code}")
            if (stage == "cancel") {
                if (!response.isSuccessful) throw SteamChatFailure("ATTACHMENT_UPLOAD_FAILED")
                return@use JSONObject()
            }
            val source = response.body.source()
            if (source.request(MAX_RESPONSE + 1)) throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
            val raw = source.readUtf8()
            val payload = runCatching { JSONObject(raw) }.getOrNull()
            // Read Steam's JSON even for HTTP 400; the shared API helper discards that body.
            val result = successCode(payload)
            SteamChatDiagnostics.info("upload.$stage.result", "http=${response.code} result=${result ?: -1}")
            if (response.code == 429) throw SteamChatFailure("RATE_LIMITED")
            if (response.code == 401 || response.code == 403) throw SteamChatFailure("AUTH_EXPIRED")
            if (payload != null && result != null && result != 1) requireSuccess(payload, stage, response.code)
            if (!response.isSuccessful) {
                throw SteamChatFailure(when (response.code) {
                    400 -> if (stage == "begin") "ATTACHMENT_SESSION_REJECTED" else "ATTACHMENT_REJECTED"
                    in 401..499 -> "ATTACHMENT_REJECTED"
                    else -> "ATTACHMENT_UPLOAD_FAILED"
                })
            }
            if (payload == null || result == null) throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
            repository.checkAccount()
            payload
        }
    }

    private fun put(reservation: JSONObject, bytes: ByteArray, mime: String, progress: (Int) -> Unit) =
        SteamChatDiagnostics.trace("upload.cloud") {
            if (reservation.optInt("use_https") != 1 && !reservation.optBoolean("use_https")) {
                throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
            }
            val url = "https://${reservation.optString("url_host")}${reservation.optString("url_path")}".toHttpUrlOrNull()
                ?: throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
            val allowed = url.host == "steamcloud-ugc.storage.googleapis.com" || CLOUD_DOMAINS.any {
                url.host == it || url.host.endsWith(".$it")
            }
            if (!allowed || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) {
                throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
            }
            val headers = reservation.optJSONArray("request_headers") ?: throw SteamChatFailure("ATTACHMENT_RESPONSE_INVALID")
            val request = Request.Builder().url(url)
            var contentType = mime.toMediaType()
            for (index in 0 until headers.length()) {
                val header = headers.getJSONObject(index)
                val name = header.getString("name")
                val value = header.getString("value")
                if (name.equals("Content-Type", ignoreCase = true)) contentType = value.toMediaType()
                // Only the reservation's credentials are allowed here, never the account Cookie.
                if (!name.equals("Host", ignoreCase = true) && !name.equals("Content-Length", ignoreCase = true)) {
                    request.addHeader(name, value)
                }
            }
            val uploadBody = object : RequestBody() {
                override fun contentType() = contentType
                override fun contentLength() = bytes.size.toLong()
                override fun writeTo(sink: BufferedSink) {
                    var offset = 0
                    while (offset < bytes.size) {
                        repository.checkAccount()
                        val count = minOf(64 * 1024, bytes.size - offset)
                        sink.write(bytes, offset, count)
                        offset += count
                        progress((offset.toLong() * 95 / bytes.size.coerceAtLeast(1)).toInt())
                    }
                }
            }
            repository.checkAccount()
            client.newCall(request.put(uploadBody).build()).execute().use { response ->
                SteamChatDiagnostics.info("upload.cloud.http", "status=${response.code}")
                if (!response.isSuccessful) throw SteamChatFailure("ATTACHMENT_UPLOAD_FAILED")
            }
        }

    private fun refreshSession() = SteamChatDiagnostics.trace("upload.session.refresh") {
        refreshAttempted = true
        repository.checkAccount()
        val root = SteamSessionData.json.value?.let(::JSONObject) ?: throw SteamChatFailure("AUTH_EXPIRED")
        if (root.optString("activeSteamID") != repository.account.id) throw SteamChatFailure("ACCOUNT_CHANGED")
        val refresh = root.optJSONObject("userContexts")?.optJSONObject(repository.account.id)?.optString("refreshToken").orEmpty()
        if (refresh.isBlank()) throw SteamChatFailure("AUTH_EXPIRED")
        // renewal_type=None mints an upload access token without rotating Steam's refresh token.
        val encoded = Base64.getEncoder().encodeToString(P.Writer().text(1, refresh)
            .fixed64(2, repository.account.id.toLong()).number(3, 0).build())
        val request = Request.Builder().url("https://api.steampowered.com/IAuthenticationService/GenerateAccessTokenForApp/v1/")
            .header("User-Agent", "okhttp/4.9.2").header("Accept", "application/json, text/plain, */*")
            .header("Cookie", "mobileClient=android; mobileClientVersion=777777 3.6.4")
            .post(FormBody.Builder().add("input_protobuf_encoded", encoded).build()).build()
        client.newCall(request).execute().use { response ->
            val result = response.header("x-eresult")?.toIntOrNull()
            SteamChatDiagnostics.info("upload.session.refresh.http", "status=${response.code} result=${result ?: -1}")
            if (response.code == 429 || result == 84) throw SteamChatFailure("RATE_LIMITED")
            if (!response.isSuccessful || result != null && result != 1) throw SteamChatFailure("AUTH_EXPIRED")
            val source = response.body.source()
            if (source.request(MAX_RESPONSE + 1)) throw SteamChatFailure("AUTH_EXPIRED")
            val token = P.read(source.readByteArray()).text(1)
            val payload = claims(token) ?: throw SteamChatFailure("AUTH_EXPIRED")
            if (payload.optString("sub") != repository.account.id || payload.optLong("exp") <= System.currentTimeMillis() / 1000) {
                throw SteamChatFailure("AUTH_EXPIRED")
            }
            repository.checkAccount()
            // Keep this credential local to this upload; never rewrite the host or CM session.
            accessToken = token
        }
    }

    private fun requireSuccess(payload: JSONObject, stage: String, status: Int) {
        val code = successCode(payload)
        if (code == 1) return
        SteamChatDiagnostics.info("upload.$stage.rejected", "http=$status result=${code ?: -1}")
        val failure = when (code) {
            5, 15, 21, 65 -> "AUTH_EXPIRED"
            25, 84 -> "RATE_LIMITED"
            8, 9, 11, 13, 20, 50, 112 -> "ATTACHMENT_REJECTED"
            else -> if (status == 400 && stage == "begin") "ATTACHMENT_SESSION_REJECTED" else "ATTACHMENT_REJECTED"
        }
        // Log fixed categories/codes only. Response messages can contain file names or signed URLs.
        throw SteamChatFailure(failure)
    }

    companion object {
        private const val MAX_RESPONSE = 1024L * 1024
        private val CLOUD_DOMAINS = setOf("steamcontent.com", "steamusercontent.com", "amazonaws.com", "steampowered.com")
        private val client = SteamChatHttp.client.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).callTimeout(90, TimeUnit.SECONDS)
            // A commit must never be replayed after an ambiguous network failure.
            .retryOnConnectionFailure(false).build()

        private fun randomHex() = UUID.randomUUID().toString().replace("-", "")
        private fun successCode(payload: JSONObject?): Int? = when (val value = payload?.opt("success")) {
            is Boolean -> if (value) 1 else 0
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: when (value.lowercase()) { "true" -> 1; "false" -> 0; else -> null }
            else -> null
        }

        private fun claims(token: String): JSONObject? = runCatching {
            val payload = token.split('.').getOrNull(1) ?: return@runCatching null
            JSONObject(Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8))
        }.getOrNull()
    }
}
