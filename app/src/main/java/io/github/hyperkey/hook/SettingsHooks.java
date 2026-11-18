package io.github.hyperkey.hook;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import io.github.hyperkey.core.Config;
import io.github.hyperkey.core.UiText;
import io.github.hyperkey.core.ConfigStore;
import io.github.hyperkey.core.HookStatus;
import io.github.hyperkey.core.Shortcut;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Host preferences and host resources: no duplicate miuix/AndroidX classes injected into Settings. */
final class SettingsHooks {
    private static final String MASTER_CATEGORY = "hyperkey_master_category";
    private final HyperKeyModule module;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Object, Binding> bindings = new WeakHashMap<>();
    private final Map<Object, WeakReference<Object>> screens = new WeakHashMap<>();
    private ClassLoader loader;
    private int hookCount;
    private boolean dedicated;
    private Class<?> dedicatedFragment;

    SettingsHooks(HyperKeyModule module) { this.module = module; }

    void install(ClassLoader classLoader, boolean securityCore) throws ReflectiveOperationException {
        loader = classLoader;
        dedicated = securityCore;
        // Discover the editor fragment from its named preference, avoiding obfuscated class names.
        if (dedicated) {
            Class<?> pref = Class.forName("com.miui.miinput.keyboard.MiuiKeyboardCustomPreference", false, loader);
            for (java.lang.reflect.Field field : pref.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type.getName().startsWith("android") || type.getName().startsWith("java")) continue;
                try { type.getMethod("getPreferenceScreen"); type.getDeclaredMethod("onResume"); dedicatedFragment = type; break; }
                catch (NoSuchMethodException ignored) { }
            }
            if (dedicatedFragment == null) throw new ClassNotFoundException("Dedicated keyboard editor fragment");
            for (Method method : pref.getDeclaredMethods()) {
                if (!method.getName().equals("onBindViewHolder")) continue;
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    Context context = (Context) Reflect.call(chain.getThisObject(), "getContext");
                    android.view.View item = (android.view.View) Reflect.field(chain.getArg(0), "itemView");
                    int id = context.getResources().getIdentifier("ks_key_checkbox", "id", context.getPackageName());
                    android.view.View toggle = item.findViewById(id);
                    if (toggle != null) toggle.setEnabled(!ConfigStore.read(context).blockAll);
                    return result;
                });
                hookCount++;
            }
        }
        Class<?> base = dedicated ? dedicatedFragment : Class.forName("com.android.settings.SettingsPreferenceFragment", false, loader);
        hook(base, "onResume", true);
        hook(base, "onPause", false);
        // The physical keyboard page rebuilds its preference tree asynchronously on device changes.
        Class<?> group = Class.forName("androidx.preference.PreferenceGroup", false, loader);
        Method clear = group.getDeclaredMethod(dedicated ? "j" : "removeAll");
        module.hook(clear).intercept(chain -> {
            Object result = chain.proceed();
            WeakReference<Object> reference = screens.get(chain.getThisObject());
            Object fragment = reference == null ? null : reference.get();
            if (fragment != null) schedule(fragment);
            return result;
        });
        hookCount++;
        module.log(Log.INFO, "HyperKey", "Settings lifecycle hooks installed");
    }

    private void hook(Class<?> base, String name, boolean resume) throws ReflectiveOperationException {
        module.hook(base.getDeclaredMethod(name)).intercept(chain -> {
            Object result = chain.proceed();
            Object fragment = chain.getThisObject();
            if (!isTarget(fragment)) return result;
            if (resume) schedule(fragment);
            else {
                Binding binding = bindings.remove(fragment);
                if (binding != null) binding.dispose();
            }
            return result;
        });
        hookCount++;
    }

    private boolean isTarget(Object fragment) {
        if (dedicated) return dedicatedFragment.isInstance(fragment);
        String name = fragment.getClass().getName();
        return name.equals("com.android.settings.stylus.MiuiPhysicalKeyboardFragment") ||
                name.equals("com.android.settings.stylus.MiuiKeyboardShortcutFragment") ||
                name.equals("com.android.settings.inputmethod.LargeScreenKeyboardPadFragment") ||
                name.equals("com.android.settings.inputmethod.PhysicalKeyboardFragment");
    }

    private void schedule(Object fragment) {
        WeakReference<Object> reference = new WeakReference<>(fragment);
        handler.postDelayed(() -> {
            Object current = reference.get();
            if (current == null) return;
            try {
                if (!Boolean.TRUE.equals(Reflect.call(current, "isResumed"))) return;
                attach(current);
            } catch (ReflectiveOperationException | RuntimeException e) {
                module.log(Log.WARN, "HyperKey", "Settings preferences could not attach", e);
            }
        }, 150L);
    }

    private void attach(Object fragment) throws ReflectiveOperationException {
        Object screen = Reflect.call(fragment, "getPreferenceScreen");
        if (screen == null) return;
        screens.put(screen, new WeakReference<>(fragment));
        Binding previous = bindings.remove(fragment);
        if (previous != null) previous.dispose();
        Context context = (Context) Reflect.call(screen, "getContext");
        Binding binding = new Binding(context.getApplicationContext(), fragment);
        Object masterGroup = category(screen, context, MASTER_CATEGORY, UiText.text(context, "host_category"), -500);
        Object master = preference(masterGroup, context, "hyperkey_block_all", UiText.text(context, "master"),
                UiText.text(context, "host_master_help"), true);
        binding.master = master;
        onChange(master, value -> {
            ConfigStore.write(binding.context, ConfigStore.read(binding.context).withBlockAll(value));
            binding.refresh();
        });
        Map<String, Object> groups = new LinkedHashMap<>();
        for (Shortcut shortcut : Shortcut.values()) {
            if (dedicated && shortcut.nativeType > 0 && shortcut.nativeType != 9 &&
                    shortcut.nativeType != 10 && shortcut.nativeType != 11 && shortcut.nativeType != 12) continue;
            Object parent = groups.get(shortcut.group);
            if (parent == null) {
                parent = category(screen, context, "hyperkey_group_" + groups.size(), UiText.group(context, shortcut.group),
                        (dedicated ? -300 : -499) + groups.size());
                groups.put(shortcut.group, parent);
            }
            Object pref = preference(parent, context, shortcut.key(), UiText.title(context, shortcut), UiText.hint(context, shortcut), true);
            binding.switches.put(shortcut, pref);
            onChange(pref, enabled -> {
                ConfigStore.write(binding.context, ConfigStore.read(binding.context).withEnabled(shortcut, enabled));
                binding.refresh();
            });
        }
        Object actions = category(screen, context, "hyperkey_actions", UiText.text(context, "module_management"), 1000);
        Object profiles = preference(actions, context, "hyperkey_profiles", UiText.text(context, "profiles"),
                UiText.text(context, "host_profile_help"), false);
        onClick(profiles, () -> context.startActivity(new android.content.Intent()
                .setClassName("io.github.hyperkey", "io.github.hyperkey.ui.ProfileActivity")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)));
        Object restore = preference(actions, context, "hyperkey_restore", UiText.text(context, "host_restore"),
                UiText.text(context, "host_restore_help"), false);
        onClick(restore, () -> {
            ConfigStore.restore(binding.context);
            binding.refresh();
            Toast.makeText(binding.context, UiText.text(context, "restored"), Toast.LENGTH_SHORT).show();
        });
        binding.status = preference(actions, context, "hyperkey_status", UiText.text(context, "host_status"), "", false);
        Reflect.call(binding.status, "setSelectable", false);
        bindings.put(fragment, binding);
        binding.observe();
        binding.refresh();
        HookStatus.mark(binding.context, HookStatus.SETTINGS, hookCount, "系统设置开关已注入");
    }

    private Object category(Object screen, Context context, String key, String title, int order) throws ReflectiveOperationException {
        Object existing = Reflect.call(screen, "findPreference", key);
        if (existing != null) return existing;
        Class<?> category = Class.forName("miuix.preference.PreferenceCategory", false, loader);
        Object value = category.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(context, null);
        Reflect.call(value, "setKey", key);
        Reflect.call(value, "setTitle", title);
        Reflect.call(value, "setOrder", order);
        Reflect.call(screen, "addPreference", value);
        return value;
    }

    private Object preference(Object parent, Context context, String key, String title, String summary, boolean toggle)
            throws ReflectiveOperationException {
        Object existing = Reflect.call(parent, "findPreference", key);
        if (existing != null) return existing;
        String name = toggle ? "androidx.preference.SwitchPreferenceCompat" : "miuix.preference.TextPreference";
        Class<?> prefClass = Class.forName(name, false, loader);
        Object pref;
        try { pref = prefClass.getConstructor(Context.class).newInstance(context); }
        catch (NoSuchMethodException e) { pref = prefClass.getConstructor(Context.class, android.util.AttributeSet.class).newInstance(context, null); }
        Reflect.call(pref, "setPersistent", false);
        Reflect.call(pref, "setKey", key);
        Reflect.call(pref, "setTitle", title);
        Reflect.call(pref, "setSummary", summary);
        Reflect.call(pref, "setIconSpaceReserved", false);
        if (toggle) {
            int widget = context.getResources().getIdentifier("miuix_preference_widget_switch_compat", "layout", context.getPackageName());
            if (widget != 0) Reflect.call(pref, "setWidgetLayoutResource", widget);
        }
        Reflect.call(parent, "addPreference", pref);
        return pref;
    }

    private interface Change { void apply(boolean value) throws ReflectiveOperationException; }
    private interface Click { void apply() throws ReflectiveOperationException; }

    private void onChange(Object pref, Change change) throws ReflectiveOperationException {
        Class<?> listener = listenerType(pref, "setOnPreferenceChangeListener");
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (self, method, args) -> {
            if (!method.getName().equals("onPreferenceChange")) return objectMethod(self, method.getName(), args);
            try { change.apply(Boolean.TRUE.equals(args[1])); return true; }
            catch (ReflectiveOperationException | RuntimeException e) {
                module.log(Log.WARN, "HyperKey", "Settings write failed", e);
                return false;
            }
        });
        Reflect.call(pref, "setOnPreferenceChangeListener", proxy);
    }
    private void onClick(Object pref, Click click) throws ReflectiveOperationException {
        Class<?> listener = listenerType(pref, "setOnPreferenceClickListener");
        Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (self, method, args) -> {
            if (!method.getName().equals("onPreferenceClick")) return objectMethod(self, method.getName(), args);
            try { click.apply(); return true; }
            catch (ReflectiveOperationException | RuntimeException e) {
                module.log(Log.WARN, "HyperKey", "Settings action failed", e);
                return false;
            }
        });
        Reflect.call(pref, "setOnPreferenceClickListener", proxy);
    }
    private static Object objectMethod(Object self, String name, Object[] args) {
        return switch (name) {
            case "equals" -> self == args[0];
            case "hashCode" -> System.identityHashCode(self);
            case "toString" -> "HyperKeyPreferenceListener";
            default -> null;
        };
    }
    private static Class<?> listenerType(Object pref, String setter) throws NoSuchMethodException {
        for (Method method : pref.getClass().getMethods()) {
            if (method.getName().equals(setter) && method.getParameterCount() == 1) return method.getParameterTypes()[0];
        }
        throw new NoSuchMethodException(setter);
    }

    private final class Binding {
        final Context context;
        final WeakReference<Object> fragment;
        String nativeRevision;
        final Map<Shortcut, Object> switches = new EnumMap<>(Shortcut.class);
        Object master;
        Object status;
        final ContentObserver observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                try { refresh(); } catch (ReflectiveOperationException | RuntimeException e) {
                    module.log(Log.WARN, "HyperKey", "Settings refresh failed", e);
                }
            }
        };
        Binding(Context context, Object fragment) { this.context = context; this.fragment = new WeakReference<>(fragment); }
        void observe() {
            context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Config.SETTING), false, observer);
            context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(HookStatus.SYSTEM), false, observer);
            context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(NativeShortcutSync.REVISION), false, observer);
        }
        void dispose() { context.getContentResolver().unregisterContentObserver(observer); }
        void refresh() throws ReflectiveOperationException {
            Config value = ConfigStore.read(context);
            if (dedicated) refreshNative(value);
            Reflect.call(master, "setChecked", value.blockAll);
            for (Map.Entry<Shortcut, Object> entry : switches.entrySet()) {
                Reflect.call(entry.getValue(), "setChecked", value.isIndividuallyEnabled(entry.getKey()));
                Reflect.call(entry.getValue(), "setEnabled", !value.blockAll);
            }
            Reflect.call(status, "setSummary", HookStatus.describe(context, HookStatus.SYSTEM));
        }
        void refreshNative(Config value) throws ReflectiveOperationException {
            Object owner = fragment.get();
            if (owner == null) return;
            String revision = Settings.Secure.getString(context.getContentResolver(), NativeShortcutSync.REVISION);
            if (!java.util.Objects.equals(nativeRevision, revision) || nativeRevision == null) {
                // Locate the native repository by its MiuiInputManager field, then reload its list.
                for (java.lang.reflect.Field field : dedicatedFragment.getDeclaredFields()) {
                    Class<?> type = field.getType();
                    boolean repository = false;
                    for (java.lang.reflect.Field candidate : type.getDeclaredFields()) {
                        if (candidate.getType().getName().equals("miui.hardware.input.MiuiInputManager")) repository = true;
                    }
                    if (!repository) continue;
                    field.setAccessible(true);
                    Reflect.call(field.get(owner), "d");
                    Reflect.call(owner, "r", true);
                    Reflect.call(owner, "r", false);
                    break;
                }
                nativeRevision = revision;
            }
            // Keep the native system shortcuts immediately after the master switch.
            order(owner, "keyboard_system_shortcut_key", -400);
            order(owner, "settings_keyboard_shortcut_key_title_esc", -100);
            order(owner, "keyboard_open_app", -90);
            for (Shortcut shortcut : Shortcut.values()) {
                if (shortcut.nativeType <= 0) continue;
                Object pref = Reflect.call(owner, "findPreference", Integer.toString(shortcut.nativeType));
                if (pref != null) {
                    Reflect.call(pref, "setChecked", value.isIndividuallyEnabled(shortcut));
                    Reflect.call(pref, "setEnabled", !value.blockAll);
                }
            }
        }
        void order(Object owner, String key, int value) throws ReflectiveOperationException {
            Object preference = Reflect.call(owner, "findPreference", key);
            if (preference != null) Reflect.call(preference, "setOrder", value);
        }
    }
}
