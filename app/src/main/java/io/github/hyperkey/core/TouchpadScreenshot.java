package io.github.hyperkey.core;

import android.view.InputDevice;

/** Separate from keyboard overrides and from Xiaomi's global three-finger touchscreen setting. */
public final class TouchpadScreenshot {
    public static final String SETTING = "hyperkey_block_touchpad_three_down";
    private TouchpadScreenshot() {}

    public static boolean shouldBlock(boolean enabled, int fingers, int source, int deviceSources,
            int vendor, int product, String gesture, String function) {
        if (!enabled || fingers != 3 || !"three_gesture_down".equals(gesture) || !"screen_shot".equals(function)) return false;
        if (has(source, InputDevice.SOURCE_TOUCHPAD)) return true;
        // Android can label trackpad motion as mouse (or translate it into touch motion).
        // A hybrid device's touchscreen stream is ambiguous; leave it to the OEM.
        boolean mouse = has(source, InputDevice.SOURCE_MOUSE);
        if (!mouse && !has(source, InputDevice.SOURCE_TOUCHSCREEN)) return false;
        if (has(deviceSources, InputDevice.SOURCE_TOUCHSCREEN)) return false;
        return has(deviceSources, InputDevice.SOURCE_TOUCHPAD) || (vendor == 0x15d9 && product == 0x00a1);
    }
    private static boolean has(int sources, int kind) { return (sources & kind) == kind; }
}
