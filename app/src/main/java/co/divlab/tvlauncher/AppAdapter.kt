package co.divlab.tvlauncher

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class Entry {
    data class Header(val title: String) : Entry()
    data class App(val app: AppItem, val recent: Boolean) : Entry()
}

class AppAdapter(
    private val onFocus: (AppItem) -> Unit,
    private val onClick: (AppItem) -> Unit,
    private val onLong: (AppItem) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val list = ArrayList<Entry>()
    val entries: List<Entry> get() = list

    /** 0 = ไม่แสดง, 1 = เฉพาะที่เลือก, 2 = ตลอด */
    var labelMode = 1
    var tileHeightPx = 0

    var movingPkg: String? = null
        set(v) {
            val old = field; field = v
            listOf(old, v).forEach { p -> indexOf(p).takeIf { it >= 0 }?.let(::notifyItemChanged) }
        }

    fun submit(l: List<Entry>) { list.clear(); list.addAll(l); notifyDataSetChanged() }

    /** ตำแหน่งของแอปในส่วน "แอปทั้งหมด" */
    fun indexOf(pkg: String?) = list.indexOfFirst { it is Entry.App && !it.recent && it.app.pkg == pkg }
    fun allRange(): IntRange {
        val a = list.indexOfFirst { it is Entry.App && !it.recent }
        return if (a < 0) IntRange.EMPTY else a until list.size
    }
    fun allApps() = list.filterIsInstance<Entry.App>().filterNot { it.recent }.map { it.app }

    fun move(from: Int, to: Int) {
        val e = list.removeAt(from); list.add(to, e)
        notifyItemMoved(from, to)
    }

    fun isHeader(pos: Int) = list.getOrNull(pos) is Entry.Header

    class AppVH(v: View) : RecyclerView.ViewHolder(v) {
        val tile: View = v.findViewById(R.id.tile)
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    class HeaderVH(val text: TextView) : RecyclerView.ViewHolder(text)

    override fun getItemViewType(position: Int) = if (list[position] is Entry.Header) 0 else 1
    override fun getItemCount() = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val d = parent.resources.displayMetrics.density
        if (viewType == 0) {
            val t = TextView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding((9 * d).toInt(), (6 * d).toInt(), 0, (2 * d).toInt())
                setTextColor(0xB3FFFFFF.toInt()); textSize = 12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                letterSpacing = 0.04f
            }
            return HeaderVH(t)
        }
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppVH(v).also { it.tile.clipToOutline = true }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val e = list[position]
        if (holder is HeaderVH && e is Entry.Header) { holder.text.text = e.title; return }
        val h = holder as AppVH
        val app = (e as Entry.App).app
        val moving = !e.recent && app.pkg == movingPkg
        if (tileHeightPx > 0 && h.tile.layoutParams.height != tileHeightPx) {
            h.tile.layoutParams = h.tile.layoutParams.apply { height = tileHeightPx }
        }
        h.icon.setImageDrawable(app.art)
        if (app.isBanner) {
            h.icon.scaleType = ImageView.ScaleType.CENTER_CROP
            h.icon.setPadding(0, 0, 0, 0)
        } else {
            val pad = (tileHeightPx * 0.2f).toInt()
            h.icon.scaleType = ImageView.ScaleType.FIT_CENTER
            h.icon.setPadding(pad, pad, pad, pad)
        }
        h.label.text = if (moving) "◀▲▼▶ ย้าย · OK เสร็จ" else app.label
        applyLabel(h, moving, h.tile.isFocused)
        h.tile.isSelected = moving
        h.itemView.alpha = if (app.hidden) 0.4f else 1f
        h.tile.contentDescription = app.label
        h.tile.setOnClickListener { cur(h)?.let(onClick) }
        h.tile.setOnLongClickListener { cur(h)?.let(onLong); true }
        h.tile.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).translationZ(if (focused) 20f else 0f).setDuration(140).start()
            val a = cur(h) ?: return@setOnFocusChangeListener
            applyLabel(h, a.pkg == movingPkg, focused)
            if (focused) onFocus(a)
        }
    }

    private fun cur(h: RecyclerView.ViewHolder): AppItem? =
        (list.getOrNull(h.bindingAdapterPosition) as? Entry.App)?.app

    private fun applyLabel(h: AppVH, moving: Boolean, focused: Boolean) {
        h.label.visibility = when {
            moving -> View.VISIBLE
            labelMode == 0 -> View.GONE
            labelMode == 2 -> View.VISIBLE
            focused -> View.VISIBLE
            else -> View.INVISIBLE
        }
    }
}
