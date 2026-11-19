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
    cap(KEYCODE_ESCAPE,"Esc",0f,0f)
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

/** Tapping Ctrl then K builds Ctrl+K; tapping Ctrl alone (from an empty draft) binds left/right Ctrl. */
internal data class BoardChord(val code: Int = 0, val mods: Int = 0) {
    fun tap(next: Int): BoardChord {
        if (!KeyMappings.keyAllowed(next)) return this
        if (code == 0) return BoardChord(next)
        val oldGroup = KeyMeta.group(code); val newGroup = KeyMeta.group(next)
        if (oldGroup != 0) {
            if (next == code) return this
            return BoardChord(next, (mods or oldGroup) and newGroup.inv())
        }
        if (newGroup != 0) return copy(mods = mods xor newGroup)
        return copy(code = next)
    }
}
