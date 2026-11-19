package io.github.hyperkey.hook;

import android.view.InputDevice;
import android.view.KeyEvent;
import io.github.hyperkey.core.Config;
import java.util.concurrent.ConcurrentHashMap;

/** Reuse a down decision for repeats and up, even after modifier release/config changes. */
final class KeyRoute {
    private final RuntimeConfig runtime;
    KeyRoute(RuntimeConfig runtime) { this.runtime = runtime; }
    private record Decision(long downTime, boolean bypass, RuntimeConfig.Snapshot snapshot) {}
    private final ConcurrentHashMap<Long, Decision> decisions = new ConcurrentHashMap<>();

    // Retain the original DOWN decision for the queue, dispatch and unhandled stages.
    // Win may be released before its companion key; do not let a late UP reach OEM policy.
    private final io.github.hyperkey.core.DirectWin direct = new io.github.hyperkey.core.DirectWin();
    boolean directWin(KeyEvent event, boolean enabled) {
        return isKeyboard(event) && direct.pass(event.getDeviceId(), event.getKeyCode(), event.getDownTime(),
                event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0, event.isMetaPressed(), enabled);
    }

    static boolean isKeyboard(KeyEvent event) {
        InputDevice device = event.getDevice();
        return device != null && !device.isVirtual() &&
                device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                device.supportsSource(InputDevice.SOURCE_KEYBOARD);
    }

    boolean bypass(KeyEvent event, Config config) {
        return bypass(event, config, false);
    }
    boolean bypass(KeyEvent event, Config config, boolean forceWin) {
        if (!isKeyboard(event)) return false;
        long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        Decision existing = decisions.get(key);
        if (existing != null && existing.downTime == event.getDownTime()) return existing.bypass;
        boolean bypass = forceWin || runtime.bypass(config, event.getKeyCode(), KeyEvent.normalizeMetaState(event.getMetaState()));
        if (decisions.size() > 512) decisions.clear();
        decisions.put(key, new Decision(event.getDownTime(), bypass, null));
        return bypass;
    }

    RuntimeConfig.Snapshot snapshot(KeyEvent event, RuntimeConfig.Snapshot fallback) {
        if (event == null || !isKeyboard(event)) return fallback;
        long key = ((long) event.getDeviceId() << 32) | (event.getKeyCode() & 0xffffffffL);
        Decision existing = decisions.get(key);
        if (existing != null && existing.downTime == event.getDownTime() && existing.snapshot != null) return existing.snapshot;
        boolean bypass = existing != null && existing.downTime == event.getDownTime() ? existing.bypass :
                runtime.bypass(fallback.config(), event.getKeyCode(), KeyEvent.normalizeMetaState(event.getMetaState()));
        if (decisions.size() > 512) decisions.clear();
        decisions.put(key, new Decision(event.getDownTime(), bypass, fallback));
        return fallback;
    }
}
