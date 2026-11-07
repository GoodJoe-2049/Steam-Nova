package steam.nova.app.features.chat.media.viewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.RippleDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.widget.*
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.min
import org.json.JSONObject
import steam.nova.app.features.chat.media.SteamChatMediaResources
import steam.nova.app.features.chat.media.SteamChatMediaTransfer
import steam.nova.app.features.chat.media.viewer.AndroidUtilities.dp
import steam.nova.app.features.chat.network.SteamChatAccount

/**
 * Native host for Nagram's ordinary photo/video viewer branches. Steam owns messages and files;
 * Android MediaPlayer is the decoder adapter. No HTML/native-player default controls are used.
 */
internal class SteamChatMediaViewer(
    private val activity: Activity,
    val account: String,
    private val transfer: SteamChatMediaTransfer,
    args: JSONObject,
    private val event: (JSONObject) -> Unit,
) : ComponentDialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor {
        Thread(it, "SteamNovaMediaViewer").apply { isDaemon = true }
    }
    private val neighborWorker =
        Executors.newFixedThreadPool(2) {
            Thread(it, "SteamNovaMediaNeighbor").apply { isDaemon = true }
        }
    private val items = args.getJSONArray("items")
    private var index = args.optInt("index").coerceIn(0, items.length() - 1)
    private val labels = args.getJSONObject("labels")
    private var media: JSONObject = items.getJSONObject(index)
    private val openingIndex = index
    private val transition = SteamChatMediaTransition(activity, media.optString("sourceTag"))
    private val root = FrameLayout(activity)
    private val stage = Stage(activity)
    private val header = FrameLayout(activity)
    private val name = TextView(activity)
    private val date = TextView(activity)
    private val count = PhotoCountView(activity)
    private val caption = TextView(activity)
    private val captionContainer = FrameLayout(activity)
    private val captionScroll =
        object : CaptionScrollView(activity, caption, captionContainer) {
            override fun onScrollStart() {
                main.removeCallbacks(hideBars)
            }

            override fun onScrollEnd() {
                scheduleHide()
            }
        }
    private val preview = SteamChatVideoPreview(activity)
    private val controls = SteamChatVideoControls(activity, ::seek, ::preview, ::rotate)
    private val photoProgress =
        PhotoProgressView(
            stage,
            SteamChatMediaResources.drawable(activity, "circle", false),
            SteamChatMediaResources.drawable(activity, "cancelBig", false),
            SteamChatMediaResources.drawable(activity, "loadBig", false),
        )
    private val forwardIcon = VideoForwardDrawable(false)
    private val speedIcon =
        SeekSpeedDrawable(activity, { stage.invalidate() }, isRound = false, isPiP = false)
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var desiredPlaying = true
    private var resumed = true
    private var buffering = false
    private var closed = false
    private var closing = false
    private var generation = 0
    private var transferPending = false
    private var status = JSONObject()
    private var local: File? = null
    private var bars = true
    private var barsProgress = 1f
    private var barsAnimator: ValueAnimator? = null
    private var menu: PopupWindow? = null
    private var resumeAfterFocus = false
    private var resumeAfterAction = false
    private val originalOrientation = activity.requestedOrientation
    private var audioFocus = false
    private val audio = requireNotNull(activity.getSystemService(AudioManager::class.java))
    private val focusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        resumeAfterFocus =
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT && playing()
                        audioFocus = false
                        pause()
                    }
                    AudioManager.AUDIOFOCUS_GAIN ->
                        if (resumeAfterFocus) {
                            resumeAfterFocus = false
                            play()
                        }
                }
            }
            .build()
    private val gestures: SteamChatMediaGestures =
        SteamChatMediaGestures(
            activity,
            object : SteamChatMediaGestures.Host {
                override val width
                    get() = stage.width

                override val height
                    get() = stage.height

                override val mediaWidth
                    get() = stage.fittedWidth

                override val mediaHeight
                    get() = stage.fittedHeight

                override fun transform(scale: Float, x: Float, y: Float) {
                    stage.zoom = scale
                    stage.offsetX = x
                    stage.offsetY = y
                    // PhotoViewer.onDraw: drag-to-close background fades to a minimum alpha of 127.
                    if (!transition.running && !closing) {
                        val maximum = (stage.width / 4f).coerceAtLeast(1f)
                        val alpha =
                            if (scale == 1f)
                                maxOf(127, (255 * (1f - min(abs(y), maximum) / maximum)).toInt())
                            else 255
                        root.setBackgroundColor(alpha shl 24)
                    }
                    stage.invalidate()
                    stage.transformVideo()
                }

                override fun tap(x: Float, y: Float): Boolean {
                    singleTap(x, y)
                    return true
                }

                override fun centralTap(x: Float, y: Float): Boolean {
                    if (
                        (video() || status.optString("state") == "downloading") &&
                            abs(x - stage.width / 2f) <= dp(50f) &&
                            abs(y - stage.height / 2f) <= dp(50f) &&
                            (bars || !playing())
                    )
                        singleTap(x, y)
                    return true
                    return false
                }

                override fun doubleTapVideo(x: Float): Boolean = doubleTap(x)

                override fun hideBars() = toggleBars(false)

                override fun close() = closeViewer()

                override fun hasPage(direction: Int) = index + direction in 0 until items.length()

                override fun page(direction: Int) {
                    index += direction
                    load()
                }

                override fun longPress(x: Float) {
                    if (prepared && duration() >= 8000) {
                        main.removeCallbacks(hideBars)
                        rewinder.start(x, stage.width, duration() > 180000)
                    }
                }

                override fun rewindMove(x: Float): Boolean {
                    if (!rewinder.active) return false
                    rewinder.move(x)
                    return true
                }

                override fun rewindEnd() = rewinder.stop()

                override fun schedule(action: Runnable, delay: Long) {
                    main.postDelayed(action, delay)
                }

                override fun cancel(action: Runnable) {
                    main.removeCallbacks(action)
                }
            },
        )
    private val rewinder: SteamChatVideoRewind =
        SteamChatVideoRewind(
            object : SteamChatVideoRewind.Player {
                override fun position() = this@SteamChatMediaViewer.position()

                override fun duration() = this@SteamChatMediaViewer.duration()

                override fun playing() = this@SteamChatMediaViewer.playing()

                override fun seek(ms: Long) {
                    player?.seekTo(ms.coerceIn(0, duration()), MediaPlayer.SEEK_CLOSEST)
                }

                override fun speed(value: Float) {
                    runCatching {
                        player?.let { it.playbackParams = it.playbackParams.setSpeed(value) }
                    }
                }

                override fun muted(value: Boolean) {
                    player?.setVolume(if (value) 0f else 1f, if (value) 0f else 1f)
                }

                override fun paused(value: Boolean) {
                    if (value) player?.pause() else if (resumed) player?.start()
                }

                override fun feedback(speed: Float, difference: Long, old: Boolean) {
                    if (old) {
                        forwardIcon.setOneShootAnimation(false)
                        forwardIcon.setLeftSide(speed < 0)
                        forwardIcon.setTime(abs(difference))
                        forwardIcon.startAnimation()
                    } else {
                        speedIcon.setSpeed(speed, true)
                        speedIcon.setShown(shown = true, animated = true)
                    }
                    stage.rewindSpeed = speed
                    stage.invalidate()
                }

                override fun ended() {
                    speedIcon.setShown(shown = false, animated = true)
                    stage.rewindSpeed = 0f
                    stage.invalidate()
                    scheduleHide()
                }
            }
        )
    private val hideBars = Runnable {
        if (
            playing() &&
                resumed &&
                menu == null &&
                !controls.seekBar.isDragging &&
                !buffering &&
                !rewinder.active
        )
            toggleBars(false)
    }
    private val tick =
        object : Runnable {
            override fun run() {
                if (closed) return
                if (runCatching { SteamChatAccount.current(account) }.isFailure) {
                    closeImmediately()
                    return
                }
                if (prepared) {
                    controls.update(position(), duration())
                    photoProgress.setBackgroundState(
                        if (playing()) PhotoProgressView.PROGRESS_PAUSE
                        else PhotoProgressView.PROGRESS_PLAY,
                        animated = false,
                        animateIcon = true,
                    )
                    stage.invalidate()
                }
                if (!transferPending && status.optString("state") == "downloading")
                    request("mediaStatus")
                main.postDelayed(this, 100)
            }
        }

    init {
        require(items.length() in 1..200)
        root.setBackgroundColor(Color.BLACK)
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))
        root.addView(header, FrameLayout.LayoutParams(-1, dp(56f), Gravity.TOP))
        header.setBackgroundColor(0x7f000000)
        val back = button("back", "chat_back") { closeViewer() }
        header.addView(back, FrameLayout.LayoutParams(dp(54f), dp(56f), Gravity.START))
        name.setTextColor(Color.WHITE)
        name.textSize = 18f
        name.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        name.isSingleLine = true
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        name.includeFontPadding = false
        date.setTextColor(Color.WHITE)
        date.textSize = 14f
        date.isSingleLine = true
        date.includeFontPadding = false
        header.addView(
            name,
            FrameLayout.LayoutParams(-1, dp(24f)).apply {
                leftMargin = dp(72f)
                rightMargin = dp(96f)
                topMargin = dp(6f)
            },
        )
        header.addView(
            date,
            FrameLayout.LayoutParams(-1, dp(21f)).apply {
                leftMargin = dp(72f)
                rightMargin = dp(96f)
                topMargin = dp(31f)
            },
        )
        header.addView(
            button("headerForward", "chat_media_forward") { action("forward") },
            FrameLayout.LayoutParams(dp(48f), dp(56f), Gravity.END).apply {
                rightMargin = dp(48f)
            },
        )
        val overflow = button("more", "chat_message_actions") { openMenu() }
        header.addView(overflow, FrameLayout.LayoutParams(dp(48f), dp(56f), Gravity.END))
        root.addView(count, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        caption.setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        caption.setTextColor(Color.WHITE)
        caption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f)
        caption.setLinkTextColor(0xff79c4fc.toInt())
        caption.highlightColor = 0x33ffffff
        caption.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        caption.setOnClickListener { captionScroll.smoothScrollBy(0, dp(64f)) }
        captionContainer.addView(caption, FrameLayout.LayoutParams(-1, -2))
        captionScroll.addView(captionContainer, FrameLayout.LayoutParams(-1, -2))
        root.addView(captionScroll, FrameLayout.LayoutParams(-1, -1, Gravity.BOTTOM))
        root.addView(controls, FrameLayout.LayoutParams(-1, dp(48f), Gravity.BOTTOM))
        root.addView(
            preview,
            FrameLayout.LayoutParams(dp(150f), dp(100f), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = dp(58f)
            },
        )
        preview.alpha = 0f
        preview.visibility = View.INVISIBLE
        stage.texture.surfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    surface?.release()
                    surface = Surface(texture)
                    player?.setSurface(surface)
                    openPlayer()
                }

                override fun onSurfaceTextureSizeChanged(
                    texture: SurfaceTexture,
                    width: Int,
                    height: Int,
                ) {
                    stage.transformVideo()
                }

                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    player?.setSurface(null)
                    surface?.release()
                    surface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
            }
        forwardIcon.setDelegate(
            object : VideoForwardDrawable.VideoForwardDrawableDelegate {
                override fun onAnimationEnd() = Unit

                override fun invalidate() {
                    stage.invalidate()
                }
            }
        )
        setContentView(root)
        setCanceledOnTouchOutside(false)
        setOnDismissListener { dispose() }
    }

    private fun label(key: String) = labels.optString(key, key)

    private fun button(icon: String, labelKey: String, clicked: () -> Unit) =
        ImageView(activity).apply {
            setImageDrawable(SteamChatMediaResources.drawable(context, icon))
            scaleType = ImageView.ScaleType.CENTER
            background = RippleDrawable(ColorStateList.valueOf(0x33ffffff), null, null)
            contentDescription = label(labelKey)
            setOnClickListener { clicked() }
        }

    override fun show() {
        super.show()
        window?.apply {
            setLayout(-1, -1)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            WindowCompat.getInsetsController(this, decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            // Keep controls outside system bars; the photo/video continues edge to edge.
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val top = systemBars.top
            val bottom = systemBars.bottom
            (header.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = top
                header.layoutParams = this
            }
            (count.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = top
                count.layoutParams = this
            }
            (controls.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bottom
                controls.layoutParams = this
            }
            (captionScroll.layoutParams as FrameLayout.LayoutParams).apply {
                topMargin = top + dp(56f)
                bottomMargin = bottom + if (video()) dp(48f) else 0
                captionScroll.layoutParams = this
            }
            (preview.layoutParams as FrameLayout.LayoutParams).apply {
                bottomMargin = bottom + dp(58f)
                preview.layoutParams = this
            }
            insets
        }
        root.requestApplyInsets()
        load()
        main.post(tick)
        root.post {
            if (closed || closing) return@post
            val ratio =
                media.optDouble("width", 1.0).toFloat() / media.optDouble("height", 1.0).toFloat()
            if (
                !transition.run(root, stage, stage.currentBitmap, true, ratio, ::transitionChrome) {
                    root.setBackgroundColor(Color.BLACK)
                }
            ) {
                // PhotoViewer's no-place-provider entry branch.
                root.alpha = 0f
                root.animate().alpha(1f).setDuration(220).start()
                header.translationY = -dp(32f).toFloat()
                header
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(150)
                    .setInterpolator(CubicBezierInterpolator.DEFAULT)
                    .start()
            }
        }
    }

    init {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = closeViewer()
            },
        )
    }

    private fun load() {
        if (index != openingIndex) transition.restoreSource()
        generation++
        transferPending = false
        rewinder.stop()
        releasePlayer()
        preview.animate().cancel()
        preview.visibility = View.INVISIBLE
        preview.clear()
        media = items.getJSONObject(index)
        local = null
        status = JSONObject()
        val neighbor = stage.neighborBitmap(media.optString("url"))
        stage.animation(null)
        stage.bitmap(neighbor)
        stage.clearNeighbors()
        stage.videoWidth = media.optInt("width", 1)
        stage.videoHeight = media.optInt("height", 1)
        name.text = media.optString("sender")
        date.text = media.optString("date")
        caption.text = media.optString("caption")
        captionScroll.visibility = if (caption.text.isEmpty()) View.GONE else View.VISIBLE
        captionScroll.reset()
        captionScroll.updateTopMargin()
        root.requestApplyInsets()
        count.set(index + 1, items.length())
        count.updateShow(items.length() > 1, true)
        controls.visibility = if (video()) View.VISIBLE else View.GONE
        stage.texture.visibility = View.INVISIBLE
        desiredPlaying = true
        gestures.reset()
        toggleBars(show = true, animated = false)
        photoProgress.setBackgroundState(
            PhotoProgressView.PROGRESS_LOAD,
            animated = false,
            animateIcon = false,
        )
        request("mediaInfo")
        preloadNeighbors()
    }

    // PhotoViewer keeps leftImage/centerImage/rightImage ready for its page drawing branches.
    private fun preloadNeighbors() {
        val ticket = generation
        for (direction in listOf(-1, 1)) {
            if (index + direction !in 0 until items.length()) continue
            val item = items.getJSONObject(index + direction)
            fun fetch(operation: String) {
                if (closed || closing || generation != ticket) return
                neighborWorker.execute {
                    val result = runCatching {
                        transfer.execute(
                            account,
                            JSONObject()
                                .put("operation", operation)
                                .put("url", item.getString("url"))
                                .put("kind", item.optString("kind")),
                        )
                    }
                        .getOrNull()
                    val video =
                        item.optString("kind") == "video" ||
                            result?.optString("mime")?.startsWith("video/") == true
                    val uri = result?.optString(if (video) "poster" else "uri").orEmpty()
                    val image =
                        if (uri.startsWith("file:"))
                            runCatching {
                                val path = uri.toUri().path
                                val bounds =
                                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(path, bounds)
                                var sample = 1
                                while (
                                    maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048
                                ) sample *= 2
                                BitmapFactory.decodeFile(
                                    path,
                                    BitmapFactory.Options().apply { inSampleSize = sample },
                                )
                            }
                                .getOrNull()
                        else null
                    main.post {
                        if (closed || closing || generation != ticket) {
                            image?.recycle()
                            return@post
                        }
                        if (image != null) stage.neighbor(direction, item.getString("url"), image)
                        else if (!video && result != null) {
                            if (result.optString("state") == "idle") fetch("mediaDownload")
                            else if (result.optString("state") == "downloading")
                                main.postDelayed({ fetch("mediaStatus") }, 160)
                        }
                    }
                }
            }
            fetch("mediaInfo")
        }
    }

    private fun request(operation: String) {
        if (closed || closing) return
        val ticket = generation
        val url = media.getString("url")
        transferPending = true
        worker.execute {
            val result = runCatching {
                transfer.execute(
                    account,
                    JSONObject()
                        .put("operation", operation)
                        .put("url", url)
                        .put("kind", media.optString("kind")),
                )
            }
            main.post {
                if (closed || closing || ticket != generation) return@post
                transferPending = false
                result
                    .onSuccess { next ->
                        status = next
                        if (next.optString("mime").startsWith("video/")) media.put("kind", "video")
                        controls.visibility = if (video()) View.VISIBLE else View.GONE
                        if (!prepared)
                            photoProgress.setBackgroundState(
                                if (next.optString("state") == "downloading")
                                    PhotoProgressView.PROGRESS_CANCEL
                                else PhotoProgressView.PROGRESS_LOAD,
                                animated = true,
                                animateIcon = true,
                            )
                        val size = next.optLong("size")
                        photoProgress.setProgress(
                            if (size > 0) next.optLong("received").toFloat() / size else 0f,
                            true,
                        )
                        val path =
                            next.optString("uri").takeIf { it.startsWith("file:") }?.toUri()?.path
                        if (path != null && local?.path != path) {
                            local = File(path)
                            if (video()) {
                                stage.texture.visibility = View.VISIBLE
                                openPlayer()
                            } else decode(File(path))
                        } else if (stage.currentBitmap == null) {
                            next
                                .optString("poster")
                                .takeIf { it.startsWith("file:") }
                                ?.toUri()
                                ?.path
                                ?.let { decode(File(it)) }
                        }
                        if (
                            !video() &&
                                operation == "mediaInfo" &&
                                next.optString("state") == "idle"
                        )
                            request("mediaDownload")
                        if (next.optString("state") == "failed") failure()
                        stage.invalidate()
                    }
                    .onFailure { failure() }
            }
        }
    }

    private fun decode(file: File) {
        val ticket = generation
        val animated =
            !video() &&
                status.optString("mime") in setOf("image/gif", "image/webp") &&
                file == local
        worker.execute {
            val animation =
                if (animated) runCatching { SteamChatImageDecoder.animation(file) }.getOrNull()
                else null
            val image = runCatching { SteamChatImageDecoder.bitmap(file) }.getOrNull()
            main.post {
                if (closed || closing || ticket != generation) {
                    image?.recycle()
                    (animation as? android.graphics.drawable.Animatable)?.stop()
                } else {
                    if (image != null) stage.bitmap(image)
                    stage.animation(animation)
                }
            }
        }
    }

    private fun openPlayer() {
        val file = local ?: return
        if (!video() || surface == null || player != null || closed || closing) return
        val ticket = generation
        val candidate = MediaPlayer()
        player = candidate
        try {
            candidate.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                setSurface(surface)
                setDataSource(file.path)
                setOnVideoSizeChangedListener { _, width, height ->
                    stage.videoWidth = width
                    stage.videoHeight = height
                    stage.transformVideo()
                }
                setOnPreparedListener { p ->
                    if (ticket != generation || closed || closing) return@setOnPreparedListener
                    prepared = true
                    stage.texture.visibility = View.VISIBLE
                    stage.transformVideo()
                    // PhotoViewer.preparePlayer defaults ordinary videos of <= 30 seconds to
                    // looping.
                    p.isLooping = p.duration <= 30_000
                    controls.looping = p.isLooping
                    val start = media.optLong("position").coerceIn(0, p.duration.toLong())
                    if (start > 0) p.seekTo(start, MediaPlayer.SEEK_CLOSEST)
                    if (desiredPlaying && resumed) play()
                    controls.update(start, duration())
                    stage.invalidate()
                }
                setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) buffering = true
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        buffering = false
                        scheduleHide()
                    }
                    false
                }
                setOnCompletionListener {
                    desiredPlaying = false
                    photoProgress.setBackgroundState(
                        PhotoProgressView.PROGRESS_PLAY,
                        animated = false,
                        animateIcon = true,
                    )
                    toggleBars(true)
                }
                setOnErrorListener { _, _, _ ->
                    failure()
                    releasePlayer()
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) {
            failure()
            releasePlayer()
        }
    }

    private fun playing() =
        prepared && runCatching { player?.isPlaying == true }.getOrDefault(false)

    private fun position(): Long =
        if (prepared) runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)
        else 0L

    private fun duration(): Long =
        if (prepared) runCatching { player?.duration?.toLong() ?: 0L }.getOrDefault(0L)
        else (status.optDouble("duration", 0.0) * 1000).toLong()

    private fun video() = media.optString("kind") == "video"

    private fun play() {
        if (!prepared || !resumed) return
        if (!audioFocus)
            audioFocus =
                audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!audioFocus) return
        if (position() >= duration() - 50) player?.seekTo(0)
        player?.start()
        desiredPlaying = true
        photoProgress.setBackgroundState(
            PhotoProgressView.PROGRESS_PAUSE,
            animated = false,
            animateIcon = true,
        )
        scheduleHide()
    }

    private fun pause() {
        if (prepared) player?.pause()
        desiredPlaying = false
        photoProgress.setBackgroundState(
            PhotoProgressView.PROGRESS_PLAY,
            animated = false,
            animateIcon = true,
        )
        main.removeCallbacks(hideBars)
        stage.invalidate()
    }

    private fun seek(value: Float) {
        if (prepared) player?.seekTo((duration() * value).toLong(), MediaPlayer.SEEK_CLOSEST)
        scheduleHide()
    }

    private fun singleTap(x: Float, y: Float) {
        if (
            (video() || local == null) &&
                abs(x - stage.width / 2f) <= dp(50f) &&
                abs(y - stage.height / 2f) <= dp(50f)
        ) {
            if (!prepared) {
                if (local != null) {
                    stage.texture.visibility = View.VISIBLE
                    openPlayer()
                } else
                    request(
                        if (status.optString("state") == "downloading") "mediaCancel"
                        else "mediaDownload"
                    )
                return
            }
            if (bars || !playing()) {
                if (playing()) pause() else play()
                return
            }
        }
        toggleBars(!bars)
        return
    }

    private fun doubleTap(x: Float): Boolean {
        if (!prepared || duration() <= 15000) return false
        val forward = x >= stage.width / 3f * 2
        if (forward && duration() - position() <= 10000) return false
        val delta = if (forward) 10000 else if (x < stage.width / 3f) -10000 else return false
        if (position() + delta < -9000) return true
        player?.seekTo((position() + delta).coerceIn(0, duration()), MediaPlayer.SEEK_CLOSEST)
        forwardIcon.setOneShootAnimation(true)
        forwardIcon.setLeftSide(delta < 0)
        forwardIcon.addTime(10000)
        stage.invalidate()
        controls.seekBar.setProgress(
            (position() + delta).coerceIn(0, duration()).toFloat() / duration(),
            true,
        )
        return true
    }

    // PhotoViewer.toggleActionBar and scheduleActionBarHide, ordinary media branches.
    private fun toggleBars(show: Boolean, animated: Boolean = true) {
        barsAnimator?.removeAllListeners()
        barsAnimator?.cancel()
        bars = show
        header.visibility = View.VISIBLE
        if (show && playing()) scheduleHide() else main.removeCallbacks(hideBars)
        val destination = if (show) 1f else 0f
        fun apply(value: Float) {
            barsProgress = value
            header.alpha = value
            header.translationY = -dp(24f) * (1f - value)
            count.alpha = value
            count.translationY = -dp(24f) * (1f - value)
            captionScroll.alpha = value
            captionScroll.translationY = dp(24f) * (1f - value)
            controls.controlsProgress = value
            stage.invalidate()
        }
        if (animated)
            barsAnimator =
                ValueAnimator.ofFloat(barsProgress, destination).apply {
                    duration = 200
                    interpolator = null
                    addUpdateListener { apply(it.animatedValue as Float) }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                if (!bars) {
                                    header.visibility = View.INVISIBLE
                                    captionScroll.visibility = View.GONE
                                    if (video() && !controls.looping)
                                        controls.visibility = View.INVISIBLE
                                }
                            }
                        }
                    )
                    start()
                }
        else apply(destination)
        if (show && video()) controls.visibility = View.VISIBLE
        if (show && caption.text.isNotEmpty()) captionScroll.visibility = View.VISIBLE
        window?.let { viewerWindow ->
            WindowCompat.getInsetsController(viewerWindow, viewerWindow.decorView).apply {
                if (show) show(WindowInsetsCompat.Type.systemBars())
                else hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    private fun scheduleHide() {
        main.removeCallbacks(hideBars)
        if (
            !closed &&
                bars &&
                playing() &&
                activity
                    .getSystemService(AccessibilityManager::class.java)
                    ?.isTouchExplorationEnabled != true
        )
            main.postDelayed(hideBars, 3000)
    }

    private fun preview(progress: Float?, width: Int) {
        preview.animate().cancel()
        if (progress == null) {
            preview
                .animate()
                .alpha(0f)
                .scaleX(.5f)
                .scaleY(.5f)
                .translationY(dp(12f).toFloat())
                .setDuration(380)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .withEndAction { preview.visibility = View.INVISIBLE }
                .start()
            scheduleHide()
            return
        }
        main.removeCallbacks(hideBars)
        val file = local ?: return
        preview.progress(file, duration(), progress, width)
        preview.x =
            (controls.seekBar.getThumbX() - preview.width / 2f).coerceIn(
                0f,
                (root.width - preview.width).coerceAtLeast(0).toFloat(),
            )
        if (preview.visibility != View.VISIBLE) {
            preview.visibility = View.VISIBLE
            preview.scaleX = .5f
            preview.scaleY = .5f
            preview.translationY = dp(12f).toFloat()
        }
        preview
            .animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(380)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .start()
    }

    private fun rotate() {
        activity.requestedOrientation =
            if (root.width > root.height) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun action(type: String) {
        resumeAfterAction = playing()
        pause()
        stage.animateImage(false)
        event(JSONObject().put("type", "action").put("action", type).put("index", index))
        hide()
    }

    fun actionFinished() {
        if (!closed && !closing) {
            super.show()
            stage.animateImage(resumed)
            toggleBars(true)
            if (prepared && resumeAfterAction) play()
            resumeAfterAction = false
        }
    }

    private fun openMenu() {
        if (menu != null) return
        main.removeCallbacks(hideBars)
        val content =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(8f), 0, dp(8f))
                setBackgroundColor(0xff232932.toInt())
            }
        val popup =
            PopupWindow(content, dp(224f), -2, true).apply {
                elevation = dp(8f).toFloat()
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                isOutsideTouchable = true
            }
        for ((type, icon, key) in
            listOf(
                Triple("gallery", "save", "chat_media_save_gallery"),
                Triple("share", "share", "chat_media_share"),
                Triple("delete", "delete", "chat_delete"),
            )) {
            val row =
                LinearLayout(activity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(18f), 0, dp(18f), 0)
                    background = RippleDrawable(ColorStateList.valueOf(0x33ffffff), null, null)
                }
            row.addView(
                ImageView(activity).apply {
                    setImageDrawable(SteamChatMediaResources.drawable(context, icon))
                },
                LinearLayout.LayoutParams(dp(24f), dp(24f)),
            )
            row.addView(
                TextView(activity).apply {
                    text = label(key)
                    textSize = 16f
                    setTextColor(Color.WHITE)
                },
                LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(18f) },
            )
            content.addView(row, LinearLayout.LayoutParams(-1, dp(48f)))
            row.setOnClickListener {
                popup.dismiss()
                action(type)
            }
        }
        popup.setOnDismissListener {
            menu = null
            scheduleHide()
        }
        menu = popup
        popup.showAtLocation(root, Gravity.TOP or Gravity.END, dp(8f), dp(56f))
        content.pivotX = dp(224f).toFloat()
        content.pivotY = 0f
        content.scaleY = .01f
        content.alpha = 0f
        content.animate().scaleY(1f).alpha(1f).setDuration(150).start()
    }

    private fun failure() {
        if (!closed) Toast.makeText(activity, label("chat_media_failed"), Toast.LENGTH_SHORT).show()
    }

    fun foreground(value: Boolean) {
        if (closed || resumed == value) return
        resumed = value
        stage.animateImage(value)
        if (!value) {
            resumeAfterFocus = playing()
            if (prepared) player?.pause()
            main.removeCallbacks(hideBars)
        } else if (resumeAfterFocus) {
            resumeAfterFocus = false
            play()
        }
    }

    fun belongsTo(host: Activity) = activity === host

    fun closeViewer() {
        if (closing || closed) return
        closing = true
        pause()
        stage.animateImage(false)
        menu?.dismiss()
        gestures.close()
        barsAnimator?.cancel()
        root.animate().cancel()
        transition.cancel()
        if (
            index == openingIndex &&
                transition.run(
                    root,
                    stage,
                    stage.currentBitmap,
                    false,
                    stage.fittedWidth / stage.fittedHeight,
                    ::transitionChrome,
                    stage.zoom,
                    stage.offsetX,
                    stage.offsetY,
                ) {
                    dismiss()
                }
        )
            return
        root
            .animate()
            .alpha(0f)
            .scaleX(.9f)
            .scaleY(.9f)
            .setDuration(200)
            .withEndAction { dismiss() }
            .start()
    }

    private fun transitionChrome(value: Float) {
        root.setBackgroundColor((value * 255).toInt().coerceIn(0, 255) shl 24)
        header.alpha = value
        count.alpha = value
        controls.alpha = value
        captionScroll.alpha = value
    }

    fun closeImmediately() {
        if (!closed) {
            dismiss()
            dispose()
        }
    }

    private fun releasePlayer() {
        prepared = false
        player?.release()
        player = null
        stage.texture.visibility = View.INVISIBLE
        buffering = false
    }

    private fun dispose() {
        if (closed) return
        closed = true
        generation++
        main.removeCallbacksAndMessages(null)
        barsAnimator?.cancel()
        root.animate().cancel()
        rewinder.stop()
        gestures.close()
        releasePlayer()
        surface?.release()
        surface = null
        preview.close()
        speedIcon.close()
        transition.close()
        stage.animation(null)
        stage.bitmap(null)
        stage.clearNeighbors()
        if (audioFocus) audio.abandonAudioFocusRequest(focusRequest)
        activity.requestedOrientation = originalOrientation
        worker.shutdownNow()
        neighborWorker.shutdownNow()
        event(JSONObject().put("type", "closed").put("index", index))
    }

    private inner class Stage(context: Context) :
        FrameLayout(context), android.graphics.drawable.Drawable.Callback {
        val texture = TextureView(context)
        var currentBitmap: Bitmap? = null
            private set

        private var animatedImage: android.graphics.drawable.Drawable? = null

        fun animation(value: android.graphics.drawable.Drawable?) {
            (animatedImage as? android.graphics.drawable.Animatable)?.stop()
            animatedImage?.callback = null
            animatedImage = value
            value?.callback = this
            animateImage(resumed)
            invalidate()
        }

        fun animateImage(play: Boolean) {
            (animatedImage as? android.graphics.drawable.Animatable)?.let {
                if (play) it.start() else it.stop()
            }
        }

        var videoWidth = 1
        var videoHeight = 1
        var zoom = 1f
        var offsetX = 0f
        var offsetY = 0f
        var rewindSpeed = 0f
        private val bitmapBounds = RectF()
        private val neighbors = mutableMapOf<Int, Pair<String, Bitmap>>()

        fun neighbor(direction: Int, url: String, image: Bitmap) {
            neighbors.put(direction, url to image)?.second?.recycle()
            invalidate()
        }

        fun neighborBitmap(url: String): Bitmap? =
            neighbors.values
                .firstOrNull { it.first == url }
                ?.second
                ?.copy(Bitmap.Config.ARGB_8888, false)

        fun clearNeighbors() {
            neighbors.values.forEach { it.second.recycle() }
            neighbors.clear()
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fittedWidth
            get() = min(width.toFloat(), height * ratio())

        val fittedHeight
            get() = fittedWidth / ratio()

        private fun ratio(): Float =
            if (video() && videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight
            else currentBitmap?.let { it.width.toFloat() / it.height } ?: 1f

        init {
            setWillNotDraw(false)
            addView(texture, LayoutParams(-1, -1))
            texture.isOpaque = false
        }

        fun bitmap(value: Bitmap?) {
            if (currentBitmap !== value) currentBitmap?.recycle()
            currentBitmap = value
            invalidate()
        }

        fun transformVideo() {
            if (width <= 0 || height <= 0) return
            val matrix = Matrix()
            val limit = maxOf(0f, (fittedWidth * zoom - width) / 2f)
            val left = if (offsetX > limit) min(1f, (offsetX - limit) / width) else 0f
            val scale = zoom - left * .3f
            matrix.setScale(
                fittedWidth / width * scale,
                fittedHeight / height * scale,
                width / 2f,
                height / 2f,
            )
            matrix.postTranslate(if (left > 0f) limit else offsetX, offsetY)
            texture.setTransform(matrix)
            texture.alpha = 1f - left
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            transformVideo()
            controls.requestLayout()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // PhotoViewer.onDraw rightImage: fade/scale in behind the outgoing center image.
            val limit = maxOf(0f, (fittedWidth * zoom - width) / 2f)
            if (zoom >= 1f && offsetX < -limit - dp(5f))
                neighbors[1]?.second?.let {
                    val progress = min(1f, (-limit - offsetX) / width)
                    drawNeighbor(canvas, it, width / 2f, 1f - (1f - progress) * .3f, progress)
                }
            val leftProgress = if (offsetX > limit) min(1f, (offsetX - limit) / width) else 0f
            currentBitmap?.let {
                canvas.withTranslation(
                    width / 2f + if (leftProgress > 0f) limit else offsetX,
                    height / 2f + offsetY,
                ) {
                    canvas.scale(zoom - leftProgress * .3f, zoom - leftProgress * .3f)
                    paint.alpha = ((1f - leftProgress) * 255).toInt()
                    val animated = animatedImage
                    if (animated == null)
                        canvas.drawBitmap(
                            it,
                            null,
                            bitmapBounds.apply {
                                set(
                                    -fittedWidth / 2,
                                    -fittedHeight / 2,
                                    fittedWidth / 2,
                                    fittedHeight / 2,
                                )
                            },
                            paint,
                        )
                    else {
                        animated.setBounds(
                            (-fittedWidth / 2).toInt(),
                            (-fittedHeight / 2).toInt(),
                            (fittedWidth / 2).toInt(),
                            (fittedHeight / 2).toInt(),
                        )
                        animated.alpha = paint.alpha
                        animated.draw(canvas)
                    }
                    paint.alpha = 255
                }
            }
        }

        private fun drawNeighbor(
            canvas: Canvas,
            bitmap: Bitmap,
            centerX: Float,
            scale: Float,
            alpha: Float,
        ) {
            val fitted = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val w = bitmap.width * fitted
            val h = bitmap.height * fitted
            canvas.withTranslation(centerX, height / 2f) {
                canvas.scale(scale, scale)
                paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(
                    bitmap,
                    null,
                    bitmapBounds.apply { set(-w / 2, -h / 2, w / 2, h / 2) },
                    paint,
                )
                paint.alpha = 255
            }
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            // PhotoViewer.onDraw leftImage slides over the scaled/faded current media.
            val limit = maxOf(0f, (fittedWidth * zoom - width) / 2f)
            if (zoom >= 1f && offsetX > limit + dp(5f))
                neighbors[-1]?.second?.let {
                    drawNeighbor(canvas, it, width / 2f - width - dp(15f) + offsetX - limit, 1f, 1f)
                }
            if ((video() || local == null) && !speedIcon.isShown) {
                val alpha = if (prepared && playing()) barsProgress else 1f
                if (alpha > 0f) {
                    photoProgress.setIndexedAlpha(1, alpha, false)
                    canvas.withTranslation(
                        if (-offsetX > limit) offsetX + limit else 0f,
                        if (zoom == 1f) offsetY else 0f,
                    ) {
                        photoProgress.onDraw(canvas)
                    }
                }
            }
            forwardIcon.setBounds(0, 0, width, height)
            forwardIcon.draw(canvas)
            speedIcon.setBounds(0, dp(56f), width, height)
            speedIcon.draw(canvas)
        }

        override fun onInterceptTouchEvent(ev: MotionEvent) = true

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (transition.running) return true
            gestures.touch(event)
            if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        override fun invalidateDrawable(who: android.graphics.drawable.Drawable) {
            invalidate()
        }

        override fun scheduleDrawable(
            who: android.graphics.drawable.Drawable,
            what: Runnable,
            `when`: Long,
        ) {
            main.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: android.graphics.drawable.Drawable, what: Runnable) {
            main.removeCallbacks(what)
        }
    }
}
