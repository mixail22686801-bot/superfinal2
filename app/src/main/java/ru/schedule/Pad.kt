package ru.schedule

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

// Полная палитра: сверху квадрат насыщенность/яркость, снизу полоса оттенков
class Pad(c: Context, color: Int, private val onPick: (Int) -> Unit) : View(c) {
    private val hsv = FloatArray(3)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density
    private var mode = 0

    init { set(color) }

    fun set(color: Int) {
        Color.colorToHSV(color, hsv)
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (240 * d).toInt())

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val sq = height - 40 * d
        val hue = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        p.style = Paint.Style.FILL
        p.shader = ComposeShader(
            LinearGradient(0f, 0f, 0f, sq, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP),
            LinearGradient(0f, 0f, w, 0f, Color.WHITE, hue, Shader.TileMode.CLAMP),
            PorterDuff.Mode.MULTIPLY
        )
        cv.drawRoundRect(0f, 0f, w, sq, 12 * d, 12 * d, p)
        val top = sq + 12 * d
        p.shader = LinearGradient(
            0f, 0f, w, 0f,
            IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f)) }, null, Shader.TileMode.CLAMP
        )
        cv.drawRoundRect(0f, top, w, top + 28 * d, 14 * d, 14 * d, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        val x = hsv[1] * w
        val y = (1 - hsv[2]) * sq
        p.strokeWidth = 4 * d; p.color = Color.BLACK; cv.drawCircle(x, y, 11 * d, p)
        p.strokeWidth = 2.5f * d; p.color = Color.WHITE; cv.drawCircle(x, y, 11 * d, p)
        val hx = hsv[0] / 360f * w
        p.strokeWidth = 4 * d; p.color = Color.BLACK
        cv.drawRoundRect(hx - 5 * d, top - 3 * d, hx + 5 * d, top + 31 * d, 5 * d, 5 * d, p)
        p.strokeWidth = 2.5f * d; p.color = Color.WHITE
        cv.drawRoundRect(hx - 5 * d, top - 3 * d, hx + 5 * d, top + 31 * d, 5 * d, 5 * d, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val sq = height - 40 * d
        if (e.action == MotionEvent.ACTION_DOWN) {
            mode = if (e.y <= sq + 6 * d) 0 else 1
            parent.requestDisallowInterceptTouchEvent(true)
        }
        val fx = (e.x / width).coerceIn(0f, 1f)
        if (mode == 0) {
            hsv[1] = fx
            hsv[2] = (1 - e.y / sq).coerceIn(0f, 1f)
        } else {
            hsv[0] = fx * 359.99f
        }
        invalidate()
        onPick(Color.HSVToColor(hsv))
        return true
    }
}
