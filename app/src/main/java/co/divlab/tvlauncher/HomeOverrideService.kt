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
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepRun = Runnable {
        Prefs(this).sleepAt = 0
        // ปิดหน้าจอ / เข้าสู่ standby
        if (android.os.Build.VERSION.SDK_INT >= 28) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        scheduleSleep(Prefs(this).sleepAt)
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRun)
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** ตั้งเวลาปิดทีวี (0 = ยกเลิก) */
    fun scheduleSleep(at: Long) {
        handler.removeCallbacks(sleepRun)
        val delay = at - System.currentTimeMillis()
        if (at > 0 && delay > 0) handler.postDelayed(sleepRun, delay)
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (e.action == KeyEvent.ACTION_UP) goHome()
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!allowStock && e.packageName?.toString() in STOCK) goHome()
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
        @Volatile var instance: HomeOverrideService? = null

        /** true = ปล่อยให้อยู่หน้า Home เดิมได้ (รีเซ็ตเมื่อกลับมาเปิด launcher นี้) */
        @Volatile var allowStock = false

        /** สลับไปหน้า Home เดิมชั่วคราว — คืน false ถ้าเปิดไม่ได้ */
        fun openStockHome(ctx: Context): Boolean {
            allowStock = true
            for (pkg in STOCK) {
                val i = ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
                    ?: ctx.packageManager.getLaunchIntentForPackage(pkg)
                    ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
                        .takeIf { it.resolveActivity(ctx.packageManager) != null }
                    ?: continue
                if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
            }
            allowStock = false
            return false
        }

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
