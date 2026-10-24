package co.divlab.tvlauncher

import android.content.Context

/** การตั้งค่าที่ผู้ใช้ปรับได้ */
class Prefs(ctx: Context) {
    companion object {
        val SAVER_MINUTES = intArrayOf(0, 2, 5, 10, 15, 30)
        val SLEEP_MINUTES = intArrayOf(0, 15, 30, 60, 90, 120)
    }

    private val sp = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** 0 = ไม่แสดง, 1 = เฉพาะที่เลือก, 2 = แสดงตลอด */
    var labels: Int
        get() = sp.getInt("labels", 1)
        set(v) = sp.edit().putInt("labels", v).apply()

    /** 0 = เล็ก, 1 = กลาง, 2 = ใหญ่ */
    var iconSize: Int
        get() = sp.getInt("iconSize", 1)
        set(v) = sp.edit().putInt("iconSize", v).apply()

    var columns: Int
        get() = sp.getInt("columns", 6)
        set(v) = sp.edit().putInt("columns", v).apply()

    var showRecent: Boolean
        get() = sp.getBoolean("showRecent", true)
        set(v) = sp.edit().putBoolean("showRecent", v).apply()

    var appColors: Boolean
        get() = sp.getBoolean("appColors", true)
        set(v) = sp.edit().putBoolean("appColors", v).apply()

    var showStatus: Boolean
        get() = sp.getBoolean("showStatus", true)
        set(v) = sp.edit().putBoolean("showStatus", v).apply()

    var showHidden: Boolean
        get() = sp.getBoolean("showHidden", false)
        set(v) = sp.edit().putBoolean("showHidden", v).apply()

    var showScreenBtn: Boolean
        get() = sp.getBoolean("showScreenBtn", true)
        set(v) = sp.edit().putBoolean("showScreenBtn", v).apply()

    /** 0 = เหลี่ยม, 1 = มน, 2 = มนมาก, 3 = แคปซูล */
    var iconShape: Int
        get() = sp.getInt("iconShape", 1)
        set(v) = sp.edit().putInt("iconShape", v).apply()

    fun cornerDp() = floatArrayOf(4f, 14f, 24f, 999f)[iconShape.coerceIn(0, 3)]

    /** 0 = ตามระบบ, 1 = ไทย, 2 = English */
    var language: Int
        get() = sp.getInt("language", 0)
        set(v) = sp.edit().putInt("language", v).apply()

    /** 0 = ตามแอป, 1.. = ธีมคงที่ (ดู Themes) */
    var theme: Int
        get() = sp.getInt("theme", if (sp.getBoolean("appColors", true)) 0 else 1)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** -1 = ปิด, อื่น ๆ = index ใน Weather.cities */
    var weatherCity: Int
        get() = sp.getInt("weatherCity", 0)
        set(v) = sp.edit().putInt("weatherCity", v).apply()

    /** index ใน SAVER_MINUTES (0 = ปิด) */
    var saver: Int
        get() = sp.getInt("saver", 2)
        set(v) = sp.edit().putInt("saver", v).apply()

    /** เวลา (epoch ms) ที่จะปิดทีวี, 0 = ไม่ได้ตั้ง */
    var sleepAt: Long
        get() = sp.getLong("sleepAt", 0)
        set(v) = sp.edit().putLong("sleepAt", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    fun tileHeightDp() = intArrayOf(60, 72, 88)[iconSize.coerceIn(0, 2)]

    fun recents(): List<String> = sp.getString("recents", "")!!.split(",").filter { it.isNotEmpty() }

    fun pushRecent(pkg: String) {
        val l = (listOf(pkg) + recents().filter { it != pkg }).take(12)
        sp.edit().putString("recents", l.joinToString(",")).apply()
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()

    var latestTag: String
        get() = sp.getString("latestTag", "")!!
        set(v) = sp.edit().putString("latestTag", v).apply()

    fun favorites(): List<String> = sp.getString("favorites", "")!!.split(",").filter { it.isNotEmpty() }

    /** ปัก/เลิกปักแอปโปรด — คืนค่า true ถ้าปักแล้ว */
    fun toggleFavorite(pkg: String): Boolean {
        val l = favorites().toMutableList()
        val pinned = if (l.remove(pkg)) false else { l += pkg; true }
        sp.edit().putString("favorites", l.joinToString(",")).apply()
        return pinned
    }

    fun clearRecents() = sp.edit().remove("recents").apply()
    fun resetOrder() = sp.edit().remove("order").apply()
}
