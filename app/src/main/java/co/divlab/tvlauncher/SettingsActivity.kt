package co.divlab.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** หน้าปรับแต่ง launcher แบบ tvOS: ซ้ายเป็นหัวเรื่อง, ขวาเป็นการ์ดกระจกรายการตั้งค่า */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var hint: TextView
    private val rows = ArrayList<Pair<Opt, View>>()

    private class Opt(val title: String, val hint: String, val value: () -> String, val onClick: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        hint = findViewById(R.id.hint)
        findViewById<AuraView>(R.id.bg).setColors(MainActivity.DEFAULT_COLORS)
        findViewById<TextView>(R.id.version).text =
            "divlab Launcher " + packageManager.getPackageInfo(packageName, 0).versionName

        val onOff = { b: Boolean -> if (b) "เปิด" else "ปิด" }
        val opts = listOf(
            Opt("ชื่อแอปใต้ไอคอน", "เลือกว่าจะแสดงชื่อแอปใต้ไอคอนเมื่อไหร่",
                { arrayOf("ไม่แสดง", "เฉพาะที่เลือก", "แสดงตลอด")[prefs.labels] }) { prefs.labels = (prefs.labels + 1) % 3 },
            Opt("ขนาดไอคอน", "ไอคอนเล็กลง = เห็นแอปได้มากขึ้นในหน้าเดียว",
                { arrayOf("เล็ก", "กลาง", "ใหญ่")[prefs.iconSize] }) { prefs.iconSize = (prefs.iconSize + 1) % 3 },
            Opt("จำนวนคอลัมน์", "จำนวนแอปต่อแถว (5–8)",
                { "${prefs.columns}" }) { prefs.columns = if (prefs.columns >= 8) 5 else prefs.columns + 1 },
            Opt("แถวเปิดล่าสุด", "แสดงแอปที่เปิดล่าสุดไว้บนสุด กดเพื่อกลับไปดูต่อได้ทันที",
                { onOff(prefs.showRecent) }) { prefs.showRecent = !prefs.showRecent },
            Opt("สีพื้นหลังตามแอป", "เปลี่ยนสี gradient พื้นหลังตามแอปที่เลือก",
                { onOff(prefs.appColors) }) { prefs.appColors = !prefs.appColors },
            Opt("ชื่อทีวีและเครือข่าย", "แสดงชื่อทีวีและ Wi-Fi/LAN ที่เชื่อมอยู่ใต้นาฬิกา",
                { onOff(prefs.showStatus) }) { prefs.showStatus = !prefs.showStatus },
            Opt("แสดงแอปที่ซ่อน", "แสดงแอปที่ซ่อนไว้แบบจาง ๆ เพื่อเลิกซ่อน",
                { onOff(prefs.showHidden) }) { prefs.showHidden = !prefs.showHidden },
            Opt("แทนที่ปุ่ม Home", "ให้ปุ่ม Home บนรีโมตเปิด launcher นี้เสมอ",
                { onOff(HomeOverrideService.isEnabled(this)) }) { toggleHome() },
            Opt("ล้างรายการเปิดล่าสุด", "ลบประวัติแอปที่เปิดล่าสุดทั้งหมด", { "" }) {
                prefs.clearRecents(); toast("ล้างแล้ว")
            },
            Opt("รีเซ็ตลำดับแอป", "เรียงแอปตามตัวอักษรเหมือนตอนติดตั้งใหม่", { "" }) {
                prefs.resetOrder(); toast("รีเซ็ตแล้ว")
            },
        )

        val container = findViewById<LinearLayout>(R.id.rows)
        val inf = LayoutInflater.from(this)
        opts.forEach { o ->
            val v = inf.inflate(R.layout.item_setting, container, false)
            v.findViewById<TextView>(R.id.title).text = o.title
            v.setOnClickListener { o.onClick(); refresh() }
            v.setOnFocusChangeListener { _, f ->
                v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f).setDuration(120).start()
                if (f) hint.text = o.hint
            }
            container.addView(v)
            rows += o to v
        }
        refresh()
        rows.firstOrNull()?.second?.requestFocus()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() = rows.forEach { (o, v) -> v.findViewById<TextView>(R.id.value).text = o.value() }

    private fun toggleHome() {
        val r = HomeOverrideService.toggle(this)
        if (r != null) { toast(if (r) "ปุ่ม Home จะมาที่ launcher นี้" else "กลับไปใช้ปุ่ม Home เดิม"); return }
        val opened = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
        ).any {
            it.resolveActivity(packageManager) != null &&
                runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("แทนที่ปุ่ม Home")
            .setMessage("รันคำสั่งนี้บนคอมพิวเตอร์ครั้งเดียว แล้วกดอีกครั้ง:\n\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            .setPositiveButton("ตกลง", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
