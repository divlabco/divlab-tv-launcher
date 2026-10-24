package co.divlab.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** หน้าปรับแต่ง launcher แบบ tvOS */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var hint: TextView
    private lateinit var rows: List<Row>
    private val handler = Handler(Looper.getMainLooper())
    private var latest: Updater.Release? = null
    private var updateState = ""
    private var downloadId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.apply(prefs.language)
        setContentView(R.layout.activity_settings)
        hint = findViewById(R.id.hint)
        findViewById<AuraView>(R.id.bg).setColors(Themes.palettes[if (prefs.theme == 0) 1 else prefs.theme])

        val onOff = { b: Boolean -> if (b) L.t("เปิด", "On") else L.t("ปิด", "Off") }
        val cyc = Rows::cyc
        rows = listOf(
            Row({ L.t("อัปเดต", "Update") },
                { L.t("ตรวจสอบเวอร์ชันใหม่จาก GitHub แล้วติดตั้งทับได้ทันที (ไม่เสียการตั้งค่า)", "Check GitHub for a new version and install it (keeps your settings)") },
                { updateState }, choice = false) { onUpdateClick() },
            Row({ L.t("ภาษา", "Language") }, { L.t("ภาษาของ launcher", "Launcher language") },
                { arrayOf(L.t("ตามระบบ", "System"), "ไทย", "English")[prefs.language] }) {
                prefs.language = cyc(prefs.language, 3, it); L.apply(prefs.language); refreshAll()
            },
            Row({ L.t("ธีม", "Theme") }, { L.t("สีพื้นหลัง: ตามแอปที่เลือก หรือชุดสีคงที่", "Background colours: from the selected app or a fixed palette") },
                { Themes.name(prefs.theme) }) {
                prefs.theme = cyc(prefs.theme, Themes.palettes.size, it)
                findViewById<AuraView>(R.id.bg).setColors(Themes.palettes[if (prefs.theme == 0) 1 else prefs.theme])
            },
            Row({ L.t("รูปทรงไอคอน", "Icon shape") }, { L.t("ความโค้งของมุมไอคอน", "Corner roundness of app tiles") },
                { arrayOf(L.t("เหลี่ยม", "Square"), L.t("มน", "Rounded"), L.t("มนมาก", "Extra round"), L.t("แคปซูล", "Capsule"))[prefs.iconShape] }) {
                prefs.iconShape = cyc(prefs.iconShape, 4, it)
            },
            Row({ L.t("ขนาดไอคอน", "Icon size") }, { L.t("ไอคอนเล็กลง = เห็นแอปได้มากขึ้น", "Smaller tiles fit more apps") },
                { arrayOf(L.t("เล็ก", "Small"), L.t("กลาง", "Medium"), L.t("ใหญ่", "Large"))[prefs.iconSize] }) {
                prefs.iconSize = cyc(prefs.iconSize, 3, it)
            },
            Row({ L.t("จำนวนคอลัมน์", "Columns") }, { L.t("จำนวนแอปต่อแถว (5–8)", "Apps per row (5–8)") },
                { "${prefs.columns}" }) { prefs.columns = 5 + cyc(prefs.columns - 5, 4, it) },
            Row({ L.t("ชื่อแอปใต้ไอคอน", "App names") }, { L.t("แสดงชื่อแอปใต้ไอคอนเมื่อไหร่", "When to show names under tiles") },
                { arrayOf(L.t("ไม่แสดง", "Never"), L.t("เฉพาะที่เลือก", "Focused"), L.t("แสดงตลอด", "Always"))[prefs.labels] }) {
                prefs.labels = cyc(prefs.labels, 3, it)
            },
            Row({ L.t("สภาพอากาศ", "Weather") }, { L.t("แสดงอุณหภูมิข้างวันที่ (Open-Meteo)", "Show temperature next to the date (Open-Meteo)") },
                { if (prefs.weatherCity < 0) L.t("ปิด", "Off") else Weather.cities[prefs.weatherCity].label }) {
                prefs.weatherCity = cyc(prefs.weatherCity + 1, Weather.cities.size + 1, it) - 1
            },
            Row({ L.t("พักหน้าจอ", "Screensaver") }, { L.t("นาฬิกาเต็มจอเมื่อไม่ได้กดรีโมต (หรี่ลงตอนกลางคืน)", "Full-screen clock when idle (dims at night)") },
                { Prefs.SAVER_MINUTES[prefs.saver].let { if (it == 0) L.t("ปิด", "Off") else L.t("$it นาที", "$it min") } }) {
                prefs.saver = cyc(prefs.saver, Prefs.SAVER_MINUTES.size, it)
            },
            Row({ L.t("แถวเปิดล่าสุด", "Recent row") }, { L.t("แอปที่เปิดล่าสุดอยู่บนสุด", "Recently opened apps on top") },
                { onOff(prefs.showRecent) }) { prefs.showRecent = !prefs.showRecent },
            Row({ L.t("ชื่อทีวีและเครือข่าย", "TV name & network") }, { L.t("แสดงใต้นาฬิกา", "Shown under the clock") },
                { onOff(prefs.showStatus) }) { prefs.showStatus = !prefs.showStatus },
            Row({ L.t("ปุ่มจอ PC", "PC Screen button") }, { L.t("ปุ่มเปิด spacedesk / Moonlight ที่แถบด้านบน", "Top-bar button for spacedesk / Moonlight") },
                { onOff(prefs.showScreenBtn) }) { prefs.showScreenBtn = !prefs.showScreenBtn },
            Row({ L.t("ปุ่ม AirPlay", "AirPlay button") }, { L.t("ปุ่มเปิด AirScreen รับภาพจาก iPhone/iPad/Mac (ถ้ายังไม่มีจะพาไปดาวน์โหลด)", "Opens AirScreen to receive from iPhone/iPad/Mac (offers to install it if missing)") },
                { onOff(prefs.showAirplayBtn) }) { prefs.showAirplayBtn = !prefs.showAirplayBtn },
            Row({ L.t("แสดงแอปที่ซ่อน", "Show hidden apps") }, { L.t("แสดงแบบจาง ๆ เพื่อเลิกซ่อน", "Shown dimmed so you can unhide them") },
                { onOff(prefs.showHidden) }) { prefs.showHidden = !prefs.showHidden },
            Row({ L.t("แทนที่ปุ่ม Home", "Replace Home button") }, { L.t("ปุ่ม Home บนรีโมตเปิด launcher นี้ (ต้องเปิดเพื่อใช้ตั้งเวลาปิดด้วย)", "Remote Home opens this launcher (also needed for the sleep timer)") },
                { onOff(HomeOverrideService.isEnabled(this)) }) { toggleHome() },
            Row({ L.t("สลับไปหน้า Home เดิม", "Switch to original Home") },
                { L.t("เปิดหน้า Google TV เดิม — กลับมาได้โดยเปิด divlab Launcher จากรายการแอป", "Open the original Google TV home. Come back by opening divlab Launcher from your apps.") },
                { "" }, choice = false) {
                if (!HomeOverrideService.openStockHome(this)) toast(L.t("ไม่พบหน้า Home เดิม (อาจถูกปิดไว้ด้วย adb)", "Original Home not found (disabled via adb?)"))
            },
            Row({ L.t("แนะนำการใช้งาน", "Setup guide") }, { L.t("เปิดหน้าแนะนำและตั้งค่าสิทธิ์อีกครั้ง", "Run the welcome & permission guide again") },
                { "" }, choice = false) { startActivity(Intent(this, OnboardingActivity::class.java)) },
            Row({ L.t("ล้างรายการเปิดล่าสุด", "Clear recents") }, { L.t("ลบประวัติแอปที่เปิดล่าสุด", "Forget recently opened apps") },
                { "" }, choice = false) { prefs.clearRecents(); toast(L.t("ล้างแล้ว", "Cleared")) },
            Row({ L.t("รีเซ็ตลำดับแอป", "Reset app order") }, { L.t("เรียงแอปตามตัวอักษร", "Sort apps alphabetically") },
                { "" }, choice = false) { prefs.resetOrder(); toast(L.t("รีเซ็ตแล้ว", "Reset")) },
        )
        Rows.build(findViewById<LinearLayout>(R.id.rows), rows, ::refreshAll) { hint.text = it }
        refreshAll()
        rows.first().view.requestFocus()
        checkUpdate()
    }

    override fun onResume() { super.onResume(); refreshAll() }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun refreshAll() {
        findViewById<TextView>(R.id.pageTitle).text = L.t("ปรับแต่ง", "Customize")
        findViewById<TextView>(R.id.version).text = "divlab Launcher " + Updater.currentVersion(this)
        if (::rows.isInitialized) rows.forEach { it.refresh() }
    }

    // ---------- อัปเดต ----------

    private fun checkUpdate() {
        updateState = L.t("กำลังตรวจสอบ…", "Checking…"); refreshAll()
        Thread {
            val r = Updater.fetchLatest()
            runOnUiThread {
                latest = r
                if (r != null) prefs.latestTag = r.tag
                updateState = when {
                    r == null -> L.t("ตรวจสอบไม่ได้", "Couldn't check")
                    Updater.isNewer(r.tag, Updater.currentVersion(this)) -> L.t("มี ${r.tag} · กดเพื่ออัปเดต", "${r.tag} available · press to update")
                    else -> L.t("เป็นเวอร์ชันล่าสุดแล้ว", "Up to date")
                }
                refreshAll()
            }
        }.start()
    }

    private fun onUpdateClick() {
        val r = latest
        if (r == null || !Updater.isNewer(r.tag, Updater.currentVersion(this))) { checkUpdate(); return }
        if (downloadId >= 0) return
        // ไม่เช็ค canRequestPackageInstalls() เพราะ TCL บางรุ่นรายงานผิด — ให้ตัวติดตั้งของระบบถามเอง
        downloadId = Updater.download(this, r)
        pollDownload()
    }

    private fun pollDownload() {
        val (st, pct) = Updater.progress(this, downloadId)
        when (st) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                updateState = L.t("ดาวน์โหลดเสร็จ · กำลังติดตั้ง", "Downloaded · installing")
                if (!Updater.install(this, downloadId)) updateState = L.t("ติดตั้งไม่ได้", "Install failed")
                downloadId = -1
            }
            DownloadManager.STATUS_FAILED -> { updateState = L.t("ดาวน์โหลดไม่สำเร็จ · กดลองใหม่", "Download failed · retry"); downloadId = -1 }
            else -> { updateState = L.t("กำลังดาวน์โหลด $pct%", "Downloading $pct%"); handler.postDelayed(::pollDownload, 700) }
        }
        refreshAll()
    }

    // ---------- ปุ่ม Home ----------

    private fun toggleHome() {
        val r = HomeOverrideService.toggle(this)
        if (r != null) {
            toast(if (r) L.t("ปุ่ม Home จะมาที่ launcher นี้", "Home now opens this launcher") else L.t("กลับไปใช้ปุ่ม Home เดิม", "Home button restored"))
            handler.postDelayed(::refreshAll, 500)
            return
        }
        val opened = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
        ).any {
            it.resolveActivity(packageManager) != null &&
                runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
        if (!opened) AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("แทนที่ปุ่ม Home", "Replace Home button"))
            .setMessage(L.t("รันคำสั่งนี้บนคอมพิวเตอร์ครั้งเดียว แล้วกดอีกครั้ง:", "Run this once from a computer, then press again:") +
                "\n\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            .setPositiveButton("OK", null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
