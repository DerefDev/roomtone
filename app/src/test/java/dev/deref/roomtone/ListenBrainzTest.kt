package dev.deref.roomtone

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzTest {
    private val page = """{"payload":{"count":3,"listens":[
        {"listened_at":300,"track_metadata":{"artist_name":"Artist A","track_name":"Song A","release_name":"Album A",
          "mbid_mapping":{"recording_mbid":"rec-1","release_mbid":"rel-1","artist_mbids":["art-1"]},
          "additional_info":{"recording_mbid":"other"}}},
        {"listened_at":200,"track_metadata":{"artist_name":"Artist B","track_name":"Song B",
          "additional_info":{"recording_mbid":"rec-2","artist_mbids":["art-2","art-3"]}}},
        {"listened_at":100,"track_metadata":{"artist_name":"Artist C","track_name":"Song C","release_name":""}},
        {"listened_at":50,"track_metadata":{"artist_name":"","track_name":"Song D"}}]}}"""

    @Test fun parsePage() {
        val l = parseListens(JSONObject(page))
        assertEquals(listOf(300L, 200L, 100L), l.map { it.uts })
        assertEquals(Mbids("rec-1", "rel-1", listOf("art-1")), l[0].mbids)
        assertEquals("Album A", l[0].album)
        assertEquals(Mbids("rec-2", null, listOf("art-2", "art-3")), l[1].mbids)
        assertNull(l[1].album)
        assertNull(l[2].mbids); assertNull(l[2].album)
    }

    @Test fun submit() {
        val q = QItem("1-0", "Artist A", "Song A", "Album A", 300, 240, QState.PENDING, mbids = Mbids("rec-1", null, listOf("art-1")))
        val q2 = QItem("1-1", "Artist B", "Song B", null, 540, null, QState.PENDING)
        val o = JSONObject(submitPayload(listOf(q, q2), "1.2.3"))
        assertEquals("import", o.getString("listen_type"))
        val p = o.getJSONArray("payload")
        val a = p.getJSONObject(0)
        assertEquals(300, a.getLong("listened_at"))
        val tm = a.getJSONObject("track_metadata")
        assertEquals("Album A", tm.getString("release_name"))
        assertFalse(tm.has("mbid_mapping"))
        val info = tm.getJSONObject("additional_info")
        assertEquals("rec-1", info.getString("recording_mbid"))
        assertFalse(info.has("release_mbid"))
        assertEquals(240_000, info.getLong("duration_ms"))
        assertEquals("1.2.3", info.getString("submission_client_version"))
        val i2 = p.getJSONObject(1).getJSONObject("track_metadata")
        assertFalse(i2.has("release_name"))
        assertFalse(i2.getJSONObject("additional_info").has("duration_ms"))
        assertFalse(i2.getJSONObject("additional_info").has("recording_mbid"))
    }

    @Test fun tsvWithMbidsRoundTrip() {
        val h = HistoryIndex()
        h.add(HistoryScrobble("Artist A", "Song A", "Album A", 100, Mbids("rec-old", "rel-old", listOf("a1"))))
        h.add(HistoryScrobble("Artist A", "Song A", "Album A", 200, Mbids("rec-new", null, listOf("a1", "a2"))))
        h.add(HistoryScrobble("Artist A", "Song A", "Album A", 150, Mbids("rec-mid", null, emptyList()))) // più vecchio del migliore → ignorato
        val h2 = HistoryIndex().apply { loadTsv(h.toTsv()) }
        val v = h2.versions(Track("Song A", "Artist A"))[0]
        assertEquals(3, v.plays)
        assertEquals(Mbids("rec-new", null, listOf("a1", "a2")), v.mbids)
        assertEquals(h.toTsv(), h2.toTsv())
    }

    @Test fun tsvLastFmUnchanged() {
        val tsv = "Artist A\tSong A\tAlbum A\t2\t200,100\n"
        val h = HistoryIndex().apply { loadTsv(tsv) }
        assertEquals(tsv, h.toTsv())
        assertNull(h.versions(Track("Song A", "Artist A"))[0].mbids)
        h.add(HistoryScrobble("Artist A", "Song A", "Album A", 300))
        assertTrue(h.toTsv().trim().split('\t').size == 5)
    }

    @Test fun metaCarriesMbids() {
        val m = Mbids("r", null, emptyList())
        assertEquals(m, scrobbleMeta(Track("Song A", "Artist A"), Version("Artist A", "Song A", null, 1, listOf(1), m)).mbids)
        assertNull(scrobbleMeta(Track("Song A", "Artist A"), null).mbids)
    }
}

class SendSplittingTest {
    private fun q(n: Int) = QItem("$n", "Artist A", "Song $n", null, n.toLong(), null, QState.PENDING)

    @Test fun badItemIsIsolated() {
        val calls = ArrayList<Int>()
        val r = kotlinx.coroutines.runBlocking {
            sendSplitting((1..4).map(::q)) { b ->
                calls += b.size
                if (b.any { it.id == "3" }) throw ListenBrainzException(400, "bad item")
            }
        }
        assertEquals(listOf(SendResult.Ok, SendResult.Ok, SendResult.Ignored("bad item"), SendResult.Ok), r)
        assertEquals(listOf(4, 1, 1, 1, 1), calls) // blocco intero, poi uno alla volta
    }

    @Test fun otherErrorsPropagate() {
        try {
            kotlinx.coroutines.runBlocking { sendSplitting(listOf(q(1), q(2))) { throw ListenBrainzException(401, "x") } }
            org.junit.Assert.fail()
        } catch (e: ListenBrainzException) { assertEquals(401, e.code) }
    }
}
