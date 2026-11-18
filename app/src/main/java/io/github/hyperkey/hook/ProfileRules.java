package io.github.hyperkey.hook;

import android.content.Context;
import android.os.Handler;
import android.util.LongSparseArray;
import android.util.Log;
import io.github.hyperkey.core.Shortcut;
import java.lang.reflect.Constructor;

/** Retain disabled OEM mappings in memory so profiles can enable them without changing native data. */
final class ProfileRules {
    private ProfileRules() {}
    static void complete(Object manager) {
        try {
            Object lock = Reflect.field(manager, "mLock");
            synchronized (lock) {
                Object utility = Reflect.field(manager, "mMiuiCustomizeShortCutUtils");
                LongSparseArray<?> infos = ((LongSparseArray<?>) Reflect.call(utility, "getMiuiKeyboardShortcutInfo")).clone();
                @SuppressWarnings("unchecked") LongSparseArray<Object> rules = (LongSparseArray<Object>) Reflect.field(manager, "mKeyboardShortcutRules");
                Context context = (Context) Reflect.field(manager, "mContext");
                Handler handler = (Handler) Reflect.field(manager, "mHandler");
                Class<?> rule = Class.forName("com.android.server.policy.KeyboardCombinationRule", false, manager.getClass().getClassLoader());
                for (int i = 0; i < infos.size(); i++) {
                    Object info = infos.valueAt(i);
                    if (info == null || Shortcut.forNativeType((int) Reflect.call(info, "getType")) == null || rules.get(infos.keyAt(i)) != null) continue;
                    Constructor<?> constructor = rule.getDeclaredConstructor(Context.class, Handler.class, info.getClass());
                    constructor.setAccessible(true);
                    rules.put(infos.keyAt(i), constructor.newInstance(context, handler, info));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) { Log.w("HyperKey", "Cannot complete profile rules", e); }
    }
    static void afterMutation(Object manager) throws ReflectiveOperationException {
        ((Handler) Reflect.field(manager, "mHandler")).post(() -> complete(manager));
    }
}
