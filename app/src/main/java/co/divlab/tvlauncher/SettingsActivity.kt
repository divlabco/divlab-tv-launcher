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
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var latest: Updater.Release? = null
    private var updateState = "กำลังตรวจสอบ…"
    private var downloadId = -1L

    /** choice = เปลี่ยนค่าด้วย ◀ ▶ (OK = ถัดไป), action = กด OK เพื่อทำ */
    private class Opt(
        val title: String, val hint: String, val value: () -> String,
        val choice: Boolean = true, val change: (Int) -> Unit,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        hint = findViewById(R.id.hint)
        findViewById<AuraView>(R.id.bg).setColors(MainActivity.DEFAULT_COLORS)
        findViewById<TextView>(R.id.version).text =
            "divlab Launcher " + packageManager.getPackageInfo(packageName, 0).versionName

        val onOff = { b: Boolean -> if (b) "เปิด" else "ปิด" }
        fun cyc(v: Int, n: Int, d: Int) = ((v + d) % n + n) % n
        val opts = listOf(
            Opt("อัปเดต", "ตรวจสอบเวอร์ชันใหม่จาก GitHub แล้วติดตั้งทับได้ทันที (ไม่เสียการตั้งค่า)",
                { updateState }, choice = false) { onUpdateClick() },
            Opt("รูปทรงไอคอน", "เลือกความโค้งของมุมไอคอน",
                { arrayOf("เหลี่ยม", "มน", "มนมาก", "แคปซูล")[prefs.iconShape] }) { prefs.iconShape = cyc(prefs.iconShape, 4, it) },
            Opt("ขนาดไอคอน", "ไอคอนเล็กลง = เห็นแอปได้มากขึ้นในหน้าเดียว",
                { arrayOf("เล็ก", "กลาง", "ใหญ่")[prefs.iconSize] }) { prefs.iconSize = cyc(prefs.iconSize, 3, it) },
            Opt("จำนวนคอลัมน์", "จำนวนแอปต่อแถว (5–8)",
                { "${prefs.columns}" }) { prefs.columns = 5 + cyc(prefs.columns - 5, 4, it) },
            Opt("ชื่อแอปใต้ไอคอน", "เลือกว่าจะแสดงชื่อแอปใต้ไอคอนเมื่อไหร่",
                { arrayOf("ไม่แสดง", "เฉพาะที่เลือก", "แสดงตลอด")[prefs.labels] }) { prefs.labels = cyc(prefs.labels, 3, it) },
            Opt("แถวเปิดล่าสุด", "แสดงแอปที่เปิดล่าสุดไว้บนสุด กดเพื่อกลับไปดูต่อได้ทันที",
                { onOff(prefs.showRecent) }) { prefs.showRecent = !prefs.showRecent },
            Opt("สีพื้นหลังตามแอป", "เปลี่ยนสี gradient พื้นหลังตามแอปที่เลือก",
                { onOff(prefs.appColors) }) { prefs.appColors = !prefs.appColors },
            Opt("ชื่อทีวีและเครือข่าย", "แสดงชื่อทีวีและ Wi-Fi/LAN ที่เชื่อมอยู่ใต้นาฬิกา",
                { onOff(prefs.showStatus) }) { prefs.showStatus = !prefs.showStatus },
            Opt("ปุ่มจอ PC", "แสดงปุ่มเปิด spacedesk / Moonlight ที่แถบด้านบน",
                { onOff(prefs.showScreenBtn) }) { prefs.showScreenBtn = !prefs.showScreenBtn },
            Opt("แสดงแอปที่ซ่อน", "แสดงแอปที่ซ่อนไว้แบบจาง ๆ เพื่อเลิกซ่อน",
                { onOff(prefs.showHidden) }) { prefs.showHidden = !prefs.showHidden },
            Opt("แทนที่ปุ่ม Home", "ให้ปุ่ม Home บนรีโมตเปิด launcher นี้เสมอ",
                { onOff(HomeOverrideService.isEnabled(this)) }) { toggleHome() },
            Opt("ล้างรายการเปิดล่าสุด", "ลบประวัติแอปที่เปิดล่าสุดทั้งหมด", { "" }, choice = false) {
                prefs.clearRecents(); toast("ล้างแล้ว")
            },
            Opt("รีเซ็ตลำดับแอป", "เรียงแอปตามตัวอักษรเหมือนตอนติดตั้งใหม่", { "" }, choice = false) {
                prefs.resetOrder(); toast("รีเซ็ตแล้ว")
            },
        )

        val container = findViewById<LinearLayout>(R.id.rows)
        val inf = LayoutInflater.from(this)
        opts.forEach { o ->
            val v = inf.inflate(R.layout.item_setting, container, false)
            v.findViewById<TextView>(R.id.title).text = o.title
            v.setOnClickListener { o.change(+1); refresh() }
            v.setOnKeyListener { _, code, e ->
                if (!o.choice || e.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val d = when (code) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> +1
                    else -> return@setOnKeyListener false
                }
                o.change(d); refresh(); true
            }
            v.setOnFocusChangeListener { _, f ->
                v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f).setDuration(120).start()
                if (f) hint.text = o.hint
            }
            container.addView(v)
            rows += o to v
        }
        refresh()
        rows.firstOrNull()?.second?.requestFocus()
        checkUpdate()
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun checkUpdate() {
        updateState = "กำลังตรวจสอบ…"; refresh()
        Thread {
            val r = Updater.fetchLatest()
            runOnUiThread {
                latest = r
                if (r != null) prefs.latestTag = r.tag
                updateState = when {
                    r == null -> "ตรวจสอบไม่ได้"
                    Updater.isNewer(r.tag, Updater.currentVersion(this)) -> "มี ${r.tag} · กดเพื่ออัปเดต"
                    else -> "เป็นเวอร์ชันล่าสุดแล้ว"
                }
                refresh()
            }
        }.start()
    }

    private fun onUpdateClick() {
        val r = latest
        if (r == null || !Updater.isNewer(r.tag, Updater.currentVersion(this))) { checkUpdate(); return }
        if (downloadId >= 0) return
        // ไม่เช็ค canRequestPackageInstalls() เพราะ TCL บางรุ่นรายงานผิด — ให้ตัวติดตั้งของระบบถามเองถ้ายังไม่ได้สิทธิ์
        downloadId = Updater.download(this, r)
        pollDownload()
    }

    private fun pollDownload() {
        val (st, pct) = Updater.progress(this, downloadId)
        when (st) {
            android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                updateState = "ดาวน์โหลดเสร็จ · กำลังติดตั้ง"
                if (!Updater.install(this, downloadId)) updateState = "ติดตั้งไม่ได้"
                downloadId = -1
            }
            android.app.DownloadManager.STATUS_FAILED -> { updateState = "ดาวน์โหลดไม่สำเร็จ · กดลองใหม่"; downloadId = -1 }
            else -> { updateState = "กำลังดาวน์โหลด $pct%"; handler.postDelayed(::pollDownload, 700) }
        }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() = rows.forEach { (o, v) ->
        val value = o.value()
        v.findViewById<TextView>(R.id.value).text = if (o.choice && value.isNotEmpty()) "‹  $value  ›" else value
    }

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
