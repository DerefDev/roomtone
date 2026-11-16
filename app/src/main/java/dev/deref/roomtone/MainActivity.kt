package dev.deref.roomtone

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.deref.roomtone.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { KIOSK, SCROBBLE, SETTINGS, COVERS, CREDITS, STANDBY }

class MainActivity : AppCompatActivity() {

    private var accessGranted by mutableStateOf(false)
    private var loginError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (savedInstanceState == null) ShazamControl.armAutoStart() // nuova apertura, non rotazione
        Prefs.init(this)
        Connectivity.init(this)
        Artwork.init(cacheDir, filesDir)
        Albums.init(cacheDir)
        Artists.init(cacheDir)
        HistorySync.init(this)
        DiscogsCollection.init(this)
        if (savedInstanceState == null) handleAuth(intent) // su ricreazione il token sarebbe già usato
        HistorySync.startIfStale()
        DiscogsCollection.startIfStale()
        setContent { Root(accessGranted, loginError) }
    }

    // modalità immersiva: barre nascoste, swipe per mostrarle temporaneamente
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuth(intent)
    }

    // roomtone://auth?token=… (Last.fm) e roomtone://discogs?oauth_token=…&oauth_verifier=… → sessione → sync.
    // I token non vanno mai loggati.
    private fun handleAuth(i: Intent) {
        val d = i.data ?: return
        if (d.scheme != "roomtone" || (d.host != "auth" && d.host != "discogs")) return
        i.data = null
        if (d.host == "discogs") {
            val token = d.getQueryParameter("oauth_token") ?: return
            val verifier = d.getQueryParameter("oauth_verifier") ?: return
            lifecycleScope.launch {
                try { DiscogsCollection.login(token, verifier); loginError = null } catch (e: Exception) { loginError = e.message ?: "error" }
            }
            return
        }
        val token = d.getQueryParameter("token") ?: return
        lifecycleScope.launch {
            try { HistorySync.login(token); loginError = null } catch (e: Exception) { loginError = e.message ?: "error" }
        }
    }

    override fun onResume() {
        super.onResume()
        ShazamControl.visible = true
        ShazamControl.maybeAutoStart(this)
        ShazamControl.onScreenOn(this) // USER_PRESENT non sempre arriva (es. sblocco senza PIN): ricontrolla qui
        ShazamControl.consumeRestart(this)
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        // Dopo un aggiornamento o se EMUI chiude il processo, il sistema può non ricollegare il listener:
        // con accesso concesso ma listener scollegato, chiedi esplicitamente il ricollegamento.
        if (accessGranted && !ShazamControl.listenerUp) {
            android.util.Log.d("Roomtone", "Listener scollegato: requestRebind")
            val cn = android.content.ComponentName(this, ShazamListener::class.java)
            android.service.notification.NotificationListenerService.requestRebind(cn)
            // Su EMUI requestRebind non sempre basta: se dopo 3 s è ancora scollegato,
            // disabilita e riabilita il componente per forzare il ricollegamento.
            lifecycleScope.launch {
                kotlinx.coroutines.delay(3_000)
                if (ShazamControl.listenerUp) return@launch
                android.util.Log.d("Roomtone", "Listener ancora scollegato: toggle del componente")
                val pm = packageManager
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            }
        }
    }

    // App chiusa (Indietro dalla schermata principale o rimossa dalle recenti): spegni anche Auto Shazam
    override fun onDestroy() {
        if (isFinishing) { android.util.Log.d("Roomtone", "App chiusa: spengo Auto Shazam"); ShazamControl.stop() }
        super.onDestroy()
    }

    override fun onPause() {
        ShazamControl.visible = false
        super.onPause()
    }
}

@Composable
private fun Root(accessGranted: Boolean, loginError: String?) {
    val np by EventStore.nowPlaying.collectAsStateWithLifecycle()
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val scrobbling by Prefs.scrobbling.collectAsStateWithLifecycle()
    val online by Connectivity.online.collectAsStateWithLifecycle()
    val autoOn by Prefs.standbyAuto.collectAsStateWithLifecycle()
    val mins by Prefs.standbyMin.collectAsStateWithLifecycle()
    val shazamOn by ShazamControl.autoOn.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var standbyWasOn by remember { mutableStateOf(false) } // Auto Shazam acceso prima dello standby manuale
    var screen by rememberSaveable { mutableStateOf(Screen.KIOSK) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var autoStandby by remember { mutableStateOf(false) } // standby entrato da solo (per ora solo informativo)

    // copertina: al cambio brano subito l'icona Shazam (se c'è), poi la copertina vera appena l'album è risolto
    val n = np
    val icon by EventStore.icon.collectAsStateWithLifecycle()
    val loaded by produceState<Bitmap?>(null, n?.track, n?.album, n?.albumDone) {
        value = null
        if (n != null && n.albumDone) value = Artwork.load(n.album, n.track)
    }
    val cover = loaded ?: icon

    val idle = n == null
    LaunchedEffect(idle, autoOn, mins, screen) {
        if (idle && autoOn && screen == Screen.KIOSK) {
            delay(mins * 60_000L)
            // anche lo standby automatico spegne Shazam (privacy): si esce solo toccando lo schermo
            autoStandby = true; menu = false
            standbyWasOn = ShazamControl.autoOn.value; ShazamControl.stop()
            screen = Screen.STANDBY
        }
    }
    // uscita dallo standby (tap o back) → riaccende Shazam se era acceso
    fun leave() {
        if (screen == Screen.STANDBY && standbyWasOn) ShazamControl.start(ctx, user = true)
        standbyWasOn = false
        screen = if (screen == Screen.COVERS || screen == Screen.CREDITS) Screen.SETTINGS else Screen.KIOSK
    }
    BackHandler(screen != Screen.KIOSK) { leave() }

    RoomtoneTheme(cover) {
        if (screen == Screen.STANDBY) StandbyScreen { leave() }
        else Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            when (screen) {
                Screen.SCROBBLE -> ScrobbleScreen(tab, { tab = it }) { screen = Screen.KIOSK }
                Screen.SETTINGS -> SettingsScreen(accessGranted, loginError, { screen = Screen.COVERS }, { screen = Screen.CREDITS }) { screen = Screen.KIOSK }
                Screen.COVERS -> CoversScreen { screen = Screen.SETTINGS }
                Screen.CREDITS -> CreditsScreen { screen = Screen.SETTINGS }
                else -> {
                    KioskScreen(
                        n, cover, scrobbling, online, list.count { it.eval?.status == Status.DRAFT }, shazamOn,
                        onMenu = { menu = true }, onDrafts = { tab = 1; screen = Screen.SCROBBLE },
                    )
                    Drawer(
                        menu, { menu = false }, scrobbling, Prefs::setScrobbling,
                        shazamOn = shazamOn, onShazam = { if (it) ShazamControl.start(ctx, user = true) else ShazamControl.stop() },
                        onSettings = { menu = false; screen = Screen.SETTINGS },
                        onScrobble = { menu = false; tab = 0; screen = Screen.SCROBBLE },
                        onStandby = {
                            menu = false; autoStandby = false
                            standbyWasOn = shazamOn; ShazamControl.stop() // all'uscita riacceso solo se era acceso
                            screen = Screen.STANDBY
                        },
                    )
                }
            }
        }
    }
}
