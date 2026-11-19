package io.github.hyperkey.hook;

import android.content.Context;
import android.os.Binder;
import android.provider.Settings;
import android.util.Log;
import io.github.hyperkey.core.Config;
import io.github.hyperkey.core.ConfigStore;
import io.github.hyperkey.core.Shortcut;
import java.util.ArrayList;
import java.util.List;

/** Serializes native edits and provider edits; only changes enable flags, never resets mappings. */
final class NativeShortcutSync {
    static final String REVISION = "hyperkey_native_revision";
    private final ClassLoader loader;
    private Context context;
    private Object utility;
    private Config published;
    private boolean applying;
    private long revision;
    private String fingerprint;
    // OEM shortcutKeyCode packs Android's logical modifiers above the 32-bit keycode.
    private volatile java.util.Map<Long, Shortcut> chords = java.util.Collections.emptyMap();
    Shortcut lookup(int key, int mods) { return chords.get(((long)(mods & 0x11003) << 32) | (key & 0xffffffffL)); }

    NativeShortcutSync(ClassLoader loader) { this.loader = loader; }

    synchronized void reset() { context = null; utility = null; published = null; fingerprint = null; chords = java.util.Collections.emptyMap(); }

    synchronized void start(Context value) throws ReflectiveOperationException {
        reset();
        context = value;
        Class<?> type = Class.forName("com.miui.server.input.util.MiuiCustomizeShortCutUtils", false, loader);
        utility = type.getMethod("getInstance", Context.class).invoke(null, value);
        // Native settings are authoritative on first load, including existing disabled shortcuts.
        publish(ConfigStore.read(context));
    }

    synchronized Config reconcile(Config requested) {
        if (utility == null || context == null) return requested;
        try {
            if (published == null) { publish(requested); return published; }
            if (requested.encode().equals(published.encode())) return requested;
            Config before = published;
            applying = true;
            for (Object info : infos()) {
                int type = (int) Reflect.call(info, "getType");
                Shortcut shortcut = Shortcut.forNativeType(type);
                if (shortcut == null || before.isIndividuallyEnabled(shortcut) == requested.isIndividuallyEnabled(shortcut)) continue;
                Reflect.call(info, "setActive", requested.isIndividuallyEnabled(shortcut));
                // OEM reflectObject does not fill history; MODIFY requires the current encoded key.
                Reflect.call(info, "setHistoryKeyCode", Reflect.call(info, "getShortcutKeyCode"));
                Reflect.call(utility, "updateKeyboardShortcut", info, 1);
            }
            publish(requested);
            return published;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w("HyperKey", "Native shortcut synchronization failed", e);
            return requested;
        } finally { applying = false; }
    }

    synchronized void nativeChanged() {
        if (applying || utility == null || context == null) return;
        long identity = Binder.clearCallingIdentity();
        try { publish(ConfigStore.read(context)); }
        catch (ReflectiveOperationException | RuntimeException e) { Log.w("HyperKey", "Cannot import native shortcut edit", e); }
        finally { Binder.restoreCallingIdentity(identity); }
    }

    private List<?> infos() throws ReflectiveOperationException {
        return new ArrayList<>((List<?>) Reflect.call(utility, "getKeyboardShortcutInfo"));
    }

    private void publish(Config base) throws ReflectiveOperationException {
        java.util.Map<Long, Shortcut> next = new java.util.HashMap<>();
        long seen = 0, enabled = 0;
        java.util.List<String> entries = new ArrayList<>();
        for (Object info : infos()) {
            Shortcut shortcut = Shortcut.forNativeType((int) Reflect.call(info, "getType"));
            if (shortcut == null) continue;
            seen |= shortcut.bit();
            boolean active = Boolean.TRUE.equals(Reflect.call(info, "isActive"));
            if (active) enabled |= shortcut.bit();
            long chord = (long) Reflect.call(info, "getShortcutKeyCode");
            next.put(chord, shortcut);
            entries.add(shortcut.nativeType + ":" + chord + ":" + active
                    + ":" + Reflect.call(info, "isCustomized") + ":" + Reflect.call(info, "getPackageName")
                    + ":" + Reflect.call(info, "getClassName"));
        }
        if (seen == 0) throw new IllegalStateException("Native shortcut list is not ready");
        chords = next;
        // App shortcuts are an aggregate: enabled if any app shortcut is active.
        published = base.withNativeStates(seen, enabled);
        if (!published.encode().equals(ConfigStore.read(context).encode())) ConfigStore.write(context, published);
        java.util.Collections.sort(entries);
        String nextFingerprint = entries.toString();
        // The OEM widget writes again while binding. Do not turn identical writes into UI rebuild loops.
        if (!nextFingerprint.equals(fingerprint)) {
            fingerprint = nextFingerprint;
            Settings.Secure.putString(context.getContentResolver(), REVISION,
                    System.currentTimeMillis() + ":" + (++revision));
        }
    }
}
