package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class SlitherSegment(
    var x: Float,
    var y: Float
)

data class SlitherPellet(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Int,
    val value: Int = 1
)

class SlitherSnake(
    val name: String,
    val isPlayer: Boolean,
    var x: Float,
    var y: Float,
    var angleRad: Float,
    val colorPalette: List<Int>,
    var radius: Float = 14f
) {
    var targetAngleRad: Float = angleRad
    var speed: Float = 150f
    var isBoosting: Boolean = false
    var isDead: Boolean = false
    var score: Int = 10
    val segments = mutableListOf<SlitherSegment>()

    // Eye direction tracking
    var eyeLookAngle: Float = angleRad

    init {
        // Initial body segments
        val segmentCount = 18
        val spacing = radius * 0.72f
        for (i in 0 until segmentCount) {
            val sx = x - cos(angleRad) * (i * spacing)
            val sy = y - sin(angleRad) * (i * spacing)
            segments.add(SlitherSegment(sx, sy))
        }
    }
}

class SlitherSnakeEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density

    // World size (larger arena with camera following player)
    private var worldW = 2200f * density
    private var worldH = 2800f * density

    // Camera viewport offset
    private var cameraX = 0f
    private var cameraY = 0f

    // Snakes
    private var playerSnake: SlitherSnake? = null
    private val botSnakes = mutableListOf<SlitherSnake>()

    // Food Pellets
    private val pellets = mutableListOf<SlitherPellet>()
    private val maxPellets = 240

    // Touch controls
    private var isTouchingScreen = false
    private var touchScreenX = 0f
    private var touchScreenY = 0f

    // Boost Button at bottom right
    private var boostBtnX = 0f
    private var boostBtnY = 0f
    private var boostBtnRadius = 34f * density
    private var isBoostPressed = false

    // Rendering Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    private var boostDropTimer = 0f
    private var botRespawnTimer = 0f
    var invulnerabilityTimer = 0f
    var onPlayerDiedListener: ((score: Int, rank: Int) -> Unit)? = null

    // Pre-allocated palette colors matching snack_game.mp4
    private val pelletColors = listOf(
        Color.parseColor("#FFD54F"), // Amber Gold
        Color.parseColor("#00E5FF"), // Bright Cyan
        Color.parseColor("#FF2A6D"), // Hot Pink
        Color.parseColor("#00E676"), // Neon Emerald
        Color.parseColor("#FF9100"), // Flame Orange
        Color.parseColor("#AB47BC"), // Purple
        Color.parseColor("#FFFFFF"), // Pure White
        Color.parseColor("#FFEE58")  // Sunny Yellow
    )

    private val playerPalette = listOf(
        Color.parseColor("#00E5FF"),
        Color.parseColor("#00F5A0"),
        Color.parseColor("#00D2FF"),
        Color.parseColor("#26FF8A")
    )

    private val botSkins = listOf(
        listOf(Color.parseColor("#FF2A6D"), Color.parseColor("#FF6584"), Color.parseColor("#FF8FA3")), // Pink gradient
        listOf(Color.parseColor("#00E676"), Color.parseColor("#76FF03"), Color.parseColor("#B2FF59")), // Lime/Green
        listOf(Color.parseColor("#FF9100"), Color.parseColor("#FFB74D"), Color.parseColor("#FFD54F")), // Orange/Yellow
        listOf(Color.parseColor("#AB47BC"), Color.parseColor("#CE93D8"), Color.parseColor("#E1BEE7")), // Purple
        listOf(Color.parseColor("#00E5FF"), Color.parseColor("#FF2A6D"), Color.parseColor("#FFD54F")), // Rainbow
        listOf(Color.parseColor("#2979FF"), Color.parseColor("#448AFF"), Color.parseColor("#82B1FF"))  // Blue
    )

    private val botNames = listOf("Charlie", "Oliver", "Alex", "Max", "Leo", "Bella", "Sam", "Luna")

    override fun calculateControlZones() {
        controlZones.clear() // No corner multiplayer buttons; virtual joystick/touch everywhere
    }

    override fun initGame() {
        isGameOver = false
        winner = null
        invulnerabilityTimer = 0f
        botSnakes.clear()
        pellets.clear()

        worldW = max(width * 2.6f, 1800f * density)
        worldH = max(height * 2.6f, 2400f * density)

        // 1. Spawn Player Snake in center of world
        val pName = if (playerNames.isNotEmpty() && playerNames[0].isNotBlank()) playerNames[0] else "You"
        val pSnake = SlitherSnake(
            name = pName,
            isPlayer = true,
            x = worldW / 2f,
            y = worldH / 2f,
            angleRad = -PI.toFloat() / 2f,
            colorPalette = playerPalette,
            radius = 13.5f * density
        )
        playerSnake = pSnake

        // 2. Spawn 6 AI Bot Snakes around the arena
        spawnInitialBots()

        // 3. Populate world with food pellets
        populatePellets(maxPellets)
    }

    private fun spawnInitialBots() {
        val botCount = 6
        for (i in 0 until botCount) {
            spawnOneBot(i)
        }
    }

    private fun spawnOneBot(index: Int) {
        val skin = botSkins[index % botSkins.size]
        val name = botNames[index % botNames.size]
        val margin = 200f * density
        val bx = random.nextFloat() * (worldW - 2 * margin) + margin
        val by = random.nextFloat() * (worldH - 2 * margin) + margin
        val bAngle = random.nextFloat() * 2f * PI.toFloat()

        val bot = SlitherSnake(
            name = name,
            isPlayer = false,
            x = bx,
            y = by,
            angleRad = bAngle,
            colorPalette = skin,
            radius = (12f + (index % 3) * 1.5f) * density
        )
        // Give some bots initial growth variation
        val initialGrowth = (index % 4) * 8
        for (g in 0 until initialGrowth) {
            growSnake(bot, 1)
        }
        botSnakes.add(bot)
    }

    private fun populatePellets(count: Int) {
        val margin = 40f * density
        for (i in 0 until count) {
            val px = random.nextFloat() * (worldW - 2 * margin) + margin
            val py = random.nextFloat() * (worldH - 2 * margin) + margin
            val pr = (4.5f + random.nextFloat() * 3.5f) * density
            val color = pelletColors[random.nextInt(pelletColors.size)]
            pellets.add(SlitherPellet(px, py, pr, color, 1))
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        boostBtnX = width - 48f * density
        boostBtnY = height - 58f * density
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val px = event.getX(pointerIndex)
                val py = event.getY(pointerIndex)

                // Check Boost Button Tap
                if (hypot(px - boostBtnX, py - boostBtnY) <= boostBtnRadius * 1.35f) {
                    isBoostPressed = true
                    triggerVibration(25)
                } else {
                    isTouchingScreen = true
                    touchScreenX = px
                    touchScreenY = py
                }
            }
            MotionEvent.ACTION_MOVE -> {
                var boostFound = false
                for (i in 0 until event.pointerCount) {
                    val px = event.getX(i)
                    val py = event.getY(i)
                    if (hypot(px - boostBtnX, py - boostBtnY) <= boostBtnRadius * 1.35f) {
                        boostFound = true
                    } else {
                        touchScreenX = px
                        touchScreenY = py
                        isTouchingScreen = true
                    }
                }
                isBoostPressed = boostFound
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val px = event.getX(pointerIndex)
                val py = event.getY(pointerIndex)
                if (hypot(px - boostBtnX, py - boostBtnY) <= boostBtnRadius * 1.35f) {
                    isBoostPressed = false
                }
                if (event.pointerCount <= 1) {
                    isTouchingScreen = false
                    isBoostPressed = false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isTouchingScreen = false
                isBoostPressed = false
            }
        }
        return true
    }

    override fun update(deltaTime: Float) {
        val p = playerSnake ?: return
        if (isGameOver) {
            particles.update()
            return
        }

        if (invulnerabilityTimer > 0f) {
            invulnerabilityTimer -= deltaTime
        }

        // 1. Update Player Snake Steering
        if (!p.isDead) {
            p.isBoosting = isBoostPressed && p.score > 12
            p.speed = if (p.isBoosting) 275f * density else 155f * density

            if (isTouchingScreen) {
                // Steer towards touch position in world space
                val worldTouchX = cameraX + touchScreenX
                val worldTouchY = cameraY + touchScreenY
                val dx = worldTouchX - p.x
                val dy = worldTouchY - p.y
                p.targetAngleRad = atan2(dy, dx)
            }

            // Smooth angular interpolation
            p.angleRad = lerpAngle(p.angleRad, p.targetAngleRad, 6.5f * deltaTime)
            p.eyeLookAngle = p.angleRad

            // If boosting, drop tiny pellets behind tail
            if (p.isBoosting) {
                boostDropTimer += deltaTime
                if (boostDropTimer >= 0.12f) {
                    boostDropTimer = 0f
                    if (p.segments.size > 14) {
                        val tail = p.segments.last()
                        pellets.add(SlitherPellet(tail.x, tail.y, 4f * density, p.colorPalette.first(), 1))
                        p.score = max(10, p.score - 1)
                        if (p.segments.size > 12) p.segments.removeAt(p.segments.lastIndex)
                    }
                }
            }
        }

        // 2. Move Player Snake & Constrain Segments
        updateSnakeMotion(p, deltaTime)

        // 3. Update AI Bots
        val botIterator = botSnakes.iterator()
        while (botIterator.hasNext()) {
            val bot = botIterator.next()
            if (bot.isDead) continue

            updateBotAI(bot, deltaTime)
            updateSnakeMotion(bot, deltaTime)
        }

        // 4. Check Arena Boundary Collisions
        val allSnakes = mutableListOf<SlitherSnake>()
        if (!p.isDead) allSnakes.add(p)
        allSnakes.addAll(botSnakes.filter { !it.isDead })

        allSnakes.forEach { s ->
            if (s.isPlayer && invulnerabilityTimer > 0f) return@forEach
            val wallMargin = s.radius
            if (s.x <= wallMargin || s.x >= worldW - wallMargin || s.y <= wallMargin || s.y >= worldH - wallMargin) {
                killSnake(s)
            }
        }

        // 5. Check Head-to-Body Collisions (crashing head into another snake's body kills you; touching own body does NOT kill)
        for (snake in allSnakes) {
            if (snake.isDead) continue
            if (snake.isPlayer && invulnerabilityTimer > 0f) continue

            for (other in allSnakes) {
                if (other.isDead || other == snake) continue

                // Check collision against another snake's body segments
                if (other.segments.size > 2) {
                    for (i in 2 until other.segments.size) {
                        val seg = other.segments[i]
                        val dist = hypot(snake.x - seg.x, snake.y - seg.y)
                        val collisionThreshold = (snake.radius + other.radius) * 0.85f
                        if (dist < collisionThreshold) {
                            killSnake(snake)
                            break
                        }
                    }
                }
                if (snake.isDead) break
            }
        }

        // 6. Check Eating Pellets
        for (snake in allSnakes) {
            if (snake.isDead) continue

            val pelletIter = pellets.iterator()
            while (pelletIter.hasNext()) {
                val pel = pelletIter.next()
                if (hypot(snake.x - pel.x, snake.y - pel.y) < snake.radius + pel.radius + 6f * density) {
                    pelletIter.remove()
                    growSnake(snake, pel.value)

                    if (snake.isPlayer) {
                        soundManager.play(SoundManager.SoundType.TAP, 0.4f)
                        if (snake.score % 15 == 0) triggerVibration(20)
                    }
                }
            }
        }

        // Maintain min pellets
        if (pellets.size < maxPellets) {
            populatePellets(maxPellets - pellets.size)
        }

        // 7. Respawn dead bots periodically
        botRespawnTimer += deltaTime
        if (botRespawnTimer >= 2.5f) {
            botRespawnTimer = 0f
            if (botSnakes.count { !it.isDead } < 6) {
                val deadIndex = botSnakes.indexOfFirst { it.isDead }
                if (deadIndex != -1) {
                    botSnakes.removeAt(deadIndex)
                    spawnOneBot(random.nextInt(100))
                }
            }
        }

        // 8. Smooth Camera tracking on player
        val targetCamX = p.x - width / 2f
        val targetCamY = p.y - height / 2f
        cameraX += (targetCamX - cameraX) * (8f * deltaTime)
        cameraY += (targetCamY - cameraY) * (8f * deltaTime)

        particles.update()
    }

    private fun updateSnakeMotion(snake: SlitherSnake, deltaTime: Float) {
        if (snake.isDead) return

        // Forward motion
        val moveStep = snake.speed * deltaTime
        snake.x += cos(snake.angleRad) * moveStep
        snake.y += sin(snake.angleRad) * moveStep

        // Smooth distance constraint for all trailing body segments
        val spacing = snake.radius * 0.68f
        var prevX = snake.x
        var prevY = snake.y

        for (seg in snake.segments) {
            val dx = seg.x - prevX
            val dy = seg.y - prevY
            val dist = hypot(dx, dy)
            if (dist > 0.001f) {
                val factor = spacing / dist
                seg.x = prevX + dx * factor
                seg.y = prevY + dy * factor
            }
            prevX = seg.x
            prevY = seg.y
        }
    }

    private fun growSnake(snake: SlitherSnake, amount: Int) {
        snake.score += amount
        val tail = snake.segments.lastOrNull() ?: SlitherSegment(snake.x, snake.y)
        for (i in 0 until amount) {
            snake.segments.add(SlitherSegment(tail.x, tail.y))
        }
        // Very subtle radius growth up to max cap
        snake.radius = (13.5f * density + min(200, snake.score) * 0.035f * density).coerceAtMost(22f * density)
    }

    private fun killSnake(snake: SlitherSnake) {
        if (snake.isDead) return
        snake.isDead = true

        particles.spawnExplosion(snake.x, snake.y, snake.colorPalette.first(), 50)
        soundManager.play(SoundManager.SoundType.EXPLOSION)
        triggerVibration(90)

        // Turn body segments into large, glowing death food orbs!
        snake.segments.forEachIndexed { idx, seg ->
            if (idx % 2 == 0) {
                val color = snake.colorPalette[idx % snake.colorPalette.size]
                val r = (7.5f + random.nextFloat() * 3f) * density
                pellets.add(SlitherPellet(seg.x, seg.y, r, color, value = 3))
            }
        }

        // If player died, trigger game over callback
        if (snake.isPlayer) {
            val allSnakes = mutableListOf<SlitherSnake>()
            allSnakes.add(snake)
            allSnakes.addAll(botSnakes)
            val rank = allSnakes.sortedByDescending { it.score }.indexOf(snake) + 1
            postDelayedGameOver(snake.score, rank)
        }
    }

    private fun postDelayedGameOver(score: Int, rank: Int) {
        // Wait ~950ms so player can watch explosion and death orbs scatter
        Thread {
            try {
                Thread.sleep(950)
            } catch (_: Exception) {}
            if (onPlayerDiedListener != null) {
                onPlayerDiedListener?.invoke(score, rank)
            } else {
                val topBot = botSnakes.filter { !it.isDead }.maxByOrNull { it.score }
                val winningPlayer = if (topBot != null) Player(id = PlayerId.PLAYER_2, isBot = true) else null
                endRound(winningPlayer)
            }
        }.start()
    }

    fun revivePlayer() {
        val p = playerSnake ?: return
        p.isDead = false
        isGameOver = false

        // Reposition to a safe open area near the center of the world
        p.x = worldW / 2f + (random.nextFloat() - 0.5f) * 160f * density
        p.y = worldH / 2f + (random.nextFloat() - 0.5f) * 160f * density
        p.targetAngleRad = -PI.toFloat() / 2f
        p.angleRad = p.targetAngleRad
        p.eyeLookAngle = p.angleRad

        // Reconstitute snake body segments
        val segmentCount = max(18, p.segments.size)
        val spacing = p.radius * 0.72f
        p.segments.clear()
        for (i in 0 until segmentCount) {
            val sx = p.x - cos(p.angleRad) * (i * spacing)
            val sy = p.y - sin(p.angleRad) * (i * spacing)
            p.segments.add(SlitherSegment(sx, sy))
        }

        // Grant 3.5 seconds of protective invulnerability shield
        invulnerabilityTimer = 3.5f
        soundManager.play(SoundManager.SoundType.GOAL)
        triggerVibration(60)
    }

    private fun updateBotAI(bot: SlitherSnake, deltaTime: Float) {
        // Find nearest food pellet
        var closestDist = Float.MAX_VALUE
        var targetX = worldW / 2f
        var targetY = worldH / 2f

        for (i in 0 until min(40, pellets.size)) {
            val pel = pellets[i]
            val d = hypot(bot.x - pel.x, bot.y - pel.y)
            if (d < closestDist) {
                closestDist = d
                targetX = pel.x
                targetY = pel.y
            }
        }

        var desiredAngle = atan2(targetY - bot.y, targetX - bot.x)

        // Avoid arena boundary walls
        val wallAlert = 140f * density
        if (bot.x < wallAlert) desiredAngle = 0f
        else if (bot.x > worldW - wallAlert) desiredAngle = PI.toFloat()
        else if (bot.y < wallAlert) desiredAngle = PI.toFloat() / 2f
        else if (bot.y > worldH - wallAlert) desiredAngle = -PI.toFloat() / 2f

        // Raycast forward to avoid other snakes' bodies
        val forwardX = bot.x + cos(bot.angleRad) * 45f * density
        val forwardY = bot.y + sin(bot.angleRad) * 45f * density

        val allSnakes = mutableListOf<SlitherSnake>()
        playerSnake?.let { if (!it.isDead) allSnakes.add(it) }
        allSnakes.addAll(botSnakes.filter { !it.isDead && it != bot })

        for (other in allSnakes) {
            for (seg in other.segments) {
                if (hypot(forwardX - seg.x, forwardY - seg.y) < (bot.radius + other.radius) * 1.5f) {
                    // Turn sharply away
                    desiredAngle = bot.angleRad + PI.toFloat() / 2f
                    break
                }
            }
        }

        val turnRate = when (botDifficulty) {
            0 -> 3.5f
            2 -> 6.5f
            else -> 5.0f
        }
        bot.targetAngleRad = desiredAngle
        bot.angleRad = lerpAngle(bot.angleRad, bot.targetAngleRad, turnRate * deltaTime)
        bot.eyeLookAngle = bot.angleRad
    }

    private fun lerpAngle(current: Float, target: Float, step: Float): Float {
        var diff = (target - current) % (2f * PI.toFloat())
        if (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
        if (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
        return current + diff.coerceIn(-step, step)
    }

    override fun render(canvas: Canvas) {
        val p = playerSnake ?: return

        // 1. Draw World Background & Wiggle Pattern
        canvas.drawColor(Color.parseColor("#0E0B1C"))

        canvas.save()
        canvas.translate(-cameraX, -cameraY)

        // Draw Arena Boundary Neon Glow
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f * density
        paint.color = Color.parseColor("#FF2A6D")
        canvas.drawRect(0f, 0f, worldW, worldH, paint)

        // Outer fence glow
        paint.strokeWidth = 14f * density
        paint.color = Color.parseColor("#33FF2A6D")
        canvas.drawRect(0f, 0f, worldW, worldH, paint)

        // Draw ambient squiggly doodle grid lines (like in video)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.parseColor("#181432")
        val gridSize = 160f * density
        var gx = 0f
        while (gx < worldW) {
            var gy = 0f
            while (gy < worldH) {
                canvas.drawCircle(gx + 40f * density, gy + 40f * density, 8f * density, paint)
                gy += gridSize
            }
            gx += gridSize
        }

        // 2. Draw Food Pellets
        paint.style = Paint.Style.FILL
        for (pel in pellets) {
            // Viewport culling for high FPS
            if (pel.x < cameraX - 20f || pel.x > cameraX + width + 20f ||
                pel.y < cameraY - 20f || pel.y > cameraY + height + 20f) continue

            // Glowing halo
            paint.color = (pel.color and 0x00FFFFFF) or 0x55000000
            canvas.drawCircle(pel.x, pel.y, pel.radius * 1.55f, paint)

            // Inner core
            paint.color = pel.color
            canvas.drawCircle(pel.x, pel.y, pel.radius, paint)
        }

        // 3. Draw AI Bot Snakes
        botSnakes.forEach { bot ->
            if (!bot.isDead) drawSnake(canvas, bot)
        }

        // 4. Draw Player Snake
        if (!p.isDead) {
            drawSnake(canvas, p)
        }

        // 5. Draw Particles in world space
        particles.draw(canvas)

        canvas.restore()

        // 6. Draw Screen-Space HUD Elements
        drawHUD(canvas, p)
    }

    private fun drawSnake(canvas: Canvas, snake: SlitherSnake) {
        val segCount = snake.segments.size

        // Draw segments from tail to head
        paint.style = Paint.Style.FILL
        for (i in segCount - 1 downTo 0) {
            val seg = snake.segments[i]
            val color = snake.colorPalette[i % snake.colorPalette.size]

            // Segment shadow / border
            paint.color = Color.parseColor("#0A0814")
            canvas.drawCircle(seg.x, seg.y, snake.radius + 1.2f * density, paint)

            // Segment body
            paint.color = color
            canvas.drawCircle(seg.x, seg.y, snake.radius, paint)
        }

        // Draw Head
        paint.color = Color.parseColor("#0A0814")
        canvas.drawCircle(snake.x, snake.y, snake.radius + 1.5f * density, paint)
        paint.color = snake.colorPalette.first()
        canvas.drawCircle(snake.x, snake.y, snake.radius, paint)

        // Draw Cute Cartoon Eyes looking in direction of motion!
        drawSnakeEyes(canvas, snake)

        // Draw Invulnerability Shield Bubble when revived
        if (snake.isPlayer && invulnerabilityTimer > 0f) {
            val pulse = 0.5f + 0.5f * sin(System.currentTimeMillis() * 0.012f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f * density
            paint.color = Color.parseColor("#00E5FF")
            paint.alpha = (pulse * 200 + 40).toInt()
            canvas.drawCircle(snake.x, snake.y, snake.radius * 1.55f, paint)

            paint.strokeWidth = 1.6f * density
            paint.color = Color.parseColor("#FFD54F")
            canvas.drawCircle(snake.x, snake.y, snake.radius * 1.82f, paint)
            paint.alpha = 255
        }

        // Draw Snake Name Tag underneath
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#B3000000")
        val nameText = if (snake.isPlayer) "YOU" else snake.name
        paint.textSize = 10f * density
        paint.textAlign = Paint.Align.CENTER
        val nameW = paint.measureText(nameText) + 12f * density
        val nameH = 16f * density
        val nameY = snake.y + snake.radius + 14f * density
        val tagRect = RectF(snake.x - nameW / 2f, nameY - nameH / 2f, snake.x + nameW / 2f, nameY + nameH / 2f)
        canvas.drawRoundRect(tagRect, 8f * density, 8f * density, paint)

        paint.color = if (snake.isPlayer) Color.parseColor("#00E5FF") else Color.WHITE
        paint.isFakeBoldText = true
        canvas.drawText(nameText, snake.x, nameY + 3.5f * density, paint)
        paint.isFakeBoldText = false
    }

    private fun drawSnakeEyes(canvas: Canvas, snake: SlitherSnake) {
        val eyeDist = snake.radius * 0.58f
        val eyeRadius = snake.radius * 0.40f
        val pupilRadius = eyeRadius * 0.52f

        val headAngle = snake.angleRad
        val perpAngle = headAngle + PI.toFloat() / 2f

        // Left & Right eye center positions
        val lx = snake.x + cos(headAngle) * (snake.radius * 0.35f) + cos(perpAngle) * eyeDist
        val ly = snake.y + sin(headAngle) * (snake.radius * 0.35f) + sin(perpAngle) * eyeDist

        val rx = snake.x + cos(headAngle) * (snake.radius * 0.35f) - cos(perpAngle) * eyeDist
        val ry = snake.y + sin(headAngle) * (snake.radius * 0.35f) - sin(perpAngle) * eyeDist

        // White eye sclera
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(lx, ly, eyeRadius, paint)
        canvas.drawCircle(rx, ry, eyeRadius, paint)

        // Black pupils looking forward
        val pupilOffset = eyeRadius * 0.32f
        val plx = lx + cos(headAngle) * pupilOffset
        val ply = ly + sin(headAngle) * pupilOffset
        val prx = rx + cos(headAngle) * pupilOffset
        val pry = ry + sin(headAngle) * pupilOffset

        paint.color = Color.BLACK
        canvas.drawCircle(plx, ply, pupilRadius, paint)
        canvas.drawCircle(prx, pry, pupilRadius, paint)

        // Cute white shine sparkle in pupils
        paint.color = Color.WHITE
        val shineR = pupilRadius * 0.38f
        canvas.drawCircle(plx - shineR * 0.6f, ply - shineR * 0.6f, shineR, paint)
        canvas.drawCircle(prx - shineR * 0.6f, pry - shineR * 0.6f, shineR, paint)
    }

    private fun drawHUD(canvas: Canvas, player: SlitherSnake) {
        // 1. Top-Center Score Display (Matching snack_game.mp4)
        val scoreBoxW = 100f * density
        val scoreBoxH = 40f * density
        val scoreBoxL = (width - scoreBoxW) / 2f
        val scoreBoxT = 65f * density
        val scoreRect = RectF(scoreBoxL, scoreBoxT, scoreBoxL + scoreBoxW, scoreBoxT + scoreBoxH)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CC181330")
        canvas.drawRoundRect(scoreRect, 16f * density, 16f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#3B2E66")
        canvas.drawRoundRect(scoreRect, 16f * density, 16f * density, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = 20f * density
        paint.isFakeBoldText = true
        val scoreFm = paint.fontMetrics
        val scoreBaseline = scoreRect.centerY() - (scoreFm.ascent + scoreFm.descent) / 2f
        canvas.drawText("${player.score}", scoreRect.centerX(), scoreBaseline, paint)
        paint.isFakeBoldText = false

        // 2. Top-Right Live Leaderboard (Matching snack_game.mp4)
        val allSnakes = mutableListOf<SlitherSnake>()
        if (!player.isDead) allSnakes.add(player)
        allSnakes.addAll(botSnakes.filter { !it.isDead })
        val leaderboard = allSnakes.sortedByDescending { it.score }.take(5)

        val lbW = 120f * density
        val lbH = (22f + leaderboard.size * 18f) * density
        val lbR = width - 16f * density
        val lbL = lbR - lbW
        val lbT = 65f * density
        val lbRect = RectF(lbL, lbT, lbR, lbT + lbH)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#B3120E26")
        canvas.drawRoundRect(lbRect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = Color.parseColor("#2E2352")
        canvas.drawRoundRect(lbRect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.FILL
        leaderboard.forEachIndexed { rank, s ->
            val rowY = lbT + (20f + rank * 17f) * density
            paint.textSize = 9.5f * density
            paint.textAlign = Paint.Align.LEFT
            paint.color = if (s.isPlayer) Color.parseColor("#FFD54F") else Color.parseColor("#C3BADB")
            paint.isFakeBoldText = s.isPlayer

            val rankPrefix = "${rank + 1}. "
            val displayName = if (s.isPlayer) "You" else s.name
            val truncatedName = if (displayName.length > 7) displayName.substring(0, 6) + "…" else displayName
            canvas.drawText("$rankPrefix$truncatedName", lbL + 8f * density, rowY, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${s.score}", lbR - 8f * density, rowY, paint)
        }
        paint.isFakeBoldText = false

        // 3. Leader Crown Indicator (Points to #1 Snake if off-screen)
        val leader = leaderboard.firstOrNull()
        if (leader != null && leader != player && !leader.isDead) {
            val screenLX = leader.x - cameraX
            val screenLY = leader.y - cameraY
            val isOffscreen = screenLX < 30f || screenLX > width - 30f || screenLY < 30f || screenLY > height - 30f

            if (isOffscreen) {
                // Draw edge indicator with Crown
                val angleToLeader = atan2(leader.y - player.y, leader.x - player.x)
                val indX = (width / 2f + cos(angleToLeader) * (width * 0.38f)).coerceIn(40f * density, width - 40f * density)
                val indY = (height / 2f + sin(angleToLeader) * (height * 0.35f)).coerceIn(80f * density, height - 90f * density)

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#D9231846")
                canvas.drawCircle(indX, indY, 16f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f * density
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawCircle(indX, indY, 16f * density, paint)

                paint.style = Paint.Style.FILL
                paint.textSize = 14f * density
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("👑", indX, indY + 5f * density, paint)
            }
        }

        // 4. Boost Button (Bottom-Right, matching snack_game.mp4)
        if (!player.isDead) {
            paint.style = Paint.Style.FILL
            paint.color = if (isBoostPressed) Color.parseColor("#CCFF2A6D") else Color.parseColor("#AA1F183C")
            canvas.drawCircle(boostBtnX, boostBtnY, boostBtnRadius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isBoostPressed) 2.5f * density else 1.5f * density
            paint.color = if (isBoostPressed) Color.parseColor("#FF2A6D") else Color.parseColor("#4B3A7A")
            canvas.drawCircle(boostBtnX, boostBtnY, boostBtnRadius, paint)

            // Double Chevron Boost Icon (⏫)
            paint.style = Paint.Style.FILL
            paint.color = if (isBoostPressed) Color.WHITE else Color.parseColor("#8E82B4")
            paint.textSize = 24f * density
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = boostBtnY - (fm.ascent + fm.descent) / 2f
            canvas.drawText("⏫", boostBtnX, baseline, paint)
        }
    }
}
