package io.github.hyperkey.hook;

import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import io.github.hyperkey.core.TouchpadScreenshot;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

final class TouchpadScreenshotHook {
    private static final String BASE = "com.miui.server.input.gesture.multifingergesture.gesture.";
    private TouchpadScreenshotHook() {}

    static boolean install(HyperKeyModule module, ClassLoader loader, RuntimeConfig config) {
        try {
            Class<?> down = Class.forName(BASE + "impl.MiuiThreeFingerDownGesture", false, loader);
            Class<?> base = Class.forName(BASE + "BaseMiuiMultiFingerGesture", false, loader);
            Method init = down.getMethod("initGesture", MotionEvent.class);
            Method key = down.getMethod("getGestureKey"), action = down.getMethod("getGestureFunction");
            Method reject = base.getDeclaredMethod("checkFail");
            if (init.getReturnType()!=void.class || reject.getReturnType()!=void.class ||
                    key.getReturnType()!=String.class || action.getReturnType()!=String.class) return false;
            reject.setAccessible(true);
            module.hook(init).setId("hyperkey:touchpad-three-down").setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object recognizer = chain.getThisObject();
                // initGesture is inherited on piano. Never veto a sibling (or upward subclass).
                if (config.blockTouchpadScreenshot() && recognizer.getClass() == down && chain.getArg(0) instanceof MotionEvent e && e.getPointerCount()==3) {
                    InputDevice d = e.getDevice();
                    if (d != null && !d.isVirtual() && TouchpadScreenshot.shouldBlock(true, e.getPointerCount(), e.getSource(), d.getSources(),
                            d.getVendorId(), d.getProductId(), (String)key.invoke(recognizer), (String)action.invoke(recognizer))) {
                        // Mirror a failed OEM precondition before this recognizer can claim the pointers.
                        reject.invoke(recognizer); return null;
                    }
                }
                return chain.proceed();
            });
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            module.log(Log.INFO,"HyperKey","Touchpad screenshot recognizer unavailable",ex); return false;
        }
    }
}
