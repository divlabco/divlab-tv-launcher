package co.divlab.tvlauncher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** แถวตั้งค่าสไตล์ tvOS — ใช้ร่วมกันระหว่างหน้าปรับแต่งและ onboarding */
class Row(
    val title: () -> String,
    val hint: () -> String,
    val value: () -> String,
    val choice: Boolean = true,
    val change: (Int) -> Unit,
) {
    lateinit var view: View

    fun refresh() {
        view.findViewById<TextView>(R.id.title).text = title()
        val v = value()
        view.findViewById<TextView>(R.id.value).text = if (choice && v.isNotEmpty()) "‹  $v  ›" else v
    }
}

object Rows {
    /** เติมแถวลง container; onAny = เรียกหลังมีการเปลี่ยนค่า, onHint = แสดงคำอธิบายเมื่อโฟกัส */
    fun build(container: LinearLayout, rows: List<Row>, onAny: () -> Unit, onHint: (String) -> Unit) {
        container.removeAllViews()
        val inf = LayoutInflater.from(container.context)
        rows.forEach { r ->
            val v = inf.inflate(R.layout.item_setting, container, false)
            r.view = v
            v.setOnClickListener { r.change(+1); onAny() }
            v.setOnKeyListener { _, code, e ->
                if (!r.choice || e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                val d = when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> +1
                    else -> return@setOnKeyListener false
                }
                r.change(d); onAny(); true
            }
            v.setOnFocusChangeListener { _, f ->
                v.animate().scaleX(if (f) 1.03f else 1f).scaleY(if (f) 1.03f else 1f).setDuration(120).start()
                if (f) onHint(r.hint())
            }
            container.addView(v)
            r.refresh()
        }
    }

    fun cyc(v: Int, n: Int, d: Int) = ((v + d) % n + n) % n
}
