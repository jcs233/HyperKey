package io.github.hyperkey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hyperkey.R
import io.github.hyperkey.core.KeyMappings
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun KeyboardPicker(title: String, initial: BoardChord, describe: (BoardChord)->String,
    dismiss: ()->Unit, accept: (BoardChord)->Unit) {
    var packed by rememberSaveable { mutableStateOf(initial.pack()) }; val chord=BoardChord.unpack(packed)
    var searching by rememberSaveable { mutableStateOf(false) }; var query by rememberSaveable { mutableStateOf("") }
    val colors = MiuixTheme.colorScheme
    val context=androidx.compose.ui.platform.LocalContext.current
    val select: (Int)->Unit = { next ->
        if (chord.held.size >= KeyMappings.MAX_ORDINARY-1 && !chord.selected(next) && io.github.hyperkey.core.KeyMeta.group(next)==0)
            android.widget.Toast.makeText(context,R.string.chord_limit,android.widget.Toast.LENGTH_SHORT).show()
        else packed = chord.tap(next).pack()
    }
    Dialog(onDismissRequest=dismiss, properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().imePadding()) {
            PageBar(stringResource(R.string.board_title,title), dismiss)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(horizontal=12.dp)) {
                val wide = maxWidth >= 920.dp
                // Keep explicit zone preference while adapting the initial view to the available width.
                var pickedZone by rememberSaveable { mutableStateOf<String?>(null) }
                val zone = pickedZone?.let(KeyZone::valueOf) ?: if(wide) KeyZone.ALL else KeyZone.MAIN
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        KeyZone.entries.forEach { z ->
                            val label = when(z) { KeyZone.ALL -> R.string.board_all; KeyZone.MAIN -> R.string.board_main; KeyZone.NAV -> R.string.board_nav; KeyZone.PAD -> R.string.board_pad }
                            Choice(stringResource(label),!searching && zone==z) { searching=false; pickedZone=z.name }
                        }
                        Choice(stringResource(R.string.board_search),searching) { searching=true }
                    }
                    if(searching) {
                        TextField(value=query,onValueChange={ query=it },label=stringResource(R.string.search_key),singleLine=true,modifier=Modifier.fillMaxWidth())
                        val hits = remember(query) {
                            val q=query.trim().removePrefix("KEYCODE_").replace('_',' ')
                            val onBoard=keyboard108.associateBy { it.code }
                            (7..288).filter { k -> (KeyMappings.keyAllowed(k) || k in onBoard) &&
                                (q.isEmpty() || k.toString()==q || keyLabel(k).contains(q,true) || onBoard[k]?.legend?.contains(q,true)==true) }
                        }
                        if(hits.isEmpty()) Text(stringResource(R.string.no_matches),modifier=Modifier.padding(12.dp))
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) { items(hits,key={it}) { k ->
                            val enabled=KeyMappings.keyAllowed(k)
                            val picked=chord.selected(k)
                            BasicComponent(title=(if(picked) "✓  " else "")+keyLabel(k),summary=if(enabled) "Android · $k" else stringResource(R.string.board_reserved_key,k),
                                enabled=enabled,onClick={ select(k) })
                        } }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.board_help),color=colors.onSurfaceVariantSummary,fontSize=13.sp)
                            key(zone) { KeyboardSurface(zone,chord,select) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(stringResource(R.string.board_selection),fontSize=12.sp,color=colors.onSurfaceVariantSummary)
                    Text(describe(chord),fontWeight=FontWeight.SemiBold,fontSize=16.sp) }
                TextButton(text=stringResource(R.string.clear),enabled=chord.code!=0,onClick={ packed=BoardChord().pack() })
                TextButton(text=stringResource(R.string.board_apply),enabled=chord.code!=0,onClick={ accept(chord) })
            }
        }
    }
}

@Composable private fun KeyboardSurface(zone: KeyZone, chord: BoardChord, select: (Int)->Unit) {
    val colors=MiuixTheme.colorScheme
    val keys=remember(zone) { keyboard108.filter { zone==KeyZone.ALL || it.zone==zone } }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer).padding(6.dp)) {
        val unit=(maxWidth/zone.width).coerceIn(if(zone==KeyZone.ALL && maxWidth >= 900.dp) 39.dp else 48.dp,62.dp)
        val needsPan=unit*zone.width > maxWidth
        val rowHeight=48.dp
        Column {
            if(needsPan) Text(stringResource(R.string.board_pan),fontSize=12.sp,color=colors.onSurfaceVariantSummary,modifier=Modifier.padding(4.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.horizontalScroll(rememberScrollState())) {
                    Box(Modifier.size(unit*zone.width,rowHeight*6.3f)) {
                        for (cap in keys) Keycap108(cap,chord,unit,rowHeight,zone,select)
                    }
                }
            }
        }
    }
}

@Composable private fun Keycap108(cap: BoardKey, chord: BoardChord, unit: Dp, rowHeight: Dp, zone: KeyZone, select: (Int)->Unit) {
    val colors=MiuixTheme.colorScheme
    val allowed=KeyMappings.keyAllowed(cap.code); val primary=cap.code==chord.code
    val modifier=chord.selected(cap.code) && !primary
    val shape=RoundedCornerShape(6.dp)
    val face=when { primary -> colors.primary; modifier -> colors.primaryContainer; !allowed -> colors.surfaceContainer; else -> colors.surface }
    val ink=when { primary -> colors.onPrimary; modifier -> colors.onPrimaryContainer; !allowed -> colors.onSurface.copy(alpha=.47f); else -> colors.onSurface }
    val description="${keyLabel(cap.code)} · ${cap.code}"
    val unavailable=stringResource(R.string.board_reserved)
    // Absolute positions preserve the physical keyboard, independently of the UI language's reading direction.
    Box(Modifier.absoluteOffset(unit*(cap.x-zone.left),rowHeight*cap.y).size(unit*cap.width,rowHeight*cap.height)
        .clickable(enabled=allowed,role=Role.Button) { select(cap.code) }
        .semantics(mergeDescendants=true) { contentDescription=description; selected=primary || modifier; if(!allowed) stateDescription=unavailable }
        .padding(1.5.dp).background(colors.outline.copy(alpha=.32f),shape).padding(bottom=2.dp)
        .background(face,shape).border(1.dp,if(modifier) colors.primary else colors.outline.copy(alpha=.2f),shape),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Text(cap.legend,fontSize=if(cap.legend.length>3) 11.sp else 14.sp,lineHeight=15.sp,color=ink,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(cap.code.toString(),fontSize=9.sp,lineHeight=11.sp,color=ink.copy(alpha=if(allowed) .75f else .55f),maxLines=1)
        }
    }
}
