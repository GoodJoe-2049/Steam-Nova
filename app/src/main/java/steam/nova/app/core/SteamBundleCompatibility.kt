package steam.nova.app.core

/** Verified APK bundles only. Adapter IDs use the 3.10.11 numbering as stable keys. */
internal object SteamBundleCompatibility {
    class Profile(val version: String, private val moduleIds: Map<Int, Int>) {
        fun source(): String {
            val entries = moduleIds.entries.joinToString(",") { (adapter, host) -> "$adapter:$host" }
            return """
    var steamModuleIds = {$entries};
    function steamModuleId(id) {
        return Object.prototype.hasOwnProperty.call(steamModuleIds, id) ? steamModuleIds[id] : id;
    }
""".trimIndent()
        }
    }

    private val steam31011 = Profile("3.10.11", emptyMap())
    private val steam31012 = Profile(
        "3.10.12",
        mapOf(
            16 to 16,       // React Native
            115 to 115,     // React
            263 to 263,     // JSX runtime
            563 to 563,     // Gesture handlers
            962 to 962,     // Steam enums
            1136 to 1148,   // Authentication state
            1137 to 1149,   // AsyncStorage
            1162 to 1174,   // Steam Guard store
            1366 to 1411,   // React Navigation
            1450 to 1495,   // Steam localization
            1476 to 1513,   // Root / main tabs navigator
            1477 to 1514,   // Stack navigator
            1486 to 1523,   // Safe area hooks
            1589 to 1626,   // Text
            1590 to 1627,   // Themed components
            1611 to 1648,   // SVG components
            1750 to 1787,   // Expo Image
            1821 to 1849,   // Menu icons
            1830 to 1858,   // Controls
            1833 to 1861,   // Icons
            1836 to 1864,   // Box / Column
            1864 to 1892,   // Persona hooks
            1888 to 1916,   // Avatar image
            1892 to 1920,   // Tab bar
            1894 to 1922,   // Notifications
            2117 to 2058,   // Parental policy
            2125 to 2064,   // Parental settings hooks
            2126 to 2065,   // Stored queries
            2127 to 2066,   // Parental navigation
            2135 to 2074,   // Link handling
            2176 to 2136,   // Steam WebView
            2210 to 2181,   // WebView screen / profile header
            2211 to 2182,   // Store header
            2212 to 2183,   // Shared header
            2352 to 2328,   // ScreenWithHeader
            2412 to 2359,   // Guard navigator
            2413 to 2360,   // CircleCodeScreen
            2416 to 2363,   // Guard account selector
            2455 to 2402,   // Main menu
            3459 to 2574,   // Home tab preference
            3466 to 2581,   // Library
        ),
    )

    // Both supplied bundles use Hermes bytecode version 96. Version names alone
    // cannot identify private Metro IDs (including a different release channel).
    fun find(sha256: String): Profile? = when (sha256) {
        "f877cc35eff2a2a833c6330e8650b296369e2ccb4e07984ca6559b2daa4090a4" -> steam31011
        "dc3bc61ad28cb1473b71f72063b0c33b2ce0c8cf799b83f12d7b0d76dc0773fc" -> steam31012
        else -> null
    }
}
