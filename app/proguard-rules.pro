# Keep JNI-bound native methods (the native library binds methods by the
# fully-qualified class name + method name).
-keep class com.grimoire.android.native.GrimoireNative { *; }
