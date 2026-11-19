package io.github.hyperkey.ui

import android.view.KeyEvent.*
import io.github.hyperkey.core.KeyMappings
import org.junit.Assert.*
import org.junit.Test

class Keyboard108Test {
    @Test fun ansiGeometryHas108DistinctNonOverlappingCapsAndCorrectBlockCounts() {
        assertEquals(108,keyboard108.size); assertEquals(108,keyboard108.map { it.code }.distinct().size)
        assertEquals(mapOf(KeyZone.MAIN to 74,KeyZone.NAV to 13,KeyZone.PAD to 21),keyboard108.groupingBy { it.zone }.eachCount())
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
}
