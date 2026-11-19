package io.github.hyperkey.core;

/** Pure key matching; lock bits and left/right modifier bits are normalized by the caller. */
public final class ShortcutPolicy {
    private ShortcutPolicy() {}

    public static boolean bypass(Config config, int keyCode, int modifiers) {
        int meaningfulModifiers = modifiers & 0x11003;
        for (Shortcut shortcut : Shortcut.values()) {
            if (shortcut.keyCode < 0 || !config.isBlocked(shortcut)) continue;
            int comparison = meaningfulModifiers;
            if (shortcut == Shortcut.ALT_TAB) comparison &= ~1; // reverse app switching
            if (shortcut.keyCode == keyCode && comparison == shortcut.modifiers) return true;
        }
        return false;
    }
}
