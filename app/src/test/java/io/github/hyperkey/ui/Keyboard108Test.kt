package io.github.hyperkey.ui

import android.view.KeyEvent.*
import io.github.hyperkey.core.KeyMappings
import org.junit.Assert.*
import org.junit.Test

class Keyboard108Test {
    @Test fun ansi108PlusFnHasDistinctNonOverlappingCapsAndCorrectBlockCounts() {
        assertEquals(109,keyboard108.size); assertEquals(109,keyboard108.map { it.code }.distinct().size)
        assertEquals(mapOf(KeyZone.MAIN to 75,KeyZone.NAV to 13,KeyZone.PAD to 21),keyboard108.groupingBy { it.zone }.eachCount())
        for((index,a) in keyboard108.withIndex()) {
            assertTrue(a.x>=a.zone.left && a.x+a.width <= a.zone.left+a.zone.width && a.y>=0 && a.y+a.height<=6.301f)
            for(b in keyboard108.drop(index+1)) assertFalse("${a.legend} overlaps ${b.legend}",
                a.x < b.x+b.width-.001f && a.x+a.width > b.x+.001f && a.y < b.y+b.height-.001f && a.y+a.height > b.y+.001f)
        }
    }
    @Test fun chordsSupportEitherTapOrderAndLeftRightModifierOnlyMappings() {
        val chord=BoardChord(KEYCODE_K,META_CTRL_ON or META_ALT_ON)
        assertEquals(chord,BoardChord().tap(KEYCODE_CTRL_RIGHT).tap(KEYCODE_ALT_LEFT).tap(KEYCODE_K))
        assertEquals(chord,BoardChord().tap(KEYCODE_K).tap(KEYCODE_ALT_RIGHT).tap(KEYCODE_CTRL_LEFT))
        assertEquals(BoardChord(KEYCODE_K,META_ALT_ON),chord.tap(KEYCODE_CTRL_LEFT))
        assertEquals(BoardChord(KEYCODE_CTRL_RIGHT),BoardChord().tap(KEYCODE_CTRL_RIGHT))
        assertEquals(BoardChord(KEYCODE_CTRL_RIGHT),BoardChord().tap(KEYCODE_CTRL_LEFT).tap(KEYCODE_CTRL_RIGHT))
    }
    @Test fun navigationAndPadUseKeyboardCodesNotSystemOrTypingEquivalents() {
        fun cap(code:Int)=keyboard108.single { it.code==code }
        assertEquals("Home",cap(KEYCODE_MOVE_HOME).legend); assertFalse(keyboard108.any { it.code==KEYCODE_HOME })
        assertEquals("Del",cap(KEYCODE_FORWARD_DEL).legend); assertEquals("Backspace",cap(KEYCODE_DEL).legend)
        assertEquals(KeyZone.MAIN,cap(KEYCODE_1).zone); assertEquals(KeyZone.PAD,cap(KEYCODE_NUMPAD_1).zone)
        assertEquals(2f,cap(KEYCODE_NUMPAD_ENTER).height); assertEquals(2f,cap(KEYCODE_NUMPAD_0).width)
        val reserved=keyboard108.filterNot { KeyMappings.keyAllowed(it.code) }.map { it.code }.toSet()
        assertEquals(setOf(KEYCODE_MENU,KEYCODE_VOLUME_MUTE,KEYCODE_VOLUME_UP,KEYCODE_VOLUME_DOWN),reserved)
        reserved.forEach { assertEquals(BoardChord(KEYCODE_Q),BoardChord(KEYCODE_Q).tap(it)) }
    }
    @Test fun fnAndOrdinaryKeysAccumulateCanBeRemovedAndSurvivePickerRecreation() {
        assertEquals("Fn",keyboard108.single { it.code==KEYCODE_FUNCTION }.legend)
        val c=BoardChord().tap(KEYCODE_FUNCTION).tap(KEYCODE_A).tap(KEYCODE_B).tap(KEYCODE_C).tap(KEYCODE_CTRL_RIGHT)
        assertEquals(BoardChord(KEYCODE_C,META_FUNCTION_ON or META_CTRL_ON,listOf(KEYCODE_A,KEYCODE_B)),c)
        assertEquals(c,BoardChord.unpack(c.pack())); assertTrue(c.selected(KEYCODE_A)); assertTrue(c.selected(KEYCODE_FUNCTION))
        assertEquals(listOf(KEYCODE_B),c.tap(KEYCODE_A).held); assertEquals(KEYCODE_B,c.tap(KEYCODE_C).code)
        assertFalse(c.tap(KEYCODE_FUNCTION).selected(KEYCODE_FUNCTION))
        val keys=(KEYCODE_A until KEYCODE_A+8).fold(BoardChord()) { v,k -> v.tap(k) }
        assertEquals(keys,keys.tap(KEYCODE_Z)); assertNotEquals(keys,keys.tap(KEYCODE_CTRL_LEFT))
    }
    @Test fun recordingCapturesOnePhysicalDeviceUntilReleaseAndAcceptsMetaOnlyFn() {
        val r=ChordRecording(); r.down(2,KEYCODE_CTRL_LEFT,0); r.down(2,KEYCODE_A,META_FUNCTION_ON)
        r.down(3,KEYCODE_X,0); r.down(2,KEYCODE_A,META_FUNCTION_ON); r.down(2,KEYCODE_B,META_FUNCTION_ON)
        assertFalse(r.contains(3,KEYCODE_X)); assertTrue(r.contains(2,KEYCODE_CTRL_LEFT))
        assertEquals(BoardChord(KEYCODE_B,META_CTRL_ON or META_FUNCTION_ON,listOf(KEYCODE_A)),r.chord)
        val fn=ChordRecording(); fn.down(7,KEYCODE_FUNCTION,0); fn.down(7,KEYCODE_CTRL_LEFT,0); fn.down(7,KEYCODE_K,0)
        assertEquals(BoardChord(KEYCODE_K,META_CTRL_ON or META_FUNCTION_ON),fn.chord)
        for(k in KEYCODE_A..KEYCODE_P) fn.down(7,k,0); assertTrue(fn.overflow)
    }
}
