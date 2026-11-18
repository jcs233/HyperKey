package io.github.hyperkey.core;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** One atomic setting; an absent bit inherits future global edits. No Android dependency. */
public final class AppProfiles {
    public static final String SETTING = "hyperkey_app_profiles_v1";
    public static final long ALL = (1L << Shortcut.values().length) - 1;
    public static final AppProfiles EMPTY = new AppProfiles(Collections.emptyMap());
    public final Map<String, Profile> entries;
    public AppProfiles(Map<String, Profile> entries) {
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
    }
    public static boolean validPackage(String name) {
        return name != null && name.length() <= 200 && name.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
    }
    public AppProfiles with(String name, Profile profile) {
        if (!validPackage(name)) throw new IllegalArgumentException("请输入有效应用包名");
        Map<String, Profile> result = new TreeMap<>(entries);
        if (profile == null) result.remove(name); else result.put(name, profile);
        if (result.size() > 128) throw new IllegalArgumentException("最多配置 128 个应用");
        return new AppProfiles(result);
    }
    public Config resolve(String name, Config global) {
        Profile profile = name == null ? null : entries.get(name);
        return profile == null ? global : profile.resolve(global);
    }
    public String encode() {
        StringBuilder out = new StringBuilder("1");
        entries.forEach((name, p) -> out.append(';').append(name).append(':').append(p.enabled ? '1' : '0')
                .append(':').append(Long.toHexString(p.overrideMask)).append(':').append(Long.toHexString(p.disabledMask))
                .append(':').append(p.rawWin ? '1' : '0'));
        return out.toString();
    }
    public static AppProfiles decode(String value) {
        if (value == null || value.length() > 32768) return EMPTY;
        String[] rows = value.split(";", -1);
        if (!rows[0].equals("1") || rows.length > 129) return EMPTY;
        Map<String, Profile> entries = new TreeMap<>();
        try {
            for (int i = 1; i < rows.length; i++) {
                String[] p = rows[i].split(":", -1);
                if ((p.length != 4 && p.length != 5) || !validPackage(p[0]) || entries.containsKey(p[0]) ||
                        !(p[1].equals("0") || p[1].equals("1")) ||
                        !p[2].matches("[0-9a-fA-F]{1,16}") || !p[3].matches("[0-9a-fA-F]{1,16}")) return EMPTY;
                long overrides = Long.parseUnsignedLong(p[2], 16), disabled = Long.parseUnsignedLong(p[3], 16);
                if ((overrides & ~ALL) != 0 || (disabled & ~overrides) != 0) return EMPTY;
                if (p.length == 5 && !p[4].equals("0") && !p[4].equals("1")) return EMPTY;
                entries.put(p[0], new Profile(p[1].equals("1"), overrides, disabled, p.length == 5 && p[4].equals("1")));
            }
            return new AppProfiles(entries);
        } catch (NumberFormatException ignored) { return EMPTY; }
    }
    public static final class Profile {
        public static final Profile INHERIT = new Profile(true, 0, 0);
        public final boolean enabled;
        public final long overrideMask, disabledMask;
        public final boolean rawWin;
        public Profile(boolean enabled, long overrides, long disabled) {
            this(enabled, overrides, disabled, false);
        }
        public Profile(boolean enabled, long overrides, long disabled, boolean rawWin) {
            this.enabled = enabled; this.overrideMask = overrides & ALL; this.disabledMask = disabled & this.overrideMask;
            this.rawWin = rawWin;
        }
        public boolean overrides(Shortcut shortcut) { return enabled && (overrideMask & shortcut.bit()) != 0; }
        public Config resolve(Config global) {
            if (!enabled) return global;
            return new Config((global.disabledMask & ~overrideMask) | disabledMask, global.blockAll);
        }
        public Profile withEnabled(boolean value) { return new Profile(value, overrideMask, disabledMask, rawWin); }
        public Profile withRawWin(boolean value) { return new Profile(enabled, overrideMask, disabledMask, value); }
        /** null clears the override. */
        public Profile withShortcut(Shortcut shortcut, Boolean allow) {
            long bit = shortcut.bit();
            if (allow == null) return new Profile(enabled, overrideMask & ~bit, disabledMask & ~bit, rawWin);
            return new Profile(enabled, overrideMask | bit, allow ? disabledMask & ~bit : disabledMask | bit, rawWin);
        }
        public Profile preset(Boolean allow) {
            return new Profile(enabled, allow == null ? 0 : ALL, Boolean.FALSE.equals(allow) ? ALL : 0, rawWin);
        }
    }
}
