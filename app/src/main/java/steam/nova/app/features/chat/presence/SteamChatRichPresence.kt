package steam.nova.app.features.chat.presence

import android.content.res.Resources
import android.os.SystemClock
import steam.nova.app.core.ModuleLanguage
import steam.nova.app.core.ModuleLocalization
import steam.nova.app.features.chat.protocol.SteamChatProtocol as P
import java.util.Locale

/** Game-owned Steam Rich Presence templates; cache access is protected by the presence lock. */
internal class SteamChatRichPresence(private val load: (Long, String) -> P.Fields) {
    data class Request(val appId: Long, val language: String)
    data class Catalog(val updatedAt: Long, val languages: Map<String, Map<String, String>>)

    private val catalogs = mutableMapOf<Request, Catalog>()
    private val queried = mutableMapOf<Request, Long>()
    private val pending = mutableSetOf<Request>()

    fun reset() {
        catalogs.clear(); queried.clear(); pending.clear()
    }

    fun requests(appIds: List<Long>): List<Request> {
        val now = SystemClock.elapsedRealtime()
        val language = language()
        return appIds.asSequence().distinct().filter { it > 0 }.map { Request(it, language) }.filter { request ->
            request !in pending && now - (queried[request] ?: -30_000L) >= 30_000L &&
                    catalogs[request]?.let { now - it.updatedAt < 7_200_000L } != true
        }.onEach { request -> queried[request] = now; pending.add(request) }.toList()
    }

    /** Only this method performs I/O; the caller runs it on the existing presence worker. */
    fun fetch(request: Request): Catalog {
        val response = load(request.appId, request.language)
        require(response.number(1) == request.appId) { "Rich Presence app mismatch" }
        val languages = response.messages(2).associate { list ->
            list.text(1).lowercase(Locale.ROOT) to list.messages(2).associate { token ->
                token.text(1).lowercase(Locale.ROOT) to token.text(2)
            }
        }
        return Catalog(SystemClock.elapsedRealtime(), languages)
    }

    fun complete(request: Request, catalog: Catalog?) {
        pending.remove(request)
        if (catalog != null) catalogs[request] = catalog
    }

    fun text(appId: Long, values: Map<String, String>): String {
        val token = values["steam_display"] ?: return ""
        val language = language()
        val catalog = catalogs[Request(appId, language)] ?: return ""
        return localize(token, values, catalog.languages[language].orEmpty(), catalog.languages["english"].orEmpty())
    }

    companion object {
        private val references = Regex("\\{[A-Za-z0-9_%#:\\[\\]\\\\^`]+\\}")
        private val parameters = Regex("%[A-Za-z0-9_:]+%")

        private fun language(): String = when (ModuleLocalization.language) {
            ModuleLanguage.SimplifiedChinese -> "schinese"
            ModuleLanguage.English -> "english"
            ModuleLanguage.System -> if (Resources.getSystem().configuration.locales[0].language == "zh") "schinese" else "english"
        }

        /** Same token/parameter order and English fallback as Steam Chat's Localize/SubstituteParams. */
        private fun localize(token: String, values: Map<String, String>, primary: Map<String, String>, fallback: Map<String, String>): String {
            var remaining = 512
            fun substitute(source: String): String {
                var result = source
                for (match in parameters.findAll(source)) {
                    val value = values[match.value.substring(1, match.value.length - 1).lowercase(Locale.ROOT)] ?: continue
                    result = result.replaceFirst(match.value, value)
                    if (result.length > 16_384) return ""
                }
                return result
            }
            fun resolve(name: String, depth: Int): String {
                if (!name.startsWith('#') || depth > 16 || --remaining < 0) return ""
                val key = name.lowercase(Locale.ROOT)
                var result = primary[key].orEmpty().ifEmpty { fallback[key].orEmpty() }
                if (result.isEmpty() || result.length > 16_384) return ""
                for (match in references.findAll(result).toList()) {
                    val nested = resolve(substitute(match.value.substring(1, match.value.length - 1)), depth + 1)
                    if (nested.isEmpty()) return ""
                    result = result.replaceFirst(match.value, nested)
                    if (result.length > 16_384) return ""
                }
                return substitute(result)
            }
            return resolve(token, 0)
        }
    }
}
