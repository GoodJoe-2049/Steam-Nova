package steam.nova.app.features.chat.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Main-thread owned, call-local platform WebRTC engine. Never changes global WebView settings. */
internal class SteamChatVoiceMedia(private val context: Context, private val callback: (String, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var view: WebView? = null
    private var stopped = false
    var userAgent: String = ""
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val web = WebView(context)
        view = web // Retain immediately so a partially failed setup can still be destroyed.
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
        }
        userAgent = web.settings.userAgentString
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        web.addJavascriptInterface(Bridge(), "NovaVoiceBridge")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                stop()
                callback("error", "renderer")
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (!stopped && request.origin.toString().trimEnd('/') == "https://steamcommunity.com" &&
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources &&
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else request.deny()
            }
        }
        web.loadDataWithBaseURL("https://steamcommunity.com/chat/voice/", SteamChatVoicePage.html, "text/html", "UTF-8", null)
    }

    fun remote(description: String, version: String = "") {
        require(description.length <= 512 * 1024)
        evaluate("remote(${JSONObject.quote(description)},${JSONObject.quote(version)})")
    }
    fun mute(microphone: Boolean, output: Boolean) = evaluate("mute($microphone,$output)")
    private fun evaluate(call: String) { if (!stopped) view?.evaluateJavascript("window.novaVoice && window.novaVoice.$call", null) }
    fun stop() {
        stopped = true
        view?.let { web ->
            runCatching { web.evaluateJavascript("window.novaVoice && window.novaVoice.stop()", null) }
            runCatching { web.removeJavascriptInterface("NovaVoiceBridge"); web.stopLoading(); web.destroy() }
        }
        view = null
    }
    private inner class Bridge {
        // Invoked by the local media page through WebView's JavaScript bridge.
        @Suppress("unused")
        @JavascriptInterface
        fun event(kind: String, value: String) {
            if (value.length > 512 * 1024 || kind !in setOf("offer", "ack", "ice", "error", "stats")) return
            main.post { if (!stopped) callback(kind, value) }
        }
    }
}
