package dev.deref.roomtone

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class SyncProgress(
    val running: Boolean = false, val page: Int = 0, val pages: Int = 0,
    val total: Int = 0, val complete: Boolean = false, val error: String? = null,
)

// Nomi di prefs/file/chiave per servizio (Last.fm: quelli storici → nessuna perdita di login/cronologia agli aggiornamenti)
class HistoryFiles(val state: String, val account: String, val userKey: String, val index: String, val secure: String)

// Account + sincronizzazione cronologia di un servizio (incrementale, riprendibile). Indice in filesDir interno.
class HistorySync(val service: Service, private val backend: HistoryBackend, private val files: HistoryFiles) {
    val index = HistoryIndex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dir: File
    private lateinit var state: SharedPreferences // newestUts, oldestUts, complete, total, lastSync
    private lateinit var account: SharedPreferences // nome utente in chiaro
    private lateinit var secure: SecureStore
    private var job: Job? = null

    private val _user = MutableStateFlow<String?>(null)
    val user: StateFlow<String?> = _user
    private val _progress = MutableStateFlow(SyncProgress())
    val progress: StateFlow<SyncProgress> = _progress

    private val indexFile get() = File(dir, files.index)

    @Synchronized fun init(ctx: Context) { // idempotente
        if (::dir.isInitialized) return
        val app = ctx.applicationContext
        dir = app.filesDir
        state = app.getSharedPreferences(files.state, Context.MODE_PRIVATE)
        account = app.getSharedPreferences(files.account, Context.MODE_PRIVATE)
        secure = SecureStore(app)
        _user.value = account.getString(files.userKey, null)
        if (state.getInt("indexVersion", 0) != 2) { // formato v2 → risincronizzazione completa
            File(dir, "history_index.tsv").delete()
            state.edit().remove("newestUts").remove("oldestUts").remove("complete").remove("total").remove("lastSync").putInt("indexVersion", 2).apply()
        }
        // ponytail: lettura sincrona sul thread chiamante → caricare in background se l'indice diventa enorme
        try { index.loadTsv(indexFile.readText()) } catch (_: Exception) { /* assente → indice vuoto */ }
        _progress.value = SyncProgress(total = state.getInt("total", 0), complete = state.getBoolean("complete", false))
    }

    fun secret(): String? = secure.get(files.secure) // session key (Last.fm) / token (ListenBrainz)

    // Il login vero (token → sessione) lo fa il chiamante; qui si salva l'account e parte la sync
    fun connect(name: String, secret: String) {
        secure.put(files.secure, secret)
        account.edit().putString(files.userKey, name).apply()
        _user.value = name
        start()
    }

    fun logout() {
        val j = job
        scope.launch {
            j?.cancelAndJoin()
            secure.remove(files.secure)
            account.edit().clear().apply()
            state.edit().clear().putInt("indexVersion", 2).apply()
            index.clear()
            indexFile.delete()
            _user.value = null
            _progress.value = SyncProgress()
        }
    }

    fun startIfStale() {
        if (_user.value != null && System.currentTimeMillis() - state.getLong("lastSync", 0) > 3_600_000) start()
    }

    fun start() {
        if (_user.value == null || job?.isActive == true) return
        job = scope.launch { sync(_user.value ?: return@launch) }
    }

    // Errori temporanei (timeout, Last.fm sovraccarico): stessa pagina ritentata dopo 5 e 15 s prima di fermarsi.
    private suspend fun <T> retry3(f: suspend () -> T): T {
        for (wait in longArrayOf(5_000, 15_000)) {
            try { return f() } catch (e: CancellationException) { throw e } catch (_: Exception) { delay(wait) }
        }
        return f()
    }

    private suspend fun sync(user: String) {
        var newest = state.getLong("newestUts", 0)
        var oldest = state.getLong("oldestUts", 0)
        var complete = state.getBoolean("complete", false)
        // fine fissata all'avvio: gli scrobble che arrivano durante la sync non spostano le pagine (niente doppioni)
        val to = if (!complete && oldest > 0) oldest - 1 else System.currentTimeMillis() / 1000
        val from = if (complete) newest + 1 else null
        val pending = ArrayList<HistoryScrobble>() // incrementale: applicato solo a finestra completa (niente doppi)

        // indice prima, stato dopo → su crash al massimo si rifà qualche pagina senza duplicati
        fun persist() {
            try {
                val tmp = File(dir, "history_index.tmp")
                tmp.writeText(index.toTsv())
                tmp.renameTo(indexFile)
            } catch (_: Exception) { return } // ponytail: scrittura fallita → stato invariato, si rifà
            state.edit().putLong("newestUts", newest).putLong("oldestUts", oldest)
                .putBoolean("complete", complete).putInt("total", index.size).apply()
        }

        _progress.value = SyncProgress(running = true, total = index.size, complete = complete)
        var page = 1
        var cursor: Long? = null
        try {
            while (true) {
                val pg = retry3 { backend.page(user, cursor, from, to) }
                val items = pg.items
                if (from != null) pending += items else {
                    items.forEach(index::add)
                    items.forEach { newest = maxOf(newest, it.uts); oldest = if (oldest == 0L) it.uts else minOf(oldest, it.uts) }
                }
                _progress.value = SyncProgress(true, page, pg.pages ?: 0, index.size + pending.size, complete)
                val last = pg.next == null
                // ponytail: indice salvato ogni 10 pagine, non a ogni pagina (file grande)
                if (from == null && (last || page % 10 == 0)) { complete = last; persist() }
                if (last) break
                page++; cursor = pg.next
                delay(250)
            }
            if (from != null) {
                pending.forEach(index::add)
                pending.forEach { newest = maxOf(newest, it.uts) }
                persist()
            }
            state.edit().putLong("lastSync", System.currentTimeMillis()).apply()
            _progress.value = SyncProgress(total = index.size, complete = complete)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (from == null) persist()
            _progress.value = SyncProgress(total = index.size, complete = complete, error = e.message ?: e.javaClass.simpleName)
        }
    }
}
