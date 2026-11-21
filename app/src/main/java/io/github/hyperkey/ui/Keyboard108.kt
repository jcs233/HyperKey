package io.github.hyperkey.ui

import android.view.KeyEvent.*
import io.github.hyperkey.core.KeyMappings
import io.github.hyperkey.core.KeyMeta

/** ANSI 104 plus calculator, mute and volume −/+. Coordinates are measured in key widths. */
internal enum class KeyZone(val left: Float, val width: Float) {
    ALL(0f, 23f), MAIN(0f, 15f), NAV(15.5f, 3f), PAD(19f, 4f)
}
internal data class BoardKey(val code: Int, val legend: String, val x: Float, val y: Float,
    val width: Float = 1f, val height: Float = 1f) {
    val zone get() = when { x >= KeyZone.PAD.left -> KeyZone.PAD; x >= KeyZone.NAV.left -> KeyZone.NAV; else -> KeyZone.MAIN }
}
internal val keyboard108: List<BoardKey> = buildList {
    fun cap(code: Int, legend: String, x: Float, y: Float, w: Float = 1f, h: Float = 1f) { add(BoardKey(code,legend,x,y,w,h)) }
    fun letters(text: String, x: Float, y: Float) = text.forEachIndexed { i,c -> cap(KEYCODE_A + (c-'A'),c.toString(),x+i,y) }
    fun row(x: Float, y: Float, vararg keys: Pair<Int,String>) = keys.forEachIndexed { i,(code,label) -> cap(code,label,x+i,y) }
    cap(KEYCODE_ESCAPE,"Esc",0f,0f); cap(KEYCODE_FUNCTION,"Fn",1f,0f)
    repeat(12) { i -> cap(KEYCODE_F1+i,"F${i+1}",2f+i+(i/4)*.5f,0f) }
    val y=1.3f
    cap(KEYCODE_GRAVE,"` ~",0f,y)
    "1234567890".forEachIndexed { i,c -> cap(KEYCODE_0+(c-'0'),c.toString(),i+1f,y) }
    row(11f,y,KEYCODE_MINUS to "−",KEYCODE_EQUALS to "="); cap(KEYCODE_DEL,"Backspace",13f,y,2f)
    cap(KEYCODE_TAB,"Tab",0f,y+1,1.5f); letters("QWERTYUIOP",1.5f,y+1)
    row(11.5f,y+1,KEYCODE_LEFT_BRACKET to "[",KEYCODE_RIGHT_BRACKET to "]"); cap(KEYCODE_BACKSLASH,"\\",13.5f,y+1,1.5f)
    cap(KEYCODE_CAPS_LOCK,"Caps Lock",0f,y+2,1.75f); letters("ASDFGHJKL",1.75f,y+2)
    row(10.75f,y+2,KEYCODE_SEMICOLON to ";",KEYCODE_APOSTROPHE to "'"); cap(KEYCODE_ENTER,"Enter ↵",12.75f,y+2,2.25f)
    cap(KEYCODE_SHIFT_LEFT,"Shift",0f,y+3,2.25f); letters("ZXCVBNM",2.25f,y+3)
    row(9.25f,y+3,KEYCODE_COMMA to ",",KEYCODE_PERIOD to ".",KEYCODE_SLASH to "/"); cap(KEYCODE_SHIFT_RIGHT,"Shift",12.25f,y+3,2.75f)
    cap(KEYCODE_CTRL_LEFT,"Ctrl",0f,y+4,1.25f); cap(KEYCODE_META_LEFT,"Win",1.25f,y+4,1.25f); cap(KEYCODE_ALT_LEFT,"Alt",2.5f,y+4,1.25f)
    cap(KEYCODE_SPACE,"Space",3.75f,y+4,6.25f); cap(KEYCODE_ALT_RIGHT,"Alt",10f,y+4,1.25f)
    cap(KEYCODE_META_RIGHT,"Win",11.25f,y+4,1.25f); cap(KEYCODE_MENU,"Menu",12.5f,y+4,1.25f); cap(KEYCODE_CTRL_RIGHT,"Ctrl",13.75f,y+4,1.25f)
    row(15.5f,0f,KEYCODE_SYSRQ to "PrtSc",KEYCODE_SCROLL_LOCK to "ScrLk",KEYCODE_BREAK to "Pause")
    row(15.5f,y,KEYCODE_INSERT to "Ins",KEYCODE_MOVE_HOME to "Home",KEYCODE_PAGE_UP to "PgUp")
    row(15.5f,y+1,KEYCODE_FORWARD_DEL to "Del",KEYCODE_MOVE_END to "End",KEYCODE_PAGE_DOWN to "PgDn")
    cap(KEYCODE_DPAD_UP,"↑",16.5f,y+3); row(15.5f,y+4,KEYCODE_DPAD_LEFT to "←",KEYCODE_DPAD_DOWN to "↓",KEYCODE_DPAD_RIGHT to "→")
    row(19f,0f,KEYCODE_CALCULATOR to "Calc",KEYCODE_VOLUME_MUTE to "Mute",KEYCODE_VOLUME_DOWN to "Vol−",KEYCODE_VOLUME_UP to "Vol+")
    row(19f,y,KEYCODE_NUM_LOCK to "Num",KEYCODE_NUMPAD_DIVIDE to "/",KEYCODE_NUMPAD_MULTIPLY to "×",KEYCODE_NUMPAD_SUBTRACT to "−")
    listOf(7,4,1).forEachIndexed { i,first -> repeat(3) { j -> cap(KEYCODE_NUMPAD_0+first+j,"${first+j}",19f+j,y+1+i) } }
    cap(KEYCODE_NUMPAD_ADD,"+",22f,y+1,1f,2f); cap(KEYCODE_NUMPAD_ENTER,"Enter",22f,y+3,1f,2f)
    cap(KEYCODE_NUMPAD_0,"0",19f,y+4,2f); cap(KEYCODE_NUMPAD_DOT,".",21f,y+4)
}

