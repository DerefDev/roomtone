package dev.deref.roomtone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var accessGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen(accessGranted) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }
}

private fun mmss(ms: Long) = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun Screen(accessGranted: Boolean) {
    val ctx = LocalContext.current
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val connected by EventStore.connected.collectAsStateWithLifecycle()
    val current by EventStore.currentTrack.collectAsStateWithLifecycle()
    val shown = list.asReversed() // più recente in alto
    val logPath = ctx.getExternalFilesDir(null)?.let { "$it/shazam_raw.jsonl" } ?: "n/d"

    val yes = if (accessGranted) R.string.access_ok else R.string.access_missing
    val conn = if (connected) R.string.listener_connected else R.string.listener_disconnected

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notif_access, stringResource(yes)))
                Text(stringResource(R.string.listener_status, stringResource(conn)))
                Text(stringResource(R.string.log_path, logPath), fontSize = 11.sp)
            }
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text(stringResource(R.string.grant_access))
            }
            Button(onClick = { EventStore.clear() }) { Text(stringResource(R.string.clear)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.language))
            // tag vuoto = lingua di sistema
            TextButton(onClick = { setLang("") }) { Text(stringResource(R.string.lang_system)) }
            TextButton(onClick = { setLang("en") }) { Text("English") }
            TextButton(onClick = { setLang("it") }) { Text("Italiano") }
        }
        HorizontalDivider()
        Text(current?.title ?: stringResource(R.string.listening), fontSize = 40.sp)
        Text(current?.artist ?: "", fontSize = 24.sp)
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(shown) { (r, ev) ->
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
            }
        }
    }
}

private fun setLang(tag: String) =
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

private val Reason.res get() = when (this) {
    Reason.OK -> R.string.reason_ok
    Reason.UNKNOWN_DURATION -> R.string.reason_unknown_duration
    Reason.TRACK_UNDER_30S -> R.string.reason_track_under_30s
    Reason.LESS_THAN_HALF -> R.string.reason_less_than_half
    Reason.AMBIGUOUS_REPEATS -> R.string.reason_ambiguous_repeats
}
