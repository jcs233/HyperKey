package io.github.hyperkey.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import io.github.hyperkey.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun PageBar(title: String, onBack: () -> Unit) {
    val label = stringResource(R.string.back)
    val ink = MiuixTheme.colorScheme.onSurface
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    SmallTopAppBar(title = title, navigationIcon = {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).semantics { contentDescription = label },
            backgroundColor = Color.Transparent) {
            Canvas(Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val outer = w * (if (rtl) .35f else .65f); val inner = w - outer
                val arrow = Path().apply { moveTo(outer, h * .14f); lineTo(inner, h * .5f); lineTo(outer, h * .86f) }
                drawPath(arrow, ink, style = Stroke(width = 2.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    })
}

@Composable internal fun EditorPage(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = { PageBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).then(modifier), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content(); Spacer(Modifier.height(12.dp))
            }
        }
    }
}
@Composable internal fun EditorGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp); content()
        }
    }
}
@Composable internal fun Choice(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.defaultMinSize(minHeight = 48.dp)
        .background(if (active) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = .08f), shape)
        .selectable(selected = active, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 14.sp, color = if (active) colors.onPrimary else colors.onSurface)
    }
}
