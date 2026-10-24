package co.divlab.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** เมนูเลือกแหล่งสัญญาณแบบ dropdown ใต้ปุ่ม: HDMI (พร้อมสถานะเชื่อมต่อ), AV, ทีวี */
class InputsMenu(private val act: Activity, private val fallback: () -> Unit) {
    private val d = act.resources.displayMetrics.density
    private var popup: PopupWindow? = null

    private data class Src(val id: String?, val label: String, val icon: Int, val connected: Boolean?, val group: Int)

    private fun sources(): List<Src> {
        val tim = act.getSystemService(TvInputManager::class.java) ?: return emptyList()
        val list = runCatching { tim.tvInputList }.getOrNull() ?: return emptyList()
        val out = ArrayList<Src>()
        for (info in list) {
            if (!info.isPassthroughInput || info.parentId != null || runCatching { info.isHidden(act) }.getOrDefault(false)) continue
            val hdmi = info.type == TvInputInfo.TYPE_HDMI
            val state = runCatching { tim.getInputState(info.id) }.getOrNull()
            out += Src(info.id, info.loadLabel(act).toString(), if (hdmi) R.drawable.ic_hdmi else R.drawable.ic_av,
                if (hdmi && state != null) state == TvInputManager.INPUT_STATE_CONNECTED else null, if (hdmi) 0 else 1)
        }
        out.sortWith(compareBy({ it.group }, { it.label.lowercase() }))
        if (out.isNotEmpty() || list.any { it.type == TvInputInfo.TYPE_TUNER })
            out += Src(null, L.t("ทีวี (Live TV)", "Live TV"), R.drawable.ic_live_tv, null, 2)
        return out
    }

    fun show(anchor: View) {
        val src = sources()
        if (src.isEmpty()) { fallback(); return }
        val card = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = act.getDrawable(R.drawable.menu_card)
            val p = (6 * d).toInt(); setPadding(p, p, p, p)
        }
        val inf = LayoutInflater.from(act)
        var lastGroup = -1
        var first: View? = null
        for (s in src) {
            if (lastGroup >= 0 && s.group != lastGroup) card.addView(View(act).apply {
                setBackgroundColor(0x26FFFFFF)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * d).toInt())
                    .apply { val m = (16 * d).toInt(); setMargins(m, (6 * d).toInt(), m, (6 * d).toInt()) }
            })
            lastGroup = s.group
            val row = inf.inflate(R.layout.item_input, card, false)
            row.findViewById<ImageView>(R.id.icon).setImageResource(s.icon)
            row.findViewById<TextView>(R.id.label).text = s.label
            if (s.connected == true) {
                row.findViewById<View>(R.id.dot).visibility = View.VISIBLE
                row.findViewById<TextView>(R.id.state).text = L.t("เชื่อมต่อแล้ว", "Connected")
            }
            row.setOnClickListener { popup?.dismiss(); open(s) }
            card.addView(row)
            if (first == null) first = row
        }
        popup = PopupWindow(card, (340 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 24 * d
            isOutsideTouchable = true
            showAsDropDown(anchor, 0, (10 * d).toInt(), Gravity.START)
        }
        first?.requestFocus()
    }

    private fun open(s: Src) {
        val pm = act.packageManager
        val tvApp = "com.tcl.tv"
        if (s.id == null) {
            val i = pm.getLeanbackLaunchIntentForPackage(tvApp) ?: pm.getLaunchIntentForPackage(tvApp)
            if (i == null || !start(i)) fallback()
            return
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(s.id)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
        // เล็งไปที่แอปทีวีก่อน กันมีหลายแอปรับ URI นี้แล้วขึ้นหน้าเลือกแอป
        if (start(Intent(view).setPackage(tvApp))) return
        if (start(view)) return
        if (!start(Intent(Intent.ACTION_VIEW, uri))) fallback()
    }

    private fun start(i: Intent) = runCatching { act.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
