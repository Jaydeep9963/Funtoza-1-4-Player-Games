package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class GolfBumper(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val color: Int,
    var pulseProgress: Float = 0f
)

data class GolfObstacle(
    val rect: RectF,
    val color: Int = Color.parseColor("#352A58")
)

data class GolfBall(
    var x: Float = 0f,
    var y: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    var isRolling: Boolean = false,
    var isInHole: Boolean = false,
    var isVisible: Boolean = true
)

data class GolfFloatingText(
    val text: String,
    val x: Float,
    var y: Float,
    val color: Int,
    var alpha: Float = 1f,
    var life: Float = 1.0f
)

/**
 * Mini Golf engine adapted from JungleFrog.FourPlayers.Minigolf in reference APK.
 * Features 5 unique cosmic neon courses, 2 shots per hole, realistic bank reflection physics,
 * slingshot aiming trajectory, turn-based party multiplayer (1-4 players),
 * intelligent bot golfers, and reliable round completion winner dialog.
 */
class MiniGolfEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // Score map
    private val playerPoints = mutableMapOf<PlayerId, Int>()

    // Courses & Holes (5 rounds)
    private val totalHoles = 5
    private var currentHoleIndex = 0 // 0..4

    // Turn & stroke management (2 shots per hole)
    private var turnPlayerIndex = 0
    private var currentStroke = 1 // 1..2
    private val maxStrokes = 2
    private var isTurnTransitioning = false
    private var turnTransitionTimer = 0f
    private var courseTransitionTimer = 0f
    private var isCourseTransitioning = false
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null

    // Geometry & Course Objects
    private var greenRect = RectF()
    private var teeX = 0f
    private var teeY = 0f
    private var holeX = 0f
    private var holeY = 0f
    private var holeRadius = 16f * density

    private val bumpers = mutableListOf<GolfBumper>()
    private val obstacles = mutableListOf<GolfObstacle>()
    private val floatingTexts = mutableListOf<GolfFloatingText>()

    // Ball
    private val ball = GolfBall(radius = 9f * density)

    // Aiming state
    private var isAiming = false
    private var aimDragStartX = 0f
    private var aimDragStartY = 0f
    private var aimCurX = 0f
    private var aimCurY = 0f

    // Bot AI
    private var botAimTimer = 0f
    private var botIsAiming = false
    private var botTargetAngle = 0f
    private var botTargetPower = 0f

    // Flag animation
    private var flagWaveTimer = 0f

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Layout
    private var topSafeY = 0f
    private var bottomSafeY = 0f

    init {
        dashPaint.style = Paint.Style.STROKE
        dashPaint.strokeWidth = 3f * density
        dashPaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
    }

    override fun initGame() {
        isGameOver = false
        currentHoleIndex = 0
        turnPlayerIndex = 0
        currentStroke = 1
        isTurnTransitioning = false
        isCourseTransitioning = false
        roundEndDelayTimer = -1f
        pendingWinner = null

        playerPoints.clear()
        players.forEach { p ->
            playerPoints[p.id] = 0
            p.score = 0
        }

        setupCourse(currentHoleIndex)
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        topSafeY = 78f * density
        bottomSafeY = height - 66f * density

        val greenMargin = 16f * density
        val greenTop = topSafeY + 74f * density
        val greenBottom = bottomSafeY - 14f * density
        greenRect.set(greenMargin, greenTop, width - greenMargin, greenBottom)

        setupCourse(currentHoleIndex)
    }

    private fun setupCourse(holeIdx: Int) {
        if (width <= 0f || height <= 0f) return

        bumpers.clear()
        obstacles.clear()

        val gw = greenRect.width()
        val gh = greenRect.height()

        // Default positions
        teeX = greenRect.left + gw * 0.5f
        teeY = greenRect.top + gh * 0.85f

        holeX = greenRect.left + gw * 0.5f
        holeY = greenRect.top + gh * 0.16f
        holeRadius = 16f * density

        when (holeIdx % 5) {
            0 -> {
                // Course 1: "Neon Fairway" - Straight fairway with 1 central glowing bumper
                val bRad = 24f * density
                bumpers.add(GolfBumper(greenRect.left + gw * 0.5f, greenRect.top + gh * 0.50f, bRad, Color.parseColor("#FF007F")))
            }
            1 -> {
                // Course 2: "Chicane Alley" - Hole shifted right, obstacles forcing bank shots
                holeX = greenRect.left + gw * 0.78f
                teeX = greenRect.left + gw * 0.22f

                val wallW = gw * 0.44f
                val wallH = 14f * density
                obstacles.add(GolfObstacle(RectF(greenRect.left + gw * 0.08f, greenRect.top + gh * 0.42f, greenRect.left + gw * 0.08f + wallW, greenRect.top + gh * 0.42f + wallH)))
                obstacles.add(GolfObstacle(RectF(greenRect.right - gw * 0.08f - wallW, greenRect.top + gh * 0.60f, greenRect.right - gw * 0.08f, greenRect.top + gh * 0.60f + wallH)))

                bumpers.add(GolfBumper(greenRect.left + gw * 0.82f, greenRect.top + gh * 0.32f, 20f * density, Color.parseColor("#00E5FF")))
            }
            2 -> {
                // Course 3: "The Gauntlet" - 3 energetic bumpers in triangle formation
                val bRad = 22f * density
                bumpers.add(GolfBumper(greenRect.left + gw * 0.30f, greenRect.top + gh * 0.45f, bRad, Color.parseColor("#FFD700")))
                bumpers.add(GolfBumper(greenRect.left + gw * 0.70f, greenRect.top + gh * 0.45f, bRad, Color.parseColor("#FFD700")))
                bumpers.add(GolfBumper(greenRect.left + gw * 0.50f, greenRect.top + gh * 0.30f, bRad, Color.parseColor("#FF2A6D")))
            }
            3 -> {
                // Course 4: "Narrow Gates" - Hole centered, gate barriers with center pass-through
                val gateW = gw * 0.36f
                val gateH = 14f * density
                obstacles.add(GolfObstacle(RectF(greenRect.left + 4f * density, greenRect.top + gh * 0.48f, greenRect.left + 4f * density + gateW, greenRect.top + gh * 0.48f + gateH)))
                obstacles.add(GolfObstacle(RectF(greenRect.right - 4f * density - gateW, greenRect.top + gh * 0.48f, greenRect.right - 4f * density, greenRect.top + gh * 0.48f + gateH)))

                // Glowing bumper guarding near the hole
                bumpers.add(GolfBumper(greenRect.left + gw * 0.5f, greenRect.top + gh * 0.30f, 18f * density, Color.parseColor("#00FF88")))
            }
            4 -> {
                // Course 5: "Championship Maze" - Angled hole on left, diamond bumpers
                holeX = greenRect.left + gw * 0.22f
                teeX = greenRect.left + gw * 0.78f

                val bRad = 20f * density
                bumpers.add(GolfBumper(greenRect.left + gw * 0.50f, greenRect.top + gh * 0.50f, bRad, Color.parseColor("#FF007F")))
                bumpers.add(GolfBumper(greenRect.left + gw * 0.22f, greenRect.top + gh * 0.36f, bRad, Color.parseColor("#00E5FF")))
                bumpers.add(GolfBumper(greenRect.left + gw * 0.78f, greenRect.top + gh * 0.36f, bRad, Color.parseColor("#FFD700")))
            }
        }

        resetBallToTee()
    }

    private fun resetBallToTee() {
        ball.x = teeX
        ball.y = teeY
        ball.vx = 0f
        ball.vy = 0f
        ball.isRolling = false
        ball.isInHole = false
        ball.isVisible = true
        isAiming = false
        botIsAiming = false
        botAimTimer = 0f

        val curPlayer = getCurrentTurnPlayer()
        if (curPlayer.isBot) {
            startBotAim()
        }
    }

    private fun getCurrentTurnPlayer(): Player {
        val idx = turnPlayerIndex.coerceIn(0, players.size - 1)
        return players[idx]
    }

    override fun update(deltaTime: Float) {
        flagWaveTimer += deltaTime * 4f

        // 1. Round completion delay before showing winner popup
        if (roundEndDelayTimer >= 0f) {
            roundEndDelayTimer -= deltaTime
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                endRound(pendingWinner)
            }
            updateVisualEffects(deltaTime)
            return
        }

        if (isGameOver) return

        // 2. Course transition delay
        if (isCourseTransitioning) {
            courseTransitionTimer -= deltaTime
            if (courseTransitionTimer <= 0f) {
                isCourseTransitioning = false
                currentHoleIndex++
                if (currentHoleIndex >= totalHoles) {
                    finishMatch()
                } else {
                    turnPlayerIndex = 0
                    currentStroke = 1
                    setupCourse(currentHoleIndex)
                }
            }
            updateVisualEffects(deltaTime)
            return
        }

        // 3. Turn transition delay
        if (isTurnTransitioning) {
            turnTransitionTimer -= deltaTime
            if (turnTransitionTimer <= 0f) {
                isTurnTransitioning = false
                advanceTurn()
            }
            updateVisualEffects(deltaTime)
            return
        }

        // 4. Bot AI Aiming & Shooting
        val curPlayer = getCurrentTurnPlayer()
        if (curPlayer.isBot && !ball.isRolling && !ball.isInHole && !isTurnTransitioning && !isCourseTransitioning) {
            updateBotAim(deltaTime)
        }

        // 5. Ball Physics update
        if (ball.isRolling) {
            updateBallPhysics(deltaTime)
        }

        // 6. Bumper pulse decay
        for (b in bumpers) {
            if (b.pulseProgress > 0f) {
                b.pulseProgress = (b.pulseProgress - deltaTime * 4f).coerceAtLeast(0f)
            }
        }

        updateVisualEffects(deltaTime)
    }

    private fun updateBallPhysics(deltaTime: Float) {
        val steps = 3
        val dt = deltaTime / steps

        for (s in 0 until steps) {
            ball.x += ball.vx * dt
            ball.y += ball.vy * dt

            // Rolling friction (grass drag)
            ball.vx *= (1f - 1.15f * dt)
            ball.vy *= (1f - 1.15f * dt)

            // Check Hole Gravity & Sinking
            val dxH = holeX - ball.x
            val dyH = holeY - ball.y
            val distH = sqrt(dxH * dxH + dyH * dyH)
            val speed = hypot(ball.vx, ball.vy)

            if (distH <= holeRadius * 1.35f) {
                val pull = (1f - distH / (holeRadius * 1.35f)) * 320f * density
                ball.vx += (dxH / distH) * pull * dt
                ball.vy += (dyH / distH) * pull * dt

                if (distH <= holeRadius * 0.75f && speed < 520f * density) {
                    ballSinkInHole()
                    return
                }
            }

            // Green Perimeter Rails Bounce
            val r = ball.radius
            if (ball.x - r < greenRect.left) {
                ball.x = greenRect.left + r
                ball.vx = -ball.vx * 0.80f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            } else if (ball.x + r > greenRect.right) {
                ball.x = greenRect.right - r
                ball.vx = -ball.vx * 0.80f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            }

            if (ball.y - r < greenRect.top) {
                ball.y = greenRect.top + r
                ball.vy = -ball.vy * 0.80f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            } else if (ball.y + r > greenRect.bottom) {
                ball.y = greenRect.bottom - r
                ball.vy = -ball.vy * 0.80f
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
            }

            // Bumper Collisions
            for (b in bumpers) {
                val dx = ball.x - b.cx
                val dy = ball.y - b.cy
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = b.radius + r

                if (dist < minDist && dist > 0.001f) {
                    val nx = dx / dist
                    val ny = dy / dist
                    ball.x = b.cx + nx * minDist
                    ball.y = b.cy + ny * minDist

                    val dot = ball.vx * nx + ball.vy * ny
                    if (dot < 0f) {
                        ball.vx -= 2f * dot * nx
                        ball.vy -= 2f * dot * ny
                        ball.vx *= 1.12f
                        ball.vy *= 1.12f
                    }

                    b.pulseProgress = 1.0f
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.8f)
                    particles.spawnExplosion(b.cx + nx * b.radius, b.cy + ny * b.radius, b.color, 16)
                    triggerVibration(40)
                }
            }

            // Obstacle Wall Collisions
            for (ob in obstacles) {
                if (ob.rect.intersects(ball.x - r, ball.y - r, ball.x + r, ball.y + r)) {
                    val cx = ob.rect.centerX()
                    val cy = ob.rect.centerY()
                    val dx = ball.x - cx
                    val dy = ball.y - cy

                    if (abs(dx) / ob.rect.width() > abs(dy) / ob.rect.height()) {
                        ball.vx = -ball.vx * 0.80f
                        ball.x = if (dx > 0) ob.rect.right + r else ob.rect.left - r
                    } else {
                        ball.vy = -ball.vy * 0.80f
                        ball.y = if (dy > 0) ob.rect.bottom + r else ob.rect.top - r
                    }
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
                }
            }
        }

        // Particle trail
        if (hypot(ball.vx, ball.vy) > 80f * density && Math.random() < 0.4) {
            particles.spawnExplosion(ball.x, ball.y, getCurrentTurnPlayer().id.color, 2)
        }

        // Ball Stopped
        if (hypot(ball.vx, ball.vy) < 18f * density) {
            ball.vx = 0f
            ball.vy = 0f
            ball.isRolling = false

            onShotEndedWithoutSink()
        }
    }

    private fun ballSinkInHole() {
        ball.vx = 0f
        ball.vy = 0f
        ball.isRolling = false
        ball.isInHole = true
        ball.x = holeX
        ball.y = holeY

        val player = getCurrentTurnPlayer()
        // 2 shots: Shot 1 = 2 Pts, Shot 2 = 1 Pt
        val (pts, title) = if (currentStroke == 1) {
            Pair(2, "HOLE IN ONE! 🌟")
        } else {
            Pair(1, "PAR! ⛳")
        }

        val curPts = (playerPoints[player.id] ?: 0) + pts
        playerPoints[player.id] = curPts
        player.score = curPts

        soundManager.play(SoundManager.SoundType.VICTORY)
        particles.spawnExplosion(holeX, holeY, player.id.color, 35)
        particles.spawnExplosion(holeX, holeY, Color.parseColor("#FFD700"), 20)
        floatingTexts.add(GolfFloatingText("$title\n+$pts Pts", holeX, holeY - 24f * density, player.id.color))
        triggerVibration(80)

        // Turn complete
        isTurnTransitioning = true
        turnTransitionTimer = 1.3f
    }

    private fun onShotEndedWithoutSink() {
        if (currentStroke < maxStrokes) {
            // Player gets shot 2 from current ball position
            currentStroke++
            val curPlayer = getCurrentTurnPlayer()
            floatingTexts.add(GolfFloatingText("Shot $currentStroke / $maxStrokes", ball.x, ball.y - 20f * density, Color.WHITE, life = 0.7f))
            if (curPlayer.isBot) {
                startBotAim()
            }
        } else {
            // Max 2 shots reached without sinking
            floatingTexts.add(GolfFloatingText("Missed Cup", ball.x, ball.y - 20f * density, Color.parseColor("#FF3B30"), life = 0.8f))
            soundManager.play(SoundManager.SoundType.TAP, 0.4f)
            isTurnTransitioning = true
            turnTransitionTimer = 1.1f
        }
    }

    private fun advanceTurn() {
        turnPlayerIndex++
        currentStroke = 1

        if (turnPlayerIndex >= players.size) {
            // All players completed this hole!
            if (currentHoleIndex + 1 >= totalHoles) {
                // Completed all 5 holes!
                finishMatch()
            } else {
                isCourseTransitioning = true
                courseTransitionTimer = 1.2f
                soundManager.play(SoundManager.SoundType.GOAL)
                val holeNum = currentHoleIndex + 1
                floatingTexts.add(GolfFloatingText("Hole $holeNum Complete!", width / 2f, height * 0.48f, Color.parseColor("#00E5FF"), life = 1.2f))
            }
        } else {
            // Next player's turn on current hole
            resetBallToTee()
        }
    }

    private fun finishMatch() {
        val maxScore = players.maxOfOrNull { playerPoints[it.id] ?: 0 } ?: 0
        val topScorers = players.filter { (playerPoints[it.id] ?: 0) == maxScore }

        // If unique top scorer, they win! If tie, pendingWinner is null (DRAW MATCH)!
        pendingWinner = if (topScorers.size == 1) topScorers.first() else null
        roundEndDelayTimer = 0.8f
        soundManager.play(SoundManager.SoundType.GOAL)
    }

    // Bot AI Logic
    private fun startBotAim() {
        botIsAiming = true
        botAimTimer = 0f

        val dx = holeX - ball.x
        val dy = holeY - ball.y
        val dist = sqrt(dx * dx + dy * dy)
        val baseAngle = atan2(dy, dx)

        val (angleErrMax, powerVariance) = when (botDifficulty) {
            0 -> Pair(0.24f, 0.30f) // Easy: ~14 deg error, 30% power variance
            2 -> Pair(0.04f, 0.06f) // Hard: ~2 deg error, very accurate
            else -> Pair(0.10f, 0.15f) // Medium
        }

        val angleOffset = (random.nextFloat() * 2f - 1f) * angleErrMax
        botTargetAngle = baseAngle + angleOffset

        val neededPower = (dist * 2.85f).coerceIn(240f * density, 850f * density)
        val powerMult = 1f + (random.nextFloat() * 2f - 1f) * powerVariance
        botTargetPower = neededPower * powerMult
    }

    private fun updateBotAim(deltaTime: Float) {
        botAimTimer += deltaTime
        if (botAimTimer >= 1.2f && !ball.isRolling && !ball.isInHole) {
            botIsAiming = false
            launchBall(cos(botTargetAngle) * botTargetPower, sin(botTargetAngle) * botTargetPower)
        }
    }

    private fun launchBall(vx: Float, vy: Float) {
        ball.vx = vx
        ball.vy = vy
        ball.isRolling = true
        soundManager.play(SoundManager.SoundType.TAP)
        triggerVibration(45)
    }

    private fun updateVisualEffects(deltaTime: Float) {
        val iter = floatingTexts.iterator()
        while (iter.hasNext()) {
            val ft = iter.next()
            ft.y -= deltaTime * 35f * density
            ft.life -= deltaTime
            ft.alpha = (ft.life / 0.85f).coerceIn(0f, 1f)
            if (ft.life <= 0f) {
                iter.remove()
            }
        }
        particles.update()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isTurnTransitioning || isCourseTransitioning || roundEndDelayTimer >= 0f) return true

        val curPlayer = getCurrentTurnPlayer()
        if (curPlayer.isBot || ball.isRolling || ball.isInHole) return true

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dx = x - ball.x
                val dy = y - ball.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= 60f * density || greenRect.contains(x, y)) {
                    isAiming = true
                    aimDragStartX = x
                    aimDragStartY = y
                    aimCurX = x
                    aimCurY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isAiming) {
                    aimCurX = x
                    aimCurY = y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isAiming) {
                    isAiming = false
                    val pullDx = aimDragStartX - aimCurX
                    val pullDy = aimDragStartY - aimCurY
                    val pullDist = hypot(pullDx, pullDy)

                    if (pullDist > 12f * density) {
                        val maxPull = 120f * density
                        val powerRatio = (pullDist / maxPull).coerceIn(0f, 1f)
                        val maxSpeed = 820f * density
                        val speed = powerRatio * maxSpeed
                        val angle = atan2(pullDy, pullDx)
                        launchBall(cos(angle) * speed, sin(angle) * speed)
                    }
                }
            }
        }
        return true
    }

    override fun render(canvas: Canvas) {
        // 1. Cosmic Background
        canvas.drawColor(Color.parseColor("#0C091A"))

        // 2. Golf Green Fairway
        drawFairway(canvas)

        // 3. Hole Cup & Pin Flag
        drawHoleAndFlag(canvas)

        // 4. Obstacles & Bumpers
        drawObstaclesAndBumpers(canvas)

        // 5. Tee off pad
        drawTeePad(canvas)

        // 6. Aiming Trajectory Line (Human or Bot)
        drawAimingGuide(canvas)

        // 7. Golf Ball
        drawBall(canvas)

        // 8. Particles & Floating Texts
        particles.draw(canvas)
        drawFloatingTexts(canvas)

        // 9. Top HUD & Player Badges
        drawTopHUDAndPlayerStations(canvas)
    }

    private fun drawFairway(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#10251C")
        canvas.drawRoundRect(greenRect, 22f * density, 22f * density, paint)

        // Fairway inner pattern
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = Color.parseColor("#193B2D")
        val gridStep = 40f * density
        var gy = greenRect.top + gridStep
        while (gy < greenRect.bottom) {
            canvas.drawLine(greenRect.left, gy, greenRect.right, gy, paint)
            gy += gridStep
        }

        // Glowing neon boundary rails
        paint.strokeWidth = 3f * density
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(greenRect, 22f * density, 22f * density, paint)
    }

    private fun drawTeePad(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B3F30")
        canvas.drawCircle(teeX, teeY, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#34C759")
        canvas.drawCircle(teeX, teeY, 14f * density, paint)
    }

    private fun drawHoleAndFlag(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#060A08")
        canvas.drawCircle(holeX, holeY, holeRadius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.parseColor("#00FF88")
        canvas.drawCircle(holeX, holeY, holeRadius, paint)

        // Flagstick
        val stickTopY = holeY - 42f * density
        paint.color = Color.WHITE
        paint.strokeWidth = 2f * density
        canvas.drawLine(holeX, holeY, holeX, stickTopY, paint)

        // Waving Flag
        val waveOffset = sin(flagWaveTimer) * 3f * density
        val flagPath = Path()
        flagPath.moveTo(holeX, stickTopY)
        flagPath.lineTo(holeX + 18f * density + waveOffset, stickTopY + 7f * density)
        flagPath.lineTo(holeX, stickTopY + 14f * density)
        flagPath.close()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FF0055")
        canvas.drawPath(flagPath, paint)
    }

    private fun drawObstaclesAndBumpers(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (ob in obstacles) {
            paint.color = ob.color
            canvas.drawRoundRect(ob.rect, 6f * density, 6f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = Color.parseColor("#5A4791")
            canvas.drawRoundRect(ob.rect, 6f * density, 6f * density, paint)
            paint.style = Paint.Style.FILL
        }

        // Pinball Bumpers
        for (b in bumpers) {
            val r = b.radius + b.pulseProgress * 6f * density
            paint.style = Paint.Style.FILL
            paint.color = b.color
            paint.alpha = 220
            canvas.drawCircle(b.cx, b.cy, r, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = Color.WHITE
            canvas.drawCircle(b.cx, b.cy, r, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(b.cx, b.cy, r * 0.45f, paint)
        }
        paint.alpha = 255
    }

    private fun drawAimingGuide(canvas: Canvas) {
        val curPlayer = getCurrentTurnPlayer()

        if (isAiming) {
            val pullDx = aimDragStartX - aimCurX
            val pullDy = aimDragStartY - aimCurY
            val pullDist = hypot(pullDx, pullDy)

            if (pullDist > 10f * density) {
                val angle = atan2(pullDy, pullDx)
                val lineLen = (pullDist * 2.2f).coerceIn(40f * density, 220f * density)
                val endX = ball.x + cos(angle) * lineLen
                val endY = ball.y + sin(angle) * lineLen

                // Dashed trajectory line
                dashPaint.color = curPlayer.id.color
                canvas.drawLine(ball.x, ball.y, endX, endY, dashPaint)

                // Arrow head at the end
                val arrowSize = 9f * density
                val arrowAngle1 = angle + Math.PI.toFloat() * 0.82f
                val arrowAngle2 = angle - Math.PI.toFloat() * 0.82f
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * density
                paint.color = curPlayer.id.color
                canvas.drawLine(endX, endY, endX + cos(arrowAngle1) * arrowSize, endY + sin(arrowAngle1) * arrowSize, paint)
                canvas.drawLine(endX, endY, endX + cos(arrowAngle2) * arrowSize, endY + sin(arrowAngle2) * arrowSize, paint)

                // Glowing halo around ball indicating power
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * density
                paint.color = curPlayer.id.color
                canvas.drawCircle(ball.x, ball.y, ball.radius + (pullDist * 0.15f), paint)

                // Trajectory power dots
                val numDots = (lineLen / (20f * density)).toInt()
                paint.style = Paint.Style.FILL
                for (i in 1..numDots) {
                    val dotDist = i * 20f * density
                    val dotX = ball.x + cos(angle) * dotDist
                    val dotY = ball.y + sin(angle) * dotDist
                    val dotAlpha = (220 * (1f - (i.toFloat() / (numDots + 1)))).toInt()
                    paint.color = curPlayer.id.color
                    paint.alpha = dotAlpha
                    canvas.drawCircle(dotX, dotY, 2.5f * density, paint)
                }
                paint.alpha = 255
            }
        } else if (botIsAiming && !ball.isRolling && !ball.isInHole) {
            val endX = ball.x + cos(botTargetAngle) * (140f * density)
            val endY = ball.y + sin(botTargetAngle) * (140f * density)
            dashPaint.color = curPlayer.id.color
            canvas.drawLine(ball.x, ball.y, endX, endY, dashPaint)

            val arrowSize = 9f * density
            val arrowAngle1 = botTargetAngle + Math.PI.toFloat() * 0.82f
            val arrowAngle2 = botTargetAngle - Math.PI.toFloat() * 0.82f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * density
            paint.color = curPlayer.id.color
            canvas.drawLine(endX, endY, endX + cos(arrowAngle1) * arrowSize, endY + sin(arrowAngle1) * arrowSize, paint)
            canvas.drawLine(endX, endY, endX + cos(arrowAngle2) * arrowSize, endY + sin(arrowAngle2) * arrowSize, paint)
        }
    }

    private fun drawBall(canvas: Canvas) {
        if (ball.isInHole || !ball.isVisible) return

        val curPlayer = getCurrentTurnPlayer()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#08140E")
        canvas.drawCircle(ball.x + 2f * density, ball.y + 3f * density, ball.radius, paint)

        paint.color = curPlayer.id.color
        canvas.drawCircle(ball.x, ball.y, ball.radius, paint)

        paint.color = Color.WHITE
        paint.alpha = 200
        canvas.drawCircle(ball.x - ball.radius * 0.3f, ball.y - ball.radius * 0.3f, ball.radius * 0.35f, paint)
        paint.alpha = 255
    }

    private fun drawFloatingTexts(canvas: Canvas) {
        textPaint.textSize = 20f * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true

        for (ft in floatingTexts) {
            textPaint.color = ft.color
            textPaint.alpha = (ft.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawText(ft.text, ft.x, ft.y, textPaint)
        }
        textPaint.alpha = 255
    }

    private fun drawTopHUDAndPlayerStations(canvas: Canvas) {
        val curPlayer = getCurrentTurnPlayer()
        val badgeW = (width * 0.38f).coerceIn(120f * density, 146f * density)
        val badgeH = 38f * density
        val topY = topSafeY
        val bottomY = bottomSafeY

        // 1. Center Turn & Progress Pill (Row 2, centered at topSafeY + 40dp)
        val infoW = 148f * density
        val infoH = 26f * density
        val infoY = topSafeY + 40f * density
        val infoRect = RectF((width - infoW) / 2f, infoY, (width + infoW) / 2f, infoY + infoH)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1A1430")
        canvas.drawRoundRect(infoRect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = curPlayer.id.color
        canvas.drawRoundRect(infoRect, 10f * density, 10f * density, paint)

        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 12f * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        val holeNum = (currentHoleIndex + 1).coerceAtMost(totalHoles)
        val shotNum = currentStroke.coerceAtMost(maxStrokes)
        canvas.drawText("HOLE $holeNum/$totalHoles • SHOT $shotNum/$maxStrokes", infoRect.centerX(), infoRect.centerY() + 4f * density, textPaint)

        // 2. Player Corner Badges
        when (players.size) {
            2 -> {
                drawSinglePlayerStation(canvas, players[1], width - badgeW - 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[0], (width - badgeW) / 2f, bottomY, badgeW, badgeH)
            }
            3 -> {
                drawSinglePlayerStation(canvas, players[1], 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[2], width - badgeW - 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[0], (width - badgeW) / 2f, bottomY, badgeW, badgeH)
            }
            4 -> {
                drawSinglePlayerStation(canvas, players[1], 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[2], width - badgeW - 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[0], 14f * density, bottomY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[3], width - badgeW - 14f * density, bottomY, badgeW, badgeH)
            }
            else -> {
                if (players.size > 1) {
                    drawSinglePlayerStation(canvas, players[1], width - badgeW - 14f * density, topY, badgeW, badgeH)
                }
                drawSinglePlayerStation(canvas, players[0], (width - badgeW) / 2f, bottomY, badgeW, badgeH)
            }
        }
    }

    private fun drawSinglePlayerStation(
        canvas: Canvas,
        player: Player,
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val rect = RectF(x, y, x + w, y + h)
        val isCurrentTurn = (player == getCurrentTurnPlayer())

        paint.style = Paint.Style.FILL
        paint.color = if (isCurrentTurn) Color.parseColor("#2E1E4D") else Color.parseColor("#1E1733")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isCurrentTurn) 2.5f * density else 1.2f * density
        paint.color = player.id.color
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        // Player Initial
        val iconR = h * 0.34f
        val iconCx = x + iconR + 8f * density
        val iconCy = y + h * 0.5f

        paint.style = Paint.Style.FILL
        paint.color = player.id.color
        canvas.drawCircle(iconCx, iconCy, iconR, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f * density
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        val initial = if (player.isBot) "🤖" else player.id.displayName.take(2).uppercase()
        canvas.drawText(initial, iconCx, iconCy + 3.5f * density, textPaint)

        // Name & Score
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 9.5f * density
        textPaint.color = Color.parseColor("#B0A4D0")
        val pName = getPlayerDisplayName(player)
        canvas.drawText(pName, iconCx + iconR + 6f * density, y + h * 0.40f, textPaint)

        val pts = playerPoints[player.id] ?: 0
        textPaint.textSize = 12f * density
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        canvas.drawText("$pts Pts ⛳", iconCx + iconR + 6f * density, y + h * 0.82f, textPaint)
    }

    override fun getPlayerScoreString(player: Player): String {
        val pts = playerPoints[player.id] ?: 0
        return "$pts ${if (pts == 1) "Pt" else "Pts"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return playerPoints[player.id] ?: 0
    }
}
