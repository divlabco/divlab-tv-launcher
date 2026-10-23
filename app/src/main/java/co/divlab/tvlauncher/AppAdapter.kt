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

    var items: List<AppItem> = emptyList()
        private set

    fun submit(list: List<AppItem>) {
        items = list
        notifyDataSetChanged()
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

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = items[position]
        h.icon.setImageDrawable(app.art)
        if (app.isBanner) {
            h.icon.scaleType = ImageView.ScaleType.CENTER_CROP
            h.icon.setPadding(0, 0, 0, 0)
        } else {
            val pad = (22 * h.icon.resources.displayMetrics.density).toInt()
            h.icon.scaleType = ImageView.ScaleType.FIT_CENTER
            h.icon.setPadding(pad, pad, pad, pad)
        }
        h.label.text = app.label
        h.itemView.alpha = if (app.hidden) 0.4f else 1f
        h.tile.contentDescription = app.label
        h.tile.setOnClickListener { onClick(app) }
        h.tile.setOnLongClickListener { onLong(app); true }
        h.tile.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.12f else 1f
            v.animate().scaleX(s).scaleY(s).translationZ(if (focused) 24f else 0f).setDuration(140).start()
            h.label.visibility = if (focused) View.VISIBLE else View.INVISIBLE
            if (focused) onFocus(app)
        }
    }
}
