package io.github.hyperkey.ui

import org.junit.Assert.*
import org.junit.Test

class KeyDisplayTest {
    private val win = TestKey(1, 117)
    private val d = TestKey(1, 32)
    private fun sample(key: TestKey, down: Boolean, repeat: Int = 0) = KeySample(key,down,0,0,repeat)
    @Test fun releasesKeepLastChordUntilAnotherStroke() {
        val both = KeyDisplay().accept(sample(win,true)).accept(sample(d,true))
        val firstUp = both.accept(sample(d,false))
        assertEquals(setOf(win), firstUp.held)
        assertEquals(listOf(win,d), firstUp.chord)
        val released = firstUp.accept(sample(win,false))
        assertTrue(released.held.isEmpty()); assertEquals(listOf(win,d), released.chord)
        assertEquals(listOf(d), released.accept(sample(d,true)).chord)
    }
    @Test fun differentDevicesAndRepeatDoNotLoseHeldKeys() {
        val other = TestKey(2,32)
        var state = KeyDisplay().accept(sample(d,true)).accept(sample(other,true))
        repeat(24) { state = state.accept(sample(other,true,it+1)) }
        assertEquals(listOf(d,other),state.chord); assertEquals(16,state.history.size)
        assertEquals(setOf(d),state.accept(sample(other,false)).held)
    }
}
