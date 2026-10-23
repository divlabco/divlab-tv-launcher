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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {
    private lateinit var repo: AppRepo
    private lateinit var adapter: AppAdapter
    private lateinit var grid: RecyclerView
    private lateinit var bg: ImageView
    private lateinit var heroTitle: TextView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var btnHidden: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val blurCache = HashMap<String, Bitmap>()
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
        val bmp = blurCache.getOrPut(app.pkg) { tinyBitmap(app.art) }
        bg.animate().cancel()
        bg.alpha = 0.6f
        bg.setImageBitmap(bmp)
        bg.animate().alpha(1f).setDuration(250).start()
    }

    /** ย่อภาพให้เล็กมาก แล้วให้ ImageView ขยาย = เบลอแบบประหยัดแรง (ไม่ต้องใช้ RenderEffect) */
    private fun tinyBitmap(d: Drawable): Bitmap {
        val src = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable(resources)?.mutate() ?: d
        copy.setBounds(0, 0, 160, 90)
        copy.draw(Canvas(src))
        val small = Bitmap.createScaledBitmap(src, 16, 9, true)
        src.recycle()
        return small
    }

    private fun showMenu(app: AppItem) {
        val items = arrayOf("เลื่อนไปซ้าย", "เลื่อนไปขวา", if (app.hidden) "เลิกซ่อน" else "ซ่อนแอปนี้", "ข้อมูลแอป")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { repo.move(app.pkg, -1, showHidden); reload(app.pkg) }
                    1 -> { repo.move(app.pkg, +1, showHidden); reload(app.pkg) }
                    2 -> { repo.toggleHidden(app.pkg); reload(if (showHidden) app.pkg else null) }
                    3 -> launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
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

    companion object { private const val COLUMNS = 5 }
}
