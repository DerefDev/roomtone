package dev.deref.vinylkiosk

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

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

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
private fun Screen(accessGranted: Boolean) {
    val ctx = LocalContext.current
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val connected by EventStore.connected.collectAsStateWithLifecycle()
    val current by EventStore.currentTrack.collectAsStateWithLifecycle()
    val shown = list.asReversed() // più recente in alto
    val logPath = ctx.getExternalFilesDir(null)?.let { "$it/shazam_raw.jsonl" } ?: "n/d"

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Accesso notifiche: ${if (accessGranted) "OK" else "NON concesso"}")
                Text("Listener: ${if (connected) "connesso" else "non connesso"}")
                Text("Log: $logPath", fontSize = 11.sp)
            }
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text("Concedi accesso notifiche")
            }
            Button(onClick = { EventStore.clear() }) { Text("Svuota") }
        }
        HorizontalDivider()
        Text(current?.title ?: "In ascolto…", fontSize = 40.sp)
        Text(current?.artist ?: "", fontSize = 24.sp)
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(shown) { r ->
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(timeFmt.format(Date(r.start)))
                    Text("${r.track.title} – ${r.track.artist}", Modifier.weight(1f))
                    val sec = r.playedMs / 1000
                    Text("%d:%02d".format(sec / 60, sec % 60))
                    Text("${r.beats} battiti")
                }
            }
        }
    }
}
