package dev.deref.roomtone

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListensJsonTest {
    // formato precedente: stato alla radice, nessuna sezione "svc"
    private val old = """{"title":"Song A","artist":"Artist A","firstBeat":1000,"lastBeat":2000,"beats":3,
        "status":"DRAFT","reason":"CHOOSE_VERSION","plays":1,"timestamps":[500],"durationMs":240000,
        "meta":{"artist":"Artist A","title":"Song A","album":"Album A","source":"HISTORY","albumSource":"HISTORY"},
        "options":[{"artist":"Artist A","title":"Song A","album":"Album A","plays":2,"recent":[9,8]}]}"""

    @Test fun oldFormatIsLastFm() {
        val x = listenFromJson(JSONObject(old))
        assertEquals(setOf(Service.LASTFM), x.svc.keys)
        val s = x.of(Service.LASTFM)!!
        assertEquals(Reason.CHOOSE_VERSION, s.eval!!.reason)
        assertEquals("Album A", s.meta!!.album)
        assertEquals(listOf(9L, 8L), s.options[0].recent)
        assertNull(x.of(Service.LISTENBRAINZ))
    }

    @Test fun roundTripPerService() {
        val x = listenFromJson(JSONObject(old))
        val lb = SvcListen(Evaluation(Status.VALID, Reason.OK, 1, listOf(7L), 100_000), ScrobbleMeta("Artist B", "Song B", null, MetaSource.SHAZAM))
        val y = listenFromJson(listenToJson(x.copy(svc = x.svc + (Service.LISTENBRAINZ to lb))))
        assertEquals(Status.DRAFT, y.of(Service.LASTFM)!!.eval!!.status)
        assertEquals(Status.VALID, y.of(Service.LISTENBRAINZ)!!.eval!!.status)
        assertEquals("Song B", y.of(Service.LISTENBRAINZ)!!.meta!!.title)
    }
}
