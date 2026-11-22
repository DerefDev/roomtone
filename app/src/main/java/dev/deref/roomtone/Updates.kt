package dev.deref.roomtone

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

data class Release(val tag: String, val version: String, val notes: String, val page: String, val apk: String?, val sha256: String?)

// Aggiornamenti in-app: controllo su GitHub Releases, notifica, changelog, installazione via PackageInstaller.
object Updates {
    private const val REPO = "https://api.github.com/repos/DerefDev/roomtone/releases"
    private const val NOTIF_ID = 1
    private const val ACTION = "dev.deref.roomtone.INSTALL_STATUS"
    private const val DAY = 24 * 3600_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sp: SharedPreferences
    private val main by lazy { Handler(Looper.getMainLooper()) } // lazy: i test JVM non hanno Looper

    private val _available = MutableStateFlow<Release?>(null)
    val available: StateFlow<Release?> = _available
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking
    private val _status = MutableStateFlow<Int?>(null)
    val status: StateFlow<Int?> = _status
    private val _installing = MutableStateFlow(false)
    val installing: StateFlow<Boolean> = _installing
    // opt-in (regola IzzyOnDroid): nessun controllo automatico finché l'utente non risponde alla domanda al primo avvio
    private val _auto = MutableStateFlow(false)
    val auto: StateFlow<Boolean> = _auto
    private val _asked = MutableStateFlow(true)
    val asked: StateFlow<Boolean> = _asked
    private val _changelog = MutableStateFlow<Release?>(null)
    val changelog: StateFlow<Release?> = _changelog
    private val _promptedTag = MutableStateFlow("")
    val promptedTag: StateFlow<String> = _promptedTag

    // App che installano e aggiornano altre app: se Roomtone arriva da una di queste, gli aggiornamenti sono loro
    private val STORES = setOf("dev.imranr.obtainium", "org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid", "com.aurora.store")
    /** Pacchetto e nome dell'app che gestisce gli aggiornamenti (Obtainium, F-Droid…), o null: aggiornamenti interni. */
    var manager: Pair<String, String>? = null; private set

