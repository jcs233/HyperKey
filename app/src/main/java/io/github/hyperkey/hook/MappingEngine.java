package io.github.hyperkey.hook;

import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import io.github.hyperkey.core.KeyMappings;
import io.github.hyperkey.core.Shortcut;
import java.util.*;

/** Pairs transformed down/up events and never feeds output back into mapping lookup. */
final class MappingEngine {
    private record Press(long downTime, KeyMappings.Rule rule) {}
    private record Token(long downTime, long eventTime, int device, int key, int action, int repeat) {}
    private final Map<Long, Press> pressed = new HashMap<>();
    private final Map<Token, Long> outputs = new HashMap<>();
    private final RuntimeConfig config;
    private final ClassLoader loader;
    private Handler handler;
    MappingEngine(RuntimeConfig config, ClassLoader loader) { this.config = config; this.loader = loader; }
    private static Token token(KeyEvent e) { return new Token(e.getDownTime(), e.getEventTime(), e.getDeviceId(), e.getKeyCode(), e.getAction(), e.getRepeatCount()); }
    synchronized boolean output(KeyEvent e) {
        Long expiry = outputs.get(token(e));
        return expiry != null && expiry > SystemClock.uptimeMillis();
    }
    private Handler handler() {
        if (handler == null) { HandlerThread thread = new HandlerThread("HyperKeyMappings"); thread.start(); handler = new Handler(thread.getLooper()); }
        return handler;
    }
    synchronized boolean handle(Object service, KeyEvent event) {
        if (output(event) || !KeyRoute.isKeyboard(event) || (event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0) return false;
        long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        Press press = pressed.get(key);
        if (press != null && press.downTime != event.getDownTime()) { pressed.remove(key); press = null; }
        int normalized = KeyEvent.normalizeMetaState(event.getMetaState()) & KeyMappings.MODIFIERS;
        int sourceModifiers = normalized & ~modifier(event.getKeyCode());
        if (press == null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            KeyMappings.Rule rule = config.mapping(event.getKeyCode(), sourceModifiers);
            if (rule != null && (!rule.systemAction() || !config.get().blockAll)) {
                press = new Press(event.getDownTime(), rule);
                if (pressed.size() > 128) return false;
                pressed.put(key, press);
            }
        }
        int transformedMeta = normalized;
        boolean modifierChanged = false;
        for (Map.Entry<Long, Press> item : pressed.entrySet()) {
            if ((int)(item.getKey() >> 32) != event.getDeviceId()) continue;
            KeyMappings.Rule r = item.getValue().rule;
            if (!r.systemAction() && r.sourceModifiers() == 0 && (modifier(r.sourceKey()) != 0 || modifier(r.targetKey()) != 0)) {
                transformedMeta &= ~modifier(r.sourceKey());
                if (!(event.getAction() == KeyEvent.ACTION_UP && r.sourceKey() == event.getKeyCode()))
                    transformedMeta |= modifier(r.targetKey()) | r.targetModifiers();
                modifierChanged = true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_UP) pressed.remove(key);
        if (press == null && !modifierChanged) return false;
        if (press != null && press.rule.systemAction()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                Shortcut action = Shortcut.valueOf(press.rule.action());
                handler().post(() -> action(action));
            }
            return true;
        }
        int target = press == null ? event.getKeyCode() : press.rule.targetKey();
        if (press != null && modifier(target) == 0)
            transformedMeta = (transformedMeta & ~press.rule.sourceModifiers()) | press.rule.targetModifiers();
        KeyEvent mapped = new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), target,
                event.getRepeatCount(), transformedMeta, event.getDeviceId(), 0,
                event.getFlags() | KeyEvent.FLAG_FALLBACK, event.getSource());
        try { Reflect.call(mapped, "setDisplayId", Reflect.call(event, "getDisplayId")); }
        catch (ReflectiveOperationException ignored) { }
        long now = SystemClock.uptimeMillis();
        outputs.entrySet().removeIf(item -> item.getValue() < now);
        outputs.put(token(mapped), now + 60000);
        handler().post(() -> {
            long identity = Binder.clearCallingIdentity();
            try {
                if (!Boolean.TRUE.equals(Reflect.call(service, "injectInputEvent", mapped, 0))) Log.w("HyperKey", "Mapped event injection refused");
            } catch (ReflectiveOperationException | RuntimeException e) { Log.w("HyperKey", "Mapping injection failed", e); }
            finally { Binder.restoreCallingIdentity(identity); }
        });
        return true;
    }
    private void action(Shortcut action) {
        try {
            if (config.context() == null) return;
            String function = switch (action) {
                case HOME -> "launch_home"; case RECENTS, ALT_TAB -> "launch_recents";
                case CLOSE_WINDOW -> "close_app"; case SEARCH -> "launch_global_search";
                case LOCK_SCREEN -> "go_to_sleep"; case SCREENSHOT -> "screen_shot";
                case PARTIAL_SCREENSHOT -> "partial_screen_shot"; case NOTIFICATIONS -> "launch_notification_center";
                case CONTROL_CENTER -> "launch_control_center"; case SMALL_WINDOW -> "launch_app_small_window";
                case FULLSCREEN -> "launch_app_full_window"; case SPLIT_LEFT -> "launch_split_screen_to_left";
                case SPLIT_RIGHT -> "launch_split_screen_to_right";
                default -> "";
            };
            Class<?> type = Class.forName("com.miui.server.input.util.ShortCutActionsUtils", false, loader);
            Object utility = type.getMethod("getInstance", android.content.Context.class).invoke(null, config.context());
            Reflect.call(utility, "triggerFunction", function, "hyperkey_mapping", null, false);
        } catch (ReflectiveOperationException | RuntimeException e) { Log.w("HyperKey", "Mapped system action failed", e); }
    }
    private static int modifier(int key) {
        return switch (key) { case 57, 58 -> 2; case 59, 60 -> 1; case 113, 114 -> 0x1000; case 117, 118 -> 0x10000; default -> 0; };
    }
}
