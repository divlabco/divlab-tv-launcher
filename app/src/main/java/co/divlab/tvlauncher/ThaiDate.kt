package co.divlab.tvlauncher

import java.util.Calendar

object ThaiDate {
    private val days = arrayOf("อาทิตย์", "จันทร์", "อังคาร", "พุธ", "พฤหัสบดี", "ศุกร์", "เสาร์")
    private val months = arrayOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
        "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")

    fun time(c: Calendar = Calendar.getInstance()) =
        "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))

    /** เช่น "วันเสาร์ 24 ต.ค. 2569" */
    fun date(c: Calendar = Calendar.getInstance()) =
        "วัน${days[c.get(Calendar.DAY_OF_WEEK) - 1]} ${c.get(Calendar.DAY_OF_MONTH)} " +
            "${months[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR) + 543}"
}
