package steam.nova.app.features.chat.network

import steam.nova.app.features.steamguard.data.SteamSessionData
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import steam.nova.app.features.chat.protocol.SteamChatProtocol as P
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

internal class SteamChatFailure(val code: String) : Exception(code)

internal data class SteamChatAccount(val id: String, val token: String) {
    companion object {
        fun current(expected: String): SteamChatAccount {
            val root = SteamSessionData.json.value?.let(::JSONObject) ?: throw SteamChatFailure("LOGIN_REQUIRED")
            val id = root.optString("activeSteamID")
            if (id != expected || !id.matches(Regex("7656119\\d{10}"))) throw SteamChatFailure("ACCOUNT_CHANGED")
            val token = root.optJSONObject("userContexts")?.optJSONObject(id)?.optString("accessToken").orEmpty()
            if (token.isBlank()) throw SteamChatFailure("LOGIN_REQUIRED")
            return SteamChatAccount(id, token)
        }
    }
}

/** Only Steam-owned authenticated endpoints are exposed; requests and credentials are never logged. */
internal object SteamChatHttp {
    val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun json(request: Request): JSONObject {
        val purpose = when (request.url.encodedPath) {
            "/chat/clientjstoken" -> "token"
            "/ISteamDirectory/GetCMListForConnect/v1/" -> "directory"
            else -> "api"
        }
        return SteamChatDiagnostics.trace("http.$purpose") {
            client.newCall(request).execute().use { response ->
                SteamChatDiagnostics.info("http.$purpose.status", "status=${response.code}")
                if (response.code == 401 || response.code == 403) throw SteamChatFailure("AUTH_EXPIRED")
                if (response.code == 429) throw SteamChatFailure("RATE_LIMITED")
                if (!response.isSuccessful) throw SteamChatFailure("NETWORK_ERROR")
                val body = response.body
                require(body.contentLength() <= 8 * 1024 * 1024)
                val source = body.source()
                if (source.request(8L * 1024 * 1024 + 1)) throw SteamChatFailure("RESPONSE_TOO_LARGE")
                JSONObject(source.readUtf8())
            }
        }
    }

    fun api(account: SteamChatAccount, path: String, params: Map<String, String> = emptyMap()): JSONObject {
        require(path.startsWith("/ISteamUserOAuth/"))
        val url = "https://api.steampowered.com$path".toHttpUrl().newBuilder()
            .addQueryParameter("access_token", account.token)
        params.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return json(Request.Builder().url(url.build()).build())
    }

    fun chatToken(account: SteamChatAccount): String {
        val result = json(Request.Builder().url("https://steamcommunity.com/chat/clientjstoken")
            .header("Cookie", cookie(account))
            .header("Referer", "https://steamcommunity.com/chat/").build())
        if (!result.optBoolean("logged_in") || result.optString("steamid") != account.id) {
            SteamChatDiagnostics.info("http.token.rejected", "loggedIn=${result.optBoolean("logged_in")} accountMatch=${result.optString("steamid") == account.id}")
            throw SteamChatFailure("AUTH_EXPIRED")
        }
        SteamChatDiagnostics.info("http.token.validated", "tokenPresent=${result.optString("token").isNotBlank()}")
        return result.optString("token").takeIf { it.isNotBlank() } ?: throw SteamChatFailure("AUTH_EXPIRED")
    }

    /** Private-chat history and session queries. Read acknowledgements use the live CM connection. */
    fun friendMessages(account: SteamChatAccount, method: String, body: P.Writer): P.Fields {
        require(method in setOf("GetActiveMessageSessions", "GetRecentMessages"))
        return protobuf(account, "IFriendMessagesService", method, body)
    }

    fun nicknames(account: SteamChatAccount): P.Fields =
        protobuf(account, "IPlayerService", "GetNicknameList", P.Writer())

    private fun protobuf(account: SteamChatAccount, service: String, method: String, body: P.Writer): P.Fields {
        val encoded = Base64.getEncoder().encodeToString(body.build())
        val url = "https://api.steampowered.com/$service/$method/v1/".toHttpUrl().newBuilder()
            .addQueryParameter("access_token", account.token)
        val request = Request.Builder().url(url.addQueryParameter("input_protobuf_encoded", encoded).build())
        return SteamChatDiagnostics.trace("http.$service.$method") {
            client.newCall(request.build()).execute().use { response ->
                val result = response.header("x-eresult")?.toIntOrNull()
                SteamChatDiagnostics.info("http.$service.$method.status", "status=${response.code} result=${result ?: 0}")
                if (response.code == 401 || response.code == 403 || result == 5 || result == 15) throw SteamChatFailure("AUTH_EXPIRED")
                if (response.code == 429 || result == 84 || result == 25) throw SteamChatFailure("RATE_LIMITED")
                if (!response.isSuccessful) throw SteamChatFailure("NETWORK_ERROR")
                if (result != null && result != 1) throw SteamChatFailure("REQUEST_REJECTED")
                val source = response.body.source()
                if (source.request(8L * 1024 * 1024 + 1)) throw SteamChatFailure("RESPONSE_TOO_LARGE")
                P.read(source.readByteArray())
            }
        }
    }

    fun cookie(account: SteamChatAccount): String = "steamLoginSecure=${account.id}%7C%7C${account.token}"

    fun servers(): List<String> {
        val result = json(Request.Builder()
            .url("https://api.steampowered.com/ISteamDirectory/GetCMListForConnect/v1/?cellid=0&cmtype=websockets")
            .build()).optJSONObject("response") ?: throw SteamChatFailure("NETWORK_ERROR")
        val list = result.optJSONArray("serverlist") ?: throw SteamChatFailure("NETWORK_ERROR")
        return (0 until list.length()).asSequence().mapNotNull { list.optJSONObject(it) }
            .filter { it.optString("type") == "websockets" && it.optString("realm") == "steamglobal" }
            .sortedBy { it.optDouble("wtd_load", 999.0) }
            .mapNotNull { item ->
                val raw = item.optString("endpoint")
                runCatching {
                    val url = "https://$raw/".toHttpUrl()
                    require(url.port == 443 && url.encodedPath == "/" && url.username.isEmpty() && url.password.isEmpty())
                    require(url.host.endsWith(".steamserver.net") || url.host.endsWith(".steampowered.com"))
                    "${url.host}:${url.port}"
                }.getOrNull()
            }.distinct().take(3).toList().ifEmpty { throw SteamChatFailure("NETWORK_ERROR") }
    }
}
