package co.divlab.tvlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onFocus: (AppItem) -> Unit,
    private val onClick: (AppItem) -> Unit,
    private val onLong: (AppItem) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private val list = ArrayList<AppItem>()
    val items: List<AppItem> get() = list

    /** แอปที่กำลังอยู่ในโหมดย้าย */
    var movingPkg: String? = null
        set(v) { val old = field; field = v; listOf(old, v).forEach { p -> indexOf(p).takeIf { it >= 0 }?.let(::notifyItemChanged) } }

    fun submit(l: List<AppItem>) { list.clear(); list.addAll(l); notifyDataSetChanged() }

    fun indexOf(pkg: String?) = list.indexOfFirst { it.pkg == pkg }

    fun move(from: Int, to: Int) {
        val it = list.removeAt(from); list.add(to, it)
        notifyItemMoved(from, to)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tile: View = v.findViewById(R.id.tile)
        val icon: ImageView = v.findViewById(R.id.icon)
        val label: TextView = v.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return VH(v).also { it.tile.clipToOutline = true }
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = list[position]
        val moving = app.pkg == movingPkg
        h.icon.setImageDrawable(app.art)
        if (app.isBanner) {
            h.icon.scaleType = ImageView.ScaleType.CENTER_CROP
            h.icon.setPadding(0, 0, 0, 0)
        } else {
            val pad = (22 * h.icon.resources.displayMetrics.density).toInt()
            h.icon.scaleType = ImageView.ScaleType.FIT_CENTER
            h.icon.setPadding(pad, pad, pad, pad)
        }
        h.label.text = if (moving) "◀▲▼▶ ย้าย · OK เสร็จ" else app.label
        h.label.visibility = if (moving || h.tile.isFocused) View.VISIBLE else View.INVISIBLE
        h.tile.isSelected = moving
        h.itemView.alpha = if (app.hidden) 0.4f else 1f
        h.tile.contentDescription = app.label
        h.tile.setOnClickListener { onClick(list[h.bindingAdapterPosition]) }
        h.tile.setOnLongClickListener { onLong(list[h.bindingAdapterPosition]); true }
        h.tile.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).translationZ(if (focused) 20f else 0f).setDuration(140).start()
            val pos = h.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnFocusChangeListener
            h.label.visibility = if (focused || list[pos].pkg == movingPkg) View.VISIBLE else View.INVISIBLE
            if (focused) onFocus(list[pos])
        }
    }
}
