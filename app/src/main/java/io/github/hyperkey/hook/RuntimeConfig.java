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
    private volatile boolean actionScreen;
    private final ThreadLocal<Snapshot> dispatch = new ThreadLocal<>();
    record Snapshot(Config config, AppProfiles.Profile profile) {}
    private Context baseContext;
    private ContentResolver resolver;
    private ContentObserver observer;
    private volatile int userId = -1;
    private volatile Context activeContext;
    private boolean nativeReady;
    private int retryDelay;
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
        return value.mappings.forProfile(focusedPackage, value.profiles, key, modifiers);
    }
    Context context() { return activeContext; }
    boolean profileRawWin() { AppProfiles.Profile p = snapshot().profile(); return p != null && p.enabled && p.rawWin; }
    boolean bypass(Config effective, int key, int mods) {
        Shortcut nativeAction = activeContext == null || nativeSync == null ? null : nativeSync.lookup(key, mods);
        return nativeAction == null ? io.github.hyperkey.core.ShortcutPolicy.bypass(effective, key, mods) : effective.isBlocked(nativeAction);
    }
    boolean mappingEditor() { return mappingEditor; }
    boolean actionScreen() { return actionScreen; }
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
        actionScreen = ownWindow.contains(".ui.ShortcutActionActivity");
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
                    @Override public void onChange(boolean selfChange) { if (activeContext != null) publishStatus(activeContext); }
                });
        int currentUser = 0;
        try { currentUser = (int) Class.forName("android.app.ActivityManager").getDeclaredMethod("getCurrentUser").invoke(null); }
        catch (ReflectiveOperationException ignored) { }
        switchUser(currentUser);
    }

    synchronized void switchUser(int id) {
        if (baseContext == null || userId == id) return;
        userId = id; activeContext = null;
        if (nativeSync != null) nativeSync.reset();
        // Drop the previous user's overrides even if the new user's provider is not ready.
        state = new State(Config.DEFAULT, AppProfiles.EMPTY, KeyMappings.EMPTY);
        focusedPackage = "";
        mappingEditor = keyTester = actionScreen = false;
        handler.post(() -> attachUser(id));
    }

    private synchronized void attachUser(int id) {
        if (id != userId) return;
        try {
            nativeReady = false; retryDelay = 1000;
            if (nativeSync != null) nativeSync.reset();
            Context userContext = (Context) Context.class.getMethod("createContextAsUser", UserHandle.class, int.class)
                    .invoke(baseContext, UserHandle.getUserHandleForUid(id * 100000), 0);
            if (resolver != null && observer != null) { resolver.unregisterContentObserver(observer); observer = null; }
            resolver = userContext.getContentResolver();
            activeContext = userContext;
            observer = new ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) { if (activeContext == userContext) reload(userContext); }
            };
            resolver.registerContentObserver(Settings.Secure.getUriFor(Config.SETTING), false, observer);
            resolver.registerContentObserver(Settings.Secure.getUriFor(AppProfiles.SETTING), false, observer);
            resolver.registerContentObserver(Settings.Secure.getUriFor(KeyMappings.SETTING), false, observer);
            reload(userContext);
            startNative(userContext);
            publishStatus(userContext);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w("HyperKey", "Cannot observe user config", e);
            handler.postDelayed(() -> attachUser(id), 10000);
        }
    }

    private synchronized void startNative(Context context) {
        if (activeContext != context || nativeSync == null || nativeReady) return;
        try { nativeSync.start(context); nativeReady = true; reload(context); publishStatus(context); }
        catch (ReflectiveOperationException | RuntimeException e) {
            nativeSync.reset();
            if (retryDelay == 1000) Log.w("HyperKey", "Native sync unavailable; keeping module rules", e);
            handler.postDelayed(() -> startNative(context), retryDelay);
            retryDelay = Math.min(60000, retryDelay * 2);
        }
    }
    private synchronized void reload(Context context) {
        if (context != activeContext) return;
        try {
            Config requested = ConfigStore.read(context);
            state = new State(!nativeReady ? requested : nativeSync.reconcile(requested), ProfileStore.read(context), MappingStore.read(context));
        }
        catch (RuntimeException e) { state = new State(Config.DEFAULT, AppProfiles.EMPTY, KeyMappings.EMPTY); Log.w("HyperKey", "Config read failed; using defaults", e); }
    }
    private void publishStatus(Context context) {
        try { HookStatus.mark(context, HookStatus.SYSTEM, hookCount, nativeReady ? hookDetail : hookDetail + ";native_wait"); }
        catch (RuntimeException e) { Log.w("HyperKey", "Cannot publish system status", e); }
    }
}
