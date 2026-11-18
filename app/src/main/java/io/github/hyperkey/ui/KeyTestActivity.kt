package io.github.hyperkey.ui

import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperkey.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

class KeyTestActivity : ThemedActivity() {
    private var display by mutableStateOf(KeyDisplay())
    private val mods = listOf("Ctrl" to KeyEvent.META_CTRL_ON, "Alt" to KeyEvent.META_ALT_ON,
        "Shift" to KeyEvent.META_SHIFT_ON, "Win" to KeyEvent.META_META_ON)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HyperTheme { Screen() } }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && display.held.isNotEmpty()) display = display.copy(held = emptySet())
    }
    override fun onStop() { display = KeyDisplay(); super.onStop() }

    private fun receive(e: KeyEvent): Boolean {
        if (!e.isFromSource(InputDevice.SOURCE_KEYBOARD) || e.action !in KeyEvent.ACTION_DOWN..KeyEvent.ACTION_UP ||
            e.keyCode in reservedKeys) return false
        val key = TestKey(e.deviceId, e.keyCode, if (e.keyCode == KeyEvent.KEYCODE_UNKNOWN) e.scanCode else 0)
        display = display.accept(KeySample(key, e.action == KeyEvent.ACTION_DOWN, e.scanCode, e.metaState, e.repeatCount))
        return true
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent) = receive(event) || super.onKeyDown(keyCode, event)
    override fun onKeyUp(keyCode: Int, event: KeyEvent) = receive(event) || super.onKeyUp(keyCode, event)
    override fun onKeyShortcut(keyCode: Int, event: KeyEvent) = receive(event) || super.onKeyShortcut(keyCode, event)

    @Composable private fun Screen() {
        val requester = remember { FocusRequester() }
        val colors = MiuixTheme.colorScheme
        val last = display.history.firstOrNull()
        val muted = colors.onSurfaceVariantSummary
        Scaffold(topBar = { PageBar(tr("key_test"), ::finish) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).onPreviewKeyEvent { receive(it.nativeKeyEvent) }
                .focusRequester(requester).focusable(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 920.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(text = tr("clear"), enabled = last != null, onClick = { display = KeyDisplay() })
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(if (display.held.isNotEmpty()) R.string.kt_held else if (last != null) R.string.kt_last else R.string.kt_live),
                                fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            if (last == null) Text(stringResource(R.string.kt_wait), color = muted)
                            else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                display.chord.forEach { key -> Keycap(key, key in display.held) }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                mods.forEach { (name, mask) ->
                                    val active = display.held.isNotEmpty() && (KeyEvent.normalizeMetaState(last?.meta ?: 0) and mask) != 0
                                    val state = stringResource(if (active) R.string.kt_down else R.string.kt_up)
                                    Text(if (active) "● $name" else name, fontSize = 13.sp,
                                        color = if (active) colors.onPrimary else muted,
                                        modifier = Modifier.semantics { stateDescription = state }
                                            .background(if (active) colors.primary else muted.copy(alpha = .12f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 5.dp))
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.kt_history), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("key_test_help"), fontSize = 13.sp, color = muted)
                    if (last != null) Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            display.history.forEach { e ->
                                val phase = stringResource(if (!e.down) R.string.kt_up else if (e.repeat > 0) R.string.kt_repeat else R.string.kt_down)
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text("${if (e.down) "↓" else "↑"} $phase  ·  ${KeyEvent.keyCodeToString(e.key.code)}", fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.kt_details, e.key.code, e.scan, e.meta.toString(16).uppercase(java.util.Locale.ROOT), e.repeat),
                                        fontSize = 13.sp, color = muted)
                                }
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { requester.requestFocus() }
        }
    }
    @Composable private fun Keycap(key: TestKey, pressed: Boolean) {
        val colors = MiuixTheme.colorScheme
        val muted = colors.onSurfaceVariantSummary
        val name = when (key.code) {
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> "Win"
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift"
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
            KeyEvent.KEYCODE_ESCAPE -> "Esc"; KeyEvent.KEYCODE_ENTER -> "↵ Enter"; KeyEvent.KEYCODE_DEL -> "⌫"
            KeyEvent.KEYCODE_DPAD_UP -> "↑"; KeyEvent.KEYCODE_DPAD_DOWN -> "↓"; KeyEvent.KEYCODE_DPAD_LEFT -> "←"; KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
            else -> KeyEvent.keyCodeToString(key.code).removePrefix("KEYCODE_").replace('_', ' ')
        }
        val state = stringResource(if (pressed) R.string.kt_down else R.string.kt_up)
        val shape = RoundedCornerShape(12.dp)
        Column(Modifier.semantics(mergeDescendants = true) { stateDescription = state }.widthIn(min = 76.dp)
            .background(if (pressed) colors.primary else muted.copy(alpha = .08f), shape)
            .border(if (pressed) 2.dp else 1.dp, if (pressed) colors.primary else muted.copy(alpha = .45f), shape)
            .padding(horizontal = 14.dp, vertical = 9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = if (pressed) colors.onPrimary else colors.onSurface)
            Text(stringResource(R.string.kt_keycode, key.code), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                color = if (pressed) colors.onPrimary else muted)
        }
    }
    private companion object {
        val reservedKeys = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_APP_SWITCH)
    }
}
