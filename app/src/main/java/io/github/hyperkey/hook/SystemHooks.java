package io.github.hyperkey.hook;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;
import io.github.hyperkey.core.Config;
import io.github.hyperkey.core.Shortcut;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class SystemHooks {
    private final HyperKeyModule module;
    private final RuntimeConfig config = new RuntimeConfig();
    private final KeyRoute route = new KeyRoute();
    private final List<String> missing = new ArrayList<>();
    private int installed;
    SystemHooks(HyperKeyModule module) { this.module = module; }

    void install(ClassLoader loader) {
        MappingEngine mappings = new MappingEngine(config, loader);
        String service = "com.android.server.input.InputManagerService";
        install(loader, service, "interceptKeyBeforeQueueing", chain -> {
            KeyEvent event = keyEvent(chain);
            if (event != null) {
                if (mappings.output(event) || route.testWin(event, config.keyTester())) return 1;
                if (KeyRoute.isKeyboard(event) && config.mappingEditor()) return 1;
                if (mappings.handle(chain.getThisObject(), event)) return 0;
                if (route.bypass(event, config.get(), rawWin(event))) return 1;
            }
            return chain.proceed();
        }, true);
        install(loader, service, "interceptKeyBeforeDispatching", chain -> {
            KeyEvent event = keyEvent(chain);
            if (event != null && (mappings.output(event) || route.testWin(event, config.keyTester()) || (KeyRoute.isKeyboard(event) && config.mappingEditor()) || route.bypass(event, config.get(), rawWin(event)))) return 0L;
            return chain.proceed();
        }, true);
        install(loader, service, "interceptUnhandledKey", chain -> {
            KeyEvent event = keyEvent(chain);
            if (event != null && (mappings.output(event) || route.testWin(event, config.keyTester()) || route.bypass(event, config.get(), rawWin(event)))) return false;
            return chain.proceed();
        }, true);
        NativeShortcutSync nativeSync = new NativeShortcutSync(loader);
        config.setNativeSync(nativeSync);
        install(loader, "com.miui.server.input.util.MiuiCustomizeShortCutUtils", "updateKeyboardShortcut", chain -> {
            synchronized (nativeSync) {
                Object result = chain.proceed();
                nativeSync.nativeChanged();
                return result;
            }
        }, true);
        String rule = "com.android.server.policy.KeyboardCombinationRule";
        install(loader, rule, "shouldInterceptKeys", chain -> {
            Shortcut shortcut = ruleShortcut(chain.getThisObject());
            if (shortcut != null && config.get().isBlocked(shortcut)) return false;
            // Disabled retained rules must pass through when no profile explicitly enables them.
            Object info = Reflect.field(chain.getThisObject(), "mKeyboardShortcutInfo");
            if (!Boolean.TRUE.equals(Reflect.call(info, "isEnable"))) return false;
            return chain.proceed();
        }, true);
        install(loader, rule, "execute", chain -> {
            Shortcut shortcut = ruleShortcut(chain.getThisObject());
            if (config.get().blockAll || (shortcut != null && config.get().isBlocked(shortcut))) return null;
            return chain.proceed();
        }, true);
        String extension = "com.android.server.policy.MiuiKeyInterceptExtend";
        install(loader, extension, "setFocusedWindow", chain -> {
            config.focus(chain.getArg(0));
            return chain.proceed();
        }, true);
        String manager = "com.android.server.input.KeyboardCombinationManagerStubImpl";
        install(loader, manager, "initRules", chain -> {
            Object result = chain.proceed();
            ProfileRules.complete(chain.getThisObject());
            return result;
        }, true);
        for (String name : new String[]{"addRule", "updateRule", "removeRule", "resetRule"}) {
            install(loader, manager, name, chain -> {
                Object result = chain.proceed();
                ProfileRules.afterMutation(chain.getThisObject());
                return result;
            }, true);
        }
        // Override the OEM enable query only inside dispatch. Persistence and Settings read real flags.
        install(loader, "com.miui.server.input.util.MiuiCustomizeShortCutUtils$MiuiKeyboardShortcutInfo", "isEnable", chain -> {
            Shortcut shortcut = Shortcut.forNativeType((int) Reflect.call(chain.getThisObject(), "getType"));
            Boolean override = config.nativeOverride(shortcut);
            return override == null ? chain.proceed() : override;
        }, true);
        for (String name : new String[]{"interceptKey", "isKeyConsumed"}) {
            install(loader, manager, name, chain -> {
                RuntimeConfig.Snapshot old = config.enter(route.snapshot(keyEvent(chain), config.snapshot()));
                try { return chain.proceed(); } finally { config.leave(old); }
            }, true);
        }
        for (String name : new String[]{"getKeyInterceptTypeBeforeQueueing", "getKeyInterceptTypeBeforeDispatching"}) {
            install(loader, extension, name, chain -> {
                RuntimeConfig.Snapshot old = config.enter(route.snapshot(keyEvent(chain), config.snapshot()));
                try {
                    KeyEvent event = keyEvent(chain);
                    if (event != null && route.bypass(event, config.get())) return 1;
                    return chain.proceed();
                } finally { config.leave(old); }
            }, true);
        }
        install(loader, extension, "getKeyboardShortcutEnable", chain ->
                config.get().blockAll ? false : chain.proceed(), true);
        install(loader, extension, "interceptMiuiKeyboard", chain -> {
            KeyEvent event = keyEvent(chain);
            if (event != null && KeyRoute.isKeyboard(event) && config.get().blockAll) return false;
            return chain.proceed();
        }, true);

        String policy = "com.android.server.policy.BaseMiuiPhoneWindowManager";
        install(loader, policy, "handleAltTab", chain -> {
            KeyEvent event = keyEvent(chain);
            // Always finish an existing session if the setting changes while Alt is held.
            if (event != null && event.getAction() == KeyEvent.ACTION_UP) return chain.proceed();
            return config.get().isBlocked(Shortcut.ALT_TAB) ? null : chain.proceed();
        }, true);
        install(loader, policy, "interceptKeyBeforeDispatching", chain -> {
            KeyEvent event = keyEvent(chain);
            if (event != null && config.get().blockAll && event.getAction() == KeyEvent.ACTION_UP &&
                    (event.getKeyCode() == KeyEvent.KEYCODE_ALT_LEFT || event.getKeyCode() == KeyEvent.KEYCODE_ALT_RIGHT)) {
                Reflect.call(chain.getThisObject(), "handleAltTab", event);
            }
            return chain.proceed();
        }, false);
        install(loader, policy, "showKeyboardShortcutsMenu", chain -> {
            if (chain.getArgs().size() == 3 && Boolean.TRUE.equals(chain.getArg(1)) &&
                    Boolean.TRUE.equals(chain.getArg(2)) && (config.keyTester() || config.get().isBlocked(Shortcut.META_HOLD))) return null;
            return chain.proceed();
        }, true);
        String input = "com.android.server.input.InputManagerServiceStubImpl";
        install(loader, input, "notifyKeyboardShortcutKey", chain -> {
            if ("KEY_META".equals(chain.getArg(0))) {
                if (config.keyTester()) return null;
                Object action = chain.getArg(1);
                if ("SINGLE_CLICK".equals(action) && config.get().isBlocked(Shortcut.META_SINGLE)) return null;
                if ("DOUBLE_CLICK".equals(action) && config.get().isBlocked(Shortcut.META_DOUBLE)) return null;
            }
            return chain.proceed();
        }, true);

        // Android 16/17 has an earlier AOSP gesture dispatcher. Return 0L to pass the key.
        String gestures = "com.android.server.input.KeyGestureController";
        install(loader, gestures, "interceptKeyBeforeDispatching", chain -> {
            KeyEvent event = keyEvent(chain);
            if (event != null && route.bypass(event, config.get())) return 0L;
            return chain.proceed();
        }, false);
        for (String name : new String[]{"interceptUnhandledKey", "interceptUnhandledShortcuts"}) {
            install(loader, gestures, name, chain -> {
                KeyEvent event = keyEvent(chain);
                if (event != null && route.bypass(event, config.get())) return false;
                return chain.proceed();
            }, false);
        }
        install(loader, gestures, "systemRunning", chain -> {
            Object result = chain.proceed();
            startConfig(chain.getThisObject());
            return result;
        }, false);
        // Older HyperOS fallback; start only once providers and system services are ready.
        install(loader, "com.android.server.policy.PhoneWindowManager", "systemReady", chain -> {
            Object result = chain.proceed();
            startConfig(chain.getThisObject());
            return result;
        }, true);
        install(loader, gestures, "setCurrentUserId", chain -> {
            Object result = chain.proceed();
            config.switchUser((int) chain.getArg(0));
            return result;
        }, false);
        module.log(Log.INFO, "HyperKey", "API 102: installed " + installed + " system hooks; missing=" + missing);
    }

    private void startConfig(Object owner) throws ReflectiveOperationException {
        Context context = (Context) Reflect.field(owner, "mContext");
        config.start(context, installed, missing.isEmpty() ? "系统拦截已加载" : "部分适配，缺少 " + missing.size() + " 个入口");
    }
    private boolean rawWin(KeyEvent event) {
        return KeyRoute.isKeyboard(event) && config.rawWin() &&
                (event.getKeyCode() == KeyEvent.KEYCODE_META_LEFT || event.getKeyCode() == KeyEvent.KEYCODE_META_RIGHT || event.isMetaPressed());
    }
    private Shortcut ruleShortcut(Object rule) throws ReflectiveOperationException {
        Object info = Reflect.field(rule, "mKeyboardShortcutInfo");
        return Shortcut.forNativeType((int) Reflect.call(info, "getType"));
    }
    private static KeyEvent keyEvent(XposedInterface.Chain chain) {
        for (Object arg : chain.getArgs()) if (arg instanceof KeyEvent event) return event;
        return null;
    }
    private void install(ClassLoader loader, String name, String methodName, XposedInterface.Hooker hook, boolean required) {
        try {
            Class<?> type = Class.forName(name, false, loader);
            boolean found = false;
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) continue;
                module.hook(method).setId("hyperkey:" + method.toGenericString())
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hook);
                installed++; found = true;
            }
            if (!found) throw new NoSuchMethodException(name + "." + methodName);
        } catch (Throwable e) {
            if (required) missing.add(methodName);
            module.log(required ? Log.WARN : Log.INFO, "HyperKey", "Unavailable hook: " + name + "." + methodName, e);
        }
    }
}
