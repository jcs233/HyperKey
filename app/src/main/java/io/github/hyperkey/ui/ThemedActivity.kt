package io.github.hyperkey.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.*
import java.util.Locale

open class ThemedActivity : ComponentActivity() {
    private var appearance = ""
    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences("appearance", MODE_PRIVATE).getString("language", "system")!!
        val config = Configuration(newBase.resources.configuration)
        if (language != "system") config.setLocale(Locale.forLanguageTag(language))
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val theme = getSharedPreferences("appearance", MODE_PRIVATE).getString("theme", "system")
        val dark = when (theme) {
            "light" -> false
            "dark" -> true
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        appearance = getSharedPreferences("appearance", MODE_PRIVATE).all.toString()
    }
    override fun onResume() {
        super.onResume()
        if (appearance != getSharedPreferences("appearance", MODE_PRIVATE).all.toString()) recreate()
    }
    fun tr(name: String, vararg args: Any): String {
        val id = io.github.hyperkey.core.ResourceIds.stringId(name)
        return if (id == 0) name else getString(id, *args)
    }
    @Composable fun HyperTheme(content: @Composable () -> Unit) {
        val prefs = getSharedPreferences("appearance", MODE_PRIVATE)
        val dark = when (prefs.getString("theme", "system")) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
        val custom = prefs.getString("accent", "")!!
        val accent = if (custom == "material-system" && android.os.Build.VERSION.SDK_INT >= 31) Color(getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)) else runCatching { Color(android.graphics.Color.parseColor(custom)) }.getOrNull()
        val foreground = if (accent != null && accent.luminance() > .179f) Color.Black else Color.White
        val colors = if (accent == null) { if (dark) darkColorScheme() else lightColorScheme() }
        else if (dark) darkColorScheme(primary = accent, onPrimary = foreground, primaryVariant = accent,
            primaryContainer = accent.copy(alpha = 0.22f), onPrimaryContainer = accent)
        else lightColorScheme(primary = accent, onPrimary = foreground, primaryVariant = accent,
            primaryContainer = accent.copy(alpha = 0.16f), onPrimaryContainer = accent)
        MiuixTheme(colors = colors, content = content)
    }
}
