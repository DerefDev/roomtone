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

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun mmss(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

// tag vuoto = lingua di sistema
private fun setLang(tag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

@Composable
private fun Section(res: Int) = Text(stringResource(res), color = LocalAccent.current, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))

@Composable
fun SettingsScreen(accessGranted: Boolean, loginError: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val connected by EventStore.connected.collectAsStateWithLifecycle()
    val user by HistorySync.user.collectAsStateWithLifecycle()
    val sync by HistorySync.progress.collectAsStateWithLifecycle()
    val auto by Prefs.standbyAuto.collectAsStateWithLifecycle()
    val mins by Prefs.standbyMin.collectAsStateWithLifecycle()
    val logPath = ctx.getExternalFilesDir(null)?.let { "$it/shazam_raw.jsonl" } ?: "n/d"
    fun open(i: Intent) { try { ctx.startActivity(i) } catch (_: Exception) { /* ponytail: schermata assente su alcuni OEM → nessuna azione */ } }

    Page(stringResource(R.string.settings_title), onBack) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            item { Text(stringResource(R.string.huawei_note), fontSize = 12.sp, color = TextSoft) }
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
                    listOf(5, 10, 15, 30).forEach { m ->
                        FilterChip(mins == m, { Prefs.setStandbyMin(m) }, { Text(stringResource(R.string.minutes, m)) }, enabled = auto)
                    }
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
        }
    }
}
