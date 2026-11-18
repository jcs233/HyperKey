package io.github.hyperkey.core;

import android.content.Context;
import android.content.res.Resources;
import java.util.Locale;

/** Resource lookup also works inside host Settings without injecting module resource IDs. */
public final class UiText {
    private UiText() {}
    public static String text(Context context, String name, Object... args) {
        try {
            Context module = "io.github.hyperkey".equals(context.getPackageName()) ? context :
                    context.createPackageContext("io.github.hyperkey", 0).createConfigurationContext(context.getResources().getConfiguration());
            Resources resources = module.getResources();
            int id = ResourceIds.stringId(name);
            return id == 0 ? name : resources.getString(id, args);
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException e) { return name; }
    }
    public static String title(Context context, Shortcut shortcut) { return text(context, "shortcut_" + shortcut.ordinal()); }
    public static String hint(Context context, Shortcut shortcut) {
        return switch (shortcut) {
            case META_SINGLE -> text(context, "hint_single"); case META_DOUBLE -> text(context, "hint_double");
            case META_HOLD -> text(context, "hint_hold"); case ESCAPE -> text(context, "hint_escape");
            case CUSTOM_APPS -> text(context, "hint_apps"); default -> shortcut.hint;
        };
    }
    public static String group(Context context, String group) {
        return text(context, switch (group) {
            case "系统快捷键" -> "group_system"; case "窗口操作" -> "group_window";
            case "任务键" -> "group_meta"; default -> "group_other";
        });
    }
}
