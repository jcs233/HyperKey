package io.github.hyperkey.ui

/** A display-only snapshot. Identity includes the input device; unknown Android keys use their scan code. */
internal data class TestKey(val device: Int, val code: Int, val unknownScan: Int = 0)
internal data class KeySample(val key: TestKey, val down: Boolean, val scan: Int, val meta: Int, val repeat: Int)
internal data class KeyDisplay(
    val held: Set<TestKey> = emptySet(), val chord: List<TestKey> = emptyList(),
    val history: List<KeySample> = emptyList()
) {
    fun accept(e: KeySample): KeyDisplay {
        val next = if (e.down) held + e.key else held - e.key
        // Keep the last chord visible through releases; a fresh stroke starts a fresh chord.
        val shown = when {
            e.down && e.repeat == 0 -> next.toList()
            e.key !in chord -> (next + e.key).toList()
            else -> chord
        }
        return KeyDisplay(next, shown, (listOf(e) + history).take(16))
    }
}
