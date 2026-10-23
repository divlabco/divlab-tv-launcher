package co.divlab.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {
    private lateinit var repo: AppRepo
    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var bg: AuraView
    private lateinit var heroTitle: TextView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var btnHidden: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val colorCache = HashMap<String, IntArray>()
    private var moving: String? = null
    private var showHidden = false
    private var focused: AppItem? = null
    private var firstLoad = true

    private val tick = object : Runnable {
        override fun run() {
            timeView.text = ThaiDate.time()
            dateView.text = ThaiDate.date()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        repo = AppRepo(this)
        bg = findViewById(R.id.bg)
        heroTitle = findViewById(R.id.heroTitle)
        timeView = findViewById(R.id.time)
        dateView = findViewById(R.id.date)
        btnHidden = findViewById(R.id.btnHidden)
        grid = findViewById(R.id.grid)

        adapter = AppAdapter(onFocus = ::showHero, onClick = { launch(it.intent) }, onLong = ::showMenu)
        grid.layoutManager = GridLayoutManager(this, COLUMNS)
        grid.adapter = adapter
        grid.itemAnimator = null
        grid.setHasFixedSize(true)

        findViewById<TextView>(R.id.btnSettings).setOnClickListener { launch(Intent(Settings.ACTION_SETTINGS)) }
        findViewById<TextView>(R.id.btnWifi).setOnClickListener { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        findViewById<TextView>(R.id.btnInput).setOnClickListener { openInputs() }
        findViewById<TextView>(R.id.btnHome).setOnClickListener { setupHomeButton() }
        btnHidden.setOnClickListener {
            showHidden = !showHidden
            btnHidden.text = if (showHidden) "ซ่อนแอปที่ซ่อน" else "แอปที่ซ่อน"
            reload(null)
        }
    }

    override fun onResume() {
        super.onResume()
        reload(focused?.pkg)
        handler.post(tick)
        updateHomeBtn()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* เป็นหน้า Home — ไม่ต้องปิด */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { focused?.let(::showMenu); return true }
        return super.onKeyDown(keyCode, event)
    }

    /** โหมดย้าย: ลูกศรเลื่อนแอปไปทันที, OK/Back = เสร็จ */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val pkg = moving ?: return super.dispatchKeyEvent(e)
        if (e.action != KeyEvent.ACTION_DOWN) return true
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> step(pkg, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> step(pkg, +1)
            KeyEvent.KEYCODE_DPAD_UP -> step(pkg, -COLUMNS)
            KeyEvent.KEYCODE_DPAD_DOWN -> step(pkg, +COLUMNS)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> if (e.repeatCount == 0) endMove()
        }
        return true
    }

    private fun startMove(app: AppItem) {
        moving = app.pkg
        adapter.movingPkg = app.pkg
    }

    private fun step(pkg: String, delta: Int) {
        val i = adapter.indexOf(pkg)
        if (i < 0) return
        val j = (i + delta).coerceIn(0, adapter.itemCount - 1)
        if (i == j) return
        adapter.move(i, j)
        grid.scrollToPosition(j)
        grid.post { grid.findViewHolderForAdapterPosition(j)?.itemView?.findViewById<android.view.View>(R.id.tile)?.requestFocus() }
    }

    private fun endMove() {
        moving = null
        adapter.movingPkg = null
        repo.saveOrder(adapter.items.map { it.pkg })
    }

    private fun reload(focusPkg: String?) {
        adapter.submit(repo.load(showHidden))
        val idx = adapter.items.indexOfFirst { it.pkg == focusPkg }
            .let { if (it < 0 && firstLoad) 0 else it }
        firstLoad = false
        if (idx >= 0) focusAt(idx)
    }

    private fun focusAt(idx: Int) {
        grid.scrollToPosition(idx)
        grid.post { grid.findViewHolderForAdapterPosition(idx)?.itemView?.findViewById<android.view.View>(R.id.tile)?.requestFocus() }
    }

    private fun showHero(app: AppItem) {
        focused = app
        heroTitle.text = app.label
        bg.setColors(colorCache.getOrPut(app.pkg) { colorsOf(app.art) })
    }

    /** ดึงสีเด่นของแอป 3 สี (หลัก, เข้ม, รอง) จากภาพขนาดเล็ก */
    private fun colorsOf(d: Drawable): IntArray {
        val bmp = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable(resources)?.mutate() ?: d
        copy.setBounds(0, 0, 96, 54)
        copy.draw(Canvas(bmp))
        val p = Palette.from(bmp).maximumColorCount(12).generate()
        bmp.recycle()
        val fallback = 0xFF4F46E5.toInt()
        val dom = p.getDominantColor(fallback)
        val main = p.getVibrantColor(p.getLightVibrantColor(dom))
        val dark = p.getDarkVibrantColor(p.getDarkMutedColor(dom))
        val accent = p.getLightVibrantColor(p.getMutedColor(main))
        return intArrayOf(main, dark, accent)
    }

    private fun showMenu(app: AppItem) {
        val items = arrayOf("ย้ายตำแหน่ง", if (app.hidden) "เลิกซ่อน" else "ซ่อนแอปนี้", "ข้อมูลแอป")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startMove(app)
                    1 -> { repo.toggleHidden(app.pkg); reload(if (showHidden) app.pkg else null) }
                    2 -> launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                }
            }
            .show()
    }

    /** เปิดหน้าเลือกแหล่งสัญญาณ (HDMI/ทีวี) — ลองหลายวิธีตามยี่ห้อ */
    private fun openInputs() {
        val candidates = listOfNotNull(
            Intent("com.tcl.tv.action.SOURCE"),
            packageManager.getLeanbackLaunchIntentForPackage("com.tcl.tv"),
            packageManager.getLaunchIntentForPackage("com.tcl.tv"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in candidates) {
            if (i.resolveActivity(packageManager) != null && launch(i, quiet = true)) return
        }
        Toast.makeText(this, "ไม่พบหน้าเลือกแหล่งสัญญาณ", Toast.LENGTH_SHORT).show()
    }

    private fun launch(i: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(this, "เปิดไม่ได้", Toast.LENGTH_SHORT).show(); false
    } catch (e: SecurityException) {
        if (!quiet) Toast.makeText(this, "ไม่มีสิทธิ์เปิด", Toast.LENGTH_SHORT).show(); false
    }

    private fun updateHomeBtn() {
        findViewById<TextView>(R.id.btnHome).text =
            if (HomeOverrideService.isEnabled(this)) "ปุ่ม Home: เปิด" else "ปุ่ม Home: ปิด"
    }

    /** พาไปเปิด service ในหน้า การช่วยเหลือพิเศษ (Accessibility) */
    private fun setupHomeButton() {
        val on = HomeOverrideService.isEnabled(this)
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("แทนที่ปุ่ม Home")
            .setMessage(if (on) "เปิดใช้อยู่แล้ว — ปุ่ม Home จะมาที่ launcher นี้\n\nปิดได้ที่ ตั้งค่า → การช่วยเหลือพิเศษ → divlab Launcher (ปุ่ม Home)"
                else "ไปที่ ตั้งค่า → การช่วยเหลือพิเศษ → divlab Launcher (ปุ่ม Home) → เปิด\n\nจากนั้นกดปุ่ม Home บนรีโมตจะมาที่ launcher นี้เสมอ")
            .setPositiveButton("เปิดหน้าตั้งค่า") { _, _ -> launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .setNegativeButton("ปิด", null)
            .show()
    }

    companion object { private const val COLUMNS = 5 }
}
