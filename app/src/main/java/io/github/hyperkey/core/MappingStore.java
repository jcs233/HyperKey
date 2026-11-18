package io.github.hyperkey.core;
import android.content.Context;
import android.provider.Settings;
public final class MappingStore {
    private MappingStore() {}
    public static KeyMappings read(Context c) { return KeyMappings.decode(Settings.Secure.getString(c.getContentResolver(), KeyMappings.SETTING)); }
    public static void write(Context c, KeyMappings value) {
        if (!Settings.Secure.putString(c.getContentResolver(), KeyMappings.SETTING, value.encode())) throw new IllegalStateException("映射保存失败");
    }
}
