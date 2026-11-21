package io.github.hyperkey.ui

import android.content.Context
import android.content.Intent
import androidx.core.graphics.createBitmap
import androidx.compose.ui.platform.LocalDensity
import android.graphics.Canvas
import android.util.LruCache
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hyperkey.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*

internal data class LaunchApp(val pkg: String, val label: String)
internal fun launchApps(c: Context): List<LaunchApp> = c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    .distinctBy { it.activityInfo.packageName }.map { LaunchApp(it.activityInfo.packageName, it.loadLabel(c.packageManager).toString()) }
    .sortedBy { it.label.lowercase() }
private val icons = LruCache<String, ImageBitmap>(64)
@Composable internal fun LauncherIcon(pkg: String) {
    val c = LocalContext.current
    val size = with(LocalDensity.current) { 44.dp.roundToPx() }.coerceIn(44, 144)
    val icon by produceState<ImageBitmap?>(null, pkg, size) {
        value = withContext(Dispatchers.IO) {
            val id = "$size:$pkg"
            icons.get(id) ?: runCatching {
                val d = runCatching { c.packageManager.getApplicationIcon(pkg) }.getOrElse { c.packageManager.defaultActivityIcon }
                createBitmap(size,size).also { d.setBounds(0,0,size,size); d.draw(Canvas(it)) }.asImageBitmap().also { icons.put(id,it) }
            }.getOrNull()
        }
    }
    Box(Modifier.padding(end=12.dp).size(44.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
}
@Composable internal fun AppTargetPicker(selected: String, onSelect: (String) -> Unit) {
    val c = LocalContext.current
    val apps by produceState<List<LaunchApp>?>(null) { value = withContext(Dispatchers.IO) { runCatching { launchApps(c) }.getOrDefault(emptyList()) } }
    var query by rememberSaveable { mutableStateOf("") }
    TextField(value=query,onValueChange={ query=it },label=stringResource(R.string.search_apps), singleLine=true, modifier=Modifier.fillMaxWidth())
    val matches = remember(apps,query) { apps?.filter { it.label.contains(query,true) || it.pkg.contains(query,true) }.orEmpty() }
    if (apps == null) Text(stringResource(R.string.loading_apps)) else if (matches.isEmpty()) Text(stringResource(R.string.no_matches))
    LazyColumn(Modifier.heightIn(max=280.dp)) { items(matches, key={ it.pkg }) { app ->
        BasicComponent(title=app.label,summary=(if(selected==app.pkg) "✓  " else "")+app.pkg,startAction={ LauncherIcon(app.pkg) },onClick={ onSelect(app.pkg) })
    } }
}
internal fun keyLabel(code: Int) = if (code == KeyEvent.KEYCODE_FUNCTION) "Fn" else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    .replace("META_LEFT","Win L").replace("META_RIGHT","Win R").replace('_',' ')

/** Only public pages that our own app UID can launch. No extras, caller-supplied intents or grants. */
internal fun accessiblePage(c: Context, component: android.content.ComponentName): Boolean = runCatching {
    val pm = c.packageManager
    val info = pm.getActivityInfo(component,0)
    val override = pm.getComponentEnabledSetting(component)
    val enabled = override == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
        (override == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && info.enabled)
    enabled && info.applicationInfo.enabled && info.exported && (info.permission.isNullOrBlank() ||
        c.checkSelfPermission(info.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
}.getOrDefault(false)

@Composable internal fun PublicPagePicker(pkg: String, selected: String, onPick: (String)->Unit) {
    val c = LocalContext.current
    val pages by produceState<List<Pair<String,String>>?>(null,pkg) {
        value = withContext(Dispatchers.IO) { runCatching {
            c.packageManager.getPackageInfo(pkg,android.content.pm.PackageManager.GET_ACTIVITIES).activities.orEmpty().mapNotNull {
                val component=android.content.ComponentName(pkg,it.name)
                if (accessiblePage(c,component)) it.loadLabel(c.packageManager).toString() to component.flattenToShortString() else null
            }.sortedBy { it.second }
        }.getOrDefault(emptyList()) }
    }
    Text(stringResource(R.string.public_page_help))
    if(pages==null) Text(stringResource(R.string.loading_apps)) else if(pages.isNullOrEmpty()) Text(stringResource(R.string.no_matches))
    LazyColumn(Modifier.heightIn(max=240.dp)) { items(pages.orEmpty(), key={ it.second }) { (label,value) ->
        BasicComponent(title=label,summary=(if(value==selected) "✓  " else "")+value.substringAfter('/'),onClick={ onPick(value) })
    } }
}
