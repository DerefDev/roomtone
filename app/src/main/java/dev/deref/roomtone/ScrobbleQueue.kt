package dev.deref.roomtone

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

enum class QState { PENDING, SENDING, SENT, IGNORED }

data class QItem(
    val id: String, val artist: String, val title: String, val album: String?, val ts: Long, val durationSec: Long?,
    val state: QState, val note: String? = null, val sentAt: Long? = null,
)

// id = "primoBattito-indice": rientrare lo stesso ascolto non crea doppioni. Kotlin puro → testabile.
fun queueIds(l: Listen, e: Evaluation): List<Pair<String, Long>> = e.timestamps.mapIndexed { i, t -> "${l.firstBeat}-$i" to t / 1000 }

// Testo del codice ignoredMessage di Last.fm
fun ignoredNote(code: Int) = when (code) {
    1 -> "artist ignored"; 2 -> "track ignored"; 3 -> "timestamp too old"; 4 -> "timestamp too new"; 5 -> "daily limit"
    else -> "code $code"
}

// Coda persistente degli scrobble (scrobble_queue.json) + invio a Last.fm. Un solo worker alla volta.
// ponytail: file JSON unico riscritto a ogni cambio → Room se cresce
object ScrobbleQueue {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var file: File? = null
    private var again = false // trigger arrivato mentre il worker girava

    private val _items = MutableStateFlow<List<QItem>>(emptyList()) // più vecchio in testa
    val items: StateFlow<List<QItem>> = _items
    private val _error = MutableStateFlow<String?>(null) // "session" | messaggio
    val error: StateFlow<String?> = _error