/** Ordinary keys accumulate; the final one triggers the chord. Modifier groups remain side-neutral
 * except when a modifier itself is the trigger (e.g. right Ctrl). */
internal data class BoardChord(val code: Int = 0, val mods: Int = 0, val held: List<Int> = emptyList()) {
    fun selected(k: Int) = k == code || k in held || (KeyMeta.group(k) and mods != 0)
    fun pack() = intArrayOf(code,mods,*held.toIntArray())
    fun tap(next: Int): BoardChord {
        if (!KeyMappings.keyAllowed(next)) return this
        val group = KeyMeta.group(next)
        val primaryGroup = KeyMeta.group(code)
        if (group != 0) {
            if (code == 0) return BoardChord(next)
            if (primaryGroup == 0) return copy(mods = mods xor group)
            if (next == code) return modifiersOnly(mods)
            if (group == primaryGroup) return copy(code=next)
            if (mods and group != 0) return copy(mods=mods xor group)
            return BoardChord(next,mods or primaryGroup)
        }
        val ordinary = if (code == 0 || primaryGroup != 0) emptyList() else held + code
        val keys = when { next in ordinary -> ordinary - next; ordinary.size < KeyMappings.MAX_ORDINARY -> ordinary + next; else -> return this }
        val mask = mods or primaryGroup
        return if (keys.isEmpty()) modifiersOnly(mask) else BoardChord(keys.last(),mask,keys.dropLast(1))
    }
    companion object {
        fun unpack(a: IntArray?) = if(a==null || a.size<2) BoardChord() else BoardChord(a[0],a[1],a.drop(2))
        private fun modifiersOnly(mask: Int): BoardChord {
            val k = listOf(KEYCODE_CTRL_LEFT, KEYCODE_ALT_LEFT, KEYCODE_SHIFT_LEFT, KEYCODE_META_LEFT, KEYCODE_FUNCTION)
                .lastOrNull { mask and KeyMeta.group(it) != 0 } ?: return BoardChord()
            return BoardChord(k, mask and KeyMeta.group(k).inv())
        }
    }
}

/** Records simultaneous downs from one device. An up confirms the peak chord; later ups are drained. */
internal class ChordRecording {
    private val pressed = linkedSetOf<Int>()
    private var device: Int? = null
    var chord = BoardChord(); private set
    var overflow = false; private set
    fun contains(id: Int, key: Int) = device == id && key in pressed
    fun accepts(id: Int) = device == null || device == id
    fun down(id: Int, code: Int, meta: Int) {
        if (!accepts(id) || !KeyMappings.keyAllowed(code) || !pressed.add(code)) return
        device = id
        val ordinary = pressed.filter { KeyMeta.group(it) == 0 }
        if (ordinary.size > KeyMappings.MAX_ORDINARY) { overflow = true; return }
        val trigger = ordinary.lastOrNull() ?: code
        val groups = pressed.fold(meta and KeyMappings.MODIFIERS) { m,k -> m or KeyMeta.group(k) }
        chord = BoardChord(trigger,groups and KeyMeta.group(trigger).inv(),ordinary.dropLast(1))
    }
}
