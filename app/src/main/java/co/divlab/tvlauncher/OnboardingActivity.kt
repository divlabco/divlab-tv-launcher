package co.divlab.tvlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView

/** แนะนำการใช้งานครั้งแรก 3 หน้า: ยินดีต้อนรับ → วิธีใช้ → สิทธิ์ */
class OnboardingActivity : Activity() {
    private lateinit var prefs: Prefs
    private var page = 0
    private var rows: List<Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.apply(prefs.language)
        setContentView(R.layout.activity_settings)
        findViewById<AuraView>(R.id.bg).setColors(Themes.palettes[2])
        findViewById<TextView>(R.id.version).apply {
            maxWidth = (360 * resources.displayMetrics.density).toInt()
            textSize = 17f
            setLineSpacing(6f, 1f)
        }
        show(0)
    }

    override fun onResume() { super.onResume(); rows.forEach { it.refresh() } }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        rows.forEach { it.refresh() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (page > 0) show(page - 1) else finishSetup() }

    private fun show(p: Int) {
        page = p
        val title = findViewById<TextView>(R.id.pageTitle)
        val body = findViewById<TextView>(R.id.version)
        val hint = findViewById<TextView>(R.id.hint)
        hint.text = ""
        val next = Row({ L.t("ถัดไป", "Next") }, { "" }, { "→" }, choice = false) { show(page + 1) }
        when (p) {
            0 -> {
                title.text = L.t("ยินดีต้อนรับ", "Welcome")
                body.text = L.t("divlab Launcher — หน้า Home ที่เบา สวย และไม่มีโฆษณาสำหรับทีวีของคุณ",
                    "divlab Launcher — a light, beautiful, ad-free home screen for your TV.")
                rows = listOf(
                    Row({ L.t("ภาษา", "Language") }, { L.t("เปลี่ยนได้ภายหลังในหน้าปรับแต่ง", "You can change this later in Customize") },
                        { arrayOf(L.t("ตามระบบ", "System"), "ไทย", "English")[prefs.language] }) {
                        prefs.language = Rows.cyc(prefs.language, 3, it); L.apply(prefs.language); show(0)
                    },
                    next,
                )
            }
            1 -> {
                title.text = L.t("วิธีใช้", "How it works")
                body.text = L.t(
                    "• กด OK เพื่อเปิดแอป\n• กดค้าง OK → ย้ายตำแหน่ง แล้วใช้ลูกศรจัดเรียง\n• กดค้าง OK → ซ่อนแอปที่ไม่ใช้\n• ปุ่ม \"ปรับแต่ง\" ด้านบน: ธีม รูปทรงไอคอน อากาศ พักหน้าจอ ฯลฯ\n• ในหน้าปรับแต่ง ใช้ ◀ ▶ เปลี่ยนค่า\n• กด Back ค้างบนหน้าแรก = สลับไปหน้า Home เดิม",
                    "• Press OK to open an app\n• Hold OK → Move, then use arrows to reorder\n• Hold OK → Hide apps you don't use\n• \"Customize\" on top: theme, icon shape, weather, screensaver…\n• In Customize, use ◀ ▶ to change values\n• Hold Back on the home screen = switch to the original Home")
                rows = listOf(next)
            }
            else -> {
                title.text = L.t("สิทธิ์การใช้งาน", "Permissions")
                body.text = L.t("เปิดสิทธิ์ที่ต้องการ (ข้ามได้ทั้งหมด)", "Enable what you need (all optional)")
                rows = listOf(
                    Row({ L.t("ชื่อ Wi-Fi", "Wi-Fi name") }, { L.t("Android ต้องใช้สิทธิ์ตำแหน่งเพื่ออ่านชื่อ Wi-Fi — ไม่มีการเก็บตำแหน่ง", "Android requires location permission to read the Wi-Fi name. Location is never stored.") },
                        { if (hasLocation()) "✓" else L.t("กดเพื่ออนุญาต", "Press to allow") }, choice = false) {
                        if (!hasLocation()) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                    },
                    Row({ L.t("แทนที่ปุ่ม Home", "Replace Home button") }, { L.t("ปุ่ม Home บนรีโมตจะเปิด launcher นี้ และใช้ตั้งเวลาปิดทีวีได้", "The remote's Home button opens this launcher; also enables the sleep timer") },
                        { if (HomeOverrideService.isEnabled(this)) "✓" else L.t("กดเพื่อเปิด", "Press to enable") }, choice = false) {
                        if (!HomeOverrideService.isEnabled(this)) enableHome()
                    },
                    Row({ L.t("อัปเดตในแอป", "In-app updates") }, { L.t("อนุญาตให้ launcher ติดตั้งเวอร์ชันใหม่ของตัวเอง", "Allow the launcher to install its own updates") },
                        { if (packageManager.canRequestPackageInstalls()) "✓" else L.t("ตั้งค่า", "Set up") }, choice = false) {
                        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                    },
                    Row({ L.t("เริ่มใช้งาน", "Get started") }, { "" }, { "→" }, choice = false) { finishSetup() },
                )
            }
        }
        Rows.build(findViewById<LinearLayout>(R.id.rows), rows, { rows.forEach { it.refresh() } }) { hint.text = it }
        rows.first().view.requestFocus()
    }

    private fun hasLocation() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun enableHome() {
        if (HomeOverrideService.toggle(this) != null) { rows.forEach { it.refresh() }; return }
        val opened = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
        ).any { it.resolveActivity(packageManager) != null && runCatching { startActivity(it) }.isSuccess }
        if (!opened) AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("แทนที่ปุ่ม Home", "Replace Home button"))
            .setMessage(L.t("ทีวีรุ่นนี้ซ่อนเมนูการช่วยเหลือพิเศษ — รันคำสั่งนี้บนคอมพิวเตอร์ครั้งเดียว แล้วกดอีกครั้ง:",
                "This TV hides the accessibility menu — run this once from a computer, then press again:") +
                "\n\nadb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS")
            .setPositiveButton("OK", null).show()
    }

    private fun finishSetup() {
        prefs.onboarded = true
        finish()
    }
}
