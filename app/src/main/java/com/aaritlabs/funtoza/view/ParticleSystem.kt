package com.aaritlabs.funtoza.view

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Int,
    var radius: Float,
    var alpha: Float = 1.0f,
    var life: Float = 1.0f,
    var decay: Float = 0.02f
) {
    fun update(): Boolean {
        x += vx
        y += vy
        vx *= 0.96f
        vy *= 0.96f
        alpha -= decay
        return alpha > 0f
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val originalAlpha = paint.alpha
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawCircle(x, y, radius, paint)
        paint.alpha = originalAlpha
    }
}

class ParticleSystem {
    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    fun spawnExplosion(x: Float, y: Float, color: Int, count: Int = 30) {
        for (i in 0 until count) {
            val angle = random.nextDouble(0.0, Math.PI * 2)
            val speed = random.nextDouble(4.0, 16.0).toFloat()
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    color = color,
                    radius = random.nextDouble(4.0, 12.0).toFloat(),
                    decay = random.nextDouble(0.02, 0.05).toFloat()
                )
            )
        }
    }

    fun spawnConfetti(width: Float, height: Float, colors: List<Int>, count: Int = 80) {
        for (i in 0 until count) {
            val px = random.nextDouble(0.0, width.toDouble()).toFloat()
            val py = random.nextDouble(-50.0, (height / 2).toDouble()).toFloat()
            val color = colors[random.nextInt(colors.size)]
            particles.add(
                Particle(
                    x = px,
                    y = py,
                    vx = random.nextDouble(-3.0, 3.0).toFloat(),
                    vy = random.nextDouble(3.0, 8.0).toFloat(),
                    color = color,
                    radius = random.nextDouble(6.0, 14.0).toFloat(),
                    decay = 0.008f
                )
            )
        }
    }

    fun update() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            if (!p.update()) {
                iterator.remove()
            }
        }
    }

    fun draw(canvas: Canvas) {
        particles.forEach { it.draw(canvas, paint) }
    }

    fun clear() {
        particles.clear()
    }
}

