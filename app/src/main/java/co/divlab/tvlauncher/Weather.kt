package co.divlab.tvlauncher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/** สภาพอากาศจาก Open-Meteo (ฟรี ไม่ต้องใช้ API key) */
object Weather {
    data class City(val th: String, val en: String, val lat: Double, val lon: Double) {
        val label get() = L.t(th, en)
    }

    val cities = listOf(
        City("กรุงเทพฯ", "Bangkok", 13.75, 100.50),
        City("พระนครศรีอยุธยา", "Ayutthaya", 14.35, 100.57),
        City("นนทบุรี", "Nonthaburi", 13.86, 100.51),
        City("ปทุมธานี", "Pathum Thani", 14.02, 100.53),
        City("สุพรรณบุรี", "Suphan Buri", 14.47, 100.12),
        City("นครราชสีมา", "Nakhon Ratchasima", 14.97, 102.10),
        City("ขอนแก่น", "Khon Kaen", 16.43, 102.83),
        City("เชียงใหม่", "Chiang Mai", 18.79, 98.98),
        City("พัทยา", "Pattaya", 12.93, 100.88),
        City("ภูเก็ต", "Phuket", 7.88, 98.39),
        City("หาดใหญ่", "Hat Yai", 7.01, 100.47),
    )

    data class Now(val temp: Int, val code: Int) {
        val icon get() = when (code) {
            0 -> "☀"
            1, 2 -> "⛅"
            3 -> "☁"
            45, 48 -> "🌫"
            in 51..67, in 80..82 -> "🌧"
            in 71..77, 85, 86 -> "❄"
            in 95..99 -> "⛈"
            else -> "🌡"
        }
        val text get() = "$icon ${temp}°"
    }

    /** เรียกนอก main thread */
    fun fetch(c: City): Now? = runCatching {
        val u = URL("https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}&current=temperature_2m,weather_code")
        val conn = u.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        val cur = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
        Now(cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"))
    }.getOrNull()
}
