package dev.deref.roomtone.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.deref.roomtone.*
import dev.deref.roomtone.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val presets = listOf(5, 10, 15, 30)
private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun mmss(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

// tag vuoto = lingua di sistema
private fun setLang(tag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

@Composable
private fun Section(res: Int) = Text(stringResource(res), color = LocalAccent.current, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun Link(res: Int, url: String, open: (Intent) -> Unit) =
    Text(stringResource(res), color = LocalAccent.current, fontSize = 14.sp, modifier = Modifier.clickable { open(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.padding(vertical = 4.dp))

// Dialogo minuti personalizzati (1..240); OK attivo solo se valido
@Composable
private fun CustomMinDialog(cur: Int, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(cur.toString()) }
    val v = t.toIntOrNull()?.takeIf { it in 1..240 }
    AlertDialog(onDismiss,
        title = { Text(stringResource(R.string.custom_title)) },
        text = { OutlinedTextField(t, { t = it.filter(Char::isDigit).take(3) }, singleLine = true, isError = v == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
        confirmButton = { TextButton({ Prefs.setStandbyMin(v!!); onDismiss() }, enabled = v != null) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun SettingsScreen(accessGranted: Boolean, loginError: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val connected by EventStore.connected.collectAsStateWithLifecycle()
    val user by HistorySync.user.collectAsStateWithLifecycle()
    val sync by HistorySync.progress.collectAsStateWithLifecycle()
    val auto by Prefs.standbyAuto.collectAsStateWithLifecycle()
    val mins by Prefs.standbyMin.collectAsStateWithLifecycle()
    val shazamOn by ShazamControl.autoOn.collectAsStateWithLifecycle()
    val notInstalled by ShazamControl.notInstalled.collectAsStateWithLifecycle()
    val startWith by Prefs.autoStartShazam.collectAsStateWithLifecycle()
    val restartOn by Prefs.restartShazamOnScreenOn.collectAsStateWithLifecycle()
    var customDlg by remember { mutableStateOf(false) }
    val logPath = ctx.getExternalFilesDir(null)?.let { "$it/shazam_raw.jsonl" } ?: "n/d"
    fun open(i: Intent) { try { ctx.startActivity(i) } catch (_: Exception) { /* ponytail: schermata assente su alcuni OEM → nessuna azione */ } }

    if (customDlg) CustomMinDialog(mins, { customDlg = false })
    Page(stringResource(R.string.settings_title), onBack) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Section(R.string.sec_lastfm) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val u = user
                    if (u == null) {
                        Text(stringResource(R.string.lastfm_not_connected))
                        Button({ open(Intent(Intent.ACTION_VIEW, Uri.parse(LastFmApi.authUrl()))) }) { Text(stringResource(R.string.lastfm_connect)) }
                    } else {
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(stringResource(R.string.lastfm_connected, u))
                            val n = NumberFormat.getInstance().format(sync.total)
                            Text(when {
                                sync.running -> stringResource(R.string.history_syncing, n, sync.page, sync.pages)
                                sync.complete -> stringResource(R.string.history_up_to_date, n)
                                else -> stringResource(R.string.history_paused, n)
                            }, fontSize = 12.sp)
                        }
                        Button({ HistorySync.logout() }) { Text(stringResource(R.string.lastfm_disconnect)) }
                    }
                    (loginError ?: sync.error)?.let { Text(stringResource(R.string.sync_error, it), fontSize = 12.sp) }
                }
            }
            item { Section(R.string.sec_language) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ setLang("") }) { Text(stringResource(R.string.lang_system)) }
                    TextButton({ setLang("en") }) { Text("English") }
                    TextButton({ setLang("it") }) { Text("Italiano") }
                }
            }
            item { Section(R.string.sec_permissions) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.notif_access, stringResource(if (accessGranted) R.string.access_ok else R.string.access_missing)), Modifier.weight(1f, fill = false))
                    Button({ open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(stringResource(R.string.grant_access)) }
                }
            }
            item { Button({ open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text(stringResource(R.string.battery_opt)) } }
            // necessario per avviare Auto Shazam e tornare a Roomtone (limite di Android 10+)
            item { Button({ open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))) }) { Text(stringResource(R.string.overlay_perm)) } }
            item { Text(stringResource(R.string.overlay_hint), style = MaterialTheme.typography.bodySmall, color = TextMain.copy(alpha = 0.6f)) }
            item { Text(stringResource(R.string.huawei_note), fontSize = 12.sp, color = TextSoft) }
            item { Section(R.string.sec_shazam) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.shazam_status, stringResource(if (shazamOn) R.string.shazam_on else R.string.shazam_off)), Modifier.weight(1f, fill = false))
                    if (!shazamOn) Button({ ShazamControl.start(ctx, user = true) }) { Text(stringResource(R.string.shazam_turn_on)) }
                }
            }
            if (notInstalled) item { Text(stringResource(R.string.shazam_not_installed), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.shazam_start_with), Modifier.weight(1f, fill = false))
                    Switch(startWith, Prefs::setAutoStartShazam)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.shazam_restart), Modifier.weight(1f, fill = false))
                    Switch(restartOn, Prefs::setRestartShazamOnScreenOn)
                }
            }
            item { Text(stringResource(R.string.shazam_privacy), fontSize = 12.sp, color = TextSoft) }
            item { Section(R.string.sec_standby) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.standby_auto), Modifier.weight(1f, fill = false))
                    Switch(auto, Prefs::setStandbyAuto)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.standby_after))
                    presets.forEach { m ->
                        FilterChip(mins == m, { Prefs.setStandbyMin(m) }, { Text(stringResource(R.string.minutes, m)) }, enabled = auto)
                    }
                    val custom = mins !in presets
                    FilterChip(custom, { customDlg = true }, { Text(if (custom) stringResource(R.string.custom_min, mins) else stringResource(R.string.custom)) }, enabled = auto)
                }
            }
            item { Section(R.string.sec_diag) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.listener_status, stringResource(if (connected) R.string.listener_connected else R.string.listener_disconnected)))
                        Text(stringResource(R.string.log_path, logPath), fontSize = 11.sp)
                    }
                    Button({ EventStore.clear() }) { Text(stringResource(R.string.clear)) }
                }
            }
            items(list.asReversed()) { (r, ev, meta) -> // più recente in alto
                Column {
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(timeFmt.format(Date(r.start)))
                        Text("${r.track.title} – ${r.track.artist}", Modifier.weight(1f))
                        val sec = r.playedMs / 1000
                        Text("%d:%02d".format(sec / 60, sec % 60))
                        Text(ev?.durationMs?.let { stringResource(R.string.track_duration, mmss(it)) } ?: "")
                        Text(when (ev?.status) {
                            null -> stringResource(R.string.duration_searching)
                            Status.VALID -> stringResource(R.string.status_valid, ev.plays)
                            Status.DRAFT -> stringResource(R.string.status_draft, stringResource(ev.reason.res))
                            Status.DISCARDED -> stringResource(R.string.status_discarded, stringResource(ev.reason.res))
                        })
                        Text(stringResource(R.string.beats, r.beats))
                    }
                    meta?.let { m ->
                        val src = stringResource(if (m.source == MetaSource.HISTORY) R.string.meta_from_history else R.string.meta_from_shazam)
                        val body = listOfNotNull("${m.title} – ${m.artist}", m.album, src).joinToString(" · ")
                        Text(stringResource(R.string.meta_as, body), fontSize = 12.sp)
                    }
                }
            }
            item { Section(R.string.sec_about) }
            item { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) }
            item { Text(stringResource(R.string.about_made_by), fontSize = 12.sp, color = TextSoft) }
            item { Link(R.string.about_source, "https://github.com/DerefDev/roomtone", ::open) }
            item { Link(R.string.about_issue, "https://github.com/DerefDev/roomtone/issues", ::open) }
            item { Link(R.string.about_license, "https://www.gnu.org/licenses/gpl-3.0.html", ::open) }
            item {
                Column {
                    Text(stringResource(R.string.about_credits), fontSize = 12.sp, color = TextSoft)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Link(R.string.credit_lastfm, "https://www.last.fm", ::open)
                        Link(R.string.credit_mb, "https://musicbrainz.org", ::open)
                        Link(R.string.credit_itunes, "https://www.apple.com/itunes/", ::open)
                    }
                    Text(stringResource(R.string.credit_shazam), fontSize = 12.sp, color = TextSoft)
                }
            }
        }
    }
}
