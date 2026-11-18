package io.github.hyperkey.core;

/** Immutable, atomic, fail-open configuration. No Android dependency. */
public final class Config {
    public static final String SETTING = "hyperkey_config_v1";
    public static final Config DEFAULT = new Config(0L, false);
    private static final long KNOWN_BITS = (1L << Shortcut.values().length) - 1;
    public final long disabledMask;
    public final boolean blockAll;

    public Config(long disabledMask, boolean blockAll) {
        this.disabledMask = disabledMask & KNOWN_BITS;
        this.blockAll = blockAll;
    }

    public boolean isIndividuallyEnabled(Shortcut shortcut) {
        return (disabledMask & shortcut.bit()) == 0;
    }

    public boolean isBlocked(Shortcut shortcut) {
        return blockAll || !isIndividuallyEnabled(shortcut);
    }

    public Config withEnabled(Shortcut shortcut, boolean enabled) {
        return new Config(enabled ? disabledMask & ~shortcut.bit() : disabledMask | shortcut.bit(), blockAll);
    }

    public Config withBlockAll(boolean value) { return new Config(disabledMask, value); }
    public Config withNativeStates(long present, long enabled) {
        return new Config((disabledMask & ~present) | (present & ~enabled), blockAll);
    }
    public String encode() { return "1:" + Long.toHexString(disabledMask) + ":" + (blockAll ? "1" : "0"); }

    public static Config decode(String value) {
        if (value == null || value.length() > 80) return DEFAULT;
        String[] parts = value.split(":", -1);
        if (parts.length != 3 || !parts[0].equals("1") ||
                !(parts[2].equals("0") || parts[2].equals("1")) ||
                !parts[1].matches("[0-9a-fA-F]{1,16}")) return DEFAULT;
        try {
            long mask = Long.parseUnsignedLong(parts[1], 16);
            if ((mask & ~KNOWN_BITS) != 0) return DEFAULT;
            return new Config(mask, parts[2].equals("1"));
        } catch (NumberFormatException ignored) { return DEFAULT; }
    }
}
