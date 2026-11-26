package dev.deref.roomtone

import android.content.Context
import java.io.IOException

enum class Service { LASTFM, LISTENBRAINZ }

// Pagina di cronologia: (scrobble, pagine totali). ponytail: forma a pagine numerate (Last.fm) → per ListenBrainz (cursore max_ts) si adatta al passo 2
interface HistoryBackend {
    suspend fun page(user: String, page: Int, from: Long?, to: Long?): Pair<List<HistoryScrobble>, Int>
}

// Invio e verifica degli scrobble di un servizio.
interface Scrobbler {
    val batchSize: Int
    // un codice per elemento nell'ordine: 0 = accettato, 5 = limite giornaliero (riprova), altri = ignorato (vedi note)
    suspend fun send(items: List<QItem>, secret: String): List<Int>
    fun note(code: Int): String
    fun sessionExpired(e: Exception): Boolean
    // uts presenti in cronologia nella finestra [from, to] (recover)
    suspend fun seen(user: String, from: Long, to: Long): Set<Long>
}

object LastFmScrobbler : Scrobbler {
    override val batchSize = 50
    override suspend fun send(items: List<QItem>, secret: String) = LastFmApi.scrobble(items, secret)
    override fun note(code: Int) = ignoredNote(code)
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

// ponytail: ListenBrainz non ancora collegato (passo 2): mai connesso → non invia né sincronizza nulla
private object NoBackend : HistoryBackend, Scrobbler {
    override val batchSize = 100
    override suspend fun page(user: String, page: Int, from: Long?, to: Long?): Pair<List<HistoryScrobble>, Int> = throw IOException("not implemented")
    override suspend fun send(items: List<QItem>, secret: String): List<Int> = throw IOException("not implemented")
    override fun note(code: Int) = "code $code"
    override fun sessionExpired(e: Exception) = false
    override suspend fun seen(user: String, from: Long, to: Long): Set<Long> = throw IOException("not implemented")
}

// Tutto ciò che è per servizio: account+cronologia, versioni scelte, coda. Last.fm mantiene i nomi di file/prefs storici.
class Svc(val service: Service, val history: HistorySync, val choices: VersionChoices, val queue: ScrobbleQueue) {
    // Last.fm sempre valutato (la coda attende il login, come prima); gli altri solo se connessi
    val active get() = service == Service.LASTFM || history.user.value != null
}

object Services {
    val lastfm: Svc
    val listenbrainz: Svc
    val all: List<Svc>

    init {
        val lh = HistorySync(Service.LASTFM, LastFmApi, HistoryFiles("history", "lastfm", "lastfm_user", "history_index_v2.tsv", "lastfm_session_key"))
        lastfm = Svc(Service.LASTFM, lh, VersionChoices("version_choices.json"), ScrobbleQueue("scrobble_queue.json", lh, LastFmScrobbler))
        val bh = HistorySync(Service.LISTENBRAINZ, NoBackend, HistoryFiles("history_lb", "listenbrainz", "listenbrainz_user", "history_index_lb.tsv", "listenbrainz_token"))
        listenbrainz = Svc(Service.LISTENBRAINZ, bh, VersionChoices("version_choices_lb.json"), ScrobbleQueue("scrobble_queue_lb.json", bh, NoBackend))
        all = listOf(lastfm, listenbrainz)
    }

    fun of(s: Service) = if (s == Service.LASTFM) lastfm else listenbrainz

    @Synchronized fun init(ctx: Context) { // idempotente (ogni init lo è)
        all.forEach { it.history.init(ctx); it.choices.init(ctx.applicationContext.filesDir); it.queue.init(ctx) }
    }
}
