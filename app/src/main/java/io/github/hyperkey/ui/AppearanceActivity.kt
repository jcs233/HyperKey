package io.github.hyperkey.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hyperkey.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val tonalRows = listOf(
    listOf("EF9A9A","F48FB1","CE93D8","B39DDB","9FA8DA","90CAF9","80CBC4","A5D6A7","FFCC80"),
    listOf("F44336","E91E63","9C27B0","673AB7","3F51B5","2196F3","009688","4CAF50","FF9800"),
    listOf("D32F2F","C2185B","7B1FA2","512DA8","303F9F","1976D2","00796B","388E3C","F57C00"))
private fun hex(input: String): String? = input.trim().removePrefix("#").takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }?.let { "#" + it.uppercase(java.util.Locale.ROOT) }
class AppearanceActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContent { HyperTheme { Screen() } }
    }
    private fun save(key: String, value: String) {
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        if (prefs.getString(key, null) == value) return
        prefs.edit().putString(key, value).apply(); recreate()
    }
    @Composable private fun Screen() {
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val accent = prefs.getString("accent", "").orEmpty()
        var draft by rememberSaveable(accent) { mutableStateOf(hex(accent).orEmpty()) }
        val candidate = hex(draft)
        val colors = MiuixTheme.colorScheme
        EditorPage(tr("appearance"), ::finish) {
            EditorGroup(tr("language")) {
                Options("language", listOf("system" to tr("follow_system"), "zh-CN" to "简体中文", "en" to "English"))
            }
            EditorGroup(tr("theme_mode")) {
                Options("theme", listOf("system" to tr("follow_system"), "light" to tr("light"), "dark" to tr("dark")))
            }
            EditorGroup(tr("accent_color")) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice(tr("miuix_default"), accent.isEmpty()) { draft = ""; save("accent", "") }
                    if (Build.VERSION.SDK_INT >= 31) Choice(stringResource(R.string.md_wallpaper), accent == "material-system") {
                        draft = ""; save("accent", "material-system") }
                }
                Text(stringResource(R.string.md_palette), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.md_help), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                var tone by rememberSaveable { mutableIntStateOf(1) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(200,500,700).forEachIndexed { index, level -> Choice(level.toString(), tone == index) { tone = index } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tonalRows[tone].forEach { value ->
                        val swatch = Color(android.graphics.Color.parseColor("#$value")); val selected = candidate == "#$value"
                        Box(Modifier.size(48.dp).selectable(selected, role = Role.RadioButton) { draft = "#$value" }
                            .semantics { contentDescription = "#$value" }.padding(3.dp)
                            .background(swatch, RoundedCornerShape(12.dp)).border(1.dp, colors.onSurface.copy(alpha = .22f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center) {
                            if (selected) Text("✓", color = if (swatch.luminance() > .179f) Color.Black else Color.White)
                        }
                    }
                }
                TextField(value = draft, onValueChange = { draft = it }, label = tr("hex_color"), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (draft.isNotBlank() && candidate == null) Text(tr("color_invalid"), color = colors.onSurfaceVariantSummary, fontSize = 13.sp)
                val preview = candidate?.let { Color(android.graphics.Color.parseColor(it)) } ?: colors.primary
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.background(preview, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.md_preview) + (candidate?.let { "  $it" } ?: ""),
                            color = if (preview.luminance() > .179f) Color.Black else Color.White)
                    }
                    TextButton(text = tr("apply"), enabled = candidate != null && candidate != accent,
                        onClick = { candidate?.let { draft = it; save("accent", it) } })
                }
            }
            Text(tr("appearance_scope"), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
        }
    }
    @Composable private fun Options(key: String, choices: List<Pair<String,String>>) {
        val selected = getSharedPreferences("appearance", MODE_PRIVATE).getString(key, "system")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { (v,label) -> Choice(label, v == selected) { save(key,v) } }
        }
    }
}
