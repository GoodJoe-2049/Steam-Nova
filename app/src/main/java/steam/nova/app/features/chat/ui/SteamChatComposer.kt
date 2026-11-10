package steam.nova.app.features.chat.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import steam.nova.app.features.chat.diagnostics.SteamChatDiagnostics
import steam.nova.app.features.chat.media.viewer.CubicBezierInterpolator
import steam.nova.app.features.chat.network.SteamChatAccount
import java.lang.ref.WeakReference
import kotlin.math.ceil
import kotlin.math.min

internal object SteamChatComposer {
    private const val EVENT = "SteamNovaChatComposer"
    private const val MIN_HEIGHT = 44
    private const val MAX_LINES = 6
    private const val DURATION = 250L
    private const val X1 = 0.19919472913616398
    private const val Y1 = 0.010644531250000006
    private const val X2 = 0.27920937042459737
    private const val Y2 = 0.91025390625
    private val main = Handler(Looper.getMainLooper())
    private val layers = mutableMapOf<String, WeakReference<Layer>>()

    fun handle(activity: Activity?, reactContext: Any, args: JSONObject, current: () -> Boolean,
               complete: (Result<JSONObject>) -> Unit) {
        main.post {
            complete(runCatching {
                val account = args.getString("account")
                SteamChatAccount.current(account)
                val owner = args.getString("owner")
                require(owner.matches(Regex("chat-composer-[A-Za-z0-9_-]{1,100}")))
                layers.entries.removeAll { it.value.get() == null }
                val previous = layers[owner]?.get()
                if (args.optBoolean("detach")) {
                    previous?.takeIf { it.account == account }?.close()
                    return@runCatching JSONObject().put("mounted", false)
                }
                check(current())
                val host = requireNotNull(activity)
                check(!host.isFinishing && !host.isDestroyed)
                val field = host.window.decorView.findViewWithTag<View>(owner) as? EditText
                if (field == null || !field.isAttachedToWindow || field.width == 0)
                    return@runCatching JSONObject().put("mounted", false)
                val layer = previous?.takeIf { it.field === field && it.account == account } ?: run {
                    previous?.close()
                    Layer(owner, account, field, reactContext).also { layers[owner] = WeakReference(it) }
                }
                layer.reduced = args.optBoolean("reduced")
                layer.inspect(force = true)
                JSONObject().put("mounted", true)
            })
        }
    }

    fun clear(accountToKeep: String? = null) {
        main.post { layers.values.mapNotNull { it.get() }.filter { it.account != accountToKeep }.forEach { it.close() } }
    }

    fun clearActivity(activity: Activity) {
        main.post { layers.values.mapNotNull { it.get() }.filter { it.root.get() === activity.window.decorView.rootView }.forEach { it.close() } }
    }

    private class Layer(val owner: String, val account: String, val field: EditText, reactContext: Any) :
        ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
        val root = WeakReference(field.rootView)
        private val react = WeakReference(reactContext)
        private var observer: ViewTreeObserver? = null
        private val originalMaxLines = field.maxLines
        private var animator: ValueAnimator? = null
        private var closed = false
        private var lastText: String? = null
        private var lastTarget = 0
        private var lastHeight = 0
        private var lastScroll = 0
        private var lastLines = 0
        private var lastWidth = 0
        private var lastViewportHeight = 0
        private var lastBottomInset = -1
        private val rootLocation = IntArray(2)
        private val viewportLocation = IntArray(2)
        var reduced = false

