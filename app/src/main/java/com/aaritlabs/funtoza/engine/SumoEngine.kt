package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import kotlin.math.*

data class SumoBall(
    val player: Player,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angleDeg: Float = 0f,
    var radius: Float = 36f,
    var isFallen: Boolean = false
)

class SumoEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val sumoBalls = mutableListOf<SumoBall>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var arenaRadius: Float = 0f
    private var arenaCx: Float = 0f
    private var arenaCy: Float = 0f

    override fun initGame() {
        sumoBalls.clear()
        isGameOver = false

        arenaCx = width / 2f
        arenaCy = height / 2f
        arenaRadius = min(width, height) * 0.42f

        val spawnRadius = arenaRadius * 0.65f
        players.forEachIndexed { index, player ->
            player.isAlive = true
            val angle = (index.toFloat() / players.size) * 360f
            val rad = Math.toRadians(angle.toDouble())
            val sx = arenaCx + (cos(rad) * spawnRadius).toFloat()
            val sy = arenaCy + (sin(rad) * spawnRadius).toFloat()

            sumoBalls.add(
                SumoBall(
                    player = player,
                    x = sx,
                    y = sy,
                    angleDeg = (angle + 180f) % 360f
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
        sumoBalls.forEach { ball ->
            if (ball.player.isBot && !ball.isFallen) {
                updateSumoBotAI(ball, deltaTime)
            }
        }

        // Move & accelerate balls
        sumoBalls.forEach { ball ->
            if (ball.isFallen) return@forEach

            if (ball.player.isTouching) {
                // Accelerate forward
                val rad = Math.toRadians(ball.angleDeg.toDouble())
                val accel = 520f * deltaTime
                ball.vx += (cos(rad) * accel).toFloat()
                ball.vy += (sin(rad) * accel).toFloat()
            } else {
                // Rotate
                ball.angleDeg = (ball.angleDeg + 180f * deltaTime) % 360f
            }

            // Apply friction & movement
            ball.x += ball.vx * deltaTime
            ball.y += ball.vy * deltaTime
            ball.vx *= (1f - 1.2f * deltaTime)
            ball.vy *= (1f - 1.2f * deltaTime)

            // Check if fallen outside ring
            val distFromCenter = hypot(ball.x - arenaCx, ball.y - arenaCy)
            if (distFromCenter > arenaRadius + ball.radius * 0.5f) {
                ball.isFallen = true
                ball.player.isAlive = false
                particles.spawnExplosion(ball.x, ball.y, ball.player.id.color, 40)
                soundManager.play(SoundManager.SoundType.EXPLOSION)
                triggerVibration(80)

                val alive = sumoBalls.filter { !it.isFallen }
                if (alive.size <= 1) {
                    val winnerPlayer = alive.firstOrNull()?.player
                    endRound(winnerPlayer)
                }
            }
        }

        // Ball vs Ball collisions
        for (i in 0 until sumoBalls.size) {
            for (j in (i + 1) until sumoBalls.size) {
                val b1 = sumoBalls[i]
                val b2 = sumoBalls[j]
                if (b1.isFallen || b2.isFallen) continue

                val dx = b2.x - b1.x
                val dy = b2.y - b1.y
                val dist = hypot(dx, dy)
                val minDist = b1.radius + b2.radius

                if (dist < minDist && dist > 0.001f) {
                    val overlap = minDist - dist
                    val nx = dx / dist
                    val ny = dy / dist

                    // Separate
                    b1.x -= nx * overlap * 0.5f
                    b1.y -= ny * overlap * 0.5f
                    b2.x += nx * overlap * 0.5f
                    b2.y += ny * overlap * 0.5f

                    // Elastic collision impulse
                    val kx = b1.vx - b2.vx
                    val ky = b1.vy - b2.vy
                    val p = 2f * (nx * kx + ny * ky) / 2f

                    val impulseMultiplier = 1.35f
                    b1.vx -= p * nx * impulseMultiplier
                    b1.vy -= p * ny * impulseMultiplier
                    b2.vx += p * nx * impulseMultiplier
                    b2.vy += p * ny * impulseMultiplier

                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.9f)
                    triggerVibration(50)
                    particles.spawnExplosion((b1.x + b2.x) / 2f, (b1.y + b2.y) / 2f, Color.YELLOW, 12)
                }
            }
        }

        particles.update()
    }

    private fun updateSumoBotAI(ball: SumoBall, deltaTime: Float) {
        val target = sumoBalls.firstOrNull { it != ball && !it.isFallen } ?: return
        val angleToTarget = Math.toDegrees(atan2((target.y - ball.y).toDouble(), (target.x - ball.x).toDouble())).toFloat()
        val normTargetAngle = (angleToTarget + 360f) % 360f
        val diff = abs(normTargetAngle - ball.angleDeg)

        // Don't drive off ledge
        val distCenter = hypot(ball.x - arenaCx, ball.y - arenaCy)
        val isNearLedge = distCenter > arenaRadius * 0.75f

        if (isNearLedge) {
            // steer back towards center
            val angleToCenter = Math.toDegrees(atan2((arenaCy - ball.y).toDouble(), (arenaCx - ball.x).toDouble())).toFloat()
            val normCenter = (angleToCenter + 360f) % 360f
            val diffCenter = abs(normCenter - ball.angleDeg)
            ball.player.isTouching = diffCenter < 35f || diffCenter > 325f
        } else {
            ball.player.isTouching = diff < 30f || diff > 330f
        }
    }

    override fun render(canvas: Canvas) {
        // Deep abyss floor
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0B0D18")
        canvas.drawRect(0f, 0f, width, height, paint)

        // Floating circular arena
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B233D")
        canvas.drawCircle(arenaCx, arenaCy, arenaRadius, paint)

        // Electric perimeter ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = Color.parseColor("#05D9E8")
        canvas.drawCircle(arenaCx, arenaCy, arenaRadius, paint)

        // Center ring mark
        paint.strokeWidth = 4f
        paint.color = Color.parseColor("#2E3A63")
        canvas.drawCircle(arenaCx, arenaCy, arenaRadius * 0.35f, paint)

        // Draw Sumo Bumper Balls
        sumoBalls.forEach { ball ->
            if (ball.isFallen) return@forEach

            // Ball shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(80, 0, 0, 0)
            canvas.drawCircle(ball.x + 6f, ball.y + 6f, ball.radius, paint)

            // Outer bumper ring
            paint.style = Paint.Style.FILL
            paint.color = ball.player.id.color
            canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

            // Inner core
            paint.color = ball.player.id.darkColor
            canvas.drawCircle(ball.x, ball.y, ball.radius * 0.65f, paint)

            // Direction arrow
            val rad = Math.toRadians(ball.angleDeg.toDouble())
            val ax = ball.x + (cos(rad) * ball.radius * 0.85f).toFloat()
            val ay = ball.y + (sin(rad) * ball.radius * 0.85f).toFloat()
            paint.color = Color.WHITE
            canvas.drawCircle(ax, ay, 6f, paint)
        }

        // Particles
        particles.draw(canvas)

        // Player buttons
        drawPlayerControlButtons(canvas, paint)
    }
}

