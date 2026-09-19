package com.fareza.blokku.core

class Board(val size: Int = 9) {

    /** 0 = empty, otherwise colorIndex + 1 */
    val cells = IntArray(size * size)

    fun at(r: Int, c: Int) = cells[r * size + c]

    fun isEmpty(r: Int, c: Int) = cells[r * size + c] == 0

    fun fits(p: Piece, row: Int, col: Int): Boolean {
        for (pc in p.cells) {
            val r = row + (pc shr 4)
            val c = col + (pc and 15)
            if (r < 0 || c < 0 || r >= size || c >= size) return false
            if (cells[r * size + c] != 0) return false
        }
        return true
    }

    /** Any top-left position where piece fits. */
    fun anyFit(p: Piece): Boolean {
        for (r in 0..size - p.rows) for (c in 0..size - p.cols) {
            if (fits(p, r, c)) return true
        }
        return false
    }

    /** First fit position or null — used by hint and AI-ish checks. */
    fun firstFit(p: Piece): Pair<Int, Int>? {
        for (r in 0..size - p.rows) for (c in 0..size - p.cols) {
            if (fits(p, r, c)) return r to c
        }
        return null
    }

    /** Count of board positions where piece fits (for smarter generation). */
    fun fitCount(p: Piece): Int {
        var n = 0
        for (r in 0..size - p.rows) for (c in 0..size - p.cols) {
            if (fits(p, r, c)) n++
        }
        return n
    }

    fun place(p: Piece, row: Int, col: Int) {
        for (pc in p.cells) {
            val r = row + (pc shr 4)
            val c = col + (pc and 15)
            cells[r * size + c] = p.colorIndex + 1
        }
    }

    /**
     * Clears that placing this piece at (row,col) would create.
     * Returns bitmask-pairs: list of completed row indices and column indices.
     */
    fun findClears(p: Piece, row: Int, col: Int): ClearResult {
        val rowsFull = ArrayList<Int>()
        val colsFull = ArrayList<Int>()
        val placedMask = HashSet<Int>()
        for (pc in p.cells) placedMask.add((row + (pc shr 4)) * size + col + (pc and 15))

        for (r in 0 until size) {
            var full = true
            for (c in 0 until size) {
                if (cells[r * size + c] == 0 && !placedMask.contains(r * size + c)) { full = false; break }
            }
            if (full) rowsFull.add(r)
        }
        for (c in 0 until size) {
            var full = true
            for (r in 0 until size) {
                if (cells[r * size + c] == 0 && !placedMask.contains(r * size + c)) { full = false; break }
            }
            if (full) colsFull.add(c)
        }
        if (rowsFull.isEmpty() && colsFull.isEmpty()) return ClearResult(rowsFull, colsFull, IntArray(0))
        val clearSet = HashSet<Int>()
        for (r in rowsFull) for (c in 0 until size) clearSet.add(r * size + c)
        for (c in colsFull) for (r in 0 until size) clearSet.add(r * size + c)
        return ClearResult(rowsFull, colsFull, clearSet.toIntArray())
    }

    fun applyClear(indices: IntArray) {
        for (i in indices) cells[i] = 0
    }

    fun clearArea(row: Int, col: Int, radius: Int): IntArray {
        val removed = ArrayList<Int>()
        for (r in (row - radius)..(row + radius)) {
            for (c in (col - radius)..(col + radius)) {
                if (r in 0 until size && c in 0 until size) {
                    val i = r * size + c
                    if (cells[i] != 0) { cells[i] = 0; removed.add(i) }
                }
            }
        }
        return removed.toIntArray()
    }

    fun filledCount(): Int {
        var n = 0
        for (v in cells) if (v != 0) n++
        return n
    }

    fun copy(): Board {
        val b = Board(size)
        cells.copyInto(b.cells)
        return b
    }

    class ClearResult(val rows: List<Int>, val cols: List<Int>, val clearCells: IntArray) {
        val lineCount get() = rows.size + cols.size
        val isEmpty get() = lineCount == 0
    }
}
