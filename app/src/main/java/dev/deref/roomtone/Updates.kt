package dev.deref.roomtone

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
    private val _auto = MutableStateFlow(true)
    val auto: StateFlow<Boolean> = _auto
    private val _changelog = MutableStateFlow<Release?>(null)
    val changelog: StateFlow<Release?> = _changelog
    private val _promptedTag = MutableStateFlow("")
    val promptedTag: StateFlow<String> = _promptedTag

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
        _auto.value = sp.getBoolean("auto", true)
        _promptedTag.value = sp.getString("prompted_tag", "") ?: ""
        // la release in cache tiene il banner vivo offline; scartata se non più nuova (es. dopo l'aggiornamento)
        val r = sp.getString("release", null)?.let(::parse)
        if (r != null && newer(r.version, BuildConfig.VERSION_NAME)) _available.value = r else sp.edit().remove("release").apply()
    }

    fun setAuto(v: Boolean) { _auto.value = v; sp.edit().putBoolean("auto", v).apply() }
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
        if (n && sp.getString("notified_tag", null) != r.tag) { notify(ctx.applicationContext, r); sp.edit().putString("notified_tag", r.tag).apply() }
        return true
    }

    fun checkIfStale(ctx: Context) {
        if (BuildConfig.DEBUG || !_auto.value || _checking.value || System.currentTimeMillis() - sp.getLong("last_check", 0) <= DAY) return
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
        try { nm.notify(1, n) } catch (_: SecurityException) { /* ponytail: permesso revocato → nessuna notifica */ }
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
                pi.openSession(id).use { s ->
                    s.openWrite("apk", 0, apk.size.toLong()).use { it.write(apk); s.fsync(it) }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                    s.commit(PendingIntent.getBroadcast(app, 0, Intent(app, UpdateReceiver::class.java), flags).intentSender)
                }
            } catch (e: Exception) {
                Log.d("Roomtone", "Installazione fallita: ${e.javaClass.simpleName}")
                toast(app, R.string.update_install_failed); _installing.value = false
            }
        }
    }

    fun installDone() { _installing.value = false }

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

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (st) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_INTENT, Intent::class.java)
                ?.let { c.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            PackageInstaller.STATUS_SUCCESS -> Updates.installDone()
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updates.installDone() // annullato dall'utente
            else -> { Log.d("Roomtone", "Installazione: stato $st"); Updates.toast(c, R.string.update_install_failed); Updates.installDone() }
        }
    }
}
