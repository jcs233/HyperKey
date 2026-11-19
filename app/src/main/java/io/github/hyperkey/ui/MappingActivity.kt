package io.github.hyperkey.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

class MappingActivity : ThemedActivity() {
    private enum class Target(val label: Int) { KEY(R.string.target_key), SYSTEM(R.string.target_action), APP(R.string.open_app), FEATURE(R.string.open_feature), SHELL(R.string.user_command), ROOT(R.string.root_command) }
    class Draft : ViewModel() { var command by mutableStateOf("") }
    private val draft by lazy { ViewModelProvider(this)[Draft::class.java] }
    private var mode by mutableStateOf(Target.KEY)
    private var app by mutableStateOf("")
    private var pageMode by mutableStateOf(false)
    private var pagePkg by mutableStateOf("")
    private var page by mutableStateOf("")
    private var feature by mutableStateOf(featureActions.first())
    private val featureLabels get() = resources.getStringArray(R.array.action_features)
    private fun featureName(value: String) = featureLabels.getOrNull(featureActions.indexOf(value)) ?: value
    private var rules by mutableStateOf(KeyMappings.EMPTY)
    private var capture by mutableIntStateOf(0)
    private var sourceKey by mutableIntStateOf(0)
    private var sourceMods by mutableIntStateOf(0)
    private var targetKey by mutableIntStateOf(0)
    private var targetMods by mutableIntStateOf(0)
    private var action by mutableStateOf("")
    private val owner: String get() = intent.getStringExtra("package")?.takeIf { AppProfiles.validPackage(it) } ?: KeyMappings.GLOBAL
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.run {
            pageMode=getBoolean("pages"); page=getString("page").orEmpty(); pagePkg=getString("pagePkg").orEmpty()
            mode = Target.entries.getOrElse(getInt("mode")) { Target.KEY }; app = getString("app").orEmpty(); feature = getString("feature") ?: featureActions.first()
            sourceKey = getInt("src"); sourceMods = getInt("sm"); targetKey = getInt("dst"); targetMods = getInt("dm"); action = getString("act").orEmpty()
        }
        setContent { HyperTheme { Screen() } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("pages",pageMode); outState.putString("page",page); outState.putString("pagePkg",pagePkg)
        outState.putInt("mode",mode.ordinal); outState.putString("app",app); outState.putString("feature",feature)
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
        val modifier = KeyMeta.group(event.keyCode)
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
            "${keyLabel(key)} ($key)").joinToString(" + ")
    }
    private fun save() {
        if (!ConfigStore.canWrite(this)) { Toast.makeText(this, tr("permission_needed"), Toast.LENGTH_LONG).show(); return }
        runCatching {
            val local = when (mode) {
                Target.APP -> LocalAction(ActionKind.APP,app); Target.FEATURE -> if(pageMode) LocalAction(ActionKind.PAGE,page) else LocalAction(ActionKind.FEATURE,feature)
                Target.SHELL, Target.ROOT -> LocalAction(if (mode == Target.ROOT) ActionKind.ROOT else ActionKind.SHELL,draft.command)
                else -> null
            }
            rules = LocalActions.save(this,owner,sourceKey,sourceMods,if (mode == Target.KEY) targetKey else 0,
                if (mode == Target.KEY) targetMods else 0, if (mode == Target.SYSTEM) action else "",local)
            sourceKey = 0; sourceMods = 0; targetKey = 0; targetMods = 0; action = ""; draft.command = ""; app = ""
            Toast.makeText(this, getString(R.string.mapping_saved), Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(this, tr("mapping_invalid"), Toast.LENGTH_LONG).show() }
    }
    @Composable private fun Screen() {
        var picker by rememberSaveable { mutableIntStateOf(0) }
        BackHandler(enabled = capture != 0 || picker != 0) { capture = 0; picker = 0 }
        if (picker != 0) {
            val source = picker == 1
            KeyboardPicker(tr(if (source) "source_key" else "target_key"), if(source) BoardChord(sourceKey,sourceMods) else BoardChord(targetKey,targetMods), ::chord,
                dismiss={ picker=0 }, accept={ selected ->
                    if(source) { sourceKey=selected.code; sourceMods=selected.mods } else { targetKey=selected.code; targetMods=selected.mods }; picker=0
                })
        }
        val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        val targetName = when (mode) {
            Target.KEY -> chord(targetKey,targetMods); Target.SYSTEM -> if (action.isEmpty()) tr("not_set") else UiText.title(this,Shortcut.valueOf(action))
            Target.APP -> app.ifEmpty { tr("not_set") }; Target.FEATURE -> if(pageMode) page.ifEmpty { tr("not_set") } else featureName(feature)
            else -> getString(mode.label) + ": " + draft.command.take(80)
        }
        val ready = sourceKey != 0 && when(mode) { Target.KEY -> targetKey != 0; Target.SYSTEM -> action.isNotEmpty(); Target.APP -> app.isNotEmpty(); Target.FEATURE -> !pageMode || page.isNotEmpty(); else -> draft.command.isNotBlank() }
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
                BasicComponent(title = tr("source_key"), summary = chord(sourceKey,sourceMods), onClick={ capture=0; picker=if(picker==1) 0 else 1 })
                FlowRow { TextButton(text=getString(R.string.pick_keys),onClick={ capture=0; picker=if(picker==1) 0 else 1 }); TextButton(text=if(capture==1) tr("press_key") else getString(R.string.record_key),onClick={ picker=0; capture=1 }) }

            }
            EditorGroup(stringResource(R.string.map_target_step)) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Target.entries.forEach { t -> Choice(stringResource(t.label),mode==t) { mode=t; capture=0; picker=0 } }
                }
                when(mode) {
                    Target.KEY -> {
                        BasicComponent(title=tr("target_key"),summary=chord(targetKey,targetMods),onClick={ capture=0; picker=if(picker==2) 0 else 2 })
                        FlowRow { TextButton(text=getString(R.string.pick_keys),onClick={ capture=0; picker=if(picker==2) 0 else 2 }); TextButton(text=if(capture==2) tr("press_key") else getString(R.string.record_key),onClick={ picker=0; capture=2 }) }

                    }
                    Target.SYSTEM -> FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Shortcut.values().filter { KeyMappings.actionAllowed(it.name) }.forEach { shortcut ->
                            Choice(UiText.title(this@MappingActivity, shortcut), action == shortcut.name) { action = shortcut.name }
                        }
                    }
                    Target.APP -> AppTargetPicker(app) { app=it }
                    Target.FEATURE -> {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            Choice(getString(R.string.system_pages),!pageMode) { pageMode=false }
                            Choice(getString(R.string.app_pages),pageMode) { pageMode=true }
                        }
                        if (!pageMode) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            featureActions.forEach { f -> Choice(featureName(f),feature==f) { feature=f } }
                        } else {
                            AppTargetPicker(pagePkg) { if(pagePkg!=it) { pagePkg=it; page="" } }
                            if(pagePkg.isNotEmpty()) PublicPagePicker(pagePkg,page) { page=it }
                        }
                    }
                    Target.SHELL,Target.ROOT -> {
                        Text(getString(if(mode==Target.ROOT) R.string.root_explanation else R.string.command_explanation))
                        TextField(value=draft.command,onValueChange={ if(it.length<=2048 && '\u0000' !in it) draft.command=it },label=getString(R.string.command_text),modifier=Modifier.fillMaxWidth())
                    }
                }
            }
            if (capture != 0) TextButton(text = tr("cancel"), onClick = { capture = 0 })
            EditorGroup(stringResource(R.string.map_preview)) {
                Text("${chord(sourceKey,sourceMods)}  →  $targetName")
                TextButton(text = tr("save_mapping"), enabled = ready && ConfigStore.canWrite(this@MappingActivity), onClick = { capture = 0; picker = 0; save() })
            }
            EditorGroup(tr("saved_mappings")) {
                val own = rules.rules.filter { it.owner() == owner }
                if (own.isEmpty()) Text(tr("no_mappings"), color = color)
                own.forEach { rule -> key(rule.id()) {
                    var deleting by remember(rule) { mutableStateOf(false) }
                    val target = if (rule.localAction()) {
                        val spec = remember(rule) { LocalActions.get(this@MappingActivity,rule) }
                        when(spec?.kind) { ActionKind.APP -> getString(R.string.open_app) + ": " + spec.value
                            ActionKind.FEATURE -> featureName(spec.value); ActionKind.PAGE -> spec.value; ActionKind.ROOT -> getString(R.string.root_command) + ": " + spec.value.take(64)
                            ActionKind.SHELL -> getString(R.string.user_command) + ": " + spec.value.take(64); null -> getString(R.string.action_unavailable) }
                    } else if (rule.systemAction()) UiText.title(this@MappingActivity, Shortcut.valueOf(rule.action())) else chord(rule.targetKey(),rule.targetModifiers())
                    BasicComponent(title = "${chord(rule.sourceKey(),rule.sourceModifiers())}  →  $target",
                        endActions = { TextButton(text = if (deleting) tr("confirm_delete") else tr("delete"), onClick = {
                            if (!deleting) deleting = true else runCatching {
                                rules = LocalActions.remove(this@MappingActivity) { it.id() == rule.id() }
                            }.onFailure { Toast.makeText(this@MappingActivity, tr("save_failed"), Toast.LENGTH_SHORT).show() }
                        }) })
                } }
            }
            Text(tr("mapping_help"), fontSize = 13.sp, color = color)
            TextButton(text = tr("key_test"), onClick = { startActivity(android.content.Intent(this@MappingActivity, KeyTestActivity::class.java)) })
        }
    }
}
