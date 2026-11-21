package io.github.hyperkey.core;

import android.view.InputDevice;
import org.junit.Test;
import static org.junit.Assert.*;

public class TouchpadScreenshotTest {
    private static final int PAD=InputDevice.SOURCE_TOUCHPAD, MOUSE=InputDevice.SOURCE_MOUSE, SCREEN=InputDevice.SOURCE_TOUCHSCREEN;
    private static final String DOWN="three_gesture_down", SHOT="screen_shot";
    @Test public void onlyBlocksEnabledThreeFingerDownFullScreenshot() {
        assertTrue(TouchpadScreenshot.shouldBlock(true,3,MOUSE,MOUSE|PAD,0,0,DOWN,SHOT));
        assertFalse(TouchpadScreenshot.shouldBlock(false,3,MOUSE,MOUSE|PAD,0,0,DOWN,SHOT));
        for (int n : new int[]{1,2,4}) assertFalse(TouchpadScreenshot.shouldBlock(true,n,PAD,PAD,0,0,DOWN,SHOT));
        for (String other : new String[]{"three_gesture_up","three_gesture_long_press","three_gesture_horizontal_drag",null})
            assertFalse(TouchpadScreenshot.shouldBlock(true,3,PAD,PAD,0,0,other,SHOT));
        for (String other : new String[]{"partial_screen_shot","launch_home","none",null})
            assertFalse(TouchpadScreenshot.shouldBlock(true,3,PAD,PAD,0,0,DOWN,other));
    }
    @Test public void touchscreenMouseAndAmbiguousDevicesStayUntouched() {
        assertFalse(TouchpadScreenshot.shouldBlock(true,3,SCREEN,SCREEN,0,0,DOWN,SHOT));
        assertFalse(TouchpadScreenshot.shouldBlock(true,3,SCREEN,SCREEN|PAD,0x15d9,0xa1,DOWN,SHOT));
        assertFalse(TouchpadScreenshot.shouldBlock(true,3,MOUSE,MOUSE,1,2,DOWN,SHOT));
        assertFalse(TouchpadScreenshot.shouldBlock(true,3,InputDevice.SOURCE_KEYBOARD,MOUSE|PAD,0x15d9,0xa1,DOWN,SHOT));
        assertTrue(TouchpadScreenshot.shouldBlock(true,3,PAD,SCREEN|PAD,0,0,DOWN,SHOT));
    }
    @Test public void recognizesPianoPadEvenWhenItsMotionIsTranslated() {
        assertTrue(TouchpadScreenshot.shouldBlock(true,3,SCREEN,MOUSE|PAD,0x15d9,0xa1,DOWN,SHOT));
        assertTrue(TouchpadScreenshot.shouldBlock(true,3,MOUSE,MOUSE,0x15d9,0xa1,DOWN,SHOT));
        assertFalse(TouchpadScreenshot.shouldBlock(true,3,MOUSE,MOUSE,0x15d9,0xa4,DOWN,SHOT));
    }
}
