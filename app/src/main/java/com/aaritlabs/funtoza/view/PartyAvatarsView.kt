package com.aaritlabs.funtoza.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Custom View that renders the 4 overlapping game tokens (Green, Red, Violet, Cyan)
 * with depth rings matching the modern cosmic Party Games mode card design.
 */
class PartyAvatarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private data class Token(
        val outerColor: String,
        val innerColor: String
    )

    private val tokens = listOf(
        Token("#00E676", "#00A850"),
        Token("#FF4765", "#C62842"),
        Token("#C042FF", "#8A1AB8"),
        Token("#00C0FF", "#0088CC")
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Compute safe radius so circles have breathing room from top/bottom bounds
        val radius = (h * 0.40f).coerceAtMost(w / 7.5f)
        val cy = h / 2f

        val startX = radius + paddingLeft + 1f
        val endX = w - radius - paddingRight - 1f
        val step = if (tokens.size > 1) (endX - startX) / (tokens.size - 1) else 0f

        for (i in tokens.indices) {
            val cx = startX + i * step
            val token = tokens[i]

            // Outer ring/token
            fillPaint.color = Color.parseColor(token.outerColor)
            canvas.drawCircle(cx, cy, radius, fillPaint)

            // Inner dark inset circle
            innerPaint.color = Color.parseColor(token.innerColor)
            canvas.drawCircle(cx, cy, radius * 0.45f, innerPaint)
        }
    }
}
