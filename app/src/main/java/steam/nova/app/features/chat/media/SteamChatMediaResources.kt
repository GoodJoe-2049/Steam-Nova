package steam.nova.app.features.chat.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import org.json.JSONObject
import steam.nova.app.R
import steam.nova.app.core.ModuleLocalization
import java.io.ByteArrayOutputStream

/** Original Nagram resources, resolved from the module APK rather than Steam's resource table. */
internal object SteamChatMediaResources {
    private val ids = mapOf(
        "back" to R.drawable.chat_media_ic_ab_back,
        "more" to R.drawable.chat_media_media_more,
        "forward" to R.drawable.chat_media_msg_forward,
        "headerForward" to R.drawable.chat_media_msg_header_share,
        "copy" to R.drawable.chat_media_msg_link,
        "save" to R.drawable.chat_media_msg_gallery,
        "download" to R.drawable.chat_media_msg_download,
        "share" to R.drawable.chat_media_msg_shareout,
        "delete" to R.drawable.chat_media_msg_delete,
        "close" to R.drawable.chat_media_msg_cancel,
        "mute" to R.drawable.chat_media_video_muted,
        "landscape" to R.drawable.chat_media_msg_maxvideo,
        "portrait" to R.drawable.chat_media_msg_minvideo,
        "circle" to R.drawable.chat_media_circle_big,
        "cancelBig" to R.drawable.chat_media_cancel_big,
        "loadBig" to R.drawable.chat_media_load_big,
        "preview" to R.drawable.chat_media_videopreview,
    )

    fun drawable(context: Context, name: String, tint: Boolean = true): Drawable =
        (if (name == "videoMore") SteamChatMediaPopupResources.drawable(context, popup = false)
        else checkNotNull(ResourcesCompat.getDrawable(ModuleLocalization.resources(context), ids.getValue(name), null))).mutate().also {
            if (tint) it.setTint(Color.WHITE)
        }

    @Volatile private var cached: String? = null
    @Volatile private var numbers: android.graphics.Typeface? = null
    fun numberTypeface(context: Context): android.graphics.Typeface = numbers ?: android.graphics.Typeface.createFromAsset(
        ModuleLocalization.resources(context).assets, "fonts/chat_media_num.otf"
    ).also { numbers = it }
    fun source(context: Context): String {
        cached?.let { return it }
        val data = JSONObject()
        (ids.keys + "videoMore").filter { it != "preview" && it != "circle" }.forEach { name ->
            val drawable = drawable(context, name)
            val bitmap = createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1))
            try {
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
                val bytes = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
                data.put(name, "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            } finally { bitmap.recycle() }
        }
        return "var chatMediaIcons = $data;\n".also { cached = it }
    }
}
