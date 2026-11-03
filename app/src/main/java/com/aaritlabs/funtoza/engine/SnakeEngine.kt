package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import kotlin.math.*

data class Snake(
    val player: Player,
    var x: Float,
    var y: Float,
    var angleDeg: Float,
    val trail: MutableList<PointF> = mutableListOf(),
    var isDead: Boolean = false
)

class SnakeEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val snakes = mutableListOf<Snake>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val speed = 150f
    private val turnSpeed = 160f // deg per sec
    private val headRadius = 9f

    override fun initGame() {
        snakes.clear()
        isGameOver = false

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.32f

        players.forEachIndexed { index, player ->
            player.isAlive = true
            val angle = (index.toFloat() / players.size) * 360f
            val rad = Math.toRadians(angle.toDouble())
            val sx = (cx + cos(rad) * radius).toFloat()
            val sy = (cy + sin(rad) * radius).toFloat()
            val headAngle = (angle + 180f) % 360f

            snakes.add(
                Snake(
                    player = player,
                    x = sx,
                    y = sy,
                    angleDeg = headAngle
                )
            )
        }
    }

    override fun update(deltaTime: Float) {
        if (isGameOver) {
            particles.update()
            return
        }

        // Bot AI
        snakes.forEach { snake ->
            if (snake.player.isBot && !snake.isDead) {
                updateSnakeBotAI(snake, deltaTime)
            }
        }

        // Move snakes & record trail
        val margin = 30f
        snakes.forEach { snake ->
            if (snake.isDead) return@forEach

            // Turn
            if (snake.player.isTouching) {
                snake.angleDeg = (snake.angleDeg + turnSpeed * deltaTime) % 360f
            } else {
                snake.angleDeg = (snake.angleDeg - turnSpeed * deltaTime + 360f) % 360f
            }

            // Move
            val rad = Math.toRadians(snake.angleDeg.toDouble())
            val nx = (snake.x + cos(rad) * speed * deltaTime).toFloat()
            val ny = (snake.y + sin(rad) * speed * deltaTime).toFloat()

            // Save old point to trail
            if (snake.trail.isEmpty() || hypot(nx - snake.x, ny - snake.y) > 4f) {
                snake.trail.add(PointF(snake.x, snake.y))
            }

            snake.x = nx
            snake.y = ny

            // Check Boundary Collision
            if (nx <= margin || nx >= width - margin || ny <= margin || ny >= height - margin) {
                killSnake(snake)
                return@forEach
            }

            // Check Tail Collision (Against all snakes' trails)
            for (other in snakes) {
                val skipCount = if (other == snake) 15 else 0 // don't collide with own head's immediate points
                if (other.trail.size > skipCount) {
                    val checkPoints = other.trail.subList(0, other.trail.size - skipCount)
                    for (pt in checkPoints) {
                        if (hypot(nx - pt.x, ny - pt.y) < headRadius * 1.5f) {
                            killSnake(snake)
                            return@forEach
                        }
                    }
                }
            }
        }

        particles.update()
    }

    private fun killSnake(snake: Snake) {
        snake.isDead = true
        snake.player.isAlive = false
        particles.spawnExplosion(snake.x, snake.y, snake.player.id.color, 45)
        soundManager.play(SoundManager.SoundType.EXPLOSION)
        triggerVibration(80)

        // Check if round won
        val alive = snakes.filter { !it.isDead }
        if (alive.size <= 1) {
            val winnerSnake = alive.firstOrNull()?.player
            endRound(winnerSnake)
        }
    }

    private fun updateSnakeBotAI(snake: Snake, deltaTime: Float) {
        // Raycast forward 40px
        val rad = Math.toRadians(snake.angleDeg.toDouble())
        val fx = (snake.x + cos(rad) * 45f).toFloat()
        val fy = (snake.y + sin(rad) * 45f).toFloat()

        val margin = 50f
        var willCollide = (fx <= margin || fx >= width - margin || fy <= margin || fy >= height - margin)

        if (!willCollide) {
            for (other in snakes) {
                for (pt in other.trail) {
                    if (hypot(fx - pt.x, fy - pt.y) < 22f) {
                        willCollide = true
                        break
                    }
                }
                if (willCollide) break
            }
        }

        if (willCollide) {
            // Turn opposite direction
            snake.player.isTouching = !snake.player.isTouching
        }
    }

    override fun render(canvas: Canvas) {
        // Neon dark floor
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0C0F1D")
        canvas.drawRect(0f, 0f, width, height, paint)

        // Arena glow border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.parseColor("#1F2742")
        canvas.drawRect(30f, 30f, width - 30f, height - 30f, paint)

        // Draw Trails
        snakes.forEach { snake ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = snake.player.id.color
            paint.alpha = if (snake.isDead) 90 else 240

            for (i in 1 until snake.trail.size) {
                val p1 = snake.trail[i - 1]
                val p2 = snake.trail[i]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }

            // Draw Head
            if (!snake.isDead) {
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(snake.x, snake.y, headRadius, paint)
                paint.color = snake.player.id.color
                canvas.drawCircle(snake.x, snake.y, headRadius * 0.6f, paint)
            }
        }

        // Particles
        particles.draw(canvas)

        // Control buttons
        drawPlayerControlButtons(canvas, paint)
    }
}

