package co.divlab.tvlauncher

import java.util.Calendar

object ThaiDate {
    private val days = arrayOf("อาทิตย์", "จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์")
    private val months = arrayOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
        "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
    private val daysEn = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val monthsEn = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun time(c: Calendar = Calendar.getInstance()) =
        "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))

    /** "วันเสาร์ 24 ต.ค. 2569" หรือ "Saturday, 24 Oct 2026" */
    fun date(c: Calendar = Calendar.getInstance()): String {
        val d = c.get(Calendar.DAY_OF_MONTH); val m = c.get(Calendar.MONTH); val w = c.get(Calendar.DAY_OF_WEEK) - 1
        return if (L.en) "${daysEn[w]}, $d ${monthsEn[m]} ${c.get(Calendar.YEAR)}"
        else "วัน${days[w]} $d ${months[m]} ${c.get(Calendar.YEAR) + 543}"
    }

    fun isNight(c: Calendar = Calendar.getInstance()) = c.get(Calendar.HOUR_OF_DAY).let { it >= 22 || it < 6 }
}
