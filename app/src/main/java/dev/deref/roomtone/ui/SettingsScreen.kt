package dev.deref.roomtone.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.deref.roomtone.*
import androidx.compose.ui.text.font.FontWeight
import dev.deref.roomtone.R
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val presets = listOf(5, 10, 15, 30)
private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private fun mmss(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

// tag vuoto = lingua di sistema
// Lingue con una cartella values-xx (inglese = values), elenco generato da Gradle: nuova lingua = nuova cartella
private val LANGS = BuildConfig.LANGS.split(',')

private fun setLang(tag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

// Area = card arrotondata con titolo in accent
@Composable
private fun Area(title: Int, content: @Composable ColumnScope.() -> Unit) =
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.06f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), color = LocalAccent.current, fontSize = 14.sp)
            content()
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangMenu() {
    var open by remember { mutableStateOf(false) }
    val cur = AppCompatDelegate.getApplicationLocales().let { if (it.isEmpty) "" else it[0]!!.toLanguageTag() }
    val opts = listOf("" to stringResource(R.string.lang_system)) + LANGS.map { t ->
        t to Locale.forLanguageTag(t).let { it.getDisplayName(it).replaceFirstChar { c -> c.titlecase(it) } }
    }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField((opts.firstOrNull { it.first == cur } ?: opts[0]).second, {}, readOnly = true, singleLine = true,
            label = { Text(stringResource(R.string.sec_language)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(320.dp))
        ExposedDropdownMenu(open, { open = false }) {
            opts.forEach { (tag, name) -> DropdownMenuItem({ Text(name) }, { open = false; setLang(tag) }) }
        }
    }
}

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

// Versioni scelte: brano → "titolo · album"; tocco → dialogo per cambiare o rimuovere la scelta
@Composable
private fun VersionsArea() {
    val all by VersionChoices.choices.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<String?>(null) }
    Area(R.string.sec_versions) {
        if (all.isEmpty()) Text(stringResource(R.string.versions_hint), fontSize = 12.sp, color = TextSoft)
        all.forEach { (hk, c) ->
            Column(Modifier.fillMaxWidth().clickable { edit = hk }.padding(vertical = 4.dp)) {
                Text("${c.song.title} – ${c.song.artist}", fontSize = 15.sp)
                Text(listOfNotNull(c.key.title, c.key.album).joinToString(" · "), fontSize = 13.sp, color = TextSoft)
            }
        }
    }
    val c = edit?.let { all[it] }
    if (c != null) {
        // ponytail: scelta non più in cronologia → compare solo come voce salvata (plays 0)
        val opts = HistorySync.index.versions(c.song).let { vs -> if (vs.any { it.key == c.key }) vs else listOf(Version(c.key.artist, c.key.title, c.key.album, 0, emptyList())) + vs }
        var sel by remember(c) { mutableStateOf(opts.first { it.key == c.key }) }
        AlertDialog({ edit = null },
            title = { Text("${c.song.title} – ${c.song.artist}", fontSize = 16.sp) },
            text = { LazyColumn { item { VersionPicker(opts, sel) { sel = it } } } },
            confirmButton = { TextButton({ VersionChoices.set(c.song, sel.key); edit = null }) { Text(stringResource(R.string.version_save)) } },
            dismissButton = {
                Row {
                    TextButton({ VersionChoices.remove(edit!!); edit = null }) { Text(stringResource(R.string.version_remove)) }
                    TextButton({ edit = null }) { Text(stringResource(R.string.cancel)) }
                }
            })
    }
}

