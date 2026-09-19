package com.fareza.blokku.core

/** A placeable block shape: list of (row, col) cells normalized to origin, plus a palette index. */
class Piece(
    val cells: IntArray, // packed as r * 16 + c, normalized so min row/col = 0
    val colorIndex: Int,
) {
    val rows: Int
    val cols: Int

    init {
        var maxR = 0
        var maxC = 0
        for (p in cells) {
            maxR = maxOf(maxR, p shr 4)
            maxC = maxOf(maxC, p and 15)
        }
        rows = maxR + 1
        cols = maxC + 1
    }

    val size get() = cells.size

    fun rotated(): Piece {
        // (r,c) -> (c, rows-1-r) then re-normalize
        val out = IntArray(cells.size)
        var minR = Int.MAX_VALUE
        var minC = Int.MAX_VALUE
        for (i in cells.indices) {
            val r = cells[i] shr 4
            val c = cells[i] and 15
            val nr = c
            val nc = rows - 1 - r
            out[i] = nr * 16 + nc
            if (nr < minR) minR = nr
            if (nc < minC) minC = nc
        }
        for (i in out.indices) {
            out[i] = ((out[i] shr 4) - minR) * 16 + ((out[i] and 15) - minC)
        }
        return Piece(out, colorIndex)
    }

    companion object {
        fun of(coords: List<Pair<Int, Int>>, colorIndex: Int): Piece {
            val minR = coords.minOf { it.first }
            val minC = coords.minOf { it.second }
            val cells = coords.map { (it.first - minR) * 16 + (it.second - minC) }.sorted().toIntArray()
            return Piece(cells, colorIndex)
        }
    }
}
