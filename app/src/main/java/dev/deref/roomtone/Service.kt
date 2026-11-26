package dev.deref.roomtone

import android.content.Context

enum class Service { LASTFM, LISTENBRAINZ }

// Pagina di cronologia: next = cursore della pagina seguente (null = finito); pages solo per l'avanzamento (null = ignoto).
// Cursore: Last.fm = numero di pagina, ListenBrainz = max_ts. from/to = finestra in secondi (estremi inclusi).
class HistoryPage(val items: List<HistoryScrobble>, val next: Long?, val pages: Int?)

interface HistoryBackend {
    suspend fun page(user: String, cursor: Long?, from: Long?, to: Long?): HistoryPage
}

// Esito di un elemento inviato
sealed interface SendResult {
    object Ok : SendResult
    object Retry : SendResult // es. limite giornaliero: resta in coda, si riprova al prossimo trigger
    data class Ignored(val note: String) : SendResult
}

// Invio e verifica degli scrobble di un servizio.
interface Scrobbler {
    val batchSize: Int
    // un esito per elemento, nell'ordine della richiesta
    suspend fun send(items: List<QItem>, secret: String): List<SendResult>
    fun sessionExpired(e: Exception): Boolean
    // uts presenti in cronologia nella finestra [from, to] (recover)
    suspend fun seen(user: String, from: Long, to: Long): Set<Long>
}

object LastFmScrobbler : Scrobbler {
    override val batchSize = 50
    override suspend fun send(items: List<QItem>, secret: String) = LastFmApi.scrobble(items, secret).map {
        when (it) { 0 -> SendResult.Ok; 5 -> SendResult.Retry; else -> SendResult.Ignored(ignoredNote(it)) }
    }
    override fun sessionExpired(e: Exception) = (e as? LastFmException)?.code == 9
    override suspend fun seen(user: String, from: Long, to: Long): Set<Long> {
        val seen = HashSet<Long>()
        var page = 1
        while (true) {
            val (found, pages) = LastFmApi.recentTracksPage(user, page, from, to)
            found.mapTo(seen) { it.uts }
            if (found.isEmpty() || page >= pages) break
            page++
            kotlinx.coroutines.delay(250)
        }
        return seen
    }
}

// Tutto ciò che è per servizio: account+cronologia, versioni scelte, coda. Last.fm mantiene i nomi di file/prefs storici.
class Svc(val service: Service, val history: HistorySync, val choices: VersionChoices, val queue: ScrobbleQueue) {
    // connesso; Last.fm anche se nessun servizio è connesso (la coda attende il login, come prima)
    val active get() = history.user.value != null || (service == Service.LASTFM && Services.listenbrainz.history.user.value == null)
}

object Services {
    val lastfm: Svc
    val listenbrainz: Svc
    val all: List<Svc>

    init {
        val lh = HistorySync(Service.LASTFM, LastFmApi, HistoryFiles("history", "lastfm", "lastfm_user", "history_index_v2.tsv", "lastfm_session_key"))
        lastfm = Svc(Service.LASTFM, lh, VersionChoices("version_choices.json"), ScrobbleQueue("scrobble_queue.json", lh, LastFmScrobbler))
        val bh = HistorySync(Service.LISTENBRAINZ, ListenBrainzApi, HistoryFiles("history_lb", "listenbrainz", "listenbrainz_user", "history_index_lb.tsv", "listenbrainz_token"))
        listenbrainz = Svc(Service.LISTENBRAINZ, bh, VersionChoices("version_choices_lb.json"), ScrobbleQueue("scrobble_queue_lb.json", bh, ListenBrainzScrobbler))
        all = listOf(lastfm, listenbrainz)
    }

    // Login ListenBrainz: il token personale va verificato (→ nome utente) prima di salvarlo. Eccezione = token non valido / rete.
    suspend fun connectListenBrainz(token: String) {
        val t = token.trim()
        listenbrainz.history.connect(ListenBrainzApi.validate(t), t)
    }

    fun of(s: Service) = if (s == Service.LASTFM) lastfm else listenbrainz

    @Synchronized fun init(ctx: Context) { // idempotente (ogni init lo è)
        all.forEach { it.history.init(ctx); it.choices.init(ctx.applicationContext.filesDir); it.queue.init(ctx) }
    }
}
