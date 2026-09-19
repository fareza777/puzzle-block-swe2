package com.fareza.blokku.core

import kotlin.random.Random

/**
 * Original shape catalog for Blokku. Weighted toward friendly pieces early on;
 * chunky shapes appear more as weightBias grows (used by level difficulty).
 */
object Shapes {

    class Def(val coords: List<Pair<Int, Int>>, val weight: Int)

    private fun d(w: Int, vararg rc: Pair<Int, Int>) = Def(rc.toList(), w)

    private fun line(n: Int, horizontal: Boolean, w: Int) =
        Def((0 until n).map { if (horizontal) 0 to it else it to 0 }, w)

    private fun rect(r: Int, c: Int, w: Int) =
        Def((0 until r).flatMap { rr -> (0 until c).map { rr to it } }, w)

    val ALL: List<Def> = listOf(
        // singles & small
        d(9, 0 to 0),
        line(2, true, 10), line(2, false, 10),
        line(3, true, 10), line(3, false, 10),
        rect(2, 2, 10),
        // medium
        line(4, true, 7), line(4, false, 7),
        rect(2, 3, 6), rect(3, 2, 6),
        d(7, 0 to 0, 1 to 0, 1 to 1),            // small L
        d(7, 0 to 0, 0 to 1, 1 to 0),
        d(7, 0 to 1, 1 to 0, 1 to 1),
        d(7, 0 to 0, 0 to 1, 1 to 1),
        d(5, 0 to 0, 0 to 1, 0 to 2, 1 to 1),    // T (4)
        d(5, 0 to 1, 1 to 0, 1 to 1, 1 to 2),
        d(4, 0 to 0, 0 to 1, 1 to 1, 1 to 2),    // S
        d(4, 0 to 1, 0 to 2, 1 to 0, 1 to 1),    // Z
        // big / chunky
        line(5, true, 4), line(5, false, 4),
        rect(3, 3, 3),
        d(4, 0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2), // corner V (5)
        d(4, 0 to 0, 0 to 1, 0 to 2, 1 to 0, 2 to 0),
        d(4, 0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2),
        d(4, 0 to 2, 1 to 2, 2 to 0, 2 to 1, 2 to 2),
        d(4, 0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2),
        d(3, 0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1), // 3x2 done via rect? duplicates ok? keep 2x3 only
        d(4, 0 to 0, 1 to 0, 1 to 1, 2 to 1),    // long S
        d(4, 0 to 1, 1 to 0, 1 to 1, 2 to 0),
        d(4, 0 to 0, 0 to 1, 0 to 2, 1 to 1, 1 to 2, 2 to 1), // plus-ish fat T
        d(3, 0 to 0, 0 to 2, 1 to 0, 1 to 1, 1 to 2, 2 to 0, 2 to 1, 2 to 2) // ring
    ).distinctBy { it.coords.toString() }

    private val totalWeight = ALL.sumOf { it.weight }

    /**
     * Weighted random piece. `bigBias` adds weight to chunky shapes (classic
     * ramps it with score); `smallBias` favours small shapes — Zen uses it so
     * trays stay gentle and almost always placeable.
     */
    fun randomPiece(rng: Random, colorCount: Int, bigBias: Float = 0f, smallBias: Float = 0f): Piece {
        fun w(d: Def): Float {
            val b = if (d.coords.size >= 5) bigBias else if (d.coords.size <= 3) smallBias else 0f
            return (d.weight + b).coerceAtLeast(0.05f)
        }
        var total = 0f
        for (d in ALL) total += w(d)
        var pick = rng.nextFloat() * total
        var idx = ALL.size - 1
        for (i in ALL.indices) {
            pick -= w(ALL[i])
            if (pick < 0) { idx = i; break }
        }
        return Piece.of(ALL[idx].coords, rng.nextInt(colorCount))
    }
}
