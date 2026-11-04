package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*

data class Paddle(
    val player: Player,
    var x: Float,
    var y: Float,
    var radius: Float = 42f,
    var targetX: Float = 0f,
    var targetY: Float = 0f
)

data class Ball(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 24f
)

class SoccerEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val paddles = mutableListOf<Paddle>()
    private val ball = Ball(0f, 0f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val playerGoals = mutableMapOf<PlayerId, Int>()
    private val maxGoals = 3

    override fun initGame() {
        paddles.clear()
        playerGoals.clear()
        isGameOver = false

        ball.x = width / 2f
        ball.y = height / 2f
        val isPortrait = height > width
        ball.vx = if (Math.random() > 0.5) 180f else -180f
        ball.vy = if (isPortrait) (if (Math.random() > 0.5) 240f else -240f) else (if (Math.random() > 0.5) 150f else -150f)

        val centerY = height / 2f
        val centerX = width / 2f

        players.forEachIndexed { index, p ->
            playerGoals[p.id] = 0
            val spawn = if (isPortrait) {
                when (index) {
                    0 -> Pair(centerX, height * 0.80f) // Bottom team / P1
                    1 -> Pair(centerX, height * 0.20f) // Top team / P2
                    2 -> Pair(width * 0.25f, centerY)   // Left team / P3
                    else -> Pair(width * 0.75f, centerY) // Right team / P4
                }
            } else {
                when (index) {
                    0 -> Pair(width * 0.25f, centerY)
                    1 -> Pair(width * 0.75f, centerY)
                    2 -> Pair(centerX, height * 0.25f)
                    else -> Pair(centerX, height * 0.75f)
                }
            }
            paddles.add(
                Paddle(
                    player = p,
                    x = spawn.first,
                    y = spawn.second,
                    targetX = spawn.first,
                    targetY = spawn.second
                )
            )
        }
    }

    override fun calculateControlZones() {
        controlZones.clear()
        val isPortrait = height > width
        if (isPortrait) {
            if (players.size == 2) {
                // P1 bottom half, P2 top half
                controlZones[PlayerId.PLAYER_1] = RectF(0f, height / 2f, width, height)
                controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width, height / 2f)
            } else {
                // 4 quadrants
                controlZones[PlayerId.PLAYER_1] = RectF(0f, height / 2f, width / 2f, height)
                controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width / 2f, height / 2f)
                controlZones[PlayerId.PLAYER_3] = RectF(width / 2f, 0f, width, height / 2f)
                controlZones[PlayerId.PLAYER_4] = RectF(width / 2f, height / 2f, width, height)
            }
        } else {
            if (players.size == 2) {
                controlZones[PlayerId.PLAYER_1] = RectF(0f, 0f, width / 2f, height)
                controlZones[PlayerId.PLAYER_2] = RectF(width / 2f, 0f, width, height)
            } else {
                controlZones[PlayerId.PLAYER_1] = RectF(0f, height / 2f, width / 2f, height)
                controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width / 2f, height / 2f)
                controlZones[PlayerId.PLAYER_3] = RectF(width / 2f, 0f, width, height / 2f)
                controlZones[PlayerId.PLAYER_4] = RectF(width / 2f, height / 2f, width, height)
            }
        }
    }

    override fun update(deltaTime: Float) {
        if (isGameOver) {
            particles.update()
            return
        }

        // Update Paddles
        paddles.forEach { paddle ->
            if (paddle.player.isBot) {
                // AI behavior: track ball
                val speed = 260f * deltaTime
                val dx = ball.x - paddle.x
                val dy = ball.y - paddle.y
                paddle.x += dx.coerceIn(-speed, speed)
                paddle.y += dy.coerceIn(-speed, speed)
            } else if (paddle.player.isTouching) {
                // Smooth follow touch within zone
                val speed = 600f * deltaTime
                val dx = paddle.player.touchX - paddle.x
                val dy = paddle.player.touchY - paddle.y
                paddle.x += dx.coerceIn(-speed, speed)
                paddle.y += dy.coerceIn(-speed, speed)
            }

            // Keep paddle inside zone
            val zone = controlZones[paddle.player.id] ?: RectF(0f, 0f, width, height)
            paddle.x = paddle.x.coerceIn(zone.left + paddle.radius, zone.right - paddle.radius)
            paddle.y = paddle.y.coerceIn(zone.top + paddle.radius, zone.bottom - paddle.radius)

            // Paddle-Ball collision
            val dist = hypot(ball.x - paddle.x, ball.y - paddle.y)
            if (dist < ball.radius + paddle.radius) {
                val overlap = (ball.radius + paddle.radius) - dist
                val nx = (ball.x - paddle.x) / (dist + 0.0001f)
                val ny = (ball.y - paddle.y) / (dist + 0.0001f)

                ball.x += nx * overlap
                ball.y += ny * overlap

                // Impulse
                val hitSpeed = 480f
                ball.vx = nx * hitSpeed
                ball.vy = ny * hitSpeed
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.8f)
                triggerVibration(40)
            }
        }

        // Update Ball Physics
        ball.x += ball.vx * deltaTime
        ball.y += ball.vy * deltaTime
        ball.vx *= (1f - 0.25f * deltaTime) // friction
        ball.vy *= (1f - 0.25f * deltaTime)

        val isPortrait = height > width
        if (isPortrait) {
            val goalSpan = width * 0.45f
            val goalLeft = (width - goalSpan) / 2f
            val goalRight = goalLeft + goalSpan

            // Check Top Goal (Player 1 scores on Player 2's net)
            if (ball.y - ball.radius <= 30f) {
                if (ball.x in goalLeft..goalRight) {
                    scoreGoal(PlayerId.PLAYER_1)
                } else {
                    ball.y = 30f + ball.radius
                    ball.vy = -ball.vy
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
                }
            }

            // Check Bottom Goal (Player 2 scores on Player 1's net)
            if (ball.y + ball.radius >= height - 30f) {
                if (ball.x in goalLeft..goalRight) {
                    scoreGoal(PlayerId.PLAYER_2)
                } else {
                    ball.y = height - 30f - ball.radius
                    ball.vy = -ball.vy
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
                }
            }

            // Left & Right side boundary bounce
            if (ball.x - ball.radius <= 30f) {
                ball.x = 30f + ball.radius
                ball.vx = -ball.vx
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            } else if (ball.x + ball.radius >= width - 30f) {
                ball.x = width - 30f - ball.radius
                ball.vx = -ball.vx
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            }
        } else {
            val goalWidth = height * 0.38f
            val goalTop = (height - goalWidth) / 2f
            val goalBottom = goalTop + goalWidth

            // Check Left Goal (Player 2 scores)
            if (ball.x - ball.radius <= 30f) {
                if (ball.y in goalTop..goalBottom) {
                    scoreGoal(PlayerId.PLAYER_2)
                } else {
                    ball.x = 30f + ball.radius
                    ball.vx = -ball.vx
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
                }
            }

            // Check Right Goal (Player 1 scores)
            if (ball.x + ball.radius >= width - 30f) {
                if (ball.y in goalTop..goalBottom) {
                    scoreGoal(PlayerId.PLAYER_1)
                } else {
                    ball.x = width - 30f - ball.radius
                    ball.vx = -ball.vx
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
                }
            }

            // Top & Bottom boundary bounce
            if (ball.y - ball.radius <= 30f) {
                ball.y = 30f + ball.radius
                ball.vy = -ball.vy
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            } else if (ball.y + ball.radius >= height - 30f) {
                ball.y = height - 30f - ball.radius
                ball.vy = -ball.vy
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            }
        }

        particles.update()
    }

    private fun scoreGoal(scorer: PlayerId) {
        val count = (playerGoals[scorer] ?: 0) + 1
        playerGoals[scorer] = count
        soundManager.play(SoundManager.SoundType.GOAL)
        particles.spawnExplosion(ball.x, ball.y, scorer.color, 50)
        triggerVibration(120)

        // Reset ball
        ball.x = width / 2f
        ball.y = height / 2f
        ball.vx = if (scorer == PlayerId.PLAYER_1) -220f else 220f
        ball.vy = (Math.random() * 200 - 100).toFloat()

        if (count >= maxGoals) {
            val winnerPlayer = players.firstOrNull { it.id == scorer }
            endRound(winnerPlayer)
        }
    }

    override fun render(canvas: Canvas) {
        // Soccer Pitch Field
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B2F23") // Field Green
        canvas.drawRect(0f, 0f, width, height, paint)

        // Field border & Center line
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.parseColor("#446950")
        canvas.drawRect(30f, 30f, width - 30f, height - 30f, paint)

        val isPortrait = height > width
        if (isPortrait) {
            // Horizontal center line
            canvas.drawLine(30f, height / 2f, width - 30f, height / 2f, paint)
            canvas.drawCircle(width / 2f, height / 2f, width * 0.18f, paint)

            // Top & Bottom Goals
            val goalSpan = width * 0.45f
            val goalLeft = (width - goalSpan) / 2f
            paint.strokeWidth = 8f
            paint.color = PlayerId.PLAYER_2.color
            canvas.drawRect(goalLeft, 5f, goalLeft + goalSpan, 30f, paint) // Top Net

            paint.color = PlayerId.PLAYER_1.color
            canvas.drawRect(goalLeft, height - 30f, goalLeft + goalSpan, height - 5f, paint) // Bottom Net
        } else {
            // Vertical center line
            canvas.drawLine(width / 2f, 30f, width / 2f, height - 30f, paint)
            canvas.drawCircle(width / 2f, height / 2f, height * 0.18f, paint)

            val goalWidth = height * 0.38f
            val goalTop = (height - goalWidth) / 2f
            paint.strokeWidth = 8f
            paint.color = PlayerId.PLAYER_1.color
            canvas.drawRect(5f, goalTop, 30f, goalTop + goalWidth, paint)

            paint.color = PlayerId.PLAYER_2.color
            canvas.drawRect(width - 30f, goalTop, width - 5f, goalTop + goalWidth, paint)
        }

        // Draw Paddles
        paddles.forEach { paddle ->
            paint.style = Paint.Style.FILL
            paint.color = paddle.player.id.color
            canvas.drawCircle(paddle.x, paddle.y, paddle.radius, paint)

            paint.color = paddle.player.id.darkColor
            canvas.drawCircle(paddle.x, paddle.y, paddle.radius * 0.5f, paint)
        }

        // Draw Ball
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(ball.x, ball.y, ball.radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.BLACK
        canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

        // Score HUD
        paint.style = Paint.Style.FILL
        paint.textSize = 54f
        paint.textAlign = Paint.Align.CENTER
        if (isPortrait) {
            paint.color = PlayerId.PLAYER_2.color
            canvas.drawText("${playerGoals[PlayerId.PLAYER_2] ?: 0}", width * 0.15f, height * 0.45f, paint)

            paint.color = PlayerId.PLAYER_1.color
            canvas.drawText("${playerGoals[PlayerId.PLAYER_1] ?: 0}", width * 0.15f, height * 0.55f, paint)
        } else {
            paint.color = PlayerId.PLAYER_1.color
            canvas.drawText("${playerGoals[PlayerId.PLAYER_1] ?: 0}", width * 0.40f, 80f, paint)

            paint.color = PlayerId.PLAYER_2.color
            canvas.drawText("${playerGoals[PlayerId.PLAYER_2] ?: 0}", width * 0.60f, 80f, paint)
        }

        // Particles
        particles.draw(canvas)
    }

    override fun getPlayerScoreString(player: Player): String {
        val goals = playerGoals[player.id] ?: 0
        return "$goals ${if (goals == 1) "Goal" else "Goals"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return playerGoals[player.id] ?: 0
    }
}

