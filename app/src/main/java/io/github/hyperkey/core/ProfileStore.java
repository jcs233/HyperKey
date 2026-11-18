package io.github.hyperkey.core;

import android.content.Context;
import android.provider.Settings;

public final class ProfileStore {
    private ProfileStore() {}
    public static AppProfiles read(Context context) {
        return AppProfiles.decode(Settings.Secure.getString(context.getContentResolver(), AppProfiles.SETTING));
    }
    public static void write(Context context, AppProfiles profiles) {
        if (!Settings.Secure.putString(context.getContentResolver(), AppProfiles.SETTING, profiles.encode()))
            throw new IllegalStateException("系统未保存应用名单");
    }
}
