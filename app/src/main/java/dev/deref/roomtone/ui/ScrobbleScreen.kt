package dev.deref.roomtone.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.deref.roomtone.*
import dev.deref.roomtone.R
import java.text.DateFormat
import java.util.Date

internal val Reason.res get() = when (this) {
    Reason.OK -> R.string.reason_ok
    Reason.UNKNOWN_DURATION -> R.string.reason_unknown_duration
    Reason.TRACK_UNDER_30S -> R.string.reason_track_under_30s
    Reason.LESS_THAN_HALF -> R.string.reason_less_than_half
    Reason.AMBIGUOUS_REPEATS -> R.string.reason_ambiguous_repeats
    Reason.CHOOSE_VERSION -> R.string.reason_choose_version
    Reason.NOT_ON_RECORD -> R.string.reason_not_on_record
}

private val dateFmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

private val SoftRed = Color(0xFFE57373)

@Composable
fun ScrobbleScreen(service: Service, tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit) {
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val sv = Services.of(service)
    val queue by sv.queue.items.collectAsStateWithLifecycle()
    Page(stringResource(if (service == Service.LASTFM) R.string.scrobble_title_lastfm else R.string.scrobble_title_lb), onBack) {
        TabRow(tab, containerColor = Color.Transparent, contentColor = LocalAccent.current) {
            Tab(tab == 0, { onTab(0) }, text = { Text(stringResource(R.string.tab_sent)) })
            Tab(tab == 1, { onTab(1) }, text = { Text(stringResource(R.string.tab_drafts)) })
            Tab(tab == 2, { onTab(2) }, text = { Text(stringResource(R.string.tab_versions)) })
        }
        val rows = list.asReversed().filter { it.of(service)?.eval?.status == Status.DRAFT } // più recente in alto
        if (tab == 2) VersionsTab(sv) else LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0) {
                item { QueueHeader(service, queue.count { it.state == QState.PENDING || it.state == QState.SENDING }) }
                if (queue.isEmpty()) item { Text(stringResource(R.string.list_empty), color = TextSoft) }
                items(queue.asReversed(), key = { it.id }) { QueueRow(it) }
            } else {
                if (rows.isEmpty()) item { Text(stringResource(R.string.list_empty), color = TextSoft) }
                items(rows, key = { it.listen.start }) { DraftCard(service, it) }
            }
        }
    }
}

// Versioni scelte del servizio: brano → "titolo · album"; tocco → dialogo per cambiare o rimuovere la scelta
@Composable
private fun ColumnScope.VersionsTab(sv: Svc) {
    val all by sv.choices.choices.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (all.isEmpty()) item { Text(stringResource(R.string.versions_hint), color = TextSoft) }
        items(all.entries.toList(), key = { it.key }) { (hk, c) ->
            Column(Modifier.fillMaxWidth().clickable { edit = hk }.padding(vertical = 4.dp)) {
                Text("${c.song.title} – ${c.song.artist}", fontSize = 16.sp)
                Text(listOfNotNull(c.key.title, c.key.album).joinToString(" · "), fontSize = 13.sp, color = TextSoft)
            }
        }
    }
    val c = edit?.let { all[it] }
    if (c != null) {
        // ponytail: scelta non più in cronologia → compare solo come voce salvata (plays 0)
        val opts = sv.history.index.versions(c.song).let { vs -> if (vs.any { it.key == c.key }) vs else listOf(Version(c.key.artist, c.key.title, c.key.album, 0, emptyList())) + vs }
        var sel by remember(c) { mutableStateOf(opts.first { it.key == c.key }) }
        AlertDialog({ edit = null },
            title = { Text("${c.song.title} – ${c.song.artist}", fontSize = 16.sp) },
            text = { LazyColumn { item { VersionPicker(opts, sel) { sel = it } } } },
            confirmButton = { TextButton({ sv.choices.set(c.song, sel.key); edit = null }) { Text(stringResource(R.string.version_save)) } },
            dismissButton = {
                Row {
                    TextButton({ sv.choices.remove(edit!!); edit = null }) { Text(stringResource(R.string.version_remove)) }
                    TextButton({ edit = null }) { Text(stringResource(R.string.cancel)) }
                }
            })
    }
}

