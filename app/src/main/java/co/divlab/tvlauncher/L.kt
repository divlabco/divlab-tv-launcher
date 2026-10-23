package co.divlab.tvlauncher

import java.util.Locale

/** แปลภาษาแบบง่าย: L.t("ไทย", "English") */
object L {
    var en = false
        private set

    /** lang: 0 = ตามระบบ, 1 = ไทย, 2 = English */
    fun apply(lang: Int) {
        en = when (lang) {
            1 -> false
            2 -> true
            else -> Locale.getDefault().language != "th"
        }
    }

    fun t(th: String, en: String) = if (this.en) en else th
}
