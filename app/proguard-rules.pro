# LSPosed discovers this class via META-INF/xposed/java_init.list, outside the Android manifest.
-keep class io.github.hyperkey.hook.HyperKeyModule {
    public <init>();
    public void onSystemServerStarting(...);
    public void onPackageReady(...);
}

# Android's default manifest rules retain Activity names, which are also used by Settings
# intents and the system hook's window-name checks. The ViewModel factory uses reflection.
-keepclassmembers class io.github.hyperkey.ui.MappingActivity$Draft {
    public <init>();
}
-keepclassmembers class io.github.hyperkey.ui.ShortcutActionActivity$Execution {
    public <init>(android.app.Application);
}

# Enum identifiers (and Shortcut order) are part of the on-device configuration format.
-keep enum io.github.hyperkey.core.Shortcut { *; }
-keep enum io.github.hyperkey.ui.ActionKind { *; }
