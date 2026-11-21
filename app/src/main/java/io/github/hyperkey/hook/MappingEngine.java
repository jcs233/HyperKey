package io.github.hyperkey.hook;

import android.os.Binder;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import io.github.hyperkey.core.KeyMeta;
import io.github.hyperkey.core.HeldChordKeys;
import io.github.hyperkey.core.TargetChord;
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
    private record Press(long downTime, KeyMappings.Rule rule, TargetChord target) {}
    private final HeldChordKeys held = new HeldChordKeys();
    private final TargetChord.Ledger ledger = new TargetChord.Ledger();
    private record Token(long downTime, long eventTime, int device, int key, int action, int repeat) {}
    private final Map<Long, Press> pressed = new HashMap<>();
    private final Map<Token, Long> outputs = new HashMap<>();
    private final RuntimeConfig config;
    private final ClassLoader loader;
    private Handler handler;
    MappingEngine(RuntimeConfig config, ClassLoader loader) { this.config = config; this.loader = loader; }
    private static Token token(KeyEvent e) { return new Token(e.getDownTime(), e.getEventTime(), e.getDeviceId(), e.getKeyCode(), e.getAction(), e.getRepeatCount()); }
    synchronized boolean output(KeyEvent e) {
        if ((e.getFlags() & KeyEvent.FLAG_FALLBACK) == 0) return false;
        Long expiry = outputs.get(token(e));
        return expiry != null && expiry > SystemClock.uptimeMillis();
    }
    private Handler handler() {
        if (handler == null) { HandlerThread thread = new HandlerThread("HyperKeyMappings"); thread.start(); handler = new Handler(thread.getLooper()); }
        return handler;
    }
    synchronized boolean handle(Object service, KeyEvent event, boolean bypass) {
        if (output(event) || !KeyRoute.isKeyboard(event) || (event.getFlags() & KeyEvent.FLAG_FALLBACK) != 0) return false;
        long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        Press press = pressed.get(key);
        boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if (!down && event.getAction()!=KeyEvent.ACTION_UP) return false;
        if (press != null && press.downTime != event.getDownTime()) {
            if (!down) return false; // late UP from an older stroke
            if (press.target!=null) press.target.emit(ledger,event.getDeviceId(),false,0,press.downTime,press.rule.targetModifiers(),
                    (k,t,m,r)->inject(service,event,k,t,KeyEvent.ACTION_UP,m,r));
            pressed.remove(key); press=null;
        }
        int device=event.getDeviceId(), code=event.getKeyCode();
        KeyMappings mappings=config.currentMappings(); held.scope(mappings,config.focusEpoch());
        if (bypass || config.actionScreen()) held.clear(device);
        if (bypass && press==null) return false;
        int normalized = KeyEvent.normalizeMetaState(event.getMetaState()) & KeyMappings.MODIFIERS;
        if (held.get(device).contains(KeyEvent.KEYCODE_FUNCTION)) normalized |= KeyEvent.META_FUNCTION_ON;
        int sourceModifiers = normalized & ~KeyMeta.group(code);
        if (press == null && !bypass && !config.actionScreen() && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            KeyMappings.Rule rule = config.mapping(code, sourceModifiers, held.get(device));
            if (rule != null && (rule.keyTarget() || !config.get().blockAll)) {
                press = new Press(event.getDownTime(), rule, rule.keyTarget() ? new TargetChord(rule, normalized) : null);
                if (pressed.size() > 128) return false;
                pressed.put(key, press);
            }
        }
        if (!bypass && !config.actionScreen()) held.update(device,code,down, event.getRepeatCount()==0 && (mappings.tracks(code) || (code==KeyEvent.KEYCODE_FUNCTION && !mappings.rules.isEmpty())));
        int transformedMeta = event.getMetaState();
        boolean modifierChanged = false;
        for (Map.Entry<Long, Press> item : pressed.entrySet()) {
            if ((int)(item.getKey() >> 32) != event.getDeviceId()) continue;
            KeyMappings.Rule r = item.getValue().rule;
            if (r.keyTarget() && r.sourceModifiers() == 0 && (KeyMeta.group(r.sourceKey()) != 0 || KeyMeta.group(r.targetKey()) != 0)) {
                transformedMeta = KeyMeta.remove(transformedMeta, KeyMeta.group(r.sourceKey()));
                if (!(event.getAction() == KeyEvent.ACTION_UP && r.sourceKey() == event.getKeyCode()))
                    transformedMeta |= KeyMeta.withSide(r.targetKey()) | r.targetModifiers();
                modifierChanged = true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_UP) pressed.remove(key);
        if (press == null && !modifierChanged) return false;
        if (press != null && !press.rule.keyTarget()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                Context owner = config.context();
                KeyMappings.Rule rule = press.rule;
                handler().post(() -> {
                    if (owner == null || owner != config.context()) return;
                    if (rule.localAction()) localAction(owner, rule);
                    else action(Shortcut.valueOf(rule.action()));
                });
            }
            return true;
        }
        if (press != null) {
            KeyMappings.Rule r=press.rule;
            transformedMeta=KeyMeta.remove(transformedMeta,r.sourceModifiers() & ~r.targetModifiers()) | r.targetModifiers();
            press.target.emit(ledger,device,down,event.getRepeatCount(),event.getDownTime(),transformedMeta,
                    (k,t,m,repeat)->inject(service,event,k,t,event.getAction(),m,repeat));
        } else inject(service,event,code,event.getDownTime(),event.getAction(),transformedMeta,event.getRepeatCount());
        return true;
    }
    private void inject(Object service,KeyEvent event,int target,long time,int action,int meta,int repeat) {
        KeyEvent mapped = new KeyEvent(time,event.getEventTime(),action,target,repeat,meta,event.getDeviceId(),
                target==event.getKeyCode()?event.getScanCode():0,event.getFlags()|KeyEvent.FLAG_FALLBACK,event.getSource());
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
    // Only an opaque reference crosses processes. The private Activity resolves it as the user who pressed the key.
    private void localAction(Context owner, KeyMappings.Rule rule) {
        try {
            KeyguardManager guard = owner.getSystemService(KeyguardManager.class);
            if (guard == null || guard.isKeyguardLocked() || guard.isDeviceLocked()) return;
            Intent intent = new Intent().setClassName("io.github.hyperkey", "io.github.hyperkey.ui.ShortcutActionActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    .putExtra("ref", rule.action()).putExtra("binding", rule.id());
            Reflect.call(owner, "startActivityAsUser", intent, Reflect.call(owner, "getUser"));
        } catch (ReflectiveOperationException | RuntimeException e) { Log.w("HyperKey", "Cannot open shortcut action", e); }
    }
}
