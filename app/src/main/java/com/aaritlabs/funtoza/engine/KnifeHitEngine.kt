package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class EmbeddedKnife(
    val owner: PlayerId,
    val angleOnTargetDeg: Float
)

data class FlyingKnife(
    val player: Player,
    var x: Float,
    var y: Float,
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val angleDeg: Float,
    var speed: Float = 1400f
)

class KnifeHitEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density

    // Target geometry
    private var targetRadius = 78f * density
    private var targetAngleDeg = 0f
    private var targetRotSpeed = 95f // deg/s
    private var rotTimer = 0f
    private var pulseTimer = 0f

    // Turn management: one-by-one play (like Tic Tac Toe)
    private var currentTurnIndex = 0
    private var isKnifeFlying = false
    private var turnTransitionTimer = 0f
    private var botThinkTimer = 0f
    private var botTurnElapsed = 0f

    // Knives state
    private val embeddedKnives = mutableListOf<EmbeddedKnife>()
    private val flyingKnives = mutableListOf<FlyingKnife>()
    private val playerKnifeCounts = mutableMapOf<PlayerId, Int>()
    private val maxTargetArrows = 15

    // Layout dimensions
    private var topCardTop = 0f
    private var bottomCardTop = 0f
    private var cardH = 0f
    private var cardW = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    // Aiming dash effect
    private val dashPathEffect = DashPathEffect(floatArrayOf(12f * density, 8f * density), 0f)

    override fun calculateControlZones() {
        controlZones.clear()
        if (width <= 0f || height <= 0f) return

        val topHudMargin = 82f * density
        val bottomMargin = 26f * density
        val badgeH = 62f * density
        val badgeW = (width - 44f * density) / 2f
        val topBadgeY = topHudMargin + 8f * density
        val bottomBadgeY = height - bottomMargin - badgeH

        // For 3P & 4P, define corner control zones
        if (totalPlayers >= 3) {
            // Player 3: Top-Left
            controlZones[PlayerId.PLAYER_3] = RectF(16f * density, topBadgeY, 16f * density + badgeW, topBadgeY + badgeH)
            // Player 2: Top-Right
            controlZones[PlayerId.PLAYER_2] = RectF(width - 16f * density - badgeW, topBadgeY, width - 16f * density, topBadgeY + badgeH)

            if (totalPlayers >= 4) {
                // Player 1: Bottom-Left
                controlZones[PlayerId.PLAYER_1] = RectF(16f * density, bottomBadgeY, 16f * density + badgeW, bottomBadgeY + badgeH)
                // Player 4: Bottom-Right
                controlZones[PlayerId.PLAYER_4] = RectF(width - 16f * density - badgeW, bottomBadgeY, width - 16f * density, bottomBadgeY + badgeH)
            } else {
                // 3 Players: Player 1 Bottom-Center
                val p1W = badgeW.coerceAtMost(220f * density)
                controlZones[PlayerId.PLAYER_1] = RectF((width - p1W) / 2f, bottomBadgeY, (width + p1W) / 2f, bottomBadgeY + badgeH)
            }
        }
    }

    override fun initGame() {
        embeddedKnives.clear()
        flyingKnives.clear()
        playerKnifeCounts.clear()
        isGameOver = false
        isKnifeFlying = false
        turnTransitionTimer = 0f
        botThinkTimer = 0f
        botTurnElapsed = 0f
        currentTurnIndex = 0

        targetRadius = 78f * density

        players.forEach {
            playerKnifeCounts[it.id] = 0
            it.isAlive = true
        }

        val firstP = players.getOrNull(0)
        if (firstP != null && firstP.isBot) {
            botThinkTimer = getBotInitialDelay()
        }
    }

    private fun getBotInitialDelay(): Float {
        return when (botDifficulty) {
            0 -> 0.75f
            2 -> 0.42f
            else -> 0.58f
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        computeLayoutGeometry()
    }

    private fun computeLayoutGeometry() {
        if (width <= 0f || height <= 0f) return

        cardH = if (totalPlayers <= 2) 74f * density else 62f * density
        cardW = (width - 32f * density).coerceAtMost(500f * density)

        val topHudMargin = 82f * density
        val bottomMargin = 26f * density

        topCardTop = topHudMargin + 8f * density
        val bottomCardBottom = height - bottomMargin
        bottomCardTop = bottomCardBottom - cardH
    }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        return players[currentTurnIndex % players.size]
    }

    /**
     * Compute firing position and angle for any player:
     * - Bottom side (P1 and P4): shown at bottom center pointing UP (270°), in between P1 & P4
     * - Top side (P2 and P3): shown at top center pointing DOWN (90°), in between P3 & P2
     */
    private fun getPlayerFiringInfo(player: Player): Triple<Float, Float, Float> {
        val cx = width / 2f
        val cy = height / 2f
        val index = players.indexOf(player).coerceAtLeast(0)

        val targetTop = cy - targetRadius
        val targetBottom = cy + targetRadius

        // Bottom shooters: Player 1 (index 0) and Player 4 (index 3)
        // Top shooters: Player 2 (index 1) and Player 3 (index 2)
        val isBottomShooter = (index == 0 || index == 3)

        return if (isBottomShooter) {
            // Arrow anchored close to the bottom player card
            val arrowY = bottomCardTop - 74f * density
            Triple(cx, arrowY, 270f) // Pointing UP towards target
        } else {
            // Arrow anchored close to the top player card
            val topCardBottom = topCardTop + cardH
            val arrowY = topCardBottom + 74f * density
            Triple(cx, arrowY, 90f) // Pointing DOWN towards target
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isKnifeFlying || turnTransitionTimer > 0f) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val activeP = getCurrentPlayer() ?: return true
            if (activeP.isBot) return true // Bot's turn, ignore human touches

            val ty = event.y
            val activeIndex = players.indexOf(activeP).coerceAtLeast(0)
            val isBottomShooter = (activeIndex == 0 || activeIndex == 3)

            // Bottom shooter (P1, P4) can tap anywhere in the bottom half
            // Top shooter (P2, P3) can tap anywhere in the top half
            if (isBottomShooter && ty >= height * 0.38f) {
                launchKnife(activeP)
            } else if (!isBottomShooter && ty <= height * 0.62f) {
                launchKnife(activeP)
            }
        }
        return true
    }

    private fun launchKnife(player: Player) {
        if (isGameOver || isKnifeFlying) return

        val (sx, sy, angleDeg) = getPlayerFiringInfo(player)
        val cx = width / 2f
        val cy = height / 2f

        isKnifeFlying = true
        flyingKnives.clear()
        flyingKnives.add(
            FlyingKnife(
                player = player,
                x = sx,
                y = sy,
                startX = sx,
                startY = sy,
                targetX = cx,
                targetY = cy,
                angleDeg = angleDeg,
                speed = 1500f * density
            )
        )

        soundManager.play(SoundManager.SoundType.SHOOT, 0.85f)
        triggerVibration(30)
    }

    override fun update(deltaTime: Float) {
        pulseTimer += deltaTime

        if (isGameOver) {
            particles.update()
            return
        }

        // 1. Target smooth rotation & occasional speed variation
        targetAngleDeg = (targetAngleDeg + targetRotSpeed * deltaTime) % 360f
        rotTimer += deltaTime
        if (rotTimer > 3.0f) {
            rotTimer = 0f
            val baseSpeed = when (botDifficulty) {
                0 -> 75f
                2 -> 115f
                else -> 95f
            }
            val dir = if (random.nextBoolean()) 1f else -1f
            targetRotSpeed = baseSpeed * dir
        }

        // 2. Handle turn transition delay after a shot
        if (turnTransitionTimer > 0f) {
            turnTransitionTimer -= deltaTime
            if (turnTransitionTimer <= 0f) {
                flyingKnives.clear()
                isKnifeFlying = false
                currentTurnIndex = (currentTurnIndex + 1) % players.size

                val nextP = getCurrentPlayer()
                botTurnElapsed = 0f
                if (nextP != null && nextP.isBot) {
                    botThinkTimer = getBotInitialDelay()
                }
            }
        }

        // 3. Bot Turn AI
        val activeP = getCurrentPlayer()
        if (activeP != null && activeP.isBot && !isKnifeFlying && turnTransitionTimer <= 0f) {
            botTurnElapsed += deltaTime
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f) {
                updateBotShot(activeP)
            }
        }

        val cx = width / 2f
        val cy = height / 2f

        // 4. Update Flying Knives
        val kIter = flyingKnives.iterator()
        while (kIter.hasNext()) {
            val k = kIter.next()
            val rad = Math.toRadians(k.angleDeg.toDouble())
            k.x += (cos(rad) * k.speed * deltaTime).toFloat()
            k.y += (sin(rad) * k.speed * deltaTime).toFloat()

            // Check if reached target perimeter
            val distToCenter = hypot(k.x - cx, k.y - cy)
            if (distToCenter <= targetRadius + 6f * density) {
                // Calculate impact angle on the rotating target
                val globalAngle = (Math.toDegrees(atan2((k.y - cy).toDouble(), (k.x - cx).toDouble())).toFloat() + 360f) % 360f
                val relativeAngle = (globalAngle - targetAngleDeg + 360f) % 360f

                // Check collision against embedded knives (7.5° threshold allows clean fit for 20+ knives)
                var collided = false
                for (emb in embeddedKnives) {
                    val angleDiff = abs(emb.angleOnTargetDeg - relativeAngle)
                    val normDiff = min(angleDiff, 360f - angleDiff)
                    if (normDiff < 7.5f) {
                        collided = true
                        break
                    }
                }

                if (collided) {
                    // Knife clatters and bounces off!
                    soundManager.play(SoundManager.SoundType.BOUNCE, 1.0f)
                    particles.spawnExplosion(k.x, k.y, Color.parseColor("#FFD54F"), 26)
                    triggerVibration(70)

                    // Deflect backward
                    k.speed = -400f * density
                    turnTransitionTimer = 0.55f
                } else {
                    // Successfully embedded into target!
                    embeddedKnives.add(EmbeddedKnife(k.player.id, relativeAngle))
                    val count = (playerKnifeCounts[k.player.id] ?: 0) + 1
                    playerKnifeCounts[k.player.id] = count

                    soundManager.play(SoundManager.SoundType.TAP, 1.0f)
                    triggerVibration(45)
                    particles.spawnExplosion(k.x, k.y, k.player.id.color, 18)
                    kIter.remove()

                    // When target reaches 15th arrow, the player with the highest arrow count wins!
                    if (embeddedKnives.size >= maxTargetArrows) {
                        if (isTeamMode) {
                            val t1Arrows = (playerKnifeCounts[PlayerId.PLAYER_1] ?: 0) + (playerKnifeCounts[PlayerId.PLAYER_2] ?: 0)
                            val t2Arrows = (playerKnifeCounts[PlayerId.PLAYER_3] ?: 0) + (playerKnifeCounts[PlayerId.PLAYER_4] ?: 0)
                            winningTeam = if (t1Arrows >= t2Arrows) 1 else 2
                            val winner = if (winningTeam == 1) players[0] else players[2]
                            endRound(winner)
                        } else {
                            val winner = players.maxByOrNull { playerKnifeCounts[it.id] ?: 0 } ?: k.player
                            endRound(winner)
                        }
                        break
                    } else {
                        turnTransitionTimer = 0.45f
                    }
                }
            }
        }

        particles.update()
    }

    private fun updateBotShot(bot: Player) {
        if (isKnifeFlying || turnTransitionTimer > 0f) return

        val (sx, sy, _) = getPlayerFiringInfo(bot)
        val cx = width / 2f
        val cy = height / 2f

        // Flight time to target perimeter
        val distToPerimeter = (abs(sy - cy) - targetRadius).coerceAtLeast(10f)
        val speed = 1500f * density
        val flightTime = (distToPerimeter / speed).coerceAtLeast(0.01f)

        // Predicted target rotation at moment of impact
        val predictedTargetAngle = (targetAngleDeg + targetRotSpeed * flightTime + 3600f) % 360f
        val globalImpactAngle = (Math.toDegrees(atan2((sy - cy).toDouble(), (sx - cx).toDouble())).toFloat() + 360f) % 360f
        val localImpactAngle = (globalImpactAngle - predictedTargetAngle + 3600f) % 360f

        // Check clearance to nearest embedded knife
        var minGap = 360f
        for (emb in embeddedKnives) {
            val angleDiff = abs(emb.angleOnTargetDeg - localImpactAngle)
            val normDiff = min(angleDiff, 360f - angleDiff)
            if (normDiff < minGap) {
                minGap = normDiff
            }
        }

        // Safe clearance threshold (collision occurs at < 7.5f)
        val safeThreshold = when (botDifficulty) {
            0 -> 10.0f
            2 -> 8.5f
            else -> 9.0f
        }

        // Maximum wait time ensuring bot NEVER gets stuck indefinitely
        val maxWaitTime = when (botDifficulty) {
            0 -> 1.4f
            2 -> 1.6f
            else -> 1.5f
        }

        val isClear = minGap >= safeThreshold

        val shouldShoot = when (botDifficulty) {
            0 -> {
                // Easy: shoots when clear, or makes a mistake after 0.7s, or forced after maxWaitTime
                isClear || botTurnElapsed >= maxWaitTime || (botTurnElapsed >= 0.7f && random.nextInt(100) < 30)
            }
            2 -> {
                // Hard: waits for clear gap with high precision, but MUST shoot once maxWaitTime reached
                isClear || botTurnElapsed >= maxWaitTime
            }
            else -> {
                // Medium: shoots when clear, occasional mistake after 0.9s, or forced after maxWaitTime
                isClear || botTurnElapsed >= maxWaitTime || (botTurnElapsed >= 0.9f && random.nextInt(100) < 20)
            }
        }

        if (shouldShoot) {
            launchKnife(bot)
        } else {
            // Re-check frequently as the target rotates
            botThinkTimer = 0.02f
        }
    }

    override fun render(canvas: Canvas) {
        if (cardW <= 0f) computeLayoutGeometry()

        // 1. Cosmic Background
        canvas.drawColor(Color.parseColor("#0C091D"))

        // Ambient cosmic stars
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1634")
        for (i in 0..16) {
            val sx = (i * 83f * density) % width
            val sy = (i * 137f * density) % height
            canvas.drawCircle(sx, sy, 2.5f * density, paint)
        }

        val cx = width / 2f
        val cy = height / 2f
        val activeP = getCurrentPlayer()

        // 2. Render Player Status Cards
        if (totalPlayers <= 2) {
            drawPlayerCard(canvas, isTop = true)
            drawPlayerCard(canvas, isTop = false)
        } else {
            drawMultiplayerBadges(canvas)
        }

        // 3. Central Rotating Target
        drawRotatingTarget(canvas, cx, cy)

        // 4. Ready Arrow & Laser Aiming Guideline for Active Player
        if (activeP != null && !isKnifeFlying && turnTransitionTimer <= 0f && !isGameOver) {
            drawReadyArrowAndGuideline(canvas, activeP, cx, cy)
        }

        // 5. Draw Flying Knives
        drawFlyingKnives(canvas)

        // 6. Particles
        particles.draw(canvas)
    }

    /**
     * Draw the player status card (Tic Tac Toe style) for 1P & 2P
     */
    private fun drawPlayerCard(canvas: Canvas, isTop: Boolean) {
        val player = if (isTop) players.getOrNull(1) else players.firstOrNull()
        if (player == null) return

        val activeP = getCurrentPlayer()
        val isTurn = (activeP?.id == player.id) && !isGameOver
        val isBot = player.isBot
        val playerColor = player.id.color

        val cardTop = if (isTop) topCardTop else bottomCardTop
        val cardLeft = (width - cardW) / 2f
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        canvas.save()
        // Invert top card for local multiplayer so Player 2 views it right-side up!
        if (isTop && !isBot) {
            canvas.rotate(180f, width / 2f, cardTop + cardH / 2f)
        }

        // 1. Glow Halo when active
        if (isTurn) {
            val glowPulse = 0.5f + 0.5f * sin(pulseTimer * 4.5f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f * density
            paint.color = playerColor
            paint.alpha = (glowPulse * 90).toInt()
            val haloRect = RectF(
                cardLeft - 2f * density,
                cardTop - 2f * density,
                cardLeft + cardW + 2f * density,
                cardTop + cardH + 2f * density
            )
            canvas.drawRoundRect(haloRect, 22f * density, 22f * density, paint)
        }

        // 2. Glass Card Body
        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) Color.parseColor("#1F1836") else Color.parseColor("#150F28")
        paint.alpha = 255
        canvas.drawRoundRect(cardRect, 20f * density, 20f * density, paint)

        // 3. Card Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isTurn) 2.2f * density else 1.2f * density
        paint.color = if (isTurn) playerColor else Color.parseColor("#2E234E")
        paint.alpha = if (isTurn) 255 else 180
        canvas.drawRoundRect(cardRect, 20f * density, 20f * density, paint)

        // 4. Left Avatar Squircle Badge
        val avatarSize = 48f * density
        val avatarL = cardLeft + 12f * density
        val avatarT = cardTop + (cardH - avatarSize) / 2f
        val avatarRect = RectF(avatarL, avatarT, avatarL + avatarSize, avatarT + avatarSize)

        paint.style = Paint.Style.FILL
        paint.color = playerColor
        paint.alpha = 255
        canvas.drawRoundRect(avatarRect, 14f * density, 14f * density, paint)

        paint.textAlign = Paint.Align.CENTER
        val avatarCenterX = avatarRect.centerX()
        val avatarCenterY = avatarRect.centerY()

        if (isBot) {
            paint.textSize = 24f * density
            paint.color = Color.WHITE
            val fm = paint.fontMetrics
            val baseline = avatarCenterY - (fm.ascent + fm.descent) / 2f
            canvas.drawText("🤖", avatarCenterX, baseline, paint)
        } else {
            val markText = if (player.id == PlayerId.PLAYER_1) "P1" else "P2"
            paint.textSize = 19f * density
            paint.isFakeBoldText = true
            paint.color = Color.WHITE
            val fm = paint.fontMetrics
            val baseline = avatarCenterY - (fm.ascent + fm.descent) / 2f
            canvas.drawText(markText, avatarCenterX, baseline, paint)
            paint.isFakeBoldText = false
        }

        // 5. Right Turn Indicator Pill
        val pillW = 108f * density
        val pillH = 34f * density
        val pillR = cardLeft + cardW - 12f * density
        val pillL = pillR - pillW
        val pillT = cardTop + (cardH - pillH) / 2f
        val pillRect = RectF(pillL, pillT, pillR, pillT + pillH)

        if (isTurn) {
            paint.style = Paint.Style.FILL
            paint.color = (playerColor and 0x00FFFFFF) or 0x33000000
            canvas.drawRoundRect(pillRect, 17f * density, 17f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = playerColor
            canvas.drawRoundRect(pillRect, 17f * density, 17f * density, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.color = playerColor
            paint.textSize = 11.5f * density
            paint.isFakeBoldText = true

            val statusText = if (isBot) "🤖 THINKING" else "⚡ YOUR TURN"
            val fm = paint.fontMetrics
            val baseline = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(statusText, pillRect.centerX(), baseline, paint)
            paint.isFakeBoldText = false
        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#1B1530")
            canvas.drawRoundRect(pillRect, 17f * density, 17f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = Color.parseColor("#2E2450")
            canvas.drawRoundRect(pillRect, 17f * density, 17f * density, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.parseColor("#7E739E")
            paint.textSize = 11f * density
            paint.isFakeBoldText = false

            val fm = paint.fontMetrics
            val baseline = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText("WAITING", pillRect.centerX(), baseline, paint)
        }

        // 6. Player Name & Knives Target Progress
        paint.textAlign = Paint.Align.LEFT
        val textStartX = avatarL + avatarSize + 12f * density
        val maxTextWidth = pillL - textStartX - 8f * density

        val score = playerKnifeCounts[player.id] ?: 0
        paint.color = playerColor
        paint.textSize = 11f * density
        paint.isFakeBoldText = true
        val roleStr = when {
            player.id == PlayerId.PLAYER_1 -> "PLAYER 1"
            isBot -> {
                val diffStr = when (botDifficulty) {
                    0 -> "EASY"
                    2 -> "HARD"
                    else -> "MEDIUM"
                }
                "AI BOT ($diffStr)"
            }
            else -> "PLAYER 2"
        }
        val tagText = "$roleStr  •  🎯 $score ARROWS"
        val tagBaseline = cardTop + 28f * density
        canvas.drawText(tagText, textStartX, tagBaseline, paint)

        paint.color = Color.WHITE
        paint.textSize = 16.5f * density
        paint.isFakeBoldText = true
        var nameText = if (isBot) "Robi the Bot" else getPlayerDisplayName(player)
        if (paint.measureText(nameText) > maxTextWidth) {
            while (nameText.length > 3 && paint.measureText(nameText + "…") > maxTextWidth) {
                nameText = nameText.substring(0, nameText.length - 1)
            }
            nameText += "…"
        }
        val nameBaseline = cardTop + 54f * density
        canvas.drawText(nameText, textStartX, nameBaseline, paint)
        paint.isFakeBoldText = false

        canvas.restore()
    }

    /**
     * Draw modern cards for 3P & 4P (top side in between, down side in between)
     */
    private fun drawMultiplayerBadges(canvas: Canvas) {
        val activeP = getCurrentPlayer()
        players.forEach { p ->
            val isTurn = (activeP?.id == p.id) && !isGameOver
            val score = playerKnifeCounts[p.id] ?: 0
            val zone = controlZones[p.id] ?: return@forEach
            val playerColor = p.id.color
            val isTopPlayer = (p.id == PlayerId.PLAYER_2 || p.id == PlayerId.PLAYER_3)

            canvas.save()
            // Invert top badges so players sitting on top read them right-side up!
            if (isTopPlayer) {
                canvas.rotate(180f, zone.centerX(), zone.centerY())
            }

            // 1. Glow Halo when active
            if (isTurn) {
                val glowPulse = 0.5f + 0.5f * sin(pulseTimer * 4.5f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f * density
                paint.color = playerColor
                paint.alpha = (glowPulse * 90).toInt()
                val haloRect = RectF(
                    zone.left - 2f * density,
                    zone.top - 2f * density,
                    zone.right + 2f * density,
                    zone.bottom + 2f * density
                )
                canvas.drawRoundRect(haloRect, 18f * density, 18f * density, paint)
            }

            // 2. Glass Card Body
            paint.style = Paint.Style.FILL
            paint.color = if (isTurn) Color.parseColor("#1F1836") else Color.parseColor("#150F28")
            paint.alpha = 255
            canvas.drawRoundRect(zone, 16f * density, 16f * density, paint)

            // 3. Card Border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isTurn) 2.2f * density else 1.2f * density
            paint.color = if (isTurn) playerColor else Color.parseColor("#2E234E")
            paint.alpha = if (isTurn) 255 else 180
            canvas.drawRoundRect(zone, 16f * density, 16f * density, paint)

            // 4. Squircle Avatar
            val avatarSize = 38f * density
            val avatarL = zone.left + 10f * density
            val avatarT = zone.centerY() - avatarSize / 2f
            val avatarRect = RectF(avatarL, avatarT, avatarL + avatarSize, avatarT + avatarSize)

            paint.style = Paint.Style.FILL
            paint.color = playerColor
            paint.alpha = 255
            canvas.drawRoundRect(avatarRect, 11f * density, 11f * density, paint)

            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            paint.textSize = 15f * density
            paint.isFakeBoldText = true
            val avatarLabel = when (p.id) {
                PlayerId.PLAYER_1 -> "P1"
                PlayerId.PLAYER_2 -> "P2"
                PlayerId.PLAYER_3 -> "P3"
                PlayerId.PLAYER_4 -> "P4"
            }
            val afm = paint.fontMetrics
            val abaseline = avatarRect.centerY() - (afm.ascent + afm.descent) / 2f
            canvas.drawText(avatarLabel, avatarRect.centerX(), abaseline, paint)

            // 5. Name and Status Text
            paint.textAlign = Paint.Align.LEFT
            val textStartX = avatarL + avatarSize + 10f * density
            val maxTextW = zone.right - textStartX - 8f * density

            paint.color = Color.WHITE
            paint.textSize = 13.5f * density
            var name = if (p.isBot) "BOT" else getPlayerDisplayName(p)
            if (paint.measureText(name) > maxTextW) {
                while (name.length > 2 && paint.measureText(name + "…") > maxTextW) {
                    name = name.substring(0, name.length - 1)
                }
                name += "…"
            }
            val nameBaseline = zone.top + 24f * density
            canvas.drawText(name, textStartX, nameBaseline, paint)

            paint.textSize = 11f * density
            paint.color = if (isTurn) playerColor else Color.parseColor("#8E82B4")
            val statusText = if (isTurn) "⚡ YOUR TURN" else "🎯 $score ARROWS"
            val statusBaseline = zone.top + 45f * density
            canvas.drawText(statusText, textStartX, statusBaseline, paint)
            paint.isFakeBoldText = false

            canvas.restore()
        }
    }

    /**
     * Draw central rotating target with embedded knives
     */
    private fun drawRotatingTarget(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(targetAngleDeg)

        // Outer Neon Target Halo
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#25193B")
        canvas.drawCircle(0f, 0f, targetRadius + 6f * density, paint)

        // Target Body
        paint.color = Color.parseColor("#16102B")
        canvas.drawCircle(0f, 0f, targetRadius, paint)

        paint.color = Color.parseColor("#22183D")
        canvas.drawCircle(0f, 0f, targetRadius * 0.72f, paint)

        // Outer Neon Ring
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f * density
        paint.color = Color.parseColor("#FF9100")
        canvas.drawCircle(0f, 0f, targetRadius, paint)

        // Inner Energy Ring
        paint.strokeWidth = 1.8f * density
        paint.color = Color.parseColor("#FF2A6D")
        canvas.drawCircle(0f, 0f, targetRadius * 0.50f, paint)

        // Draw Embedded Knives rotating with the target
        embeddedKnives.forEach { emb ->
            canvas.save()
            canvas.rotate(emb.angleOnTargetDeg)

            // Neon Blade sticking out
            paint.style = Paint.Style.FILL
            paint.color = emb.owner.color
            val bladeL = targetRadius - 8f * density
            val bladeR = targetRadius + 32f * density
            canvas.drawRoundRect(
                RectF(bladeL, -3.5f * density, bladeR, 3.5f * density),
                3f * density,
                3f * density,
                paint
            )

            // White metallic hilt & handle
            paint.color = Color.WHITE
            canvas.drawCircle(bladeR + 4f * density, 0f, 5.5f * density, paint)

            paint.color = Color.parseColor("#100B20")
            canvas.drawCircle(bladeR + 4f * density, 0f, 2.5f * density, paint)

            canvas.restore()
        }

        canvas.restore()

        // Center Digital Counter Hub (drawn upright, not rotating)
        val hubR = 17f * density
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#150F28")
        canvas.drawCircle(cx, cy, hubR, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(cx, cy, hubR, paint)

        // Count of arrows in circle e.g. "8/15"
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 9.5f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val hubText = "${embeddedKnives.size}/$maxTargetArrows"
        val fm = paint.fontMetrics
        val hubBaseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(hubText, cx, hubBaseline, paint)
        paint.isFakeBoldText = false
    }

    /**
     * USER REQUEST:
     * - "fix tap to hit place it is overright" -> plenty of clearance between arrow and card!
     * - "for player 3 and 4 show the arrow at where 1 and 2 player show"
     * - "for 1 side both plyer show in between them and for down side also in between them do this"
     */
    private fun drawReadyArrowAndGuideline(canvas: Canvas, player: Player, cx: Float, cy: Float) {
        val (arrowX, arrowY, angleDeg) = getPlayerFiringInfo(player)
        val playerColor = player.id.color

        // 1. Draw Laser Aiming Guideline to Target Perimeter
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = (playerColor and 0x00FFFFFF) or 0x66000000
        paint.pathEffect = dashPathEffect

        // Line to target perimeter
        val dx = cx - arrowX
        val dy = cy - arrowY
        val dist = hypot(dx, dy)
        if (dist > targetRadius) {
            val stopX = cx - (dx / dist) * targetRadius
            val stopY = cy - (dy / dist) * targetRadius
            canvas.drawLine(arrowX, arrowY, stopX, stopY, paint)
        }
        paint.pathEffect = null

        // 2. Draw Ready Arrow sitting at the firing station
        canvas.save()
        canvas.translate(arrowX, arrowY)
        canvas.rotate(angleDeg)

        // Gentle breathing pulse
        val breathe = sin(pulseTimer * 5f) * 2f * density
        canvas.translate(breathe, 0f)

        // Arrow Glowing Blade
        paint.style = Paint.Style.FILL
        paint.color = playerColor

        val path = Path().apply {
            moveTo(24f * density, 0f)
            lineTo(-12f * density, -7f * density)
            lineTo(-6f * density, 0f)
            lineTo(-12f * density, 7f * density)
            close()
        }
        canvas.drawPath(path, paint)

        // White energy core
        paint.color = Color.WHITE
        canvas.drawCircle(0f, 0f, 3.5f * density, paint)

        // Handle
        paint.color = Color.parseColor("#342756")
        canvas.drawRect(-26f * density, -2.5f * density, -12f * density, 2.5f * density, paint)

        // Pommel
        paint.color = playerColor
        canvas.drawCircle(-26f * density, 0f, 4f * density, paint)

        canvas.restore()

        // 3. Draw Prompt Pill near Ready Arrow with SAFE NON-OVERLAPPING CLEARANCE
        val pillW = 108f * density
        val pillH = 26f * density
        val pillX = arrowX
        val pillY = if (angleDeg == 270f) {
            bottomCardTop - 25f * density // Positioned with tight ~12dp space above the player card
        } else {
            (topCardTop + cardH) + 25f * density // Positioned with tight ~12dp space below the top player card
        }

        canvas.save()
        // If top shooter and human, rotate 180° around pill center so the top player reads it right-side up!
        if (angleDeg == 90f && !player.isBot) {
            canvas.rotate(180f, pillX, pillY)
        }

        val pillRect = RectF(pillX - pillW / 2f, pillY - pillH / 2f, pillX + pillW / 2f, pillY + pillH / 2f)
        paint.style = Paint.Style.FILL
        paint.color = (playerColor and 0x00FFFFFF) or 0x33000000
        canvas.drawRoundRect(pillRect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = playerColor
        canvas.drawRoundRect(pillRect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 11f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val promptText = if (player.isBot) "🤖 BOT AIMING..." else "⚡ TAP TO HIT!"
        val fm = paint.fontMetrics
        val baseline = pillRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(promptText, pillX, baseline, paint)
        paint.isFakeBoldText = false
        canvas.restore()
    }

    /**
     * Draw knives currently traveling through the air towards the target
     */
    private fun drawFlyingKnives(canvas: Canvas) {
        flyingKnives.forEach { k ->
            canvas.save()
            canvas.translate(k.x, k.y)
            canvas.rotate(k.angleDeg)

            paint.style = Paint.Style.FILL
            paint.color = k.player.id.color

            val path = Path().apply {
                moveTo(24f * density, 0f)
                lineTo(-14f * density, -7f * density)
                lineTo(-8f * density, 0f)
                lineTo(-14f * density, 7f * density)
                close()
            }
            canvas.drawPath(path, paint)

            paint.color = Color.WHITE
            canvas.drawCircle(0f, 0f, 3.5f * density, paint)

            canvas.restore()
        }
    }

    override fun getPlayerScoreString(player: Player): String {
        val arrows = playerKnifeCounts[player.id] ?: 0
        return "$arrows ${if (arrows == 1) "Arrow" else "Arrows"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return playerKnifeCounts[player.id] ?: 0
    }

    override fun getTeamScoreString(team: Int): String {
        val count = if (team == 1) {
            (playerKnifeCounts[PlayerId.PLAYER_1] ?: 0) + (playerKnifeCounts[PlayerId.PLAYER_2] ?: 0)
        } else {
            (playerKnifeCounts[PlayerId.PLAYER_3] ?: 0) + (playerKnifeCounts[PlayerId.PLAYER_4] ?: 0)
        }
        return "$count ${if (count == 1) "Arrow" else "Arrows"}"
    }
}
