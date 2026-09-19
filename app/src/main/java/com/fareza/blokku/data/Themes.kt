package com.fareza.blokku.data

class GameTheme(
    val id: String,
    val displayName: String,
    val price: Int,
    val bgTop: Long,
    val bgBottom: Long,
    val boardBg: Long,
    val cellEmpty: Long,
    val gridLine: Long,
    val accent: Long,
    val textPrimary: Long,
    val blockColors: LongArray, // 7 colors
    val glow: Long,
)

/**
 * Original theme collection. Each theme re-skins the whole game.
 */
object Themes {

    val ALL: List<GameTheme> = listOf(
        GameTheme(
            "sunrise", "Sunrise", 0,
            0xFF2B1B4A, 0xFF0E1024, 0xFF1B1740, 0xFF2F2A68, 0xFF3A3580,
            0xFFFFB84D, 0xFFF4F1FF,
            longArrayOf(
                0xFFFF5D73, 0xFFFFA53D, 0xFFFFD84D, 0xFF62D97B,
                0xFF4DC9FF, 0xFF9B6DFF, 0xFFFF6FD8
            ),
            0xFFFFE28A,
        ),
        GameTheme(
            "lagoon", "Lagoon", 250,
            0xFF042A3A, 0xFF011620, 0xFF083347, 0xFF0E4A63, 0xFF155D78,
            0xFF38E1C6, 0xFFEAFBFF,
            longArrayOf(
                0xFFFF6B81, 0xFFFFB84D, 0xFFF5E95B, 0xFF52E0A8,
                0xFF45D5F0, 0xFF6E8BFF, 0xFFFF7AC6
            ),
            0xFF7CF2D8,
        ),
        GameTheme(
            "meadow", "Meadow", 250,
            0xFF14301F, 0xFF0A1A12, 0xFF1B4029, 0xFF235436, 0xFF2F6B45,
            0xFFB7E26B, 0xFFF2FBEC,
            longArrayOf(
                0xFFE85D75, 0xFFF2A65A, 0xFFF2E35B, 0xFF8CD96B,
                0xFF66C7A3, 0xFF7EA6E0, 0xFFD98CE0
            ),
            0xFFD6F28A,
        ),
        GameTheme(
            "dusk", "Dusk Pop", 400,
            0xFF351035, 0xFF170B21, 0xFF2A1740, 0xFF3A2157, 0xFF4C2E70,
            0xFFFF6FA5, 0xFFFFF0F7,
            longArrayOf(
                0xFFFF4D6D, 0xFFFF8E3D, 0xFFFFCE45, 0xFF4DD599,
                0xFF45B7E8, 0xFFB84DFF, 0xFFFF5DC8
            ),
            0xFFFF9AD5,
        ),
        GameTheme(
            "mono", "Mono", 400,
            0xFF1C1C1E, 0xFF0A0A0B, 0xFF232326, 0xFF2E2E33, 0xFF3C3C44,
            0xFFF5F5F0, 0xFFF2F2EA,
            longArrayOf(
                0xFFEFEFEA, 0xFFD6D6CE, 0xFFBDBDB5, 0xFFA3A39C,
                0xFF8C8C86, 0xFF757570, 0xFF5C5C58
            ),
            0xFFFFFFFF,
        ),
        GameTheme(
            "neon", "Neon Night", 600,
            0xFF0B0824, 0xFF050412, 0xFF131040, 0xFF1C1960, 0xFF262380,
            0xFF00E5FF, 0xFFE8E6FF,
            longArrayOf(
                0xFFFF2E63, 0xFFFF9F1C, 0xFFFAF62D, 0xFF2EF2B8,
                0xFF00D1FF, 0xFF8C52FF, 0xFFFF3DF0
            ),
            0xFF00F5D4,
        ),
        GameTheme(
            "sakura", "Sakura", 600,
            0xFF3A1F33, 0xFF1B0F1E, 0xFF33204A, 0xFF40285C, 0xFF503570,
            0xFFFFB3D1, 0xFFFFF4F9,
            longArrayOf(
                0xFFE85A9B, 0xFFF29B45, 0xFFF5D94D, 0xFF78D98C,
                0xFF62C4E8, 0xFFA67AE0, 0xFFFF8FC7
            ),
            0xFFFFC7E0,
        ),
        GameTheme(
            "royal", "Royal Gold", 900,
            0xFF241A0E, 0xFF120C06, 0xFF241C33, 0xFF32284A, 0xFF403660,
            0xFFFFD166, 0xFFFCF4E3,
            longArrayOf(
                0xFFD94F4F, 0xFFE8912D, 0xFFF2C230, 0xFF66B84D,
                0xFF3D9BE0, 0xFF7A5CD9, 0xFFD957C7
            ),
            0xFFFFE39E,
        ),
    )

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL[0]

    fun current() = byId(Save.selectedTheme)

    /**
     * Colorblind-safe palette (Okabe-Ito inspired, high contrast + symbols drawn on cells).
     */
    val COLORBLIND_COLORS = longArrayOf(
        0xFFE69F00, 0xFF56B4E9, 0xFF009E73, 0xFFF0E442,
        0xFF0072B2, 0xFFD55E00, 0xFFCC79A7
    )

    /** Symbols per color index drawn on blocks in colorblind mode. */
    val COLORBLIND_SYMBOLS = arrayOf("●", "■", "▲", "◆", "★", "✚", "○")
}
