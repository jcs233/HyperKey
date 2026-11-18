package io.github.hyperkey.ui

import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperkey.core.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.theme.*
import java.util.concurrent.Executors

class ProfileActivity : ThemedActivity() {
    private data class App(val pkg: String, val label: String, val icon: ImageBitmap)
    private var profiles by mutableStateOf(AppProfiles.EMPTY)
    private var global by mutableStateOf(Config.DEFAULT)
    private var writable by mutableStateOf(false)
    private var apps by mutableStateOf<List<App>>(emptyList())
    private var loading by mutableStateOf(true)
    private var fallbackIcon by mutableStateOf<ImageBitmap?>(null)
    private var extraIcons by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    private val requestedIcons = mutableSetOf<String>()
    private val worker = Executors.newSingleThreadExecutor()
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        worker.execute {
            val fallback = iconBitmap(packageManager.defaultActivityIcon)
            val result = runCatching {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .distinctBy { it.activityInfo.packageName }
                    .map { App(it.activityInfo.packageName, it.loadLabel(packageManager).toString(),
                        runCatching { iconBitmap(it.loadIcon(packageManager)) }.getOrDefault(fallback)) }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            runOnUiThread { apps = result; fallbackIcon = fallback; loading = false; loadExtraIcons() }
        }
        setContent {
            HyperTheme { Screen() }
        }
    }
    override fun onStart() {
        super.onStart()
        listOf(AppProfiles.SETTING, Config.SETTING).forEach {
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(it), false, observer)
        }
        reload()
    }
    override fun onStop() { contentResolver.unregisterContentObserver(observer); super.onStop() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
    private fun reload() {
        writable = ConfigStore.canWrite(this)
        profiles = ProfileStore.read(this)
        global = ConfigStore.read(this)
        loadExtraIcons()
    }
    private fun iconBitmap(drawable: Drawable): ImageBitmap {
        val size = (48 * resources.displayMetrics.density).toInt().coerceIn(48, 192)
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(it))
        }.asImageBitmap()
    }
    private fun loadExtraIcons() {
        val missing = profiles.entries.keys.filter { name -> apps.none { it.pkg == name } && requestedIcons.add(name) }
        if (missing.isEmpty() || worker.isShutdown) return
        worker.execute {
            val icons = missing.mapNotNull { name -> runCatching { name to iconBitmap(packageManager.getApplicationIcon(name)) }.getOrNull() }.toMap()
            runOnUiThread { extraIcons = extraIcons + icons }
        }
    }
    @Composable private fun AppIcon(pkg: String) {
        val icon = apps.firstOrNull { it.pkg == pkg }?.icon ?: extraIcons[pkg] ?: fallbackIcon
        if (icon != null) Image(bitmap = icon, contentDescription = tr("app_icon"),
            modifier = Modifier.padding(end = 12.dp).size(44.dp))
        else Spacer(Modifier.padding(end = 12.dp).size(44.dp))
    }
    private fun change(pkg: String, transform: (AppProfiles.Profile) -> AppProfiles.Profile?) {
        if (!writable) return
        runCatching {
            val latest = ProfileStore.read(this)
            ProfileStore.write(this, latest.with(pkg, transform(latest.entries[pkg] ?: AppProfiles.Profile.INHERIT)))
            reload()
        }.onFailure { Toast.makeText(this, tr("save_error", it.message ?: ""), Toast.LENGTH_LONG).show() }
    }
    private fun label(pkg: String): String = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    @Composable private fun Screen() {
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        var choosing by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        val navigateBack = {
            if (selected != null || choosing) { selected = null; choosing = false; query = "" } else finish()
        }
        BackHandler(enabled = selected != null || choosing, onBack = navigateBack)
        val pkg = selected
        Scaffold(topBar = { PageBar(if (pkg != null) label(pkg) else if (choosing) tr("add_app") else tr("profiles"), navigateBack) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 920.dp).fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pkg != null) {
                    Editor(pkg, onRemove = {
                        runCatching {
                            var mappings = MappingStore.read(this@ProfileActivity)
                            mappings.rules.filter { it.owner() == pkg }.forEach { mappings = mappings.remove(it.id()) }
                            MappingStore.write(this@ProfileActivity, mappings)
                        }.onFailure { Toast.makeText(this@ProfileActivity, tr("save_failed"), Toast.LENGTH_SHORT).show() }
                        change(pkg) { null }; selected = null
                    })
                } else if (choosing) {
                    TextField(value = query, onValueChange = { query = it }, label = tr("search_apps"), singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    if (loading) Text(tr("loading_apps"), modifier = Modifier.padding(12.dp))
                    val matches = apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (AppProfiles.validPackage(query.trim()) && apps.none { it.pkg == query.trim() }) item {
                            Card(Modifier.fillMaxWidth()) {
                                BasicComponent(title = tr("add_package"), summary = query.trim(), enabled = writable, onClick = {
                                    val name = query.trim(); change(name) { it }; selected = name; choosing = false
                                })
                            }
                        }
                        items(matches, key = { it.pkg }) { app ->
                            Card(Modifier.fillMaxWidth()) {
                                BasicComponent(title = app.label, summary = app.pkg + if (profiles.entries.containsKey(app.pkg)) tr("already_added") else "",
                                    startAction = { AppIcon(app.pkg) },
                                    enabled = writable, onClick = { change(app.pkg) { it }; selected = app.pkg; choosing = false })
                            }
                        }
                        if (!loading && matches.isEmpty()) item { Text(tr("no_matches"), modifier = Modifier.padding(12.dp)) }
                    }
                } else {
                    Text(tr("profiles_help"),
                        fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Card(Modifier.fillMaxWidth()) {
                        BasicComponent(title = tr("add_app"), summary = tr("pick_app"), enabled = writable,
                            onClick = { choosing = true; query = "" })
                    }
                    if (!writable) Text(tr("profile_permission"))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        if (profiles.entries.isEmpty()) item { Text(tr("no_profiles"), modifier = Modifier.padding(16.dp)) }
                        items(profiles.entries.keys.toList(), key = { it }) { name ->
                            val p = profiles.entries.getValue(name)
                            Card(Modifier.fillMaxWidth()) {
                                BasicComponent(title = label(name), summary = "$name\n${description(p)}", startAction = { AppIcon(name) }, onClick = { selected = name })
                            }
                        }
                    }
                }
            }
            }
        }
    }
    private fun description(p: AppProfiles.Profile): String = when {
        !p.enabled -> tr("profile_paused")
        p.overrideMask == 0L -> tr("inherit")
        p.overrideMask == AppProfiles.ALL && p.disabledMask == 0L -> tr("allow_all")
        p.disabledMask == AppProfiles.ALL -> tr("disable_all")
        else -> tr("custom_count", java.lang.Long.bitCount(p.overrideMask))
    }
    @Composable private fun Editor(pkg: String, onRemove: () -> Unit) {
        val profile = profiles.entries[pkg] ?: AppProfiles.Profile.INHERIT
        val effective = profile.resolve(global)
        val editable = writable && profile.enabled
        var confirmRemove by rememberSaveable(pkg) { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg)
                Text(pkg, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Card(Modifier.fillMaxWidth()) {
                SuperSwitch(title = tr("enable_profile"), summary = tr("pause_help"), checked = profile.enabled,
                    enabled = writable, onCheckedChange = { value -> change(pkg) { it.withEnabled(value) } })
                BasicComponent(title = tr("current_profile"), summary = description(profile))
                SuperSwitch(title = tr("win_passthrough"), summary = tr("win_passthrough_help"), checked = profile.rawWin,
                    enabled = editable, onCheckedChange = { value -> change(pkg) { it.withRawWin(value) } })
                BasicComponent(title = tr("mappings"), summary = tr("app_mapping", label(pkg)), onClick = {
                    startActivity(Intent(this@ProfileActivity, MappingActivity::class.java).putExtra("package", pkg))
                })
                FlowRow(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = tr("inherit"), enabled = editable, onClick = { change(pkg) { it.preset(null) } })
                    TextButton(text = tr("allow_all"), enabled = editable, onClick = { change(pkg) { it.preset(true) } })
                    TextButton(text = tr("disable_all"), enabled = editable, onClick = { change(pkg) { it.preset(false) } })
                }
            }
            Text(if (global.blockAll) tr("profile_master")
                else tr("profile_inherit_help"),
                fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Shortcut.values().groupBy { it.group }.forEach { (group, shortcuts) ->
                Text(UiText.group(this@ProfileActivity, group), modifier = Modifier.padding(start = 16.dp, top = 8.dp), fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Card(Modifier.fillMaxWidth()) {
                    shortcuts.forEach { shortcut ->
                        val overridden = (profile.overrideMask and shortcut.bit()) != 0L
                        SuperSwitch(title = UiText.title(this@ProfileActivity, shortcut), summary = tr("shortcut_profile_hint", UiText.hint(this@ProfileActivity, shortcut), if (overridden) tr("custom_item") else tr("inherit")),
                            checked = effective.isIndividuallyEnabled(shortcut), enabled = editable,
                            onCheckedChange = { value -> change(pkg) { it.withShortcut(shortcut, value) } })
                        if (overridden) TextButton(text = tr("item_inherit"), enabled = editable,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                            onClick = { change(pkg) { it.withShortcut(shortcut, null) } })
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                BasicComponent(title = if (confirmRemove) tr("remove_confirm") else tr("remove_profile"), summary = tr("remove_help"),
                    enabled = writable, onClick = { if (confirmRemove) onRemove() else confirmRemove = true })
            }
        }
    }
}
