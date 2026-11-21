package io.github.hyperkey.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperkey.core.Config
import io.github.hyperkey.core.UiText
import io.github.hyperkey.core.ConfigStore
import io.github.hyperkey.core.HookStatus
import io.github.hyperkey.core.Shortcut
import io.github.hyperkey.core.AppProfiles
import io.github.hyperkey.core.ProfileStore
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ThemedActivity() {
    private var config by mutableStateOf(Config.DEFAULT)
    private var blockTouchpad by mutableStateOf(false)
    private var writable by mutableStateOf(false)
    private var systemStatus by mutableStateOf("")
    private var settingsStatus by mutableStateOf("")
    private var profileCount by mutableStateOf(0)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HyperTheme {
                KeyboardScreen()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        listOf(Config.SETTING, io.github.hyperkey.core.TouchpadScreenshot.SETTING, AppProfiles.SETTING, HookStatus.SYSTEM, HookStatus.SETTINGS).forEach {
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }
        reload()
    }
    override fun onResume() { super.onResume(); reload() }
    override fun onStop() { contentResolver.unregisterContentObserver(observer); super.onStop() }

    private fun reload() {
        writable = ConfigStore.canWrite(this)
        config = runCatching { ConfigStore.read(this) }.getOrDefault(Config.DEFAULT)
        blockTouchpad = runCatching { ConfigStore.blockTouchpadScreenshot(this) }.getOrDefault(false)
        profileCount = ProfileStore.read(this).entries.size
        systemStatus = HookStatus.describe(this, HookStatus.SYSTEM)
        settingsStatus = HookStatus.describe(this, HookStatus.SETTINGS)
    }
    private fun update(transform: (Config) -> Config) {
        runCatching { ConfigStore.write(this, transform(ConfigStore.read(this))); reload() }
            .onFailure { Toast.makeText(this, tr("save_error", it.message ?: ""), Toast.LENGTH_LONG).show() }
    }

    @Composable
    private fun KeyboardScreen() {
        var detailsExpanded by rememberSaveable { mutableStateOf(false) }
        val groups = remember { Shortcut.values().groupBy { it.group }.entries.toList() }
        val enabledCount = Shortcut.values().count { config.isIndividuallyEnabled(it) }
        Scaffold(topBar = { SmallTopAppBar(title = "HyperKey") }) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                // Font scaling also participates: each column must have enough space for long labels.
                val twoColumns = maxWidth >= 840.dp &&
                    maxWidth.value / androidx.compose.ui.platform.LocalDensity.current.fontScale >= 720f
                val gutter = if (maxWidth < 600.dp) 16.dp else 32.dp
                Column(
                    Modifier.align(Alignment.TopCenter).widthIn(max = 1120.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = gutter).padding(top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(tr("keyboard_shortcuts"), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(if (config.blockAll) tr("paused_summary")
                            else tr("enabled_summary", enabledCount, Shortcut.values().size),
                            fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        SuperSwitch(checked = config.blockAll,
                            onCheckedChange = { value -> update { it.withBlockAll(value) } },
                            title = tr("master"), enabled = writable,
                            summary = if (config.blockAll) tr("master_restore") else tr("master_help"))
                    }
                    if (!writable) PermissionCard()
                    Card(Modifier.fillMaxWidth()) {
                        SuperSwitch(checked = blockTouchpad, enabled = writable, title = tr("touchpad_block_down"), summary = tr("touchpad_down_hint"),
                            onCheckedChange = { block -> runCatching { ConfigStore.setBlockTouchpadScreenshot(this@MainActivity, block); reload() }
                                .onFailure { Toast.makeText(this@MainActivity, tr("save_error", it.message ?: ""), Toast.LENGTH_LONG).show() } })
                    }
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = tr("profiles"), summary = tr("profiles_summary", profileCount),
                            onClick = { startActivity(Intent(this@MainActivity, ProfileActivity::class.java)) })
                    }
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = tr("mappings"), summary = tr("global_mapping"), onClick = {
                            startActivity(Intent(this@MainActivity, MappingActivity::class.java))
                        })
                        BasicComponent(title = tr("appearance"), summary = tr("appearance_summary"), onClick = {
                            startActivity(Intent(this@MainActivity, AppearanceActivity::class.java))
                        })
                        BasicComponent(title = tr("key_test"), summary = tr("key_test_summary"), onClick = {
                            startActivity(Intent(this@MainActivity, KeyTestActivity::class.java))
                        })
                    }
                    if (twoColumns) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            KeyboardEntry(tr("general_keyboard"), tr("physical_settings"), Modifier.weight(1f)) {
                                openSystemKeyboardSettings("com.android.settings.inputmethod.PhysicalKeyboardFragment")
                            }
                            KeyboardEntry(tr("dedicated_keyboard"), tr("native_editor"), Modifier.weight(1f)) { openDedicatedShortcuts() }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Column(Modifier.weight(1f)) { ShortcutGroup(groups.first().key, groups.first().value) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                groups.drop(1).forEach { ShortcutGroup(it.key, it.value) }
                            }
                        }
                    } else {
                        Card(Modifier.fillMaxWidth()) {
                            BasicComponent(title = tr("general_keyboard_settings"), summary = tr("open_physical"), onClick = {
                                openSystemKeyboardSettings("com.android.settings.inputmethod.PhysicalKeyboardFragment")
                            })
                            BasicComponent(title = tr("dedicated_keyboard"), summary = tr("open_native"), onClick = { openDedicatedShortcuts() })
                        }
                        groups.forEach { ShortcutGroup(it.key, it.value) }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = tr("runtime_help"), summary = if (detailsExpanded) tr("collapse") else tr("expand"),
                            onClick = { detailsExpanded = !detailsExpanded })
                        if (detailsExpanded) {
                            BasicComponent(title = tr("system_hook"), summary = systemStatus)
                            BasicComponent(title = tr("system_settings"), summary = settingsStatus)
                            Text(tr("help_text"),
                                fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp))
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = tr("restore"), summary = tr("restore_help"),
                            enabled = writable, onClick = { runCatching { ConfigStore.restore(this@MainActivity); reload() }.onFailure { Toast.makeText(this@MainActivity, tr("save_error", it.message ?: ""), Toast.LENGTH_LONG).show() } })
                    }
                }
            }
        }
    }

    @Composable
    private fun ShortcutGroup(title: String, shortcuts: List<Shortcut>) {
        val enabled = shortcuts.count { config.isIndividuallyEnabled(it) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.group(this@MainActivity, title), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f))
                Text("$enabled / ${shortcuts.size}", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Card(Modifier.fillMaxWidth()) {
                shortcuts.forEach { shortcut ->
                    SuperSwitch(checked = config.isIndividuallyEnabled(shortcut),
                        onCheckedChange = { enabled -> update { it.withEnabled(shortcut, enabled) } },
                        title = UiText.title(this@MainActivity, shortcut), summary = UiText.hint(this@MainActivity, shortcut),
                        enabled = writable && !config.blockAll)
                }
            }
        }
    }

    @Composable
    private fun KeyboardEntry(title: String, summary: String, modifier: Modifier, onClick: () -> Unit) {
        Card(modifier, onClick = onClick) { BasicComponent(title = title, summary = summary) }
    }

    @Composable
    private fun PermissionCard() {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(title = tr("permission_title"), summary = tr("permission_help"))
            TextButton(text = tr("copy_command"), onClick = {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ADB",
                    "adb shell pm grant io.github.hyperkey android.permission.WRITE_SECURE_SETTINGS"))
                Toast.makeText(this@MainActivity, tr("copied"), Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
        }
    }

    private fun openDedicatedShortcuts() {
        runCatching {
            startActivity(Intent().setClassName("com.miui.securitycore", "com.miui.miinput.keyboard.MiuiKeyboardShortcutActivity"))
        }.onFailure { openSystemKeyboardSettings("com.android.settings.inputmethod.LargeScreenKeyboardPadFragment") }
    }

    private fun openSystemKeyboardSettings(fragment: String) {
        runCatching {
            startActivity(Intent().setClassName("com.android.settings", "com.android.settings.SubSettings")
                .putExtra(":settings:show_fragment", fragment)
                .putExtra(":settings:show_fragment_title", tr("keyboard_shortcuts")))
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS)) }
                .onFailure { Toast.makeText(this, tr("open_settings_failed"), Toast.LENGTH_SHORT).show() }
        }
    }
}