@Composable
private fun QueueHeader(service: Service, pending: Int) {
    if (pending == 0) return
    val online by Connectivity.online.collectAsStateWithLifecycle()
    val user by Services.of(service).history.user.collectAsStateWithLifecycle()
    val err by Services.of(service).queue.error.collectAsStateWithLifecycle()
    val why = when {
        err == "session" -> if (service == Service.LASTFM) R.string.queue_session else R.string.queue_session_lb
        user == null -> if (service == Service.LASTFM) R.string.queue_login else R.string.queue_login_lb
        !online -> R.string.queue_offline
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(pluralStringResource(R.plurals.queue_pending, pending, pending), fontSize = 14.sp)
            why?.let { Text(stringResource(it), color = TextSoft, fontSize = 13.sp) }
        }
        TextButton({ Services.of(service).queue.retry() }) { Text(stringResource(R.string.queue_retry)) }
    }
}

@Composable
private fun QueueRow(q: QItem) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(q.title, fontSize = 16.sp)
            Text(listOfNotNull(q.artist, q.album).joinToString(" · "), color = TextSoft, fontSize = 13.sp)
            if (q.state == QState.IGNORED) Text(stringResource(R.string.queue_ignored, q.note.orEmpty()), color = SoftRed, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(dateFmt.format(Date(q.ts * 1000)), color = TextSoft, fontSize = 13.sp)
            when (q.state) {
                QState.PENDING -> Text(stringResource(R.string.queue_waiting), color = TextSoft, fontSize = 13.sp)
                QState.SENDING -> Text(stringResource(R.string.queue_sending), color = TextSoft, fontSize = 13.sp)
                QState.SENT -> Text("✓", fontSize = 16.sp)
                QState.IGNORED -> {}
            }
        }
    }
}

@Composable
private fun DraftCard(s: Service, x: EvaluatedListen) {
    val it = x.of(s)!!
    val l = x.listen
    val e = it.eval!!
    val m = it.meta ?: scrobbleMeta(l.track, null)
    val choose = e.reason == Reason.CHOOSE_VERSION
    var title by remember(l, m) { mutableStateOf(m.title) }
    var artist by remember(l, m) { mutableStateOf(m.artist) }
    var album by remember(l, m) { mutableStateOf(m.album.orEmpty()) }
    var sel by remember(l) { mutableStateOf(it.options.firstOrNull { v -> v.artist == m.artist && v.title == m.title && v.album == m.album } ?: it.options.firstOrNull()) }
    var plays by remember(l) { mutableIntStateOf(e.plays) }
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${stringResource(e.reason.res)} · ${dateFmt.format(Date(l.start))}", color = TextSoft, fontSize = 13.sp)
        if (it.options.isNotEmpty()) VersionPicker(it.options, sel) { v ->
            sel = v
            if (!choose) EventStore.chooseVersion(l, s, v) // altre cause: aggiorna solo i metadati, conferma a mano
        }
        if (!choose) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(title, { title = it }, R.string.field_title, Modifier.weight(1f))
            Field(artist, { artist = it }, R.string.field_artist, Modifier.weight(1f))
            Field(album, { album = it }, R.string.field_album, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (e.reason == Reason.AMBIGUOUS_REPEATS) { // ripetizioni 1..n+1
                OutlinedButton({ plays-- }, enabled = plays > 1) { Text("−") }
                Text(stringResource(R.string.draft_repeats, plays))
                OutlinedButton({ plays++ }, enabled = plays < e.plays + 1) { Text("+") }
            }
            Spacer(Modifier.weight(1f))
            TextButton({ EventStore.resolve(l, s, Status.DISCARDED) }) { Text(stringResource(R.string.draft_discard)) }
            Button(
                { if (choose) EventStore.chooseVersion(l, s, sel!!) else EventStore.resolve(l, s, Status.VALID, ScrobbleMeta(artist.trim(), title.trim(), album.trim().ifEmpty { null }, m.source, null,
                    m.mbids.takeIf { artist.trim() == m.artist && title.trim() == m.title && album.trim().ifEmpty { null } == m.album }), plays) }, // modificato a mano → niente MBID
                enabled = if (choose) sel != null else title.isNotBlank() && artist.isNotBlank(), // titolo e artista obbligatori
            ) { Text(stringResource(R.string.draft_confirm)) }
        }
    }
}

@Composable
private fun Field(v: String, set: (String) -> Unit, label: Int, modifier: Modifier) =
    OutlinedTextField(v, set, modifier, label = { Text(stringResource(label)) }, singleLine = true)
