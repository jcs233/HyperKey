package io.github.hyperkey.core;

import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

public final class ConfigStore {
    private ConfigStore() {}
    public static Config read(Context context) {
        return Config.decode(Settings.Secure.getString(context.getContentResolver(), Config.SETTING));
    }
    public static boolean canWrite(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
    }
    public static void write(Context context, Config config) {
        if (!Settings.Secure.putString(context.getContentResolver(), Config.SETTING, config.encode())) {
            throw new IllegalStateException("系统未保存快捷键配置");
        }
    }
    public static void restore(Context context) { write(context, Config.DEFAULT); }
}
