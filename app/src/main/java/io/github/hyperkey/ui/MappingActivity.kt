package io.github.hyperkey.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import io.github.hyperkey.R
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.unit.dp
import io.github.hyperkey.core.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperSwitch

class MappingActivity : ThemedActivity() {
    private var rules by mutableStateOf(KeyMappings.EMPTY)
    private var capture by mutableStateOf(0)
    private var sourceKey by mutableIntStateOf(0)
    private var sourceMods by mutableIntStateOf(0)
    private var targetKey by mutableIntStateOf(0)
    private var targetMods by mutableIntStateOf(0)
    private var action by mutableStateOf("")
    private val owner: String get() = intent.getStringExtra("package")?.takeIf { AppProfiles.validPackage(it) } ?: KeyMappings.GLOBAL
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.run {
            sourceKey = getInt("src"); sourceMods = getInt("sm"); targetKey = getInt("dst"); targetMods = getInt("dm"); action = getString("act").orEmpty()
        }
        setContent { HyperTheme { Screen() } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("src",sourceKey); outState.putInt("sm",sourceMods); outState.putInt("dst",targetKey); outState.putInt("dm",targetMods); outState.putString("act",action)
    }
    override fun onResume() { super.onResume(); rules = MappingStore.read(this) }
    private val capturedKeys = mutableSetOf<Int>()
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (capture == 0 || !KeyMappings.keyAllowed(keyCode)) return super.onKeyDown(keyCode, event)
        capturedKeys.add(keyCode)
        return captureKey(event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!capturedKeys.remove(keyCode)) return super.onKeyUp(keyCode, event)
        return if (capture == 0) true else captureKey(event)
    }
    private fun captureKey(event: KeyEvent): Boolean {
        val modifier = when (event.keyCode) { 57, 58 -> 2; 59, 60 -> 1; 113, 114 -> 0x1000; 117, 118 -> 0x10000; else -> 0 }
        // Capture modifiers on release, so Ctrl+letter records the chord rather than Ctrl alone.
        if (modifier != 0 && event.action == KeyEvent.ACTION_DOWN) return true
        if ((modifier == 0 && event.action == KeyEvent.ACTION_DOWN) || (modifier != 0 && event.action == KeyEvent.ACTION_UP)) {
            val mods = KeyEvent.normalizeMetaState(event.metaState) and KeyMappings.MODIFIERS and modifier.inv()
            if (capture == 1) { sourceKey = event.keyCode; sourceMods = mods }
            else { targetKey = event.keyCode; targetMods = mods; action = "" }
            capture = 0
        }
        return true
    }
    private fun chord(key: Int, mods: Int): String {
        if (key == 0) return tr("not_set")
        return listOfNotNull(if (mods and 0x1000 != 0) "Ctrl" else null, if (mods and 2 != 0) "Alt" else null,
            if (mods and 1 != 0) "Shift" else null, if (mods and 0x10000 != 0) "Win" else null,
            KeyEvent.keyCodeToString(key).removePrefix("KEYCODE_")).joinToString(" + ")
    }
    private fun save() {
        if (!ConfigStore.canWrite(this)) { Toast.makeText(this, tr("permission_needed"), Toast.LENGTH_LONG).show(); return }
        runCatching {
            val rule = KeyMappings.Rule(owner, sourceKey, sourceMods, if (action.isEmpty()) targetKey else 0,
                if (action.isEmpty()) targetMods else 0, action)
            val next = MappingStore.read(this).put(rule); MappingStore.write(this, next); rules = next
            sourceKey = 0; targetKey = 0; action = ""
        }.onFailure { Toast.makeText(this, tr("mapping_invalid"), Toast.LENGTH_LONG).show() }
    }
    @Composable private fun Screen() {
        var actionMode by rememberSaveable { mutableStateOf(action.isNotEmpty()) }
        BackHandler(enabled = capture != 0) { capture = 0 }
        val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        val targetName = if (actionMode && action.isNotEmpty()) UiText.title(this, Shortcut.valueOf(action)) else chord(targetKey, targetMods)
        val ready = sourceKey != 0 && (if (actionMode) action.isNotEmpty() else targetKey != 0)
        EditorPage(tr("mappings"), ::finish, Modifier.onPreviewKeyEvent {
            val e = it.nativeKeyEvent
            when {
                e.action == KeyEvent.ACTION_DOWN && capture != 0 && KeyMappings.keyAllowed(e.keyCode) -> { capturedKeys.add(e.keyCode); captureKey(e) }
                e.action == KeyEvent.ACTION_UP && capturedKeys.remove(e.keyCode) -> if (capture == 0) true else captureKey(e)
                else -> false
            }
        }) {
            Text(if (owner == KeyMappings.GLOBAL) tr("global_mapping") else tr("app_mapping", owner))
            EditorGroup(stringResource(R.string.map_source_step)) {
                BasicComponent(title = tr("source_key"), summary = if (capture == 1) tr("press_key") else chord(sourceKey, sourceMods), onClick = { capture = 1 })
            }
            EditorGroup(stringResource(R.string.map_target_step)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice(tr("target_key"), !actionMode) { if (actionMode) { actionMode = false; action = ""; capture = 0 } }
                    Choice(tr("target_action"), actionMode) { if (!actionMode) { actionMode = true; targetKey = 0; targetMods = 0; capture = 0 } }
                }
                if (!actionMode) {
                    BasicComponent(title = stringResource(R.string.map_record_target), summary = if (capture == 2) tr("press_key") else chord(targetKey,targetMods), onClick = { capture = 2 })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(111 to "Esc", 117 to "Win", 115 to "Caps Lock", 61 to "Tab", 66 to "Enter", 67 to "Backspace").forEach { (key, name) ->
                            Choice(name, targetKey == key && targetMods == 0) { targetKey = key; targetMods = 0; capture = 0 }
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Shortcut.values().filter { KeyMappings.actionAllowed(it.name) }.forEach { shortcut ->
                            Choice(UiText.title(this@MappingActivity, shortcut), action == shortcut.name) { action = shortcut.name; capture = 0 }
                        }
                    }
                }
            }
            if (capture != 0) TextButton(text = tr("cancel"), onClick = { capture = 0 })
            EditorGroup(stringResource(R.string.map_preview)) {
                Text("${chord(sourceKey,sourceMods)}  →  $targetName")
                TextButton(text = tr("save_mapping"), enabled = ready && ConfigStore.canWrite(this@MappingActivity), onClick = { capture = 0; save() })
            }
            EditorGroup(tr("saved_mappings")) {
                val own = rules.rules.filter { it.owner() == owner }
                if (own.isEmpty()) Text(tr("no_mappings"), color = color)
                own.forEach { rule -> key(rule.id()) {
                    var deleting by remember(rule) { mutableStateOf(false) }
                    val target = if (rule.systemAction()) UiText.title(this@MappingActivity, Shortcut.valueOf(rule.action())) else chord(rule.targetKey(),rule.targetModifiers())
                    BasicComponent(title = "${chord(rule.sourceKey(),rule.sourceModifiers())}  →  $target",
                        endActions = { TextButton(text = if (deleting) tr("confirm_delete") else tr("delete"), onClick = {
                            if (!deleting) deleting = true else runCatching {
                                val next = MappingStore.read(this@MappingActivity).remove(rule.id()); MappingStore.write(this@MappingActivity, next); rules = next
                            }.onFailure { Toast.makeText(this@MappingActivity, tr("save_failed"), Toast.LENGTH_SHORT).show() }
                        }) })
                } }
            }
            Text(tr("mapping_help"), fontSize = 13.sp, color = color)
            TextButton(text = tr("key_test"), onClick = { startActivity(android.content.Intent(this@MappingActivity, KeyTestActivity::class.java)) })
        }
    }
}
