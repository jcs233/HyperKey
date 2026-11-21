package io.github.hyperkey.core;

import java.util.*;

/** Keeps only configured prefix keys (and an explicit Fn-down) while they are held. */
public final class HeldChordKeys {
    private final Map<Integer, Set<Integer>> devices = new HashMap<>();
    private Object rules; private long focus;
    public void scope(Object nextRules, long nextFocus) {
        if (rules != nextRules || focus != nextFocus) { devices.clear(); rules=nextRules; focus=nextFocus; }
    }
    public Set<Integer> get(int device) { return devices.getOrDefault(device, Collections.emptySet()); }
    public void update(int device, int key, boolean down, boolean track) {
        if (down && track) {
            if (devices.size() >= 64 && !devices.containsKey(device)) devices.clear();
            devices.computeIfAbsent(device, k -> new HashSet<>()).add(key);
        } else if (!down) {
            Set<Integer> keys=devices.get(device);
            if (keys!=null) { keys.remove(key); if (keys.isEmpty()) devices.remove(device); }
        }
    }
    public void clear(int device) { devices.remove(device); }
}
