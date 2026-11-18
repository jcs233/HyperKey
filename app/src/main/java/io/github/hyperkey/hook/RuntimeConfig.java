package io.github.hyperkey.hook;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import io.github.hyperkey.core.Config;
import io.github.hyperkey.core.ConfigStore;
import io.github.hyperkey.core.HookStatus;
import io.github.hyperkey.core.AppProfiles;
import io.github.hyperkey.core.ProfileStore;
import io.github.hyperkey.core.Shortcut;
import io.github.hyperkey.core.KeyMappings;
import io.github.hyperkey.core.MappingStore;

/** Provider I/O occurs on startup/change, never in the per-key hot path. */
final class RuntimeConfig {
    private record State(Config global, AppProfiles profiles, KeyMappings mappings) {}
    private volatile State state = new State(Config.DEFAULT, AppProfiles.EMPTY, KeyMappings.EMPTY);
    private volatile String focusedPackage = "";
    private volatile boolean mappingEditor;
    private volatile boolean keyTester;
    private final ThreadLocal<Snapshot> dispatch = new ThreadLocal<>();
    record Snapshot(Config config, AppProfiles.Profile profile) {}
    private Context baseContext;
    private ContentResolver resolver;
    private ContentObserver observer;
    private int userId = -1;
    private int hookCount;
    private String hookDetail;
    private NativeShortcutSync nativeSync;
    private final Handler handler = new Handler(Looper.getMainLooper());

    Snapshot snapshot() {
        Snapshot active = dispatch.get();
        if (active != null) return active;
        State value = state;
        AppProfiles.Profile profile = value.profiles.entries.get(focusedPackage);
        return new Snapshot(profile == null ? value.global : profile.resolve(value.global), profile);
    }
    Config get() { return snapshot().config; }
    KeyMappings.Rule mapping(int key, int modifiers) {
        State value = state;
        AppProfiles.Profile p = value.profiles.entries.get(focusedPackage);
        String owner = p != null && !p.enabled ? "" : focusedPackage;
        return value.mappings.find(owner, key, modifiers);
    }
    Context context() { return baseContext; }
    boolean mappingEditor() { return mappingEditor; }
    boolean keyTester() { return keyTester; }
    boolean rawWin() {
        Snapshot value = snapshot();
        return value.config.blockAll || (value.profile != null && value.profile.enabled && value.profile.rawWin) ||
                (value.config.isBlocked(Shortcut.META_SINGLE) && value.config.isBlocked(Shortcut.META_DOUBLE) &&
                        value.config.isBlocked(Shortcut.META_HOLD));
    }
    Snapshot enter(Snapshot value) { Snapshot old = dispatch.get(); dispatch.set(value); return old; }
    void leave(Snapshot old) { if (old == null) dispatch.remove(); else dispatch.set(old); }
    Boolean nativeOverride(Shortcut shortcut) {
        Snapshot active = dispatch.get();
        if (active == null || shortcut == null) return null;
        if (active.config.blockAll) return false;
        return active.profile != null && active.profile.overrides(shortcut) ? !active.config.isBlocked(shortcut) : null;
    }
    void focus(Object window) {
        String name = "";
        try { if (window != null) name = (String) Reflect.call(window, "getOwningPackage"); }
        catch (ReflectiveOperationException | RuntimeException ignored) { }
        focusedPackage = name == null ? "" : name;
        String ownWindow = "io.github.hyperkey".equals(focusedPackage) && window != null ? window.toString() : "";
        mappingEditor = ownWindow.contains(".ui.MappingActivity");
        keyTester = ownWindow.contains(".ui.KeyTestActivity");
    }
    void setNativeSync(NativeShortcutSync value) { nativeSync = value; }

    void start(Context context, int count, String detail) {
        if (baseContext != null) return;
        baseContext = context;
        hookCount = count;
        hookDetail = detail;
        // boot_count increments after systemReady on this HyperOS build.
        context.getContentResolver().registerContentObserver(Settings.Global.getUriFor("boot_count"), false,
                new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) { publishStatus(baseContext); }
                });
        int currentUser = 0;
        try { currentUser = (int) Class.forName("android.app.ActivityManager").getDeclaredMethod("getCurrentUser").invoke(null); }
        catch (ReflectiveOperationException ignored) { }
        switchUser(currentUser);
        publishStatus(context);
    }

    void switchUser(int id) {
        if (baseContext == null || userId == id) return;
        // Drop the previous user's overrides even if the new user's provider is not ready.
        state = new State(Config.DEFAULT, AppProfiles.EMPTY, KeyMappings.EMPTY);
        focusedPackage = "";
        mappingEditor = keyTester = false;
        handler.post(() -> {
            try {
                Context userContext = (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class)
                        .invoke(baseContext, UserHandle.getUserHandleForUid(id * 100000), 0);
                if (resolver != null && observer != null) resolver.unregisterContentObserver(observer);
                resolver = userContext.getContentResolver();
                observer = new ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) { reload(userContext); }
                };
                resolver.registerContentObserver(Settings.Secure.getUriFor(Config.SETTING), false, observer);
                resolver.registerContentObserver(Settings.Secure.getUriFor(AppProfiles.SETTING), false, observer);
                resolver.registerContentObserver(Settings.Secure.getUriFor(KeyMappings.SETTING), false, observer);
                userId = id;
                if (nativeSync != null) nativeSync.start(userContext);
                reload(userContext);
                publishStatus(userContext);
            } catch (ReflectiveOperationException | RuntimeException e) { Log.w("HyperKey", "Cannot observe user config", e); }
        });
    }

    private void reload(Context context) {
        try {
            Config requested = ConfigStore.read(context);
            state = new State(nativeSync == null ? requested : nativeSync.reconcile(requested), ProfileStore.read(context), MappingStore.read(context));
        }
        catch (RuntimeException e) { state = new State(Config.DEFAULT, AppProfiles.EMPTY, KeyMappings.EMPTY); Log.w("HyperKey", "Config read failed; using defaults", e); }
    }
    private void publishStatus(Context context) {
        try { HookStatus.mark(context, HookStatus.SYSTEM, hookCount, hookDetail); }
        catch (RuntimeException e) { Log.w("HyperKey", "Cannot publish system status", e); }
    }
}