@Composable
fun SettingsScreen(accessGranted: Boolean, loginError: String?, onCovers: () -> Unit, onCredits: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val connected by EventStore.connected.collectAsStateWithLifecycle()
    val diag by Prefs.diagnostics.collectAsStateWithLifecycle()
    val logDays by Prefs.logDays.collectAsStateWithLifecycle()
    val user by HistorySync.user.collectAsStateWithLifecycle()
    val sync by HistorySync.progress.collectAsStateWithLifecycle()
    val auto by Prefs.standbyAuto.collectAsStateWithLifecycle()
    val mins by Prefs.standbyMin.collectAsStateWithLifecycle()
    val shazamOn by ShazamControl.autoOn.collectAsStateWithLifecycle()
    val notInstalled by ShazamControl.notInstalled.collectAsStateWithLifecycle()
    val startWith by Prefs.autoStartShazam.collectAsStateWithLifecycle()
    val restartOn by Prefs.restartShazamOnScreenOn.collectAsStateWithLifecycle()
    val dgUser by DiscogsCollection.user.collectAsStateWithLifecycle()
    val dg by DiscogsCollection.progress.collectAsStateWithLifecycle()
    val albumMode by Prefs.albumMode.collectAsStateWithLifecycle()
    val edit by Prefs.editOptions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var dgError by remember { mutableStateOf<String?>(null) }
    var customDlg by remember { mutableStateOf(false) }
    val logPath = ctx.getExternalFilesDir(null)?.let { "$it/shazam_raw.jsonl" } ?: "n/d"
    fun open(i: Intent) { try { ctx.startActivity(i) } catch (_: Exception) { /* ponytail: schermata assente su alcuni OEM → nessuna azione */ } }

    if (customDlg) CustomMinDialog(mins, { customDlg = false })
    Page(stringResource(R.string.settings_title), onBack) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Area(R.string.sec_account) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val u = user
                        if (u == null) {
                            Text(stringResource(R.string.lastfm_not_connected), Modifier.weight(1f, fill = false))
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
                    }
                    (loginError ?: sync.error)?.let { Text(stringResource(R.string.sync_error, it), fontSize = 12.sp) }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val u = dgUser
                        if (u == null) {
                            Text(stringResource(R.string.discogs_not_connected), Modifier.weight(1f, fill = false))
                            Button({
                                scope.launch {
                                    try { open(Intent(Intent.ACTION_VIEW, Uri.parse(DiscogsApi.authorizeUrl(DiscogsApi.requestToken())))); dgError = null }
                                    catch (e: Exception) { dgError = e.message ?: "error" }
                                }
                            }) { Text(stringResource(R.string.discogs_connect)) }
                        } else {
                            Column(Modifier.weight(1f, fill = false)) {
                                Text(stringResource(R.string.discogs_connected, u))
                                Text(when {
                                    dg.running && dg.coversTotal > 0 -> stringResource(R.string.discogs_covers, dg.coversDone, dg.coversTotal)
                                    dg.running && dg.detailsTotal > 0 -> stringResource(R.string.discogs_details, dg.detailsDone, dg.detailsTotal)
                                    dg.running -> pluralStringResource(R.plurals.discogs_listing, dg.releases, dg.releases)
                                    else -> pluralStringResource(R.plurals.discogs_up_to_date, dg.releases, dg.releases)
                                }, fontSize = 12.sp)
                            }
                            Button({ DiscogsCollection.logout() }) { Text(stringResource(R.string.lastfm_disconnect)) }
                        }
                    }
                    (dgError ?: dg.error)?.let { Text(stringResource(R.string.sync_error, it), fontSize = 12.sp) }
                    if (dgUser != null) Button(onCovers) { Text(stringResource(R.string.covers_button)) }
                }
            }
            item {
                Area(R.string.sec_shazam) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.shazam_status, stringResource(if (shazamOn) R.string.shazam_on else R.string.shazam_off)), Modifier.weight(1f, fill = false))
                        if (!shazamOn) Button({ ShazamControl.start(ctx, user = true) }) { Text(stringResource(R.string.shazam_turn_on)) }
                    }
                    if (notInstalled) Text(stringResource(R.string.shazam_not_installed), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.shazam_start_with), Modifier.weight(1f))
                        Switch(startWith, Prefs::setAutoStartShazam)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.shazam_restart), Modifier.weight(1f))
                        Switch(restartOn, Prefs::setRestartShazamOnScreenOn)
                    }
                    Text(stringResource(R.string.shazam_privacy), fontSize = 12.sp, color = TextSoft)
                }
            }
            item {
                Area(R.string.sec_standby) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.standby_auto), Modifier.weight(1f))
                        Switch(auto, Prefs::setStandbyAuto)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.standby_after))
                        presets.forEach { m ->
                            FilterChip(mins == m, { Prefs.setStandbyMin(m) }, { Text(stringResource(R.string.minutes, m)) }, enabled = auto)
                        }
                        val custom = mins !in presets
                        FilterChip(custom, { customDlg = true }, { Text(if (custom) stringResource(R.string.custom_min, mins) else stringResource(R.string.custom)) }, enabled = auto)
                    }
                }
            }
            item {
                Area(R.string.sec_album) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(albumMode == "history", { Prefs.setAlbumMode("history") }, { Text(stringResource(R.string.album_history)) })
                        FilterChip(albumMode == "owned", { Prefs.setAlbumMode("owned") }, { Text(stringResource(R.string.album_owned)) })
                    }
                    Text(stringResource(R.string.album_hint), fontSize = 12.sp, color = TextSoft)
                    if (albumMode == "owned") {
                        Text(stringResource(R.string.edit_title), fontWeight = FontWeight.Medium)
                        listOf<Triple<Int, Boolean, (Boolean) -> EditOptions>>(
                            Triple(R.string.edit_remaster, edit.remaster) { edit.copy(remaster = it) },
                            Triple(R.string.edit_versions, edit.versions) { edit.copy(versions = it) },
                            Triple(R.string.edit_live, edit.live) { edit.copy(live = it) },
                            Triple(R.string.edit_explicit, edit.explicit) { edit.copy(explicit = it) },
                            Triple(R.string.edit_introOutro, edit.introOutro) { edit.copy(introOutro = it) },
                            Triple(R.string.edit_featToArtist, edit.featToArtist) { edit.copy(featToArtist = it) },
                            Triple(R.string.edit_albumEditions, edit.albumEditions) { edit.copy(albumEditions = it) },
                            Triple(R.string.edit_mainArtistOnly, edit.mainArtistOnly) { edit.copy(mainArtistOnly = it) },
                        ).forEach { (label, on, set) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(label), Modifier.weight(1f))
                                Switch(on, { Prefs.setEditOptions(set(it)) })
                            }
                        }
                        Text(stringResource(R.string.edit_note), fontSize = 12.sp, color = TextSoft)
                    }
                }
            }
            item { VersionsArea() }
            item { Area(R.string.sec_general) { LangMenu() } }
            item {
                Area(R.string.sec_permissions) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.notif_access, stringResource(if (accessGranted) R.string.access_ok else R.string.access_missing)), Modifier.weight(1f, fill = false))
                        Button({ open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text(stringResource(R.string.grant_access)) }
                    }
                    if (Build.VERSION.SDK_INT >= 33) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val ok = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
                        Text(stringResource(R.string.notif_perm, stringResource(if (ok) R.string.access_ok else R.string.access_missing)), Modifier.weight(1f, fill = false))
                        Button({ open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }) { Text(stringResource(R.string.grant_access)) }
                    }
                    Button({ open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) { Text(stringResource(R.string.battery_opt)) }
                    // necessario per avviare Auto Shazam e tornare a Roomtone (limite di Android 10+)
                    Button({ open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))) }) { Text(stringResource(R.string.overlay_perm)) }
                    Text(stringResource(R.string.overlay_hint), style = MaterialTheme.typography.bodySmall, color = TextMain.copy(alpha = 0.6f))
                    // istruzioni per marca (Huawei, Xiaomi…) sul sito: cambiano spesso, lì si aggiornano senza release
                    Link(R.string.battery_help, "https://derefdev.github.io/roomtone/troubleshooting/#roomtone-stops-working-after-a-while", ::open)
                }
            }
            if (diag) {
                item {
                    Area(R.string.sec_diag) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.listener_status, stringResource(if (connected) R.string.listener_connected else R.string.listener_disconnected)))
                                Text(stringResource(R.string.log_path, logPath), fontSize = 11.sp)
                            }
                            Button({ EventStore.clear() }) { Text(stringResource(R.string.clear)) }
                        }
                        Text(stringResource(R.string.log_keep))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (d in listOf(1, 2, 7, 30)) FilterChip(logDays == d, { Prefs.setLogDays(d) }, { Text(pluralStringResource(R.plurals.days, d, d)) })
                        }
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
                            Text(pluralStringResource(R.plurals.beats, r.beats, r.beats))
                        }
                        meta?.let { m ->
                            val src = stringResource(when (m.source) { MetaSource.HISTORY -> R.string.meta_from_history; MetaSource.OWNED -> R.string.meta_from_owned; MetaSource.DISCOGS -> R.string.meta_from_discogs; else -> R.string.meta_from_shazam })
                            val body = listOfNotNull("${m.title} – ${m.artist}", m.album, src).joinToString(" · ")
                            Text(stringResource(R.string.meta_as, body), fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                Area(R.string.sec_about) {
                    // 7 tocchi sulla versione: mostra/nasconde la diagnostica
                    var taps by remember { mutableIntStateOf(0) }
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), Modifier.clickable {
                        if (++taps < 7) return@clickable
                        taps = 0
                        Prefs.setDiagnostics(!diag)
                        Toast.makeText(ctx, if (diag) R.string.diag_hidden else R.string.diag_shown, Toast.LENGTH_SHORT).show()
                    })
                    val checking by Updates.checking.collectAsStateWithLifecycle()
                    val avail by Updates.available.collectAsStateWithLifecycle()
                    val status by Updates.status.collectAsStateWithLifecycle()
                    val updAuto by Updates.auto.collectAsStateWithLifecycle()
                    val installing by Updates.installing.collectAsStateWithLifecycle()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button({ Updates.checkNow(ctx) }, enabled = !checking) {
                            if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.update_check))
                        }
                        val a = avail
                        if (a != null) {
                            Text(stringResource(R.string.update_available, a.version), Modifier.weight(1f, fill = false))
                            Button({ Updates.install(ctx) }, enabled = !installing) { Text(stringResource(R.string.update_install)) }
                        } else status?.let { Text(stringResource(it), fontSize = 12.sp, color = TextSoft) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.update_auto), Modifier.weight(1f))
                        Switch(updAuto, Updates::setAuto)
                    }
                    Text(stringResource(R.string.update_auto_hint), fontSize = 12.sp, color = TextSoft)
                    Text(stringResource(R.string.about_made_by), fontSize = 12.sp, color = TextSoft)
                    Link(R.string.about_site, "https://derefdev.github.io/roomtone/", ::open)
                    Link(R.string.about_source, "https://github.com/DerefDev/roomtone", ::open)
                    Link(R.string.about_issue, "https://github.com/DerefDev/roomtone/issues", ::open)
                    Link(R.string.about_license, "https://www.gnu.org/licenses/gpl-3.0.html", ::open)
                    Row(Modifier.fillMaxWidth().clickable(onClick = onCredits).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_credits), Modifier.weight(1f), color = LocalAccent.current, fontSize = 14.sp)
                        Text("›", color = LocalAccent.current, fontSize = 20.sp) // ponytail: chevron testuale → icona dedicata
                    }
                }
            }
        }
    }
}
