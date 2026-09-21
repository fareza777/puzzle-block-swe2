package com.fareza.blokku.core

/**
 * Mosaic mode — 9x9 pixel-art pictures hidden under stone cells.
 * Each art is a char grid: '.' = empty, other chars index the palette.
 */
object Mosaics {

    private val PALETTE = intArrayOf(
        0xFFFF5A5A.toInt(), // A coral red
        0xFFFF9F1C.toInt(), // B orange
        0xFFFFD93D.toInt(), // C yellow
        0xFF6BCB77.toInt(), // D green
        0xFF4D96FF.toInt(), // E blue
        0xFF9B5DE5.toInt(), // F purple
        0xFF38BDF8.toInt(), // G cyan
        0xFFFDFDFD.toInt(), // H white
        0xFF2D3142.toInt(), // I charcoal
        0xFFFF70A6.toInt(), // J pink
        0xFF8B5E34.toInt(), // K brown
    )

    private fun col(ch: Char): Int {
        if (ch == '.') return 0
        val i = ch - 'A'
        return if (i in PALETTE.indices) PALETTE[i] else PALETTE[0]
    }

    private val ARTS = arrayOf(
        // ROCKET
        """
        ....E....
        ...EEE...
        ..EGEGE..
        ..EEEEE..
        ..EGDGE..
        ..EEEEE..
        .AEEEEE..
        .ABBBBA..
        ...B.B...
        """,
        // HEART
        """
        .JJ...JJ.
        JJJJ.JJJJ
        JJJJJJJJJ
        JJJJJJJJJ
        .JJJJJJJ.
        ..JJJJJ..
        ...JJJ...
        ....J....
        .........
        """,
        // CAT
        """
        KK.....KK
        KKK...KKK
        KKKKKKKKK
        KKHKKHKKK
        KKKKKKKKK
        KKKCKKKKK
        .KKKKKKK.
        ..KKKKK..
        .........
        """,
        // TREE
        """
        ....D....
        ...DDD...
        ..DDDDD..
        .DDDDDDD.
        ..DDDDD..
        .DDDDDDD.
        ....K....
        ...KKK...
        .........
        """,
        // FISH
        """
        .........
        ...BBB...
        ..BBBBB..
        .BBHBBBB.
        BBBBBBBB.
        .BBBBBB..
        ..BBBB...
        ...BB....
        .........
        """,
        // STAR
        """
        ....C....
        ...CCC...
        CCCCCCCCC
        .CCCCCCC.
        ..CCCCC..
        ..CC.CC..
        .CC...CC.
        CC.....CC
        .........
        """,
        // MUSHROOM
        """
        ..AAAAA..
        .AAHAAHA.
        AAAAAAAAA
        AAHAAAAAA
        ..HHHHH..
        ..HHHHH..
        ..HHHHH..
        ...HHH...
        .........
        """,
        // GEM
        """
        ...GGG...
        ..GGGGG..
        .GGGGGGG.
        GGGGGGGGG
        .GGGGGGG.
        ..GGGGG..
        ...GGG...
        ....G....
        .........
        """,
    )

    val COUNT get() = ARTS.size

    /** Color per cell (0 = transparent / shows board). */
    fun art(index: Int): IntArray {
        val rows = ARTS[index.coerceIn(0, ARTS.size - 1)].trimIndent().trim().lines()
        val out = IntArray(81)
        var i = 0
        for (r in rows.indices) {
            val line = rows[r].replace(" ", "")
            for (cx in line.indices) {
                if (i < 81) out[i++] = col(line[cx])
            }
        }
        return out
    }

    /** Count of painted pixels — the stone budget for the mode. */
    fun artSize(index: Int) = art(index).count { it != 0 }
}
