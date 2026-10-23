package co.divlab.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

data class AppItem(
    val pkg: String,
    val label: String,
    val art: Drawable,
    val isBanner: Boolean,
    val intent: Intent,
    val hidden: Boolean,
)

/** อ่านรายการแอป TV และจำลำดับ/การซ่อนไว้ใน SharedPreferences */
class AppRepo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load(includeHidden: Boolean): List<AppItem> {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val hidden = prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()
        val seen = HashSet<String>()
        val all = pm.queryIntentActivities(query, 0).mapNotNull { ri ->
            val ai = ri.activityInfo
            if (ai.packageName == ctx.packageName || !seen.add(ai.packageName)) return@mapNotNull null
            val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
            AppItem(
                pkg = ai.packageName,
                label = ri.loadLabel(pm).toString(),
                art = banner ?: ri.loadIcon(pm),
                isBanner = banner != null,
                intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ai.packageName, ai.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                hidden = ai.packageName in hidden,
            )
        }
        val order = order()
        val sorted = all.sortedWith(compareBy<AppItem>(
            { order.indexOf(it.pkg).let { i -> if (i < 0) Int.MAX_VALUE else i } },
            { it.label.lowercase() },
        ))
        return if (includeHidden) sorted else sorted.filterNot { it.hidden }
    }

    /** บันทึกลำดับใหม่ของแอปที่มองเห็น (แอปอื่นต่อท้ายตามลำดับเดิม) */
    fun saveOrder(visible: List<String>) {
        val rest = order().filterNot { it in visible }
        prefs.edit().putString(KEY_ORDER, (visible + rest).joinToString(",")).apply()
    }

    fun toggleHidden(pkg: String) {
        val set = HashSet(prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())
        if (!set.add(pkg)) set.remove(pkg)
        prefs.edit().putStringSet(KEY_HIDDEN, set).apply()
    }

    private fun order(): List<String> =
        prefs.getString(KEY_ORDER, "")!!.split(",").filter { it.isNotEmpty() }

    companion object {
        private const val KEY_ORDER = "order"
        private const val KEY_HIDDEN = "hidden"
    }
}
