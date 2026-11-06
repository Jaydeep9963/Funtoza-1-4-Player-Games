package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*

data class Paddle(
    val player: Player,
    var x: Float,
    var y: Float,
    var radius: Float = 25f,
    var targetX: Float = 0f,
    var targetY: Float = 0f,
    var prevX: Float = 0f,
    var prevY: Float = 0f
)

data class Ball(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var radius: Float = 13f
)

class SoccerEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val paddles = mutableListOf<Paddle>()
    private val ball = Ball(0f, 0f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val playerGoals = mutableMapOf<PlayerId, Int>()
    private val maxGoals = 3

    // Multi-touch tracking: pointerId -> PlayerId
    private val pointerToPlayer = mutableMapOf<Int, PlayerId>()

    // Stuck Detection (restarts kickoff without affecting scores)
    private var stuckTimer = 0f
    private var lastBallX = 0f
    private var lastBallY = 0f
    private var stuckBannerTimer = 0f

    override fun initGame() {
        paddles.clear()
        playerGoals.clear()
        pointerToPlayer.clear()
        isGameOver = false
        stuckTimer = 0f
        stuckBannerTimer = 0f

        ball.radius = 13f * density
        ball.x = width / 2f
        ball.y = height / 2f
        lastBallX = ball.x
        lastBallY = ball.y

        // Fast energetic initial launch
        launchKickoffBall()

        players.forEachIndexed { index, p ->
            playerGoals[p.id] = 0
            val spawn = getPaddleSpawn(index)
            paddles.add(
                Paddle(
                    player = p,
                    x = spawn.first,
                    y = spawn.second,
                    radius = 25f * density,
                    targetX = spawn.first,
                    targetY = spawn.second,
                    prevX = spawn.first,
                    prevY = spawn.second
                )
            )
        }
    }

    private fun launchKickoffBall(forceDirectionY: Float? = null) {
        val isPortrait = height > width
        val speedY = (520f + (Math.random() * 200).toFloat()) * density
        val speedX = ((Math.random() * 320 - 160).toFloat()) * density
        ball.vx = speedX
        ball.vy = forceDirectionY ?: (if (isPortrait) (if (Math.random() > 0.5) speedY else -speedY) else (if (Math.random() > 0.5) 300f * density else -300f * density))
    }

    private fun getPaddleSpawn(index: Int): Pair<Float, Float> {
        val isPortrait = height > width
        val centerY = height / 2f
        val centerX = width / 2f
        return if (isPortrait) {
            if (isTeamMode) {
                when (index) {
                    0 -> Pair(width * 0.25f, height * 0.78f) // Team 1: P1
                    1 -> Pair(width * 0.75f, height * 0.78f) // Team 1: P2
                    2 -> Pair(width * 0.25f, height * 0.22f) // Team 2: P3
                    else -> Pair(width * 0.75f, height * 0.22f) // Team 2: P4
                }
            } else {
                when (index) {
                    0 -> Pair(centerX, height * 0.82f) // Bottom / P1
                    1 -> Pair(centerX, height * 0.18f) // Top / P2
                    2 -> Pair(width * 0.22f, centerY)   // Left / P3
                    else -> Pair(width * 0.78f, centerY) // Right / P4
                }
            }
        } else {
            when (index) {
                0 -> Pair(width * 0.22f, centerY)
                1 -> Pair(width * 0.78f, centerY)
                2 -> Pair(centerX, height * 0.22f)
                else -> Pair(centerX, height * 0.78f)
            }
        }
    }

    private fun resetPaddlesToSpawn() {
        paddles.forEachIndexed { index, paddle ->
            val spawn = getPaddleSpawn(index)
            paddle.x = spawn.first
            paddle.y = spawn.second
            paddle.prevX = spawn.first
            paddle.prevY = spawn.second
            paddle.targetX = spawn.first
            paddle.targetY = spawn.second
            paddle.player.isTouching = false
        }
        pointerToPlayer.clear()
    }

    override fun calculateControlZones() {
        controlZones.clear()
        val isPortrait = height > width
        if (isPortrait) {
            if (players.size == 2) {
                // P1 bottom half, P2 top half
                controlZones[PlayerId.PLAYER_1] = RectF(0f, height / 2f, width, height)
                controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width, height / 2f)
            } else if (isTeamMode) {
                // Team 1: P1 (bottom-left) & P2 (bottom-right)
                controlZones[PlayerId.PLAYER_1] = RectF(0f, height / 2f, width / 2f, height)
                controlZones[PlayerId.PLAYER_2] = RectF(width / 2f, height / 2f, width, height)
                // Team 2: P3 (top-left) & P4 (top-right)
                controlZones[PlayerId.PLAYER_3] = RectF(0f, 0f, width / 2f, height / 2f)
                controlZones[PlayerId.PLAYER_4] = RectF(width / 2f, 0f, width, height / 2f)
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

    /**
     * Dedicated multi-touch handler for responsive, zero-lag paddle tracking
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver) return true

        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                for ((pId, zone) in controlZones) {
                    if (zone.contains(x, y)) {
                        val player = players.firstOrNull { it.id == pId && !it.isBot } ?: continue
                        pointerToPlayer[pointerId] = pId
                        player.isTouching = true
                        val paddle = paddles.firstOrNull { it.player.id == pId }
                        if (paddle != null) {
                            paddle.targetX = x
                            paddle.targetY = y
                        }
                        break
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val pId = pointerToPlayer[pid] ?: continue
                    val player = players.firstOrNull { it.id == pId && !it.isBot } ?: continue
                    val paddle = paddles.firstOrNull { it.player.id == pId } ?: continue
                    player.isTouching = true
                    paddle.targetX = event.getX(i)
                    paddle.targetY = event.getY(i)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pId = pointerToPlayer.remove(pointerId)
                if (pId != null) {
                    val player = players.firstOrNull { it.id == pId }
                    if (player != null) {
                        player.isTouching = false
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pointerToPlayer.clear()
                players.forEach { it.isTouching = false }
            }
        }
        return true
    }

    override fun update(deltaTime: Float) {
        if (isGameOver) {
            particles.update()
            return
        }

        if (stuckBannerTimer > 0f) {
            stuckBannerTimer -= deltaTime
        }

        val isPortrait = height > width
        val borderPad = 26f * density

        // 1. Update Paddles (Instant zero-lag follow within zone)
        paddles.forEach { paddle ->
            paddle.prevX = paddle.x
            paddle.prevY = paddle.y
            val zone = controlZones[paddle.player.id] ?: RectF(0f, 0f, width, height)

            if (paddle.player.isBot) {
                // Dynamic AI tracking
                val botSpeed = 520f * density * deltaTime
                val targetX = ball.x.coerceIn(zone.left + paddle.radius, zone.right - paddle.radius)
                val targetY = ball.y.coerceIn(zone.top + paddle.radius, zone.bottom - paddle.radius)
                val dx = targetX - paddle.x
                val dy = targetY - paddle.y
                val dist = hypot(dx, dy)
                if (dist > 1f) {
                    val step = min(dist, botSpeed)
                    paddle.x += (dx / dist) * step
                    paddle.y += (dy / dist) * step
                }
            } else if (paddle.player.isTouching) {
                // 1:1 Instant direct finger following (Clamped to player's designated field half/quadrant)
                val targetX = paddle.targetX.coerceIn(zone.left + paddle.radius, zone.right - paddle.radius)
                val targetY = paddle.targetY.coerceIn(zone.top + paddle.radius, zone.bottom - paddle.radius)
                paddle.x = targetX
                paddle.y = targetY
            }

            // Paddle-Ball collision with real impulse & finger swing speed
            val dist = hypot(ball.x - paddle.x, ball.y - paddle.y)
            val minDist = ball.radius + paddle.radius
            if (dist < minDist) {
                val overlap = minDist - dist
                val nx = if (dist > 0.001f) (ball.x - paddle.x) / dist else 0f
                val ny = if (dist > 0.001f) (ball.y - paddle.y) / dist else (if (paddle.y > height / 2f) -1f else 1f)

                // Push ball out of paddle penetration
                ball.x += nx * overlap
                ball.y += ny * overlap

                // Paddle finger velocity
                val dtSafe = deltaTime.coerceAtLeast(0.001f)
                val pvx = (paddle.x - paddle.prevX) / dtSafe
                val pvy = (paddle.y - paddle.prevY) / dtSafe

                // High-speed energetic hit
                val baseSpeed = 620f * density
                val shotVx = nx * baseSpeed + pvx * 0.70f
                val shotVy = ny * baseSpeed + pvy * 0.70f
                val shotSpeed = hypot(shotVx, shotVy).coerceIn(480f * density, 1400f * density)
                val totalH = hypot(shotVx, shotVy).coerceAtLeast(0.001f)

                ball.vx = (shotVx / totalH) * shotSpeed
                ball.vy = (shotVy / totalH) * shotSpeed

                soundManager.play(SoundManager.SoundType.BOUNCE, 0.9f)
                triggerVibration(45)
                particles.spawnExplosion(ball.x, ball.y, paddle.player.id.color, 12)
            }
        }

        // 2. Update Ball Physics
        ball.x += ball.vx * deltaTime
        ball.y += ball.vy * deltaTime

        // Gentle turf friction so ball glides fast and smoothly
        val friction = (1f - 0.12f * deltaTime)
        ball.vx *= friction
        ball.vy *= friction

        // Maintain minimum lively ball speed so it never crawls
        val curSpeed = hypot(ball.vx, ball.vy)
        val minSpeed = 220f * density
        if (curSpeed in 1f..minSpeed) {
            val boost = minSpeed / curSpeed
            ball.vx *= boost
            ball.vy *= boost
        }

        // Boundary checks and goal detections
        if (isPortrait) {
            val goalSpan = width * 0.48f
            val goalLeft = (width - goalSpan) / 2f
            val goalRight = goalLeft + goalSpan

            // Top Goal: Player 1 scores on Player 2's net
            if (ball.y - ball.radius <= borderPad) {
                if (ball.x in goalLeft..goalRight) {
                    scoreGoal(PlayerId.PLAYER_1)
                    return
                } else {
                    ball.y = borderPad + ball.radius
                    ball.vy = abs(ball.vy) * 1.02f // energetic bounce
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                }
            }

            // Bottom Goal: Player 2 scores on Player 1's net
            if (ball.y + ball.radius >= height - borderPad) {
                if (ball.x in goalLeft..goalRight) {
                    scoreGoal(PlayerId.PLAYER_2)
                    return
                } else {
                    ball.y = height - borderPad - ball.radius
                    ball.vy = -abs(ball.vy) * 1.02f
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                }
            }

            // Left & Right side boundary bounce
            if (ball.x - ball.radius <= borderPad) {
                ball.x = borderPad + ball.radius
                ball.vx = abs(ball.vx) * 1.02f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
            } else if (ball.x + ball.radius >= width - borderPad) {
                ball.x = width - borderPad - ball.radius
                ball.vx = -abs(ball.vx) * 1.02f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
            }
        } else {
            val goalWidth = height * 0.42f
            val goalTop = (height - goalWidth) / 2f
            val goalBottom = goalTop + goalWidth

            // Left Goal: Player 2 scores
            if (ball.x - ball.radius <= borderPad) {
                if (ball.y in goalTop..goalBottom) {
                    scoreGoal(PlayerId.PLAYER_2)
                    return
                } else {
                    ball.x = borderPad + ball.radius
                    ball.vx = abs(ball.vx) * 1.02f
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                }
            }

            // Right Goal: Player 1 scores
            if (ball.x + ball.radius >= width - borderPad) {
                if (ball.y in goalTop..goalBottom) {
                    scoreGoal(PlayerId.PLAYER_1)
                    return
                } else {
                    ball.x = width - borderPad - ball.radius
                    ball.vx = -abs(ball.vx) * 1.02f
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                }
            }

            // Top & Bottom boundary bounce
            if (ball.y - ball.radius <= borderPad) {
                ball.y = borderPad + ball.radius
                ball.vy = abs(ball.vy) * 1.02f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
            } else if (ball.y + ball.radius >= height - borderPad) {
                ball.y = height - borderPad - ball.radius
                ball.vy = -abs(ball.vy) * 1.02f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
            }
        }

        // 3. Stuck Detection & Auto Kickoff Restart (Does NOT affect score)
        val ballMoved = hypot(ball.x - lastBallX, ball.y - lastBallY)
        val isBallTrapped = curSpeed < 60f * density || ballMoved < (15f * density * deltaTime)
        val isOverlappingPaddle = paddles.any { p -> hypot(ball.x - p.x, ball.y - p.y) < (ball.radius + p.radius + 2f * density) }

        if (isBallTrapped || isOverlappingPaddle) {
            stuckTimer += deltaTime
            if (stuckTimer >= 2.0f) {
                triggerStuckKickoffReset()
                stuckTimer = 0f
            }
        } else {
            stuckTimer = (stuckTimer - deltaTime * 0.7f).coerceAtLeast(0f)
        }
        lastBallX = ball.x
        lastBallY = ball.y

        particles.update()
    }

    /**
     * Resets kickoff when the ball or paddles get stuck, preserving the current scores.
     */
    private fun triggerStuckKickoffReset() {
        ball.x = width / 2f
        ball.y = height / 2f
        launchKickoffBall()
        resetPaddlesToSpawn()

        stuckBannerTimer = 1.6f
        soundManager.play(SoundManager.SoundType.BOUNCE, 1.0f)
        triggerVibration(60)
        particles.spawnExplosion(width / 2f, height / 2f, Color.parseColor("#FFD54F"), 35)
    }

    private fun scoreGoal(scorer: PlayerId) {
        val count = (playerGoals[scorer] ?: 0) + 1
        playerGoals[scorer] = count
        soundManager.play(SoundManager.SoundType.GOAL)
        particles.spawnExplosion(ball.x, ball.y, scorer.color, 55)
        triggerVibration(120)

        // Reset ball with fast shot towards opponent
        ball.x = width / 2f
        ball.y = height / 2f
        val isPortrait = height > width
        val speedY = (520f + (Math.random() * 180).toFloat()) * density
        val speedX = ((Math.random() * 260 - 130).toFloat()) * density
        ball.vx = speedX
        ball.vy = if (scorer == PlayerId.PLAYER_1) -speedY else speedY

        resetPaddlesToSpawn()
        stuckTimer = 0f

        if (isTeamMode) {
            val team1Goals = (playerGoals[PlayerId.PLAYER_1] ?: 0) + (playerGoals[PlayerId.PLAYER_2] ?: 0)
            val team2Goals = (playerGoals[PlayerId.PLAYER_3] ?: 0) + (playerGoals[PlayerId.PLAYER_4] ?: 0)
            if (team1Goals >= maxGoals) {
                winningTeam = 1
                endRound(players[0])
            } else if (team2Goals >= maxGoals) {
                winningTeam = 2
                endRound(players[2])
            }
        } else {
            if (count >= maxGoals) {
                val winnerPlayer = players.firstOrNull { it.id == scorer }
                endRound(winnerPlayer)
            }
        }
    }

    override fun getTeamScoreString(team: Int): String {
        val goals = if (team == 1) {
            (playerGoals[PlayerId.PLAYER_1] ?: 0) + (playerGoals[PlayerId.PLAYER_2] ?: 0)
        } else {
            (playerGoals[PlayerId.PLAYER_3] ?: 0) + (playerGoals[PlayerId.PLAYER_4] ?: 0)
        }
        return "$goals / $maxGoals Goals"
    }

    override fun render(canvas: Canvas) {
        val isPortrait = height > width
        val borderPad = 26f * density

        // 1. Dark Neon Soccer Pitch
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0F2217") // Deep Dark Turf Green
        canvas.drawRect(0f, 0f, width, height, paint)

        // Field border & Center line
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = Color.parseColor("#2E5B3D")
        canvas.drawRect(borderPad, borderPad, width - borderPad, height - borderPad, paint)

        if (isPortrait) {
            // Center Line & Center Circle
            canvas.drawLine(borderPad, height / 2f, width - borderPad, height / 2f, paint)
            canvas.drawCircle(width / 2f, height / 2f, width * 0.18f, paint)

            // Top & Bottom Goals
            val goalSpan = width * 0.48f
            val goalLeft = (width - goalSpan) / 2f
            paint.strokeWidth = 4f * density

            // Top Goal Post (Player 2)
            paint.color = PlayerId.PLAYER_2.color
            canvas.drawRect(goalLeft, borderPad - 16f * density, goalLeft + goalSpan, borderPad, paint)

            // Bottom Goal Post (Player 1)
            paint.color = PlayerId.PLAYER_1.color
            canvas.drawRect(goalLeft, height - borderPad, goalLeft + goalSpan, height - borderPad + 16f * density, paint)
        } else {
            canvas.drawLine(width / 2f, borderPad, width / 2f, height - borderPad, paint)
            canvas.drawCircle(width / 2f, height / 2f, height * 0.18f, paint)

            val goalWidth = height * 0.42f
            val goalTop = (height - goalWidth) / 2f
            paint.strokeWidth = 4f * density

            paint.color = PlayerId.PLAYER_1.color
            canvas.drawRect(borderPad - 16f * density, goalTop, borderPad, goalTop + goalWidth, paint)

            paint.color = PlayerId.PLAYER_2.color
            canvas.drawRect(width - borderPad, goalTop, width - borderPad + 16f * density, goalTop + goalWidth, paint)
        }

        // 2. Render Paddles with 3D Puck Shading
        paddles.forEach { paddle ->
            // Base shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#4D000000")
            canvas.drawCircle(paddle.x, paddle.y + 3f * density, paddle.radius, paint)

            // Outer Body
            paint.color = paddle.player.id.color
            canvas.drawCircle(paddle.x, paddle.y, paddle.radius, paint)

            // Metallic/Glow Rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.2f * density
            paint.color = Color.WHITE
            canvas.drawCircle(paddle.x, paddle.y, paddle.radius, paint)

            // Inner Ring Handle
            paint.style = Paint.Style.FILL
            paint.color = paddle.player.id.darkColor
            canvas.drawCircle(paddle.x, paddle.y, paddle.radius * 0.52f, paint)

            // Center Knob
            paint.color = Color.WHITE
            canvas.drawCircle(paddle.x, paddle.y, paddle.radius * 0.22f, paint)
        }

        // 3. Render Soccer Ball
        // Drop shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#4D000000")
        canvas.drawCircle(ball.x, ball.y + 2f * density, ball.radius, paint)

        // White ball body
        paint.color = Color.WHITE
        canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

        // Black outer rim
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = Color.parseColor("#1B1A1E")
        canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

        // Center soccer pentagon motif
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1A1E")
        canvas.drawCircle(ball.x, ball.y, ball.radius * 0.36f, paint)

        // 4. Score HUD
        paint.style = Paint.Style.FILL
        paint.textSize = 28f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        if (isPortrait) {
            paint.color = (PlayerId.PLAYER_2.color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawText("${playerGoals[PlayerId.PLAYER_2] ?: 0}", width * 0.14f, height * 0.46f, paint)

            paint.color = (PlayerId.PLAYER_1.color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawText("${playerGoals[PlayerId.PLAYER_1] ?: 0}", width * 0.14f, height * 0.57f, paint)
        } else {
            paint.color = (PlayerId.PLAYER_1.color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawText("${playerGoals[PlayerId.PLAYER_1] ?: 0}", width * 0.38f, 50f * density, paint)

            paint.color = (PlayerId.PLAYER_2.color and 0x00FFFFFF) or 0xCC000000.toInt()
            canvas.drawText("${playerGoals[PlayerId.PLAYER_2] ?: 0}", width * 0.62f, 50f * density, paint)
        }
        paint.isFakeBoldText = false

        // 5. Stuck Kickoff Reset Banner
        if (stuckBannerTimer > 0f) {
            val bannerW = 220f * density
            val bannerH = 36f * density
            val cx = width / 2f
            val cy = height / 2f
            val r = RectF(cx - bannerW / 2f, cy - bannerH / 2f, cx + bannerW / 2f, cy + bannerH / 2f)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#E6150F28")
            canvas.drawRoundRect(r, 18f * density, 18f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f * density
            paint.color = Color.parseColor("#FFD54F")
            canvas.drawRoundRect(r, 18f * density, 18f * density, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFD54F")
            paint.textSize = 12.5f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = cy - (fm.ascent + fm.descent) / 2f
            canvas.drawText("⚽ BALL STUCK • KICKOFF!", cx, baseline, paint)
            paint.isFakeBoldText = false
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
