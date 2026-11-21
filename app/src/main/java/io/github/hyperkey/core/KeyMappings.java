package io.github.hyperkey.core;

import java.util.*;

/** Non-recursive mappings; zero modifiers means an exact bare key, not any chord. */
public final class KeyMappings {
    public static final String SETTING = "hyperkey_mappings_v1";
    public static final String GLOBAL = "*";
    public static final int MODIFIERS = 0x1100b; // Shift, Alt, Fn, Ctrl, Win
    public static final int MAX_ORDINARY = 8;
    public static final KeyMappings EMPTY = new KeyMappings(Collections.emptyList());
    public final List<Rule> rules;
    private final Set<Integer> prefixKeys = new HashSet<>();
    public boolean tracks(int key) { return prefixKeys.contains(key); }
    public KeyMappings(List<Rule> rules) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        for (Rule r : rules) prefixKeys.addAll(r.sourceHeld);
    }
    public record Rule(String owner, int sourceKey, int sourceModifiers, int targetKey, int targetModifiers,
                       String action, List<Integer> sourceHeld, List<Integer> targetHeld) {
        public Rule(String owner, int src, int sm, int dst, int dm, String action) {
            this(owner,src,sm,dst,dm,action,Collections.emptyList(),Collections.emptyList());
        }
        public Rule {
            List<Integer> sorted = new ArrayList<>(sourceHeld); Collections.sort(sorted);
            sourceHeld = Collections.unmodifiableList(sorted); // only the last (trigger) key has a required position
            targetHeld = Collections.unmodifiableList(new ArrayList<>(targetHeld));
        }
        public boolean localAction() { return action.startsWith("local_"); }
        public boolean keyTarget() { return action.isEmpty(); }
        public boolean systemAction() { return !keyTarget() && !localAction(); }
        public String id() { return owner + ":" + keys(sourceHeld,sourceKey) + ":" + sourceModifiers; }
    }
    private static String keys(List<Integer> held, int last) {
        StringBuilder s = new StringBuilder(); for (int k : held) s.append(k).append('+'); return s.append(last).toString();
    }
    public Rule find(String owner, int key, int mods) { return find(owner,key,mods,Collections.emptySet()); }
    public Rule find(String owner, int key, int mods, Set<Integer> held) {
        Rule best = null; boolean ambiguous = false;
        for (Rule r : rules) {
            if (r.sourceKey != key || r.sourceModifiers != (mods & MODIFIERS) ||
                    !(r.owner.equals(owner) || r.owner.equals(GLOBAL)) || !held.containsAll(r.sourceHeld)) continue;
            int rank = r.sourceHeld.size() + (r.owner.equals(owner) ? MAX_ORDINARY : 0);
            int old = best == null ? -1 : best.sourceHeld.size() + (best.owner.equals(owner) ? MAX_ORDINARY : 0);
            if (rank > old) { best = r; ambiguous = false; } else if (rank == old) ambiguous = true;
        }
        return ambiguous ? null : best;
    }
    public Rule forProfile(String pkg, AppProfiles profiles, int key, int mods) {
        return forProfile(pkg, profiles, key, mods, Collections.emptySet());
    }
    public Rule forProfile(String pkg, AppProfiles profiles, int key, int mods, Set<Integer> held) {
        AppProfiles.Profile p = profiles.entries.get(pkg);
        return find(p != null && p.enabled ? pkg : GLOBAL, key, mods, held);
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
        checkHeld(r.sourceHeld, r.sourceKey); checkHeld(r.targetHeld, r.targetKey);
        if ((!r.owner.equals(GLOBAL) && !AppProfiles.validPackage(r.owner)) || !keyAllowed(r.sourceKey) ||
                (r.sourceModifiers & ~MODIFIERS) != 0 || (r.targetModifiers & ~MODIFIERS) != 0 ||
                (!r.keyTarget() ? !(actionAllowed(r.action) || localId(r.action)) || r.targetKey != 0 || r.targetModifiers != 0 : !keyAllowed(r.targetKey)))
            throw new IllegalArgumentException("映射参数无效");
        if (r.keyTarget() && r.sourceKey == r.targetKey && r.sourceModifiers == r.targetModifiers && new HashSet<>(r.sourceHeld).equals(new HashSet<>(r.targetHeld)))
            throw new IllegalArgumentException("来源与目标相同");
    }
    private static void checkHeld(List<Integer> held, int last) {
        if (!held.isEmpty() && (last == 0 || KeyMeta.group(last) != 0 || held.size() >= MAX_ORDINARY || held.contains(last) ||
                new HashSet<>(held).size() != held.size())) throw new IllegalArgumentException("Invalid chord");
        for (int k : held) if (!keyAllowed(k) || KeyMeta.group(k) != 0) throw new IllegalArgumentException("Invalid held key");
    }
    private static int readKeys(String field, List<Integer> held) {
        String[] parts = field.split("\\+", -1);
        if (parts.length > MAX_ORDINARY) throw new IllegalArgumentException("Invalid chord size");
        for (int i=0;i<parts.length-1;i++) held.add(Integer.parseInt(parts[i]));
        return Integer.parseInt(parts[parts.length-1]);
    }
    public String encode() {
        StringBuilder out = new StringBuilder(rules.stream().anyMatch(r -> !r.sourceHeld.isEmpty() || !r.targetHeld.isEmpty() || ((r.sourceModifiers | r.targetModifiers) & 8) != 0 || r.sourceKey == 119 || r.targetKey == 119) ? "2" : "1");
        for (Rule r : rules) out.append(';').append(r.owner).append(':').append(keys(r.sourceHeld,r.sourceKey)).append(':').append(r.sourceModifiers)
                .append(':').append(keys(r.targetHeld,r.targetKey)).append(':').append(r.targetModifiers).append(':').append(r.action);
        return out.toString();
    }
    public static KeyMappings decode(String value) {
        if (value == null || value.length() > 65536) return EMPTY;
        String[] rows = value.split(";", -1);
        boolean v2 = rows[0].equals("2"); if ((!v2 && !rows[0].equals("1")) || rows.length > 257) return EMPTY;
        List<Rule> result = new ArrayList<>(); Set<String> seen = new HashSet<>();
        try {
            for (int i = 1; i < rows.length; i++) {
                String[] p = rows[i].split(":", -1);
                if (p.length != 6) return EMPTY;
                List<Integer> source = new ArrayList<>(), target = new ArrayList<>();
                Rule r = new Rule(p[0], v2 ? readKeys(p[1],source) : Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        v2 ? readKeys(p[3],target) : Integer.parseInt(p[3]), Integer.parseInt(p[4]), p[5], source, target);
                validate(r); if (!seen.add(r.id())) return EMPTY; result.add(r);
            }
            return new KeyMappings(result);
        } catch (IllegalArgumentException e) { return EMPTY; }
    }
}
