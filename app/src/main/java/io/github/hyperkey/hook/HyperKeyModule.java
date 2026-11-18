package io.github.hyperkey.hook;

import android.util.Log;
import io.github.libxposed.api.XposedModule;

/** Modern libxposed API 102 entry. API classes are compileOnly. */
public final class HyperKeyModule extends XposedModule {
    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        try { new SystemHooks(this).install(param.getClassLoader()); }
        catch (Throwable e) { log(Log.ERROR, "HyperKey", "System hook installation failed", e); }
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        String name = param.getPackageName();
        if (!"com.android.settings".equals(name) && !"com.miui.securitycore".equals(name)) return;
        try { new SettingsHooks(this).install(param.getClassLoader(), "com.miui.securitycore".equals(name)); }
        catch (Throwable e) { log(Log.ERROR, "HyperKey", "Settings hook installation failed", e); }
    }
}
