package io.github.hyperkey.core;

import java.util.*;

/** Non-recursive mappings; zero modifiers means an exact bare key, not any chord. */
public final class KeyMappings {
    public static final String SETTING = "hyperkey_mappings_v1";
    public static final String GLOBAL = "*";
    public static final int MODIFIERS = 0x11003;
    public static final KeyMappings EMPTY = new KeyMappings(Collections.emptyList());
    public final List<Rule> rules;
    public KeyMappings(List<Rule> rules) { this.rules = Collections.unmodifiableList(new ArrayList<>(rules)); }
    public record Rule(String owner, int sourceKey, int sourceModifiers, int targetKey, int targetModifiers, String action) {
        public boolean localAction() { return action.startsWith("local_"); }
        public boolean keyTarget() { return action.isEmpty(); }
        public boolean systemAction() { return !keyTarget() && !localAction(); }
        public String id() { return owner + ":" + sourceKey + ":" + sourceModifiers; }
    }
    public Rule find(String owner, int key, int modifiers) {
        Rule global = null;
        for (Rule r : rules) if (r.sourceKey == key && r.sourceModifiers == (modifiers & MODIFIERS)) {
            if (r.owner.equals(owner)) return r;
            if (r.owner.equals(GLOBAL)) global = r;
        }
        return global;
    }
    public Rule forProfile(String pkg, AppProfiles profiles, int key, int modifiers) {
        AppProfiles.Profile p = profiles.entries.get(pkg);
        return find(p != null && p.enabled ? pkg : GLOBAL, key, modifiers);
    }
    public static boolean localId(String id) { return id != null && id.matches("local_[0-9a-f]{32}"); }
    public KeyMappings put(Rule rule) {
        validate(rule);
        List<Rule> result = new ArrayList<>(rules);
        result.removeIf(r -> r.id().equals(rule.id())); result.add(rule);
        if (result.size() > 256) throw new IllegalArgumentException("最多 256 条映射");
        return new KeyMappings(result);
    }
    public KeyMappings remove(String id) {
        List<Rule> result = new ArrayList<>(rules); result.removeIf(r -> r.id().equals(id)); return new KeyMappings(result);
    }
    public static boolean keyAllowed(int key) {
        // Exclude power, system navigation, volume, wakeup, sleep and OEM private keycodes.
        return key >= 7 && key <= 288 && key != 24 && key != 25 && key != 26 && key != 82 && key != 164 &&
                key != 187 && key != 219 && key != 223 && key != 224;
    }
    public static boolean actionAllowed(String name) {
        try { Shortcut s = Shortcut.valueOf(name); return s.nativeType > 0 && s != Shortcut.ALT_TAB; }
        catch (IllegalArgumentException e) { return false; }
    }
    private static void validate(Rule r) {
        if ((!r.owner.equals(GLOBAL) && !AppProfiles.validPackage(r.owner)) || !keyAllowed(r.sourceKey) ||
                (r.sourceModifiers & ~MODIFIERS) != 0 || (r.targetModifiers & ~MODIFIERS) != 0 ||
                (!r.keyTarget() ? !(actionAllowed(r.action) || localId(r.action)) || r.targetKey != 0 || r.targetModifiers != 0 : !keyAllowed(r.targetKey)))
            throw new IllegalArgumentException("映射参数无效");
        if (r.keyTarget() && r.sourceKey == r.targetKey && r.sourceModifiers == r.targetModifiers)
            throw new IllegalArgumentException("来源与目标相同");
    }
    public String encode() {
        StringBuilder out = new StringBuilder("1");
        for (Rule r : rules) out.append(';').append(r.owner).append(':').append(r.sourceKey).append(':').append(r.sourceModifiers)
                .append(':').append(r.targetKey).append(':').append(r.targetModifiers).append(':').append(r.action);
        return out.toString();
    }
    public static KeyMappings decode(String value) {
        if (value == null || value.length() > 65536) return EMPTY;
        String[] rows = value.split(";", -1);
        if (!rows[0].equals("1") || rows.length > 257) return EMPTY;
        List<Rule> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        try {
            for (int i = 1; i < rows.length; i++) {
                String[] p = rows[i].split(":", -1);
                if (p.length != 6) return EMPTY;
                Rule r = new Rule(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), p[5]);
                validate(r); if (!seen.add(r.id())) return EMPTY; result.add(r);
            }
            return new KeyMappings(result);
        } catch (IllegalArgumentException e) { return EMPTY; }
    }
}
