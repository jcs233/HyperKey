package io.github.hyperkey.core;

import android.content.Context;
import android.provider.Settings;

public final class HookStatus {
    public static final String SYSTEM = "hyperkey_status_system";
    public static final String SETTINGS = "hyperkey_status_settings";
    private HookStatus() {}

    public static void mark(Context context, String key, int count, String detail) {
        int boot = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        Settings.Secure.putString(context.getContentResolver(), key,
                boot + "|" + System.currentTimeMillis() + "|" + count + "|" + detail);
    }
    public static String describe(Context context, String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            if (value == null) return UiText.text(context, "status_none");
            String[] fields = value.split("\\|", 4);
            if (fields.length != 4) return UiText.text(context, "status_invalid");
            int boot = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
            if (boot < 0 || Integer.parseInt(fields[0]) != boot) return UiText.text(context, "status_wait");
            boolean chinese = context.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
            boolean pending = fields[3].endsWith(";native_wait");
            String detail = pending ? fields[3].substring(0, fields[3].length() - ";native_wait".length()) : fields[3];
            boolean padMissing = detail.endsWith(";pad_unavailable");
            if (padMissing) detail = detail.substring(0, detail.length() - ";pad_unavailable".length());
            String summary = detail.startsWith("partial:") ? UiText.text(context, "hook_partial", fields[2], detail.substring(8)) :
                    chinese ? detail + "（" + fields[2] + " 个 Hook）" : UiText.text(context, "status_loaded", fields[2]);
            if (padMissing) summary += " · " + UiText.text(context, "touchpad_unavailable");
            return pending ? summary + " · " + UiText.text(context, "native_wait") : summary;
        } catch (RuntimeException ignored) { return UiText.text(context, "status_unreadable"); }
    }
}
