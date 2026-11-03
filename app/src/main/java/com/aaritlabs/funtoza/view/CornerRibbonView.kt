package com.aaritlabs.funtoza.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * Custom View that renders a pixel-perfect diagonal corner ribbon badge
 * (e.g. "NEW!") that is cleanly flush and symmetrically clipped to the card's 22dp rounded corner.
 */
class CornerRibbonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bannerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A0F2E")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val bannerPath = Path()
    private val clipPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Clip to top-right rounded corner matching the card's 22dp radius
        val cornerRadius = 22f * resources.displayMetrics.density
        clipPath.reset()
        val radii = floatArrayOf(
            0f, 0f,                      // top-left
            cornerRadius, cornerRadius,  // top-right
            0f, 0f,                      // bottom-right
            0f, 0f                       // bottom-left
        )
        clipPath.addRoundRect(RectF(0f, 0f, w, h), radii, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 2. Geometry: 45-degree diagonal band passing symmetrically between top & right edges
        val ribbonStart = w * 0.15f
        val ribbonEnd = w * 0.85f

        bannerPath.reset()
        bannerPath.moveTo(w - ribbonEnd, 0f)
        bannerPath.lineTo(w - ribbonStart, 0f)
        bannerPath.lineTo(w, ribbonStart)
        bannerPath.lineTo(w, ribbonEnd)
        bannerPath.close()

        bannerPaint.shader = LinearGradient(
            w - ribbonEnd, 0f,
            w, ribbonEnd,
            Color.parseColor("#FFD54F"),
            Color.parseColor("#FF9800"),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(bannerPath, bannerPaint)

        // 3. Draw rotated "NEW!" text along the center of the diagonal banner
        val midX = w - (ribbonEnd + ribbonStart) * 0.25f
        val midY = (ribbonEnd + ribbonStart) * 0.25f

        textPaint.textSize = h * 0.18f
        textPaint.letterSpacing = 0.08f

        canvas.save()
        canvas.rotate(45f, midX, midY)
        val fontMetrics = textPaint.fontMetrics
        val textY = midY - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText("NEW!", midX, textY, textPaint)
        canvas.restore()
    }
}
