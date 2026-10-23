package co.divlab.tvlauncher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** พื้นหลังแบบ mesh gradient นุ่ม ๆ จากสีของแอป (glass aura) — เปลี่ยนสีแบบ crossfade */
class AuraView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val ev = ArgbEvaluator()
    private var from = intArrayOf(0xFF4F46E5.toInt(), 0xFF1F2937.toInt(), 0xFF0EA5E9.toInt())
    private var to = from.clone()
    private var t = 1f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 450
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    /** colors = [หลัก, เข้ม, รอง] */
    fun setColors(colors: IntArray) {
        from = IntArray(3) { c(it) }
        to = colors
        anim.cancel(); t = 0f; anim.start()
    }

    private fun c(i: Int) = ev.evaluate(t, from[i], to[i]) as Int
    private fun alpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    private fun darken(c: Int, f: Float) =
        Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w == 0f) return
        val main = c(0); val dark = c(1); val accent = c(2)
        paint.shader = LinearGradient(0f, 0f, w, h, darken(dark, 0.55f), Color.BLACK, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        blob(canvas, w * 0.20f, h * 0.20f, w * 0.60f, main, 210, w, h)
        blob(canvas, w * 0.88f, h * 0.10f, w * 0.45f, accent, 170, w, h)
        blob(canvas, w * 0.65f, h * 0.95f, w * 0.55f, dark, 160, w, h)
        blob(canvas, w * 0.05f, h * 0.95f, w * 0.35f, accent, 90, w, h)
        paint.shader = null
    }

    private fun blob(cv: Canvas, x: Float, y: Float, r: Float, color: Int, a: Int, w: Float, h: Float) {
        paint.shader = RadialGradient(x, y, r, intArrayOf(alpha(color, a), alpha(color, a / 3), alpha(color, 0)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, paint)
    }
}
