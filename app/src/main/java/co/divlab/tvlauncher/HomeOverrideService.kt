package co.divlab.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * แทนที่ปุ่ม Home ของรีโมต:
 * 1) ดักปุ่ม HOME โดยตรง (ถ้าระบบส่ง key มาให้)
 * 2) ถ้าหน้า Home เดิมของ Google TV/TCL เด้งขึ้นมา → เปิด launcher นี้ทับทันที
 */
class HomeOverrideService : AccessibilityService() {
    private var last = 0L

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (e.action == KeyEvent.ACTION_UP) goHome()
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (e.packageName?.toString() in STOCK) goHome()
    }

    override fun onInterrupt() {}

    private fun goHome() {
        val now = SystemClock.uptimeMillis()
        if (now - last < 600) return
        last = now
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    companion object {
        val STOCK = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.tcl.waterfall.overseas",
        )

        /** สลับเปิด/ปิด — คืนค่าสถานะใหม่ หรือ null ถ้ายังไม่ได้สิทธิ์ WRITE_SECURE_SETTINGS */
        fun toggle(ctx: Context): Boolean? {
            if (ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) return null
            val cr = ctx.contentResolver
            val on = isEnabled(ctx)
            val list = (Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
                .split(":").filter { it.isNotBlank() && !it.startsWith("${ctx.packageName}/") }.toMutableList()
            if (!on) list += "${ctx.packageName}/${HomeOverrideService::class.java.name}"
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, if (list.isEmpty()) 0 else 1)
            return !on
        }

        fun isEnabled(ctx: Context): Boolean =
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.contains("${ctx.packageName}/") == true
    }
}
