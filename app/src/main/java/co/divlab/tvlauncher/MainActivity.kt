package co.divlab.tvlauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var adapter: AppAdapter
    private lateinit var layoutManager: GridLayoutManager
    private lateinit var grid: RecyclerView
    private lateinit var bg: AuraView
    private lateinit var heroTitle: TextView
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var statusPill: View
    private lateinit var netIcon: ImageView
    private lateinit var netName: TextView
    private lateinit var tvName: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val colorCache = HashMap<String, IntArray>()
    private var focused: AppItem? = null
    private var moving: String? = null
    private var firstLoad = true
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    private val tick = object : Runnable {
        override fun run() {
            timeView.text = ThaiDate.time()
            dateView.text = ThaiDate.date()
            updateStatus()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        repo = AppRepo(this)
        bg = findViewById(R.id.bg)
        heroTitle = findViewById(R.id.heroTitle)
        timeView = findViewById(R.id.time)
        dateView = findViewById(R.id.date)
        statusPill = findViewById(R.id.statusPill)
        netIcon = findViewById(R.id.netIcon)
        netName = findViewById(R.id.netName)
        tvName = findViewById(R.id.tvName)
        grid = findViewById(R.id.grid)

        adapter = AppAdapter(onFocus = ::showHero, onClick = ::open, onLong = ::showMenu)
        layoutManager = GridLayoutManager(this, prefs.columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (adapter.isHeader(position)) spanCount else 1
            }
        }
        grid.layoutManager = layoutManager
        grid.adapter = adapter
        grid.itemAnimator = null

        findViewById<View>(R.id.btnInput).setOnClickListener { openInputs() }
        findViewById<View>(R.id.btnWifi).setOnClickListener { launch(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { launch(Intent(Settings.ACTION_SETTINGS)) }
        findViewById<View>(R.id.btnScreen).setOnClickListener { openScreenShare() }
        findViewById<View>(R.id.btnCustomize).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        watchNetwork()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1) // สำหรับอ่านชื่อ Wi-Fi
    }

    override fun onResume() {
        super.onResume()
        applyPrefs()
        reload(focused?.pkg)
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        if (moving != null) endMove()
    }

    override fun onDestroy() {
        netCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* หน้า Home — ไม่ปิด */ }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) { focused?.let(::showMenu); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }

    // ---------- การตั้งค่า ----------

    private fun applyPrefs() {
        layoutManager.spanCount = prefs.columns
        adapter.labelMode = prefs.labels
        adapter.tileHeightPx = (prefs.tileHeightDp() * resources.displayMetrics.density).toInt()
        statusPill.visibility = if (prefs.showStatus) View.VISIBLE else View.GONE
        if (!prefs.appColors) bg.setColors(DEFAULT_COLORS)
    }

    private fun reload(focusPkg: String?) {
        val apps = repo.load(prefs.showHidden)
        val entries = ArrayList<Entry>()
        if (prefs.showRecent) {
            val byPkg = apps.associateBy { it.pkg }
            val recent = prefs.recents().mapNotNull { byPkg[it] }.take(prefs.columns)
            if (recent.isNotEmpty()) {
                entries += Entry.Header("เปิดล่าสุด")
                recent.forEach { entries += Entry.App(it, true) }
                entries += Entry.Header("แอปทั้งหมด")
            }
        }
        apps.forEach { entries += Entry.App(it, false) }
        adapter.submit(entries)

        var idx = if (focusPkg != null) entries.indexOfFirst { it is Entry.App && it.app.pkg == focusPkg } else -1
        if (idx < 0 && firstLoad) idx = entries.indexOfFirst { it is Entry.App }
        firstLoad = false
        if (idx >= 0) focusAt(idx)
    }

    private fun focusAt(idx: Int) {
        grid.scrollToPosition(idx)
        grid.post { grid.findViewHolderForAdapterPosition(idx)?.itemView?.findViewById<View>(R.id.tile)?.requestFocus() }
    }

    // ---------- โหมดย้าย ----------

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val pkg = moving ?: return super.dispatchKeyEvent(e)
        if (e.action != KeyEvent.ACTION_DOWN) return true
        val cols = prefs.columns
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> step(pkg, -1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> step(pkg, +1)
            KeyEvent.KEYCODE_DPAD_UP -> step(pkg, -cols)
            KeyEvent.KEYCODE_DPAD_DOWN -> step(pkg, +cols)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> if (e.repeatCount == 0) endMove()
        }
        return true
    }

    private fun startMove(app: AppItem) {
        moving = app.pkg
        adapter.movingPkg = app.pkg
        adapter.indexOf(app.pkg).takeIf { it >= 0 }?.let(::focusAt)
    }

    private fun step(pkg: String, delta: Int) {
        val i = adapter.indexOf(pkg)
        val range = adapter.allRange()
        if (i < 0 || range.isEmpty()) return
        val j = (i + delta).coerceIn(range.first, range.last)
        if (i == j) return
        adapter.move(i, j)
        focusAt(j)
    }

    private fun endMove() {
        moving = null
        adapter.movingPkg = null
        repo.saveOrder(adapter.allApps().map { it.pkg })
    }

    // ---------- พื้นหลัง ----------

    private fun showHero(app: AppItem) {
        focused = app
        heroTitle.text = app.label
        if (prefs.appColors) bg.setColors(colorCache.getOrPut(app.pkg) { colorsOf(app.art) })
    }

    /** ดึงสีเด่น 3 สี (หลัก, เข้ม, รอง) จากภาพขนาดเล็ก */
    private fun colorsOf(d: Drawable): IntArray {
        val bmp = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888)
        val copy = d.constantState?.newDrawable(resources)?.mutate() ?: d
        copy.setBounds(0, 0, 96, 54)
        copy.draw(Canvas(bmp))
        val p = Palette.from(bmp).maximumColorCount(12).generate()
        bmp.recycle()
        val dom = p.getDominantColor(DEFAULT_COLORS[0])
        val main = p.getVibrantColor(p.getLightVibrantColor(dom))
        val dark = p.getDarkVibrantColor(p.getDarkMutedColor(dom))
        val accent = p.getLightVibrantColor(p.getMutedColor(main))
        return intArrayOf(main, dark, accent)
    }

    // ---------- เมนูแอป ----------

    private fun showMenu(app: AppItem) {
        val items = arrayOf("ย้ายตำแหน่ง", if (app.hidden) "เลิกซ่อน" else "ซ่อนแอปนี้", "ข้อมูลแอป")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.label)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startMove(app)
                    1 -> { repo.toggleHidden(app.pkg); reload(if (prefs.showHidden) app.pkg else null) }
                    2 -> launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                }
            }
            .show()
    }

    private fun open(app: AppItem) {
        if (launch(app.intent)) prefs.pushRecent(app.pkg)
    }

    private fun openInputs() {
        val candidates = listOfNotNull(
            Intent("com.tcl.tv.action.SOURCE"),
            packageManager.getLeanbackLaunchIntentForPackage("com.tcl.tv"),
            packageManager.getLaunchIntentForPackage("com.tcl.tv"),
            Intent(Settings.ACTION_SETTINGS),
        )
        if (candidates.none { it.resolveActivity(packageManager) != null && launch(it, quiet = true) })
            Toast.makeText(this, "ไม่พบหน้าเลือกแหล่งสัญญาณ", Toast.LENGTH_SHORT).show()
    }

    private fun openScreenShare() {
        val i = listOf("ph.spacedesk.beta", "ph.spacedesk.viewer", "com.limelight")
            .firstNotNullOfOrNull { packageManager.getLeanbackLaunchIntentForPackage(it) ?: packageManager.getLaunchIntentForPackage(it) }
        if (i != null) launch(i) else Toast.makeText(this, "ติดตั้ง spacedesk หรือ Moonlight ก่อน", Toast.LENGTH_LONG).show()
    }

    private fun launch(i: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(this, "เปิดไม่ได้", Toast.LENGTH_SHORT).show(); false
    } catch (e: SecurityException) {
        if (!quiet) Toast.makeText(this, "ไม่มีสิทธิ์เปิด", Toast.LENGTH_SHORT).show(); false
    }

    // ---------- สถานะเครือข่าย ----------

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) { runOnUiThread { updateStatus() } }
            override fun onLost(n: Network) { runOnUiThread { updateStatus() } }
        }
        try { cm.registerDefaultNetworkCallback(cb); netCallback = cb } catch (_: Exception) {}
    }

    private fun updateStatus() {
        if (!prefs.showStatus) return
        tvName.text = Settings.Global.getString(contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val (icon, name) = when {
            caps == null -> R.drawable.ic_wifi_off to "ไม่ได้เชื่อมต่อ"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.drawable.ic_ethernet to "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.drawable.ic_wifi to (wifiName() ?: "Wi-Fi")
            else -> R.drawable.ic_wifi to "เชื่อมต่อแล้ว"
        }
        netIcon.setImageResource(icon)
        val offline = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        netName.text = if (offline) "$name · ไม่มีอินเทอร์เน็ต" else name
        netIcon.alpha = if (caps == null || offline) 0.5f else 1f
    }

    @Suppress("DEPRECATION")
    private fun wifiName(): String? {
        val wm = applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return wm.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    companion object {
        val DEFAULT_COLORS = intArrayOf(0xFF4F46E5.toInt(), 0xFF1F2937.toInt(), 0xFF0EA5E9.toInt())
    }
}
