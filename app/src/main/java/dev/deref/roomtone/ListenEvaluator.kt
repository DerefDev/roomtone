package dev.deref.roomtone

import kotlin.math.abs
import kotlin.math.max

// Kotlin puro. Valuta un tratto d'ascolto contro la durata del brano.
enum class Status { VALID, DRAFT, DISCARDED }
enum class Reason { OK, UNKNOWN_DURATION, TRACK_UNDER_30S, LESS_THAN_HALF, AMBIGUOUS_REPEATS, CHOOSE_VERSION, NOT_ON_RECORD }

data class Evaluation(
    val status: Status,
    val reason: Reason,
    val plays: Int,
    val timestamps: List<Long>,
    val durationMs: Long?,
)

fun evaluate(l: Listen, durationMs: Long?): Evaluation {
    if (durationMs == null) return Evaluation(Status.DRAFT, Reason.UNKNOWN_DURATION, 1, listOf(l.start), null)
    fun ev(s: Status, r: Reason, n: Int) =
        Evaluation(s, r, n, List(n) { l.start + it * durationMs }, durationMs)
    if (durationMs <= 30_000) return ev(Status.DISCARDED, Reason.TRACK_UNDER_30S, 0) // regola Last.fm
    val played = l.playedMs
    var n = Math.round(played.toDouble() / durationMs).toInt()
    if (n == 0 && played >= 240_000) n = 1 // regola dei 4 minuti
    if (n == 0) return ev(Status.DISCARDED, Reason.LESS_THAN_HALF, 0)
    if (n >= 2 && abs(played - n * durationMs) > max(20_000.0, 0.15 * durationMs))
        return ev(Status.DRAFT, Reason.AMBIGUOUS_REPEATS, n)
    return ev(Status.VALID, Reason.OK, n)
}
