package dev.deref.roomtone.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
}

private val dateFmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

@Composable
fun ScrobbleScreen(tab: Int, onTab: (Int) -> Unit, onBack: () -> Unit) {
    val list by EventStore.listens.collectAsStateWithLifecycle()
    Page(stringResource(R.string.scrobble_title), onBack) {
        TabRow(tab, containerColor = Color.Transparent, contentColor = LocalAccent.current) {
            Tab(tab == 0, { onTab(0) }, text = { Text(stringResource(R.string.tab_sent)) })
            Tab(tab == 1, { onTab(1) }, text = { Text(stringResource(R.string.tab_drafts)) })
        }
        val status = if (tab == 0) Status.VALID else Status.DRAFT
        val rows = list.asReversed().filter { it.eval?.status == status } // più recente in alto
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0) item { Text(stringResource(R.string.sent_note), color = TextSoft, fontSize = 13.sp) } // ponytail: lista in memoria
            if (rows.isEmpty()) item { Text(stringResource(R.string.list_empty), color = TextSoft) }
            if (tab == 0) items(rows) { SentRow(it) } else items(rows, key = { it.listen.start }) { DraftCard(it) }
        }
    }
}

@Composable
private fun SentRow(it: EvaluatedListen) {
    val m = it.meta ?: scrobbleMeta(it.listen.track, null)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(m.title, fontSize = 16.sp)
            Text(listOfNotNull(m.artist, m.album).joinToString(" · "), color = TextSoft, fontSize = 13.sp)
        }
        Text(dateFmt.format(Date(it.listen.start)), color = TextSoft, fontSize = 13.sp)
        Text(stringResource(R.string.times, it.eval?.plays ?: 1), fontSize = 16.sp)
    }
}

@Composable
private fun DraftCard(it: EvaluatedListen) {
    val l = it.listen
    val e = it.eval!!
    val m = it.meta ?: scrobbleMeta(l.track, null)
    var title by remember(l) { mutableStateOf(m.title) }
    var artist by remember(l) { mutableStateOf(m.artist) }
    var album by remember(l) { mutableStateOf(m.album.orEmpty()) }
    var plays by remember(l) { mutableIntStateOf(e.plays) }
    val accent = LocalAccent.current
    Column(
        Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${stringResource(e.reason.res)} · ${dateFmt.format(Date(l.start))}", color = TextSoft, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton({ EventStore.resolve(l, Status.DISCARDED) }) { Text(stringResource(R.string.draft_discard)) }
            Button(
                { EventStore.resolve(l, Status.VALID, ScrobbleMeta(artist.trim(), title.trim(), album.trim().ifEmpty { null }, m.source), plays) },
                enabled = title.isNotBlank() && artist.isNotBlank(), // titolo e artista obbligatori
            ) { Text(stringResource(R.string.draft_confirm)) }
        }
    }
}

@Composable
private fun Field(v: String, set: (String) -> Unit, label: Int, modifier: Modifier) =
    OutlinedTextField(v, set, modifier, label = { Text(stringResource(label)) }, singleLine = true)
