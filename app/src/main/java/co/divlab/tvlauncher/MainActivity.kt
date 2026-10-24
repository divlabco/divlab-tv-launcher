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
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.random.Random

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
    private lateinit var saver: View
    private lateinit var saverBg: AuraView

    private val handler = Handler(Looper.getMainLooper())
    private val colorCache = HashMap<String, IntArray>()
    private var focused: AppItem? = null
    private var moving: String? = null
    private var firstLoad = true
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastInput = SystemClock.uptimeMillis()
    private var weather: Weather.Now? = null
    private var weatherAt = 0L
    private var saverStep = 0

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            updateStatus()
            updateSleepPill()
            refreshWeather()
            checkIdle()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        L.apply(prefs.language)
        setContentView(R.layout.activity_main)
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
        saver = findViewById(R.id.saver)
        saverBg = findViewById(R.id.saverBg)

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
        findViewById<View>(R.id.btnSleep).setOnClickListener { showSleepMenu() }
        findViewById<View>(R.id.btnCustomize).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        watchNetwork()
        if (!prefs.onboarded) startActivity(Intent(this, OnboardingActivity::class.java))
        else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    }

    override fun onResume() {
        super.onResume()
        HomeOverrideService.allowStock = false
        L.apply(prefs.language)
        applyPrefs()
        hideSaver()
        reload(focused?.pkg)
        weatherAt = 0
        handler.post(tick)
        checkUpdateInBackground()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
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

    // ---------- การตั้งค่า / ข้อความ ----------

    private fun applyPrefs() {
        layoutManager.spanCount = prefs.columns
        adapter.labelMode = prefs.labels
        adapter.tileHeightPx = (prefs.tileHeightDp() * resources.displayMetrics.density).toInt()
        adapter.cornerPx = prefs.cornerDp() * resources.displayMetrics.density
        statusPill.visibility = if (prefs.showStatus) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnScreen).visibility = if (prefs.showScreenBtn) View.VISIBLE else View.GONE
        if (prefs.theme != 0) bg.setColors(Themes.palettes[prefs.theme])
        findViewById<TextView>(R.id.btnInput).text = L.t("แหล่งสัญญาณ", "Inputs")
        findViewById<TextView>(R.id.btnSettings).text = L.t("ตั้งค่าทีวี", "TV Settings")
        findViewById<TextView>(R.id.btnScreen).text = L.t("จอ PC", "PC Screen")
        updateClock()
    }

    private fun updateClock() {
        timeView.text = ThaiDate.time()
        val w = weather?.takeIf { prefs.weatherCity >= 0 }
        dateView.text = ThaiDate.date() + (w?.let { "  ·  ${it.text}" } ?: "")
    }

    private fun reload(focusPkg: String?) {
        val apps = repo.load(prefs.showHidden)
        val entries = ArrayList<Entry>()
        if (prefs.showRecent) {
            val byPkg = apps.associateBy { it.pkg }
            val recent = prefs.recents().mapNotNull { byPkg[it] }.take(prefs.columns)
            if (recent.isNotEmpty()) {
                entries += Entry.Header(L.t("เปิดล่าสุด", "Recent"))
                recent.forEach { entries += Entry.App(it, true) }
                entries += Entry.Header(L.t("แอปทั้งหมด", "All apps"))
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

    // ---------- ปุ่มรีโมต ----------

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        lastInput = SystemClock.uptimeMillis()
        if (saver.visibility == View.VISIBLE) {
            if (e.action == KeyEvent.ACTION_UP) hideSaver()
            return true
        }
        if (moving == null && e.action == KeyEvent.ACTION_DOWN && e.keyCode == KeyEvent.KEYCODE_DPAD_UP && jumpToTopBar()) return true
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

    /** กดขึ้นจากแถวแรกของ grid → ไปที่ปุ่มด้านบนที่อยู่ใกล้ที่สุด */
    private fun jumpToTopBar(): Boolean {
        val cur = currentFocus ?: return false
        val holder = grid.findContainingViewHolder(cur) ?: return false
        val pos = holder.bindingAdapterPosition
        val entries = adapter.entries
        val e = entries.getOrNull(pos) as? Entry.App ?: return false
        val firstOfSection = entries.indexOfFirst { it is Entry.App && it.recent == e.recent }
        val inFirstRow = pos - firstOfSection < prefs.columns
        if (!inFirstRow || (!e.recent && entries.any { it is Entry.App && it.recent })) return false
        val loc = IntArray(2); cur.getLocationOnScreen(loc)
        val cx = loc[0] + cur.width / 2
        val target = TOP_BUTTONS.map { findViewById<View>(it) }.filter { it.isShown }.minByOrNull {
            it.getLocationOnScreen(loc); kotlin.math.abs(loc[0] + it.width / 2 - cx)
        } ?: return false
        grid.scrollToPosition(0)
        target.requestFocus()
        return true
    }

    // ---------- โหมดย้าย ----------

    private fun startMove(app: AppItem) {
        moving = app.pkg
        adapter.movingText = L.t("◀▲▼▶ ย้าย · OK เสร็จ", "◀▲▼▶ move · OK done")
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
        if (prefs.theme == 0) bg.setColors(colorCache.getOrPut(app.pkg) { colorsOf(app.art) })
    }

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

    // ---------- โหมดพักหน้าจอ ----------

    private fun checkIdle() {
        val min = Prefs.SAVER_MINUTES[prefs.saver.coerceIn(0, Prefs.SAVER_MINUTES.size - 1)]
        if (min == 0 || moving != null || saver.visibility == View.VISIBLE) return
        if (SystemClock.uptimeMillis() - lastInput >= min * 60_000L) showSaver()
    }

    private val saverTick = object : Runnable {
        override fun run() {
            saverStep++
            val pal = if (prefs.theme == 0) Themes.palettes[1 + saverStep % (Themes.palettes.size - 1)]
            else Themes.palettes[prefs.theme].let { p -> IntArray(3) { p[(it + saverStep) % 3] } }
            saverBg.setColors(pal)
            findViewById<TextView>(R.id.saverTime).text = ThaiDate.time()
            findViewById<TextView>(R.id.saverDate).text = ThaiDate.date()
            findViewById<TextView>(R.id.saverWeather).text =
                weather?.takeIf { prefs.weatherCity >= 0 }?.let { "${it.text}  ${Weather.cities[prefs.weatherCity].label}" } ?: ""
            findViewById<View>(R.id.saverDim).animate().alpha(if (ThaiDate.isNight()) 0.45f else 0f).setDuration(2000).start()
            // ขยับนาฬิกาเล็กน้อยกันจอ burn-in
            if (saverStep % 6 == 0) findViewById<View>(R.id.saverClock).animate()
                .translationX(Random.nextInt(-120, 120).toFloat()).translationY(Random.nextInt(-60, 60).toFloat())
                .setDuration(4000).start()
            handler.postDelayed(this, 10_000)
        }
    }

    private fun showSaver() {
        saver.alpha = 0f
        saver.visibility = View.VISIBLE
        saver.animate().alpha(1f).setDuration(800).start()
        handler.post(saverTick)
    }

    private fun hideSaver() {
        handler.removeCallbacks(saverTick)
        if (saver.visibility != View.VISIBLE) return
        saver.animate().alpha(0f).setDuration(250).withEndAction { saver.visibility = View.GONE }.start()
        lastInput = SystemClock.uptimeMillis()
    }

    // ---------- ตั้งเวลาปิด ----------

    private fun showSleepMenu() {
        val svc = HomeOverrideService.instance
        if (svc == null) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(L.t("ตั้งเวลาปิด", "Sleep timer"))
                .setMessage(L.t("ต้องเปิด \"แทนที่ปุ่ม Home\" ในหน้าปรับแต่งก่อน (ใช้บริการเดียวกันในการปิดหน้าจอ)",
                    "Turn on \"Replace Home button\" in Customize first (the same service turns the screen off)."))
                .setPositiveButton(L.t("ไปที่ปรับแต่ง", "Open Customize")) { _, _ -> startActivity(Intent(this, SettingsActivity::class.java)) }
                .setNegativeButton(L.t("ปิด", "Close"), null).show()
            return
        }
        val labels = Prefs.SLEEP_MINUTES.map { m ->
            if (m == 0) L.t("ไม่ตั้งเวลา", "Off") else if (m < 60) L.t("$m นาที", "$m min")
            else L.t("${m / 60}${if (m % 60 > 0) ".5" else ""} ชั่วโมง", "${m / 60}${if (m % 60 > 0) ".5" else ""} h")
        }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(L.t("ปิดทีวีใน…", "Turn off TV in…"))
            .setItems(labels) { _, which ->
                val m = Prefs.SLEEP_MINUTES[which]
                prefs.sleepAt = if (m == 0) 0 else System.currentTimeMillis() + m * 60_000L
                svc.scheduleSleep(prefs.sleepAt)
                updateSleepPill()
            }.show()
    }

    private fun updateSleepPill() {
        val left = prefs.sleepAt - System.currentTimeMillis()
        findViewById<TextView>(R.id.btnSleep).text = if (prefs.sleepAt > 0 && left > 0)
            L.t("⏾ ปิดใน ${(left / 60_000) + 1} นาที", "⏾ Off in ${(left / 60_000) + 1} min")
        else L.t("ตั้งเวลาปิด", "Sleep")
    }

    // ---------- สภาพอากาศ ----------

    private fun refreshWeather() {
        val idx = prefs.weatherCity
        if (idx < 0 || idx >= Weather.cities.size) { weather = null; return }
        if (System.currentTimeMillis() - weatherAt < 30 * 60_000L) return
        weatherAt = System.currentTimeMillis()
        Thread {
            val w = Weather.fetch(Weather.cities[idx]) ?: return@Thread
            runOnUiThread { weather = w; updateClock() }
        }.start()
    }

    // ---------- เมนูแอป / เปิดแอป ----------

    private fun showMenu(app: AppItem) {
        val items = arrayOf(L.t("ย้ายตำแหน่ง", "Move"),
            if (app.hidden) L.t("เลิกซ่อน", "Unhide") else L.t("ซ่อนแอปนี้", "Hide"),
            L.t("ข้อมูลแอป", "App info"))
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
            Toast.makeText(this, L.t("ไม่พบหน้าเลือกแหล่งสัญญาณ", "Input picker not found"), Toast.LENGTH_SHORT).show()
    }

    private fun openScreenShare() {
        val i = listOf("ph.spacedesk.beta", "ph.spacedesk.viewer", "com.limelight")
            .firstNotNullOfOrNull { packageManager.getLeanbackLaunchIntentForPackage(it) ?: packageManager.getLaunchIntentForPackage(it) }
        if (i != null) launch(i)
        else Toast.makeText(this, L.t("ติดตั้ง spacedesk หรือ Moonlight ก่อน", "Install spacedesk or Moonlight first"), Toast.LENGTH_LONG).show()
    }

    private fun launch(i: Intent, quiet: Boolean = false): Boolean = try {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) {
        if (!quiet) Toast.makeText(this, L.t("เปิดไม่ได้", "Can't open"), Toast.LENGTH_SHORT).show(); false
    } catch (e: SecurityException) {
        if (!quiet) Toast.makeText(this, L.t("ไม่มีสิทธิ์เปิด", "Not allowed"), Toast.LENGTH_SHORT).show(); false
    }

    // ---------- อัปเดต ----------

    private fun checkUpdateInBackground() {
        showUpdateBadge()
        if (System.currentTimeMillis() - prefs.lastUpdateCheck < 12 * 3600_000L) return
        prefs.lastUpdateCheck = System.currentTimeMillis()
        Thread {
            val r = Updater.fetchLatest() ?: return@Thread
            prefs.latestTag = r.tag
            runOnUiThread { showUpdateBadge() }
        }.start()
    }

    private fun showUpdateBadge() {
        val has = prefs.latestTag.isNotEmpty() && Updater.isNewer(prefs.latestTag, Updater.currentVersion(this))
        findViewById<TextView>(R.id.btnCustomize).text = L.t("ปรับแต่ง", "Customize") + if (has) "  ●" else ""
    }

    // ---------- เครือข่าย ----------

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
            caps == null -> R.drawable.ic_wifi_off to L.t("ไม่ได้เชื่อมต่อ", "Not connected")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> R.drawable.ic_ethernet to "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> R.drawable.ic_wifi to (wifiName() ?: "Wi-Fi")
            else -> R.drawable.ic_wifi to L.t("เชื่อมต่อแล้ว", "Connected")
        }
        netIcon.setImageResource(icon)
        val offline = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        netName.text = if (offline) "$name · " + L.t("ไม่มีอินเทอร์เน็ต", "No internet") else name
        netIcon.alpha = if (caps == null || offline) 0.5f else 1f
    }

    @Suppress("DEPRECATION")
    private fun wifiName(): String? {
        val wm = applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return wm.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    }

    companion object {
        private val TOP_BUTTONS = intArrayOf(R.id.btnInput, R.id.btnWifi, R.id.btnSettings, R.id.btnScreen, R.id.btnSleep, R.id.btnCustomize)
        val DEFAULT_COLORS = Themes.palettes[1]
    }
}
