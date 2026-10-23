package co.divlab.tvlauncher

import android.content.Context

/** การตั้งค่าที่ผู้ใช้ปรับได้ */
class Prefs(ctx: Context) {
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

    fun tileHeightDp() = intArrayOf(60, 72, 88)[iconSize.coerceIn(0, 2)]

    fun recents(): List<String> = sp.getString("recents", "")!!.split(",").filter { it.isNotEmpty() }

    fun pushRecent(pkg: String) {
        val l = (listOf(pkg) + recents().filter { it != pkg }).take(12)
        sp.edit().putString("recents", l.joinToString(",")).apply()
    }

    fun clearRecents() = sp.edit().remove("recents").apply()
    fun resetOrder() = sp.edit().remove("order").apply()
}