    @Synchronized fun init(ctx: Context) { // idempotente
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "scrobble_queue.json")
        file = f
        try {
            val a = JSONArray(f.readText())
            _items.value = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                QItem(o.getString("id"), o.getString("artist"), o.getString("title"), o.optString("album").takeIf { s -> s.isNotEmpty() },
                    o.getLong("ts"), if (o.isNull("durationSec")) null else o.getLong("durationSec"), QState.valueOf(o.getString("state")),
                    o.optString("note").takeIf { s -> s.isNotEmpty() }, if (o.isNull("sentAt")) null else o.getLong("sentAt"))
            }
        } catch (_: Exception) { /* assente o corrotto → vuota */ }
        scope.launch { Connectivity.online.filter { it }.collect { flush() } }
        scope.launch { HistorySync.user.filter { it != null }.collect { flush() } }
        flush()
    }

    fun retry() { _error.value = null; flush() }

    fun enqueue(l: Listen, e: Evaluation, m: ScrobbleMeta) {
        val dur = e.durationMs?.div(1000)
        val add = queueIds(l, e)
        synchronized(this) {
            val have = _items.value.mapTo(HashSet()) { it.id }
            val new = add.filter { it.first !in have }.map { (id, ts) -> QItem(id, m.artist, m.title, m.album, ts, dur, QState.PENDING) }
            if (new.isEmpty()) return
            _items.update { (it + new).sortedBy { x -> x.ts } }
            save()
        }
        flush()
    }

    private fun setState(ids: Set<String>, st: QState, note: String? = null, sentAt: Long? = null) = synchronized(this) {
        _items.update { l -> l.map { if (it.id in ids) it.copy(state = st, note = note, sentAt = sentAt) else it } }
        save()
    }

    @Synchronized private fun save() {
        val f = file ?: return
        val min = System.currentTimeMillis() - 30 * 86_400_000L
        _items.update { l -> l.filter { (it.state != QState.SENT && it.state != QState.IGNORED) || (it.sentAt ?: it.ts * 1000) >= min } }
        val a = JSONArray()
        _items.value.forEach {
            a.put(JSONObject().put("id", it.id).put("artist", it.artist).put("title", it.title).put("album", it.album ?: JSONObject.NULL)
                .put("ts", it.ts).put("durationSec", it.durationSec ?: JSONObject.NULL).put("state", it.state.name)
                .put("note", it.note ?: JSONObject.NULL).put("sentAt", it.sentAt ?: JSONObject.NULL))
        }
        try {
            val tmp = File(f.parentFile, "scrobble_queue.tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(f)
        } catch (_: Exception) { /* ponytail: scrittura fallita → si riprova al prossimo cambio di stato */ }
    }

    private var running = false
    private var wake: Job? = null

    // Idempotente: se il worker gira, segna solo "again" e interrompe l'attesa di backoff.
    @Synchronized fun flush() {
        if (file == null) return
        if (running) { again = true; wake?.cancel(); return }
        running = true
        scope.launch {
            while (true) {
                try { run() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { }
                synchronized(this@ScrobbleQueue) { if (!again) { running = false; return@launch }; again = false }
            }
        }
    }

    private fun ready() = HistorySync.user.value != null && HistorySync.sessionKey() != null && Connectivity.online.value

    private suspend fun run() {
        var backoff = 0L
        while (true) {
            if (!ready()) return // riparte da un trigger (rete / login)
            if (!recover()) { backoff = next(backoff); if (!pause(backoff)) return; continue }
            val batch = _items.value.filter { it.state == QState.PENDING }.take(50)
            if (batch.isEmpty()) return
            val ids = batch.map { it.id }.toSet()
            val sk = HistorySync.sessionKey() ?: return
            setState(ids, QState.SENDING) // salvato prima della chiamata: se l'app muore si ricontrolla su Last.fm
            try {
                val codes = LastFmApi.scrobble(batch, sk)
                _error.value = null
                backoff = 0
                var daily = false
                val now = System.currentTimeMillis()
                batch.forEachIndexed { i, q ->
                    when (val c = codes[i]) {
                        0 -> setState(setOf(q.id), QState.SENT, sentAt = now)
                        5 -> { setState(setOf(q.id), QState.PENDING); daily = true }
                        else -> setState(setOf(q.id), QState.IGNORED, ignoredNote(c), now)
                    }
                }
                if (daily) return // limite giornaliero: si riprova al prossimo trigger
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // annullato a metà: esito incerto, restano SENDING (recover)
            } catch (e: IOException) {
                // esito incerto (richiesta forse arrivata): restano SENDING → recover() controlla la cronologia, niente doppioni
                backoff = next(backoff)
                if (!pause(backoff)) return
            } catch (e: Exception) {
                setState(ids, QState.PENDING) // errore Last.fm esplicito → non registrati
                val c = (e as? LastFmException)?.code
                when {
                    c == 9 -> { _error.value = "session"; return }
                    c == 11 || c == 16 || c == 29 -> {}
                    else -> _error.value = e.message ?: e.javaClass.simpleName
                }
                backoff = next(backoff)
                if (!pause(backoff)) return
            }
        }
    }

    // 1, 2, 4… minuti fino a 30
    private fun next(b: Long) = if (b == 0L) 60_000L else minOf(b * 2, 30 * 60_000L)

    // Attesa di backoff interrompibile da un nuovo trigger; false = rete assente → esce (riparte con la rete).
    private suspend fun pause(ms: Long): Boolean {
        if (!Connectivity.online.value) return false
        val j = scope.launch { delay(ms) }
        wake = j
        j.join()
        wake = null
        return true
    }

    // Recupero SENDING (app uccisa o rete caduta a metà richiesta): un'unica lettura della cronologia sulla finestra
    // dei loro timestamp (±1 s, estremi non garantiti inclusivi). Presente con lo stesso uts → SENT, altrimenti PENDING.
    private suspend fun recover(): Boolean {
        val sending = _items.value.filter { it.state == QState.SENDING }
        if (sending.isEmpty()) return true
        val user = HistorySync.user.value ?: return false
        val seen = HashSet<Long>()
        try {
            var page = 1
            while (true) {
                val (found, pages) = LastFmApi.recentTracksPage(user, page, sending.minOf { it.ts } - 1, sending.maxOf { it.ts } + 1)
                found.mapTo(seen) { it.uts }
                if (found.isEmpty() || page >= pages) break
                page++
                delay(250)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { return false }
        // ponytail: confronto solo sull'uts (stesso secondo di un altro scrobble dell'utente: caso trascurato)
        val now = System.currentTimeMillis()
        val (ok, ko) = sending.partition { it.ts in seen }
        if (ok.isNotEmpty()) setState(ok.mapTo(HashSet()) { it.id }, QState.SENT, sentAt = now)
        if (ko.isNotEmpty()) setState(ko.mapTo(HashSet()) { it.id }, QState.PENDING)
        return true
    }
}