    @Suppress("DEPRECATION")
    private fun installer(ctx: Context): String? = try {
        if (Build.VERSION.SDK_INT >= 30) ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        else ctx.packageManager.getInstallerPackageName(ctx.packageName)
    } catch (_: Exception) { null }

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
        manager = installer(ctx)?.takeIf { it in STORES }?.let { p ->
            p to (try { ctx.packageManager.run { getApplicationLabel(getApplicationInfo(p, 0)).toString() } } catch (_: Exception) { p })
        }
        _auto.value = manager == null && sp.getBoolean("auto", false)
        _asked.value = manager != null || sp.contains("auto") // con un gestore esterno nessuna domanda al primo avvio
        _promptedTag.value = sp.getString("prompted_tag", "") ?: ""
        // la release in cache tiene il banner vivo offline; scartata se non più nuova (es. dopo l'aggiornamento)
        val r = sp.getString("release", null)?.let(::parse)
        if (manager == null && r != null && newer(r.version, BuildConfig.VERSION_NAME)) _available.value = r
        else { sp.edit().remove("release").apply(); NotificationManagerCompat.from(ctx.applicationContext).cancel(NOTIF_ID) } // avviso ormai vecchio
    }

    fun setAuto(v: Boolean) { _auto.value = v; _asked.value = true; sp.edit().putBoolean("auto", v).apply() }
    fun prompted(tag: String) { _promptedTag.value = tag; sp.edit().putString("prompted_tag", tag).apply() }
    var notifAsked: Boolean
        get() = sp.getBoolean("notif_asked", false)
        set(v) { sp.edit().putBoolean("notif_asked", v).apply() }

    private fun parse(json: String): Release? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        val apk = o.optJSONArray("assets")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
        Release(tag, tag.removePrefix("v"), o.optStr("body") ?: "", o.optString("html_url"), apk?.optStr("browser_download_url"),
            apk?.optStr("digest")?.removePrefix("sha256:")?.lowercase())
    } catch (_: Exception) { null }

    // ponytail: suffisso confrontato solo come presente/assente (beta1 vs beta2 = non più nuova) → ordine tra pre-release
    fun newer(remote: String, local: String): Boolean {
        fun split(v: String) = v.removePrefix("v").let { it.substringBefore('-') to it.contains('-') }
        val (rn, rs) = split(remote); val (ln, ls) = split(local)
        val r = rn.split('.').map { it.toIntOrNull() ?: 0 }; val l = ln.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return ls && !rs
    }

    private fun get(path: String, timeoutMs: Int = 10_000) =
        httpGet("$REPO/$path", "Roomtone/${BuildConfig.VERSION_NAME}", mapOf("Accept" to "application/vnd.github+json"), timeoutMs)

    suspend fun check(ctx: Context): Boolean {
        val body = try { get("latest")?.decodeToString() } catch (_: java.io.IOException) { null } ?: return false
        val r = parse(body) ?: return false
        val n = newer(r.version, BuildConfig.VERSION_NAME)
        sp.edit().putLong("last_check", System.currentTimeMillis()).apply { if (n) putString("release", body) else remove("release") }.apply()
        _available.value = if (n) r else null
        if (!n) NotificationManagerCompat.from(ctx.applicationContext).cancel(NOTIF_ID)
        if (n && sp.getString("notified_tag", null) != r.tag) { notify(ctx.applicationContext, r); sp.edit().putString("notified_tag", r.tag).apply() }
        return true
    }

    fun checkIfStale(ctx: Context) {
        if (BuildConfig.DEBUG || manager != null || !_auto.value || _checking.value || System.currentTimeMillis() - sp.getLong("last_check", 0) <= DAY) return
        run(ctx) {}
    }

    fun checkNow(ctx: Context) {
        if (_checking.value) return
        run(ctx) { ok -> _status.value = if (_available.value != null) null else if (ok) R.string.update_none else R.string.update_failed }
    }

    private fun run(ctx: Context, done: (Boolean) -> Unit) {
        if (!_checking.compareAndSet(false, true)) return
        scope.launch { try { done(check(ctx)) } finally { _checking.value = false } }
    }

    @SuppressLint("MissingPermission") // controllato con areNotificationsEnabled
    private fun notify(ctx: Context, r: Release) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(NotificationChannel("updates", ctx.getString(R.string.notif_channel_updates), NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, "updates").setSmallIcon(R.drawable.ic_update)
            .setContentTitle(ctx.getString(R.string.update_notif_title, r.version)).setContentText(ctx.getString(R.string.update_notif_text))
            .setContentIntent(tap).setAutoCancel(true).build()
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) { /* ponytail: permesso revocato → nessuna notifica */ }
    }

    fun toast(ctx: Context, res: Int) { main.post { Toast.makeText(ctx.applicationContext, res, Toast.LENGTH_LONG).show() } }

    fun install(ctx: Context) {
        val r = _available.value ?: return
        val app = ctx.applicationContext
        if (!app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + app.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            toast(app, R.string.update_allow_install); return
        }
        if (r.apk == null) { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        if (!_installing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val apk = httpGet(r.apk, "Roomtone/${BuildConfig.VERSION_NAME}", timeoutMs = 60_000)
                if (apk == null || (r.sha256 != null && MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) } != r.sha256)) {
                    toast(app, R.string.update_install_failed); _installing.value = false; return@launch
                }
                val pi = app.packageManager.packageInstaller
                val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(app.packageName) })
                registerStatusReceiver(app)
                pi.openSession(id).use { s ->
                    s.openWrite("apk", 0, apk.size.toLong()).use { it.write(apk); s.fsync(it) }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    s.commit(PendingIntent.getBroadcast(app, 0, Intent(ACTION).setPackage(app.packageName), flags).intentSender)
                }
            } catch (e: Exception) {
                Log.d("Roomtone", "Installazione fallita: ${e.javaClass.simpleName}")
                toast(app, R.string.update_install_failed); _installing.value = false; unregisterStatusReceiver(app)
            }
        }
    }

    fun installDone() { _installing.value = false }

    // Receiver a runtime (coda parallela): quello da manifest dopo un riavvio restava dietro BOOT_COMPLETED (~3 min di ritardo)
    private var statusRx: BroadcastReceiver? = null
    @Synchronized private fun registerStatusReceiver(app: Context) {
        unregisterStatusReceiver(app)
        val rx = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = onInstallStatus(c, i) }
        statusRx = rx
        ContextCompat.registerReceiver(app, rx, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    @Synchronized private fun unregisterStatusReceiver(app: Context) {
        statusRx?.let { try { app.unregisterReceiver(it) } catch (_: IllegalArgumentException) { /* già rimosso */ } }
        statusRx = null
    }

    private fun onInstallStatus(c: Context, i: Intent) {
        val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (st) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> { // non terminale: il receiver resta
                IntentCompat.getParcelableExtra(i, Intent.EXTRA_INTENT, Intent::class.java)?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                return
            }
            PackageInstaller.STATUS_SUCCESS, PackageInstaller.STATUS_FAILURE_ABORTED -> {} // ok / annullato dall'utente
            else -> { Log.d("Roomtone", "Installazione: stato $st"); toast(c, R.string.update_install_failed) }
        }
        installDone(); unregisterStatusReceiver(c.applicationContext)
    }

    // Changelog dopo un aggiornamento: seen_code salvato solo alla chiusura (offline → riprova alla prossima apertura)
    fun checkChangelog(ctx: Context) {
        if (BuildConfig.DEBUG || sp.getInt("seen_code", 0) >= BuildConfig.VERSION_CODE) return
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (pi.firstInstallTime == pi.lastUpdateTime) { changelogSeen(); return }
        scope.launch {
            try {
                // ponytail: ogni risposta non 200 (404, rate limit) = niente changelog, non si riprova → distinguere 404
                val r = get("tags/v${BuildConfig.VERSION_NAME}")?.decodeToString()?.let(::parse)
                if (r == null) changelogSeen() else _changelog.value = r
            } catch (_: java.io.IOException) { /* offline: riprova alla prossima apertura */ }
        }
    }

    fun changelogSeen() { _changelog.value = null; sp.edit().putInt("seen_code", BuildConfig.VERSION_CODE).apply() }
}
