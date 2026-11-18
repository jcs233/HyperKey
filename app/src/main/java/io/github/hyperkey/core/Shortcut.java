package io.github.hyperkey.core;

/** Stable ordering is part of the v1 configuration format. Append new entries only. */
public enum Shortcut {
    CLOSE_WINDOW("关闭当前窗口", "⌘ W", 8, 51, 0x10000, "系统快捷键"),
    PARTIAL_SCREENSHOT("区域截屏", "⌘ Shift A", 7, 29, 0x10001, "系统快捷键"),
    SCREENSHOT("截全屏", "⌘ A", 6, 29, 0x10000, "系统快捷键"),
    LOCK_SCREEN("锁屏", "⌘ L", 5, 40, 0x10000, "系统快捷键"),
    HOME("回到桌面", "⌘ D", 4, 32, 0x10000, "系统快捷键"),
    SEARCH("全局搜索", "⌘ S", 14, 47, 0x10000, "系统快捷键"),
    RECENTS("显示最近任务", "⌘ Tab", 3, 61, 0x10000, "系统快捷键"),
    ALT_TAB("切换应用", "Alt Tab / Alt Shift Tab", 13, 61, 2, "系统快捷键"),
    NOTIFICATIONS("打开通知中心", "⌘ N", 2, 42, 0x10000, "系统快捷键"),
    CONTROL_CENTER("打开控制中心", "⌘ M", 1, 41, 0x10000, "系统快捷键"),
    SMALL_WINDOW("进入小窗", "⌘ ↓", 9, 20, 0x10000, "窗口操作"),
    FULLSCREEN("进入全屏", "⌘ ↑", 10, 19, 0x10000, "窗口操作"),
    SPLIT_LEFT("进入分屏 / 向左调整比例", "⌘ ←", 11, 21, 0x10000, "窗口操作"),
    SPLIT_RIGHT("进入分屏 / 向右调整比例", "⌘ →", 12, 22, 0x10000, "窗口操作"),
    META_SINGLE("显示全局任务栏", "单击 ⌘", -1, -1, 0, "任务键"),
    META_DOUBLE("快切最近应用", "双击 ⌘", -1, -1, 0, "任务键"),
    META_HOLD("显示快捷键面板", "长按 ⌘", -1, -1, 0, "任务键"),
    ESCAPE("Esc 系统操作", "关闭后将原始 Esc 交给当前应用", -1, 111, 0, "其他快捷键"),
    CUSTOM_APPS("打开应用快捷键", "统一控制系统中自定义的启动应用组合键", 0, -1, 0, "其他快捷键");

    public final String title;
    public final String hint;
    public final int nativeType;
    public final int keyCode;
    public final int modifiers;
    public final String group;

    Shortcut(String title, String hint, int nativeType, int keyCode, int modifiers, String group) {
        this.title = title; this.hint = hint; this.nativeType = nativeType;
        this.keyCode = keyCode; this.modifiers = modifiers; this.group = group;
    }

    public long bit() { return 1L << ordinal(); }
    public String key() { return "hyperkey_" + name().toLowerCase(java.util.Locale.ROOT); }

    public static Shortcut forNativeType(int type) {
        for (Shortcut shortcut : values()) if (shortcut.nativeType == type && type >= 0) return shortcut;
        return null;
    }
}
