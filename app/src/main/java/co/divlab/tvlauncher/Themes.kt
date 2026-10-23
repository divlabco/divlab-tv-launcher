package co.divlab.tvlauncher

/** ธีมสีพื้นหลัง: [หลัก, เข้ม, รอง] — index 0 = ตามแอป */
object Themes {
    val names = listOf(
        "ตามแอป" to "From app", "Aurora" to "Aurora", "Midnight" to "Midnight", "Sunset" to "Sunset",
        "Ocean" to "Ocean", "Forest" to "Forest", "Rose" to "Rose",
    )
    private fun c(vararg v: Long) = v.map { it.toInt() }.toIntArray()
    val palettes = listOf(
        c(0xFF4F46E5, 0xFF1F2937, 0xFF0EA5E9),
        c(0xFF4F46E5, 0xFF1F2937, 0xFF0EA5E9),
        c(0xFF1E3A8A, 0xFF020617, 0xFF6366F1),
        c(0xFFF97316, 0xFF7C2D12, 0xFFEC4899),
        c(0xFF06B6D4, 0xFF0C4A6E, 0xFF3B82F6),
        c(0xFF22C55E, 0xFF052E16, 0xFF84CC16),
        c(0xFFF43F5E, 0xFF4C0519, 0xFFFB7185),
    )
    fun name(i: Int) = names[i].let { L.t(it.first, it.second) }
}
