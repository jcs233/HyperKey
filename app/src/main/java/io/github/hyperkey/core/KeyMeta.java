package io.github.hyperkey.core;

/** Match logical modifiers but preserve unrelated directional, lock and OEM bits on output. */
public final class KeyMeta {
    private KeyMeta() {}
    public static int group(int key) {
        return switch (key) { case 57,58 -> 2; case 59,60 -> 1; case 113,114 -> 0x1000; case 117,118 -> 0x10000; default -> 0; };
    }
    public static int withSide(int key) {
        return group(key) | switch (key) { case 57 -> 0x10; case 58 -> 0x20; case 59 -> 0x40; case 60 -> 0x80;
            case 113 -> 0x2000; case 114 -> 0x4000; case 117 -> 0x20000; case 118 -> 0x40000; default -> 0; };
    }
    public static int remove(int meta, int groups) {
        int mask = groups;
        if ((groups & 1) != 0) mask |= 0xC0;
        if ((groups & 2) != 0) mask |= 0x30;
        if ((groups & 0x1000) != 0) mask |= 0x6000;
        if ((groups & 0x10000) != 0) mask |= 0x60000;
        return meta & ~mask;
    }
}