        init {
            field.maxLines = MAX_LINES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) field.isFallbackLineSpacing = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                field.isLocalePreferredLineHeightForMinimumUsed = false
            field.addOnAttachStateChangeListener(this)
            observe()
        }

        fun inspect(force: Boolean = false) {
            if (closed || field.width == 0 || !field.isAttachedToWindow) return
            val layout = field.layout ?: return
            val text = field.text.toString()
            if (layout.text.toString() != text) return
            val density = field.resources.displayMetrics.density
            val minimum = ceil(MIN_HEIGHT * density).toInt()
            val lines = layout.lineCount
            // Layout measures the text independently of the height assigned by Yoga.
            val target = if (text.isEmpty()) minimum else maxOf(minimum,
                layout.getLineTop(min(MAX_LINES, lines)) + field.compoundPaddingTop + field.compoundPaddingBottom)
            val panel = generateSequence(field.parent as? View) { it.parent as? View }
                .firstOrNull { it.tag == "$owner.panel" }
            val viewport = panel?.parent as? View
            val viewportHeight = viewport?.height ?: field.rootView.height
            val bottomInset = bottomInset(viewport)
            val resized = lastWidth != field.width || lastViewportHeight != viewportHeight
            val animate = lastHeight > 0 && !resized && !reduced && field.isShown && field.hasWindowFocus() && ValueAnimator.areAnimatorsEnabled()
            if (text.isEmpty() && (field.scrollY != 0 || field.scrollX != 0)) field.scrollTo(0, 0)
            if (!animate) {
                stopOffset()
                field.translationY = 0f
            } else if (lastHeight != field.height || (lastText != text && lastLines != lines)) {
                val delta = (lastHeight - field.height) + (lastScroll - field.scrollY)
                if (delta != 0) {
                    val offset = field.translationY - delta
                    stopOffset()
                    field.translationY = offset
                    animator = ValueAnimator.ofFloat(offset, 0f).apply {
                        duration = DURATION
                        interpolator = CubicBezierInterpolator(X1, Y1, X2, Y2)
                        addUpdateListener { field.translationY = it.animatedValue as Float }
                        start()
                    }
                }
            }
            val changed = force || lastText != text || lastTarget != target || resized || lastBottomInset != bottomInset
            lastText = text
            lastTarget = target
            lastHeight = field.height
            lastScroll = field.scrollY
            lastLines = lines
            lastWidth = field.width
            lastViewportHeight = viewportHeight
            lastBottomInset = bottomInset
            if (changed) emit(JSONObject().put("owner", owner).put("text", text)
                .put("height", target / density).put("animate", animate).put("bottomInset", bottomInset / density))
        }

        private fun bottomInset(viewport: View?): Int {
            if (viewport == null || viewport.height == 0) return 0
            val windowRoot = field.rootView
            val insets = ViewCompat.getRootWindowInsets(windowRoot) ?: return 0
            // KeyboardAvoidingView/adjustResize already own the keyboard clearance.
            if (insets.isVisible(WindowInsetsCompat.Type.ime())) return 0
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).bottom
            if (bottom == 0) return 0
            windowRoot.getLocationOnScreen(rootLocation)
            viewport.getLocationOnScreen(viewportLocation)
            val safeBottom = rootLocation[1] + windowRoot.height - bottom
            return (viewportLocation[1] + viewport.height - safeBottom).coerceIn(0, bottom)
        }

        private fun emit(value: JSONObject) {
            val context = react.get() ?: return
            runCatching {
                val type = Class.forName($$"com.facebook.react.modules.core.DeviceEventManagerModule$RCTDeviceEventEmitter", false, context.javaClass.classLoader)
                val emitter = context.javaClass.getMethod("getJSModule", Class::class.java).invoke(context, type)
                type.getMethod("emit", String::class.java, Any::class.java).invoke(emitter, EVENT, value.toString())
            }.onFailure { SteamChatDiagnostics.failure("composer.event", it) }
        }

        private fun stopOffset() { animator?.cancel(); animator = null }

        override fun onPreDraw(): Boolean {
            runCatching { inspect() }.onFailure {
                SteamChatDiagnostics.failure("composer.measure", it)
                close()
            }
            return true
        }

        private fun observe() {
            if (observer?.isAlive == true) return
            observer = field.viewTreeObserver.also { it.addOnPreDrawListener(this) }
            lastHeight = 0
        }

        private fun unobserve() {
            observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
            observer = null
            stopOffset()
            field.translationY = 0f
        }

        override fun onViewAttachedToWindow(view: View) { observe(); inspect(force = true) }
        override fun onViewDetachedFromWindow(view: View) { unobserve() }

        fun close() {
            if (closed) return
            closed = true
            unobserve()
            field.maxLines = originalMaxLines
            field.removeOnAttachStateChangeListener(this)
            if (layers[owner]?.get() === this) layers.remove(owner)
        }
    }

    fun source() = """
    function chatComposer(env,props){
        var R=env.React,N=env.RN,account=env.currentAccount();
        var owner=R.useMemo(function(){return 'chat-composer-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2);},[account,props.target.key]);
        var field=R.useState($MIN_HEIGHT),height=R.useRef(new N.Animated.Value(52)).current,bottomInset=R.useState(0);
        var draft=R.useRef(props.draft),pending=R.useRef(null),fieldTarget=R.useRef($MIN_HEIGHT),destination=R.useRef(52),animation=R.useRef(null),reduced=R.useRef(false),connect=R.useRef(null);
        draft.current=props.draft;
        function resize(value,animate){
            fieldTarget.current=value;
            field[1](value);
            var next=Math.max(52,value);
            if(next===destination.current&&animate)return;
            destination.current=next;
            if(animation.current){animation.current.stop();animation.current=null;}
            if(!animate||reduced.current){height.setValue(next);return;}
            animation.current=N.Animated.timing(height,{toValue:next,duration:$DURATION,
                easing:N.Easing.bezier($X1,$Y1,$X2,$Y2),useNativeDriver:false});
            animation.current.start();
        }
        R.useEffect(function(){
            var live=true,attached=false,waiting=false,timer=null,attempts=0;
            function attach(force){
                if(!live||waiting||(attached&&!force))return;
                waiting=true;
                env.request('chatComposer',{owner:owner,reduced:reduced.current},account).then(function(value){
                    waiting=false;if(!live)return;attached=value.mounted;
                    if(!attached&&++attempts<10)timer=setTimeout(function(){attach(false);},100);
                }).catch(function(){waiting=false;if(live&&++attempts<10)timer=setTimeout(function(){attach(false);},100);});
            }
            connect.current=function(){attempts=0;clearTimeout(timer);attach(false);};
            var events=N.DeviceEventEmitter.addListener('$EVENT',function(raw){
                if(!live)return;
                try{var event=typeof raw==='string'?JSON.parse(raw):raw;
                    if(event.owner!==owner||!Number.isFinite(event.height)||event.height<$MIN_HEIGHT)return;
                    if(Number.isFinite(event.bottomInset)&&event.bottomInset>=0)bottomInset[1](event.bottomInset);
                    pending.current=event;
                    if(event.text===draft.current)resize(event.height,event.animate);
                }catch(ignore){}
            });
            function motion(value){if(!live)return;reduced.current=value;if(value)resize(fieldTarget.current,false);attach(true);}
            N.AccessibilityInfo.isReduceMotionEnabled().then(motion).catch(function(){});
            var preference=N.AccessibilityInfo.addEventListener('reduceMotionChanged',motion);
            attach(false);
            return function(){live=false;connect.current=null;clearTimeout(timer);events.remove();preference.remove();
                if(animation.current)animation.current.stop();env.request('chatComposer',{owner:owner,detach:true},account).catch(function(){});};
        },[owner]);
        R.useEffect(function(){
            if(props.draft==='')resize($MIN_HEIGHT,true);
            else if(pending.current&&pending.current.text===props.draft)resize(pending.current.height,pending.current.animate);
        },[props.draft]);
        return {owner:owner,height:height,fieldHeight:field[0],bottomInset:bottomInset[0],layout:function(){if(connect.current)connect.current();}};
    }
    """.trimIndent()
}
