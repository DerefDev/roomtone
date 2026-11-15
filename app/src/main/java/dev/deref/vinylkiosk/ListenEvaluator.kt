package dev.deref.vinylkiosk

import kotlin.math.abs
import kotlin.math.max

// Kotlin puro. Valuta un tratto d'ascolto contro la durata del brano.
enum class Status { VALID, DRAFT, DISCARDED }

data class Evaluation(
    val status: Status,
    val reason: String,
    val plays: Int,
    val timestamps: List<Long>,
    val durationMs: Long?,
)

fun evaluate(l: Listen, durationMs: Long?): Evaluation {
    if (durationMs == null) return Evaluation(Status.DRAFT, "durata sconosciuta", 1, listOf(l.start), null)
    fun ev(s: Status, r: String, n: Int) =
        Evaluation(s, r, n, List(n) { l.start + it * durationMs }, durationMs)
    if (durationMs <= 30_000) return ev(Status.DISCARDED, "brano sotto 30 s", 0) // regola Last.fm
    val played = l.playedMs
    var n = Math.round(played.toDouble() / durationMs).toInt()
    if (n == 0 && played >= 240_000) n = 1 // regola dei 4 minuti
    if (n == 0) return ev(Status.DISCARDED, "meno di metà brano", 0)
    if (n >= 2 && abs(played - n * durationMs) > max(20_000.0, 0.15 * durationMs))
        return ev(Status.DRAFT, "ripetizioni ambigue", n)
    return ev(Status.VALID, "ok", n)
}
