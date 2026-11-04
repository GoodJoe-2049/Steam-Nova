package steam.nova.app.features.guest

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import steam.nova.app.core.SteamBundleCompatibility
import steam.nova.app.features.ui.settings.SteamModuleSettings
import steam.nova.app.features.chat.bridge.SteamChatBridge
import io.github.libxposed.api.XposedModule
import steam.nova.app.features.profile.customization.SteamProfileCustomizationBridge
import java.io.File
import java.security.MessageDigest

/** Installs the guest navigation policy before Steam registers its React components. */
internal class SteamGuestBrowsingFeature(
    private val context: () -> Context?,
    private val emit: (Int, String) -> Unit,
) {
    private var prepared = false
    private var bootstrap: File? = null

    fun install(module: XposedModule, loader: ClassLoader) {
        runCatching { SteamProfileCustomizationBridge.install(module, loader, context) }
            .onFailure { emit(Log.WARN, "Profile customization bridge unavailable: ${it.javaClass.simpleName}") }
        installBootstrapHook(module, loader)
    }

    private fun installBootstrapHook(module: XposedModule, loader: ClassLoader) {
        val instance = Class.forName("com.facebook.react.runtime.ReactInstance", false, loader)
        val bundleLoader = Class.forName("com.facebook.react.bridge.JSBundleLoader", false, loader)
        val assetLoader = Class.forName("com.facebook.react.bridge.JSBundleLoader$1", false, loader)
        val assetUrl = assetLoader.getDeclaredField($$"val$assetUrl").apply { isAccessible = true }
        val load = instance.getDeclaredMethod("loadJSBundle", bundleLoader)
        val loadFile = instance.getDeclaredMethod("loadJSBundleFromFile", String::class.java, String::class.java)
            .apply { isAccessible = true }
        module.hook(load).setId("millennium.guest.bootstrap").intercept { chain ->
            runCatching {
                val sourceLoader = chain.args.firstOrNull()
                // The APK hash cannot validate an Expo-downloaded bundle or another source.
                if (assetLoader.isInstance(sourceLoader) && assetUrl.get(sourceLoader) == "assets://index.android.bundle") {
                    prepare()?.let { file ->
                        // React queues both scripts on its JS runtime before starting the surface.
                        loadFile.invoke(chain.thisObject, file.absolutePath, file.absolutePath)
                    }
                } else {
                    emit(Log.WARN, "Guest browsing unavailable for this bundle source; native navigation retained")
                }
            }.onFailure {
                emit(Log.WARN, "Steam JS bootstrap failed: ${it.javaClass.simpleName}")
            }
            chain.proceed()
        }
    }

    @Synchronized
    private fun prepare(): File? {
        if (prepared) return bootstrap
        val app = context() ?: return null
        prepared = true
        // Module IDs are private to this bundle. Never apply them to a different Steam build.
        val digest = MessageDigest.getInstance("SHA-256")
        app.assets.open("index.android.bundle").use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val bundle = SteamBundleCompatibility.find(hash)
        if (bundle == null) {
            emit(Log.WARN, "Steam JS bootstrap skipped: unsupported bundle sha256=$hash")
            return null
        }
        val file = File(app.codeCacheDir, "millennium-guest-bootstrap.js")
        val script = SteamGuestBrowsingScript.source(
            context = app,
            bundle = bundle,
            defaultRoute = SteamModuleSettings.steamGuardDefaultPage(app).routeName,
            nextCodeEnabled = SteamModuleSettings.isSteamGuardNextCodeEnabled(app),
            profileCustomizationBridgePrefix = SteamProfileCustomizationBridge.prefix.takeIf {
                SteamProfileCustomizationBridge.available && SteamModuleSettings.isProfileCustomizationEnabled(app)
            },
            betterProfileEnabled = SteamModuleSettings.isBetterProfileEnabled(app),
            animatedStoreBannerEnabled = SteamModuleSettings.isAnimatedStoreBannerEnabled(app),
            libraryPinchZoomEnabled = SteamModuleSettings.isSteamLibraryPinchZoomEnabled(app),
            chatBridgePrefix = SteamChatBridge.prefix.takeIf { SteamChatBridge.available && SteamModuleSettings.isBuiltInChatEnabled(app) },
        )
        val bytes = script.toByteArray(Charsets.UTF_8)
        if (!file.isFile || file.readText() != script) {
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        }
        bootstrap = file
        emit(Log.INFO, "Steam JS bootstrap prepared: version=${bundle.version} sha256=$hash")
        return file
    }
}
