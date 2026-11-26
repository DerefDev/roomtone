package dev.deref.roomtone

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import android.Manifest
import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
    private var overlayGranted by mutableStateOf(false)
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
        Services.init(this)
        DiscogsCollection.init(this)
        EventStore.init(filesDir)
        Updates.init(this)
        Updates.checkChangelog(this)
        // una sola volta: permesso notifiche (API 33+) per l'avviso di nuove versioni
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 && !Updates.notifAsked) {
            Updates.notifAsked = true
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null) handleAuth(intent) // su ricreazione il token sarebbe già usato
        Services.all.forEach { it.history.startIfStale() }
        DiscogsCollection.startIfStale()
        setContent { Root(accessGranted, overlayGranted, loginError) }
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
            try { val (name, key) = LastFmApi.getSession(token); Services.lastfm.history.connect(name, key); loginError = null } catch (e: Exception) { loginError = e.message ?: "error" }
        }
    }

    override fun onResume() {
        super.onResume()
        ShazamControl.visible = true
        Updates.checkIfStale(this)
        DiscogsCollection.startIfStale()
        ShazamControl.maybeAutoStart(this)
        ShazamControl.onScreenOn(this) // USER_PRESENT non sempre arriva (es. sblocco senza PIN): ricontrolla qui
        ShazamControl.consumeRestart(this)
        ShazamControl.onForeground(this)
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        overlayGranted = android.provider.Settings.canDrawOverlays(this)
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

    // Home / altra app: Roomtone in secondo piano (non distrutta) → spegni Auto Shazam, riacceso al ritorno
    override fun onStop() {
        if (!isChangingConfigurations) ShazamControl.onBackground()
        super.onStop()
    }

    override fun onPause() {
        ShazamControl.visible = false
        super.onPause()
    }
}

@Composable
private fun Root(accessGranted: Boolean, overlayGranted: Boolean, loginError: String?) {
    val np by EventStore.nowPlaying.collectAsStateWithLifecycle()
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val scrobbling by Prefs.scrobbling.collectAsStateWithLifecycle()
    val online by Connectivity.online.collectAsStateWithLifecycle()
    val autoOn by Prefs.standbyAuto.collectAsStateWithLifecycle()
    val mins by Prefs.standbyMin.collectAsStateWithLifecycle()
    val shazamOn by ShazamControl.autoOn.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val update by Updates.available.collectAsStateWithLifecycle()
    val changelog by Updates.changelog.collectAsStateWithLifecycle()
    val promptedTag by Updates.promptedTag.collectAsStateWithLifecycle()
    val asked by Updates.asked.collectAsStateWithLifecycle()
    var standbyWasOn by remember { mutableStateOf(false) } // Auto Shazam acceso prima dello standby manuale
    var screen by rememberSaveable { mutableStateOf(Screen.KIOSK) }
    var tab by rememberSaveable { mutableIntStateOf(0) } // 0 inviati, 1 da rivedere, 2 versioni scelte
    var svcName by rememberSaveable { mutableStateOf(Service.LASTFM.name) }
    val lbUser by Services.listenbrainz.history.user.collectAsStateWithLifecycle()
    val lfUser by Services.lastfm.history.user.collectAsStateWithLifecycle()
    // Last.fm sempre attivo (come prima); ListenBrainz solo se connesso. Nessuno connesso → solo la voce Last.fm.
    val lfDrafts = if (lfUser != null || lbUser == null) list.count { it.of(Service.LASTFM)?.eval?.status == Status.DRAFT } else 0 // pannello Last.fm visibile
    val lbDrafts = if (lbUser != null) list.count { it.of(Service.LISTENBRAINZ)?.eval?.status == Status.DRAFT } else 0
    fun openScrobble(s: Service, t: Int) { svcName = s.name; tab = t; screen = Screen.SCROBBLE }
    var menu by remember { mutableStateOf(false) }

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
            menu = false
            standbyWasOn = ShazamControl.autoOn.value; ShazamControl.stop()
            screen = Screen.STANDBY
        }
    }
    // schermate secondarie dimenticate aperte (Impostazioni, Scrobble…): dopo gli stessi minuti senza tocchi
    // si torna alla principale, da cui parte il normale timer dello standby. Ogni tocco fa ripartire il conto.
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(screen, lastTouch, autoOn, mins) {
        if (autoOn && screen != Screen.KIOSK && screen != Screen.STANDBY) {
            delay(mins * 60_000L)
            screen = Screen.KIOSK
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
        else Box(Modifier.fillMaxSize()
            // osserva ogni tocco senza consumarlo (passaggio Initial): serve solo a far ripartire il conto qui sopra
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); lastTouch = System.currentTimeMillis() } } }
            .windowInsetsPadding(WindowInsets.safeDrawing)) {
            val cl = changelog; val up = update
            if (cl != null) ChangelogDialog(cl, Updates::changelogSeen)
            else if (!asked) AutoCheckDialog { Updates.setAuto(it); if (it) Updates.checkIfStale(ctx) }
            else if (up != null && up.tag != promptedTag) UpdateDialog(up, { Updates.prompted(up.tag); Updates.install(ctx) }, { Updates.prompted(up.tag) })
            when (screen) {
                Screen.SCROBBLE -> ScrobbleScreen(Service.valueOf(svcName), tab, { tab = it }) { screen = Screen.KIOSK }
                Screen.SETTINGS -> SettingsScreen(accessGranted, overlayGranted, loginError, { screen = Screen.COVERS }, { screen = Screen.CREDITS }) { screen = Screen.KIOSK }
                Screen.COVERS -> CoversScreen { screen = Screen.SETTINGS }
                Screen.CREDITS -> CreditsScreen { screen = Screen.SETTINGS }
                else -> {
                    KioskScreen(
                        n, cover, scrobbling, online, lfDrafts + lbDrafts, shazamOn, accessGranted, overlayGranted,
                        onMenu = { menu = true }, onDrafts = { openScrobble(if (lfDrafts == 0 && lbDrafts > 0) Service.LISTENBRAINZ else Service.LASTFM, 1) },
                    )
                    Drawer(
                        menu, { menu = false }, scrobbling, Prefs::setScrobbling,
                        update = update, onUpdate = { Updates.install(ctx) },
                        onSettings = { menu = false; screen = Screen.SETTINGS },
                        scrobbleEntries = buildList {
                            if (lfUser != null || lbUser == null) add(ScrobbleEntry(R.string.menu_scrobble_lastfm, lfDrafts) { menu = false; openScrobble(Service.LASTFM, 0) })
                            if (lbUser != null) add(ScrobbleEntry(R.string.menu_scrobble_lb, lbDrafts) { menu = false; openScrobble(Service.LISTENBRAINZ, 0) })
                        },
                        onStandby = {
                            menu = false
                            standbyWasOn = shazamOn; ShazamControl.stop() // all'uscita riacceso solo se era acceso
                            screen = Screen.STANDBY
                        },
                    )
                }
            }
        }
    }
}
