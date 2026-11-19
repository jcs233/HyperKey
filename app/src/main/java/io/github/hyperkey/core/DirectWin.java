package io.github.hyperkey.core;

import java.util.concurrent.ConcurrentHashMap;

/** Remembers explicit raw Win presses through repeat, dispatch and release, even if focus or meta changes. */
public final class DirectWin {
    private final ConcurrentHashMap<Long, Long> held = new ConcurrentHashMap<>();
    public boolean pass(int device, int code, long downTime, boolean firstDown, boolean winHeld, boolean enabled) {
        long key = ((long)device << 32) | (code & 0xffffffffL);
        Long old = held.get(key);
        if (old != null && old == downTime) return true;
        if (old != null) held.remove(key,old);
        if (!enabled || !firstDown || !(code == 117 || code == 118 || winHeld)) return false;
        if (held.size() >= 512) held.clear();
        held.put(key,downTime); return true;
    }
}
