-keep,allowoptimization class steam.nova.app.ModuleMain {
    public <init>();
    public <methods>;
}

# Dhizuku's unused no-argument init() references this hidden platform class.
# Millennium always passes Steam's application context to init(Context).
-dontwarn android.app.ActivityThread

# Rhino's optional JavaToJSONConverters.BEAN uses desktop JavaBeans APIs absent on Android.
# Effects use a safe JS scope and JSON data; they never select the BEAN converter.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Rhino 1.9.1 resolves these types by name in ScriptRuntime.
-keepnames class org.mozilla.javascript.Context
-keepnames class org.mozilla.javascript.ContextFactory
-keepnames interface org.mozilla.javascript.Function

# The local voice media page calls this one annotated JavaScript bridge method.
-keepclassmembers class steam.nova.app.features.chat.voice.SteamChatVoiceMedia$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepnames class org.mozilla.javascript.ScriptableObject

# Context constructs the interpreter reflectively; RhinoException matches this stack frame.
-keep class org.mozilla.javascript.Interpreter {
    public <init>();
    private static java.lang.Object interpretLoop(...);
}

# This safe-scope built-in still uses the legacy reflective initializer.
-keep,allowoptimization class org.mozilla.javascript.NativeContinuation {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}

# Keep the service descriptor's interface/provider names and the provider constructor.
-keepnames interface org.mozilla.javascript.RegExpLoader
-keep,allowoptimization class org.mozilla.javascript.regexp.RegExpLoaderImpl {
    public <init>();
}
