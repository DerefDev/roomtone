package dev.deref.roomtone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// Versione scelta dall'utente + brano Shazam di riferimento (per mostrarlo nelle impostazioni).
data class Choice(val song: Track, val key: VersionKey)

// Scelte salvate: histKey → versione. File per servizio (es. version_choices.json; scrittura atomica tmp+rename).
class VersionChoices(private val name: String) {
    private val _choices = MutableStateFlow<Map<String, Choice>>(emptyMap())
    val choices: StateFlow<Map<String, Choice>> = _choices
    private var file: File? = null

    @Synchronized fun init(dir: File) { // idempotente
        if (file != null) return
        file = File(dir, name)
        try {
            val a = JSONArray(file!!.readText())
            _choices.value = (0 until a.length()).associate {
                val o = a.getJSONObject(it)
                o.getString("hk") to Choice(Track(o.getString("title"), o.getString("artist")),
                    VersionKey(o.getString("vArtist"), o.getString("vTitle"), o.optString("vAlbum").takeIf { s -> s.isNotEmpty() }))
            }
        } catch (_: Exception) { /* assente o corrotto → vuoto */ }
    }

    fun get(t: Track): VersionKey? = _choices.value[histKey(t)]?.key

    fun set(t: Track, k: VersionKey) = update { it + (histKey(t) to Choice(t, k)) }

    fun remove(hk: String) = update { it - hk }

    @Synchronized private fun update(f: (Map<String, Choice>) -> Map<String, Choice>) {
        _choices.value = f(_choices.value)
        val dir = file?.parentFile ?: return
        val a = JSONArray()
        _choices.value.forEach { (hk, c) ->
            a.put(JSONObject().put("hk", hk).put("title", c.song.title).put("artist", c.song.artist)
                .put("vArtist", c.key.artist).put("vTitle", c.key.title).put("vAlbum", c.key.album ?: ""))
        }
        try {
            val tmp = File(dir, name.substringBeforeLast('.') + ".tmp")
            tmp.writeText(a.toString())
            tmp.renameTo(file!!)
        } catch (_: Exception) { /* ponytail: scrittura fallita → si riprova al prossimo salvataggio */ }
    }
}
