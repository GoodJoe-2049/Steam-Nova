package steam.nova.app.features.chat.linkpreview

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import steam.nova.app.core.ModuleLocalization
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import java.util.Locale

internal object SteamChatLinkPreviewRegistry {
    private const val DIRECTORY = "chat_link_preview"
    private data class Route(val path: Regex, val idGroup: Int, val idQuery: String)
    private data class ContentType(val json: JSONObject, val id: Regex, val routes: List<Route>)
    private data class Provider(val json: JSONObject, val hosts: List<String>, val types: List<ContentType>)
    @Volatile private var cached: List<Provider>? = null

    data class Embed(
        val provider: String,
        val kind: String,
        val src: String,
        val origin: String,
        val layout: JSONObject,
        val permissions: List<String>,
        val externalSchemes: List<String>,
        val pauseMessage: String?,
    )

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }

    private fun providers(context: Context): List<Provider> = cached ?: synchronized(this) {
        cached ?: runCatching {
            val assets = ModuleLocalization.resources(context).assets
            assets.list(DIRECTORY).orEmpty().filter { it.endsWith(".json") }.sorted().mapNotNull { file ->
                runCatching {
                    val json = assets.open("$DIRECTORY/$file").bufferedReader().use { JSONObject(it.readText()) }
                    require(json.getInt("version") == 1)
                    require(json.getString("provider").matches(Regex("[a-z0-9_-]+")))
                    require(json.getString("name").isNotBlank())
                    val hosts = json.getJSONArray("hosts").strings()
                    require(hosts.isNotEmpty() && hosts.all { it.matches(Regex("[a-z0-9.-]+")) })
                    require(json.optJSONArray("externalSchemes")?.strings().orEmpty().all { it.matches(Regex("[a-z][a-z0-9+.-]*")) && it !in setOf("javascript", "file", "content", "intent", "data") })
                    val types = json.getJSONArray("types")
                    Provider(json, hosts, (0 until types.length()).map { index ->
                        val type = types.getJSONObject(index)
                        require(type.getString("kind").matches(Regex("[a-z0-9_-]+")))
                        require(type.getString("canonicalPath").startsWith("/") && !type.getString("canonicalPath").startsWith("//"))
                        val embed = type.getJSONObject("embed")
                        val origin = embed.getString("origin").toUri()
                        require(origin.scheme == "https" && origin.host != null && origin.userInfo == null)
                        require(origin.port == -1 && origin.path.isNullOrEmpty() && origin.query == null && origin.fragment == null)
                        require(embed.getString("path").startsWith("/") && !embed.getString("path").startsWith("//"))
                        val layout = embed.getJSONObject("layout")
                        when (layout.getString("mode")) {
                            "fixedHeight" -> require(layout.getInt("height") in 80..800)
                            "aspectRatio" -> require(layout.getDouble("ratio") in 0.3..3.0)
                            else -> error("Unsupported embed layout")
                        }
                        require(layout.optInt("radius") in 0..32)
                        require(embed.getJSONArray("allow").strings().all { it.matches(Regex("[a-z-]+")) })
                        val routes = type.getJSONArray("routes")
                        ContentType(type, Regex(type.getString("idPattern")), (0 until routes.length()).map { routeIndex ->
                            val route = routes.getJSONObject(routeIndex)
                            Route(Regex(route.getString("path")), route.optInt("idGroup", 1), route.optString("idQuery"))
                        })
                    })
                }.onFailure { SteamChatDiagnostics.failure("linkPreview.config", it) }.getOrNull()
            }
        }.onFailure { SteamChatDiagnostics.failure("linkPreview.config", it) }.getOrDefault(emptyList()).also { cached = it }
    }

    fun configuration(context: Context): String = JSONArray(providers(context).map { it.json }).toString()

    fun resolve(context: Context, raw: String): Embed? {
        if (raw.length > 4096 || raw.any { it.isWhitespace() || it.isISOControl() || it == '\\' }) return null
        val uri = raw.toUri()
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.userInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.encodedPath.orEmpty()
        for (provider in providers(context)) {
            if (host !in provider.hosts) continue
            for (type in provider.types) for (route in type.routes) {
                val match = route.path.matchEntire(path) ?: continue
                val id = if (route.idQuery.isNotEmpty()) uri.getQueryParameter(route.idQuery)
                else match.groupValues.getOrNull(route.idGroup)
                if (id == null || !type.id.matches(id)) continue
                val embed = type.json.getJSONObject("embed")
                val origin = embed.getString("origin")
                val pathTemplate = embed.getString("path").replace("{id}", Uri.encode(id))
                val src = "$origin$pathTemplate".toUri().buildUpon()
                embed.optJSONObject("query")?.let { query ->
                    query.keys().forEach { name -> src.appendQueryParameter(name, query.getString(name)) }
                }
                return Embed(provider.json.getString("provider"), type.json.getString("kind"), src.toString(), origin,
                    embed.getJSONObject("layout"), embed.getJSONArray("allow").strings(),
                    provider.json.optJSONArray("externalSchemes")?.strings().orEmpty(),
                    provider.json.optJSONObject("pauseMessage")?.toString())
            }
        }
        return null
    }
}
