package dev.deref.roomtone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deref.roomtone.R
import dev.deref.roomtone.Release
import dev.deref.roomtone.Updates
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape

// Voce "Scrobble <servizio>": drafts > 0 → pallino accento
class ScrobbleEntry(val label: Int, val drafts: Int, val onClick: () -> Unit)

// Pannello da destra su overlay scuro; chiusura con tap fuori o back.
@Composable
fun Drawer(
    open: Boolean, onClose: () -> Unit, scrobbling: Boolean, onScrobbling: (Boolean) -> Unit,
    onSettings: () -> Unit, scrobbleEntries: List<ScrobbleEntry>, onStandby: () -> Unit,
    update: Release? = null, onUpdate: () -> Unit = {},
) {
    val accent = LocalAccent.current
    BackHandler(open, onClose)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose))
        }
        AnimatedVisibility(open, Modifier.align(Alignment.CenterEnd), enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            Column(
                Modifier.width(300.dp).fillMaxHeight()
                    .shadow(24.dp, ambientColor = accent, spotColor = accent)
                    .background(Color(0xFF161616))
                    .drawBehind { drawLine(accent.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, size.height), 3.dp.toPx()) }
                    .clickable(remember { MutableInteractionSource() }, null) {} // tap sul pannello non chiude
                    .padding(vertical = 16.dp),
            ) {
                if (update != null) {
                    val busy by Updates.installing.collectAsState()
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f))
                        .clickable(enabled = !busy, onClick = onUpdate).padding(12.dp)) {
                        Text(stringResource(R.string.update_banner, update.version), color = accent, fontSize = 16.sp)
                        if (busy) CircularProgressIndicator(Modifier.padding(top = 4.dp).size(16.dp), color = accent, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.update_banner_hint), color = accent, fontSize = 12.sp)
                    }
                }
                Item(R.drawable.ic_settings, stringResource(R.string.menu_settings), onSettings)
                scrobbleEntries.forEach { Item(R.drawable.ic_scrobble, stringResource(it.label), it.onClick, it.drafts > 0) }
                Item(R.drawable.ic_power, stringResource(R.string.menu_standby), onStandby)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = accent.copy(alpha = 0.3f))
                SwitchRow(stringResource(R.string.menu_scrobbling), scrobbling, onScrobbling)
            }
        }
    }
}

@Composable
private fun Item(icon: Int, label: String, onClick: () -> Unit, dot: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = LocalAccent.current)
        Spacer(Modifier.width(16.dp))
        Text(label, fontSize = 18.sp)
        if (dot) { Spacer(Modifier.width(8.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(LocalAccent.current)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 18.sp)
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = accent, checkedThumbColor = Color.Black, checkedBorderColor = accent,
        ))
    }
}
