package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class MolePit(
    val index: Int,
    var x: Float = 0f,
    var y: Float = 0f,
    var radiusX: Float = 0f,
    var radiusY: Float = 0f,
    var isOut: Boolean = false,
    var moleColor: Int = Color.RED,
    var targetPlayerId: PlayerId = PlayerId.PLAYER_1,
    var isStruck: Boolean = false,
    var struckBy: PlayerId? = null,
    var popProgress: Float = 0f, // 0 = underground, 1 = surfaced
    var isSurfacing: Boolean = false,
    var isRetreating: Boolean = false,
    var strikeAnimProgress: Float = 0f,
    var lifeTimer: Float = 0f,
    var maxLife: Float = 1.3f,
    var botReactionTimer: Float = -1f,
    var botPlayerId: PlayerId? = null
)

data class FloatingScore(
    val text: String,
    val x: Float,
    var y: Float,
    val color: Int,
    var alpha: Float = 1f,
    var life: Float = 0.85f
)

data class HammerSlam(
    val x: Float,
    val y: Float,
    val color: Int,
    var progress: Float = 0f
)

/**
 * Whack-A-Mole engine adapted from JungleFrog.FourPlayers.WhackAMole in reference APK.
 * Features 3D perspective pits, lively grounded moles with player-colored bandanas,
 * reliable mouse & multi-touch support with direct color point attribution,
 * fair draw match handling, and spacious cosmic neon visuals.
 */
class WhackAMoleEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // In-game score map (dedicated so BaseGameEngine / GameActivity sessionWins never overwrites mole counts)
    private val molesWhacked = mutableMapOf<PlayerId, Int>()

    // Game duration & timing
    private val roundDuration = 45f
    private var roundTimeRemaining = 45f
    private var spawnTimer = 0.5f
    private val easySpawnTimeGap = 1.30f
    private val hardSpawnTimeGap = 0.52f
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null

    // Pits (3x3 grid)
    private val pits = mutableListOf<MolePit>()
    private val floatingScores = mutableListOf<FloatingScore>()
    private val hammerSlams = mutableListOf<HammerSlam>()

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()

    // Safe layout bounds
    private var topSafeY = 0f
    private var timerY = 0f
    private var bottomSafeY = 0f
    private var arenaRect = RectF()

    override fun initGame() {
        isGameOver = false
        roundTimeRemaining = roundDuration
        spawnTimer = 0.5f
        roundEndDelayTimer = -1f
        pendingWinner = null
        floatingScores.clear()
        hammerSlams.clear()

        molesWhacked.clear()
        players.forEach { p ->
            molesWhacked[p.id] = 0
            p.score = 0
            p.isTouching = false
        }

        buildPitsLayout()
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        buildPitsLayout()
    }

    private fun buildPitsLayout() {
        if (width <= 0f || height <= 0f) return

        // Generous safe margins to stay completely clear of the top HUD pill and bottom gesture/nav bar
        topSafeY = 80f * density
        timerY = topSafeY + 44f * density
        bottomSafeY = height - 66f * density

        val arenaMarginX = 16f * density
        val arenaTop = timerY + 38f * density // Clear space below timer pill row
        val arenaBottom = bottomSafeY - 20f * density // Clear space above bottom player badges
        val arenaW = width - arenaMarginX * 2f
        val arenaH = arenaBottom - arenaTop

        arenaRect.set(arenaMarginX, arenaTop, width - arenaMarginX, arenaBottom)

        pits.clear()
        val cols = 3
        val rows = 3

        val cellW = arenaW / cols
        val cellH = arenaH / rows

        // 3D elliptical hole dimensions (perspective view)
        val pitRx = (min(cellW * 0.38f, 50f * density)).coerceIn(32f * density, 50f * density)
        val pitRy = pitRx * 0.60f

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = arenaRect.left + cellW * (c + 0.5f)
                val cy = arenaRect.top + cellH * (r + 0.52f)
                val index = r * cols + c

                pits.add(
                    MolePit(
                        index = index,
                        x = cx,
                        y = cy,
                        radiusX = pitRx,
                        radiusY = pitRy
                    )
                )
            }
        }
    }

    override fun update(deltaTime: Float) {
        if (isGameOver) return

        // Round completion delay before showing winner popup
        if (roundEndDelayTimer >= 0f) {
            roundEndDelayTimer -= deltaTime
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                endRound(pendingWinner)
            }
            updateVisualEffects(deltaTime)
            return
        }

        // Countdown timer
        roundTimeRemaining -= deltaTime
        if (roundTimeRemaining <= 0f) {
            roundTimeRemaining = 0f
            if (!isGameOver && roundEndDelayTimer < 0f) {
                // Determine winner strictly by the highest molesWhacked count!
                val maxScore = players.maxOfOrNull { molesWhacked[it.id] ?: 0 } ?: 0
                val topScorers = players.filter { (molesWhacked[it.id] ?: 0) == maxScore }

                // If only 1 player has the unique top score, they win!
                // If there's a tie (same count), pendingWinner = null (DRAW MATCH)!
                pendingWinner = if (topScorers.size == 1) topScorers.first() else null
                roundEndDelayTimer = 0.8f
                soundManager.play(SoundManager.SoundType.GOAL)
            }
            return
        }

        // Spawn interval ramp-up
        val difficultyRatio = ((roundDuration - roundTimeRemaining) / roundDuration).coerceIn(0f, 1f)
        val currentSpawnInterval = easySpawnTimeGap - (easySpawnTimeGap - hardSpawnTimeGap) * difficultyRatio

        spawnTimer -= deltaTime
        if (spawnTimer <= 0f) {
            spawnTimer = currentSpawnInterval
            val maxOut = when {
                difficultyRatio > 0.65f -> 3
                difficultyRatio > 0.30f -> 2
                else -> 1
            }
            val currentOut = pits.count { it.isOut }
            if (currentOut < maxOut) {
                spawnRandomMole(difficultyRatio)
            }
        }

        // Update pits & moles
        for (pit in pits) {
            if (!pit.isOut) continue

            // Surfacing animation
            if (pit.isSurfacing) {
                pit.popProgress += deltaTime * 5.5f
                if (pit.popProgress >= 1f) {
                    pit.popProgress = 1f
                    pit.isSurfacing = false
                }
            }

            // Struck animation
            if (pit.isStruck) {
                pit.strikeAnimProgress += deltaTime * 2.8f
                if (pit.strikeAnimProgress >= 1f) {
                    // Descend back down after knockout
                    pit.popProgress -= deltaTime * 4f
                    if (pit.popProgress <= 0f) {
                        pit.popProgress = 0f
                        pit.isOut = false
                        pit.isStruck = false
                    }
                }
            } else if (pit.isRetreating) {
                // Duck back into hole when time expires
                pit.popProgress -= deltaTime * 4.5f
                if (pit.popProgress <= 0f) {
                    pit.popProgress = 0f
                    pit.isOut = false
                    pit.isRetreating = false
                }
            } else {
                // Active on screen waiting to be whacked
                pit.lifeTimer += deltaTime
                if (pit.lifeTimer >= pit.maxLife) {
                    pit.isRetreating = true
                    particles.spawnExplosion(pit.x, pit.y + pit.radiusY * 0.2f, Color.parseColor("#443322"), 8)
                }

                // AI Bot logic for this mole
                if (pit.botReactionTimer > 0f) {
                    pit.botReactionTimer -= deltaTime
                    if (pit.botReactionTimer <= 0f && pit.botPlayerId != null) {
                        val bot = players.firstOrNull { it.id == pit.botPlayerId }
                        if (bot != null && pit.isOut && !pit.isStruck) {
                            strikeMole(pit, bot)
                        }
                    }
                }
            }
        }

        updateVisualEffects(deltaTime)
    }

    private fun spawnRandomMole(difficultyRatio: Float) {
        val availablePits = pits.filter { !it.isOut }
        if (availablePits.isEmpty()) return

        val pit = availablePits[random.nextInt(availablePits.size)]

        // Choose random target player from active players
        val targetPlayer = players[random.nextInt(players.size)]

        pit.isOut = true
        pit.isSurfacing = true
        pit.isRetreating = false
        pit.isStruck = false
        pit.struckBy = null
        pit.popProgress = 0f
        pit.strikeAnimProgress = 0f
        pit.lifeTimer = 0f
        pit.maxLife = (1.30f - 0.45f * difficultyRatio).coerceAtLeast(0.65f)
        pit.targetPlayerId = targetPlayer.id
        pit.moleColor = targetPlayer.id.color
        pit.botReactionTimer = -1f
        pit.botPlayerId = null

        // Pop sound & dust puff
        soundManager.play(SoundManager.SoundType.BOUNCE, 0.40f)
        particles.spawnExplosion(pit.x, pit.y + pit.radiusY * 0.2f, Color.parseColor("#6D543C"), 8)

        // Queue bot reaction if mole matches any bot
        for (player in players) {
            if (player.isBot && player.id == targetPlayer.id) {
                val (minDelay, maxDelay, missChance) = when (botDifficulty) {
                    0 -> Triple(0.68f, 0.98f, 0.20f) // Easy
                    2 -> Triple(0.22f, 0.36f, 0.02f) // Hard
                    else -> Triple(0.40f, 0.60f, 0.08f) // Medium
                }

                if (random.nextFloat() > missChance) {
                    pit.botReactionTimer = minDelay + random.nextFloat() * (maxDelay - minDelay)
                    pit.botPlayerId = player.id
                    break
                }
            }
        }
    }

    /**
     * Struck logic: Award points directly to the mole's player color!
     * When user taps a mole of that color, that color user gets the points!
     */
    private fun strikeMole(pit: MolePit, tappingPlayer: Player?) {
        if (!pit.isOut || pit.isStruck) return

        pit.isStruck = true
        pit.strikeAnimProgress = 0f
        pit.botReactionTimer = -1f

        // That color user gets the points!
        val awardedPlayer: Player = players.firstOrNull { it.id == pit.targetPlayerId }
            ?: tappingPlayer
            ?: players.first()

        pit.struckBy = awardedPlayer.id

        // Hammer slam visual FX
        hammerSlams.add(HammerSlam(pit.x, pit.y, awardedPlayer.id.color))

        // Increment mole count
        val curCount = (molesWhacked[awardedPlayer.id] ?: 0) + 1
        molesWhacked[awardedPlayer.id] = curCount
        awardedPlayer.score = curCount

        soundManager.play(SoundManager.SoundType.BOUNCE)
        particles.spawnExplosion(pit.x, pit.y, awardedPlayer.id.color, 25)
        particles.spawnExplosion(pit.x, pit.y, Color.WHITE, 10)
        floatingScores.add(FloatingScore("+1", pit.x, pit.y - pit.radiusY * 1.1f, awardedPlayer.id.color))
        triggerVibration(40)
    }

    private fun updateVisualEffects(deltaTime: Float) {
        // Floating score popups
        val iter = floatingScores.iterator()
        while (iter.hasNext()) {
            val fs = iter.next()
            fs.y -= deltaTime * 55f * density
            fs.life -= deltaTime
            fs.alpha = (fs.life / 0.85f).coerceIn(0f, 1f)
            if (fs.life <= 0f) {
                iter.remove()
            }
        }

        // Hammer slam rings
        val hIter = hammerSlams.iterator()
        while (hIter.hasNext()) {
            val hs = hIter.next()
            hs.progress += deltaTime * 5f
            if (hs.progress >= 1f) {
                hIter.remove()
            }
        }

        particles.update()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || roundEndDelayTimer >= 0f) return true

        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex

        if (actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val tx = event.getX(pointerIndex)
            val ty = event.getY(pointerIndex)

            val tappingPlayer = resolvePlayerForTouch(tx, ty)
            handleTapAt(tx, ty, tappingPlayer)
        }

        return true
    }

    private fun resolvePlayerForTouch(tx: Float, ty: Float): Player? {
        val nonBotPlayers = players.filter { !it.isBot }
        if (nonBotPlayers.isEmpty()) return players.firstOrNull()
        if (nonBotPlayers.size == 1) return nonBotPlayers.first()

        // 2 Players: Split vertically into Top (P2) and Bottom (P1)
        if (players.size == 2) {
            return if (ty >= height * 0.5f) {
                players.firstOrNull { it.id == PlayerId.PLAYER_1 }
            } else {
                players.firstOrNull { it.id == PlayerId.PLAYER_2 }
            }
        }

        // 3 Players: Bottom = P1, Top-Left = P2, Top-Right = P3
        if (players.size == 3) {
            return if (ty >= height * 0.52f) {
                players.firstOrNull { it.id == PlayerId.PLAYER_1 }
            } else if (tx < width * 0.5f) {
                players.firstOrNull { it.id == PlayerId.PLAYER_2 }
            } else {
                players.firstOrNull { it.id == PlayerId.PLAYER_3 }
            }
        }

        // 4 Players: 4 quadrants
        val isBottom = ty >= height * 0.5f
        val isLeft = tx < width * 0.5f
        return when {
            isBottom && isLeft -> players.firstOrNull { it.id == PlayerId.PLAYER_1 }
            !isBottom && isLeft -> players.firstOrNull { it.id == PlayerId.PLAYER_2 }
            !isBottom && !isLeft -> players.firstOrNull { it.id == PlayerId.PLAYER_3 }
            else -> players.firstOrNull { it.id == PlayerId.PLAYER_4 }
        }
    }

    private fun handleTapAt(tx: Float, ty: Float, tappingPlayer: Player?) {
        var hitAnyPit = false

        for (pit in pits) {
            if (!pit.isOut || pit.isStruck) continue

            // Generous touch bounds covering both the pit hole and the surfaced mole body
            val moleHeight = pit.radiusY * 2.4f
            val moleTop = pit.y - (moleHeight * pit.popProgress)
            val moleBottom = pit.y + pit.radiusY * 0.9f
            val moleLeft = pit.x - pit.radiusX * 1.25f
            val moleRight = pit.x + pit.radiusX * 1.25f

            val hitMole = tx in moleLeft..moleRight && ty in moleTop..moleBottom
            val dx = (tx - pit.x) / (pit.radiusX * 1.35f)
            val dy = (ty - pit.y) / (pit.radiusY * 1.35f)
            val hitPit = (dx * dx + dy * dy) <= 1f

            if (hitMole || hitPit) {
                hitAnyPit = true
                strikeMole(pit, tappingPlayer)
                break
            }
        }

        if (!hitAnyPit) {
            val pColor = tappingPlayer?.id?.color ?: Color.WHITE
            hammerSlams.add(HammerSlam(tx, ty, pColor))
            soundManager.play(SoundManager.SoundType.TAP, 0.4f)
        }
    }

    override fun render(canvas: Canvas) {
        // 1. Cosmic Neon Arena Background
        canvas.drawColor(Color.parseColor("#0C091A"))
        drawCosmicArenaGrid(canvas)

        // 2. Mole Pits & Moles
        for (pit in pits) {
            drawMolePit(canvas, pit)
        }

        // 3. Hammer Slam shockwaves
        drawHammerSlams(canvas)

        // 4. Particles & Floating Scores
        particles.draw(canvas)
        drawFloatingScores(canvas)

        // 5. Timer HUD & Player Station Badges
        drawTopHUDAndPlayerStations(canvas)
    }

    private fun drawCosmicArenaGrid(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#1F193B")

        // Arena outer boundary
        canvas.drawRoundRect(arenaRect, 22f * density, 22f * density, paint)

        // Subtle decorative crosslines
        paint.strokeWidth = 1f * density
        paint.color = Color.parseColor("#151128")
        val cols = 3
        val rows = 3
        val cellW = arenaRect.width() / cols
        val cellH = arenaRect.height() / rows
        for (i in 1 until cols) {
            val lx = arenaRect.left + cellW * i
            canvas.drawLine(lx, arenaRect.top, lx, arenaRect.bottom, paint)
        }
        for (i in 1 until rows) {
            val ly = arenaRect.top + cellH * i
            canvas.drawLine(arenaRect.left, ly, arenaRect.right, ly, paint)
        }
    }

    private fun drawMolePit(canvas: Canvas, pit: MolePit) {
        val cx = pit.x
        val cy = pit.y
        val rx = pit.radiusX
        val ry = pit.radiusY

        // 1. Outer Crater Dirt Mound (3D perspective ellipse)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E1730")
        val moundRect = RectF(cx - rx * 1.18f, cy - ry * 1.15f, cx + rx * 1.18f, cy + ry * 1.25f)
        canvas.drawOval(moundRect, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.parseColor("#382A54")
        canvas.drawOval(moundRect, paint)

        // 2. Inner Crater Hole (Dark depth inside)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#080512")
        val holeRect = RectF(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(holeRect, paint)

        // 3. Draw Mole inside clipping path (mole emerges vertically from inside hole)
        if (pit.isOut && pit.popProgress > 0.02f) {
            canvas.save()

            clipPath.reset()
            val clipTop = cy - ry * 3.2f
            val clipBottom = cy + ry * 0.90f
            val clipLeft = cx - rx * 1.20f
            val clipRight = cx + rx * 1.20f
            clipPath.addRect(clipLeft, clipTop, clipRight, clipBottom, Path.Direction.CW)
            canvas.clipPath(clipPath)

            drawMole(canvas, pit)

            canvas.restore()
        }

        // 4. Crater Front Rim (Overlaps the mole base so it sits authentically inside the crater)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1A1429")
        val frontRimRect = RectF(cx - rx * 1.05f, cy - ry * 0.1f, cx + rx * 1.05f, cy + ry * 1.10f)
        canvas.drawArc(frontRimRect, 0f, 180f, true, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = Color.parseColor("#342750")
        canvas.drawArc(frontRimRect, 0f, 180f, false, paint)
    }

    private fun drawMole(canvas: Canvas, pit: MolePit) {
        val cx = pit.x
        val baseCy = pit.y
        val rx = pit.radiusX
        val ry = pit.radiusY

        // Emergence vertical offset: sits snugly inside the hole without detaching
        val maxRise = ry * 1.45f
        val moleCy = baseCy - (maxRise * pit.popProgress)

        // Squash & stretch on hit
        var scaleX = 1.0f
        var scaleY = 1.0f
        if (pit.isStruck) {
            val p = pit.strikeAnimProgress.coerceIn(0f, 1f)
            val squish = sin(p * Math.PI.toFloat()) * 0.35f
            scaleX = 1.0f + squish * 0.40f
            scaleY = 1.0f - squish
        }

        canvas.save()
        canvas.scale(scaleX, scaleY, cx, moleCy + ry * 0.7f)

        val moleW = rx * 0.82f
        val moleH = ry * 1.50f

        // 1. Mole Body (Warm Rich Brown Fur Gradient)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            cx - moleW, moleCy - moleH, cx + moleW, moleCy + moleH,
            Color.parseColor("#855440"), Color.parseColor("#5A3625"),
            Shader.TileMode.CLAMP
        )
        val bodyRect = RectF(cx - moleW, moleCy - moleH, cx + moleW, moleCy + moleH * 0.8f)
        canvas.drawRoundRect(bodyRect, moleW * 0.85f, moleH * 0.70f, paint)
        paint.shader = null

        // 2. Mole Ears
        val earR = rx * 0.25f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#6E4230")
        canvas.drawCircle(cx - moleW * 0.72f, moleCy - moleH * 0.62f, earR, paint)
        canvas.drawCircle(cx + moleW * 0.72f, moleCy - moleH * 0.62f, earR, paint)

        // Inner Pink Ear
        paint.color = Color.parseColor("#FF80AB")
        canvas.drawCircle(cx - moleW * 0.72f, moleCy - moleH * 0.62f, earR * 0.55f, paint)
        canvas.drawCircle(cx + moleW * 0.72f, moleCy - moleH * 0.62f, earR * 0.55f, paint)

        // 3. Player Bandana / Headband (Player Color)
        val bandanaCy = moleCy - moleH * 0.55f
        val bandanaH = ry * 0.30f
        paint.style = Paint.Style.FILL
        paint.color = pit.moleColor

        val bandRect = RectF(cx - moleW * 0.94f, bandanaCy - bandanaH * 0.5f, cx + moleW * 0.94f, bandanaCy + bandanaH * 0.5f)
        canvas.drawRoundRect(bandRect, 4f * density, 4f * density, paint)

        // Bandana knot & flapping ties on the side
        val knotX = cx + moleW * 0.88f
        val knotY = bandanaCy
        canvas.drawCircle(knotX, knotY, bandanaH * 0.45f, paint)

        val tiePath = Path()
        tiePath.moveTo(knotX, knotY)
        tiePath.lineTo(knotX + bandanaH * 0.85f, knotY - bandanaH * 0.5f)
        tiePath.lineTo(knotX + bandanaH * 0.65f, knotY + bandanaH * 0.7f)
        tiePath.close()
        canvas.drawPath(tiePath, paint)

        // White accent stripe
        paint.color = Color.WHITE
        paint.alpha = 160
        paint.strokeWidth = 2f * density
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cx - moleW * 0.8f, bandanaCy, cx + moleW * 0.8f, bandanaCy, paint)
        paint.alpha = 255

        // 4. Snout / Muzzle (Cream Beige)
        val snoutW = moleW * 0.68f
        val snoutH = moleH * 0.35f
        val snoutCy = moleCy + moleH * 0.05f
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#EAD7C6")
        val snoutRect = RectF(cx - snoutW, snoutCy - snoutH, cx + snoutW, snoutCy + snoutH)
        canvas.drawRoundRect(snoutRect, snoutW * 0.7f, snoutH * 0.8f, paint)

        // 5. Pink Nose
        val noseW = rx * 0.17f
        val noseH = ry * 0.16f
        val noseCy = snoutCy - snoutH * 0.30f
        paint.color = Color.parseColor("#FF4081")
        val noseRect = RectF(cx - noseW, noseCy - noseH, cx + noseW, noseCy + noseH)
        canvas.drawRoundRect(noseRect, noseW * 0.5f, noseH * 0.5f, paint)

        // 6. Buck Teeth
        paint.color = Color.WHITE
        val toothW = rx * 0.08f
        val toothH = ry * 0.20f
        val toothTop = snoutCy + snoutH * 0.65f
        canvas.drawRoundRect(RectF(cx - toothW * 1.15f, toothTop, cx - toothW * 0.15f, toothTop + toothH), 2.5f * density, 2.5f * density, paint)
        canvas.drawRoundRect(RectF(cx + toothW * 0.15f, toothTop, cx + toothW * 1.15f, toothTop + toothH), 2.5f * density, 2.5f * density, paint)

        // 7. Whiskers
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = Color.parseColor("#3A2218")
        canvas.drawLine(cx - snoutW * 0.8f, snoutCy, cx - snoutW * 1.3f, snoutCy - 3f * density, paint)
        canvas.drawLine(cx - snoutW * 0.8f, snoutCy + 4f * density, cx - snoutW * 1.3f, snoutCy + 6f * density, paint)
        canvas.drawLine(cx + snoutW * 0.8f, snoutCy, cx + snoutW * 1.3f, snoutCy - 3f * density, paint)
        canvas.drawLine(cx + snoutW * 0.8f, snoutCy + 4f * density, cx + snoutW * 1.3f, snoutCy + 6f * density, paint)

        // 8. Eyes
        val eyeSpacing = moleW * 0.42f
        val eyeCy = moleCy - moleH * 0.28f
        val eyeR = rx * 0.14f

        if (pit.isStruck) {
            // Knockout X X Eyes!
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.8f * density
            paint.color = Color.parseColor("#221100")
            val lx = cx - eyeSpacing
            canvas.drawLine(lx - eyeR, eyeCy - eyeR, lx + eyeR, eyeCy + eyeR, paint)
            canvas.drawLine(lx - eyeR, eyeCy + eyeR, lx + eyeR, eyeCy - eyeR, paint)
            val rxP = cx + eyeSpacing
            canvas.drawLine(rxP - eyeR, eyeCy - eyeR, rxP + eyeR, eyeCy + eyeR, paint)
            canvas.drawLine(rxP - eyeR, eyeCy + eyeR, rxP + eyeR, eyeCy - eyeR, paint)

            drawDizzyStars(canvas, cx, moleCy - moleH * 1.05f, rx * 0.48f, pit.strikeAnimProgress)
        } else {
            // Big Shiny Eyes
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#150A05")
            canvas.drawCircle(cx - eyeSpacing, eyeCy, eyeR, paint)
            canvas.drawCircle(cx + eyeSpacing, eyeCy, eyeR, paint)

            // White catchlight shine
            paint.color = Color.WHITE
            canvas.drawCircle(cx - eyeSpacing - eyeR * 0.3f, eyeCy - eyeR * 0.3f, eyeR * 0.35f, paint)
            canvas.drawCircle(cx + eyeSpacing - eyeR * 0.3f, eyeCy - eyeR * 0.3f, eyeR * 0.35f, paint)
        }

        canvas.restore()
    }

    private fun drawDizzyStars(canvas: Canvas, cx: Float, cy: Float, radius: Float, animP: Float) {
        val angle = animP * 720f * (Math.PI.toFloat() / 180f)
        val starColors = intArrayOf(Color.parseColor("#FFD700"), Color.parseColor("#FF9500"), Color.WHITE)

        paint.style = Paint.Style.FILL
        for (i in 0 until 3) {
            val a = angle + i * (2f * Math.PI.toFloat() / 3f)
            val sx = cx + cos(a) * radius
            val sy = cy + sin(a) * (radius * 0.45f)
            paint.color = starColors[i % starColors.size]
            canvas.drawCircle(sx, sy, 3.5f * density, paint)
        }
    }

    private fun drawHammerSlams(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        for (slam in hammerSlams) {
            val r = (25f + slam.progress * 40f) * density
            val alpha = ((1f - slam.progress) * 255).toInt().coerceIn(0, 255)
            paint.strokeWidth = (3.5f * (1f - slam.progress)) * density
            paint.color = slam.color
            paint.alpha = alpha
            canvas.drawCircle(slam.x, slam.y, r, paint)

            val len = r * 0.55f
            canvas.drawLine(slam.x - len, slam.y, slam.x + len, slam.y, paint)
            canvas.drawLine(slam.x, slam.y - len, slam.x, slam.y + len, paint)
        }
        paint.alpha = 255
    }

    private fun drawFloatingScores(canvas: Canvas) {
        textPaint.textSize = 22f * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isFakeBoldText = true

        for (fs in floatingScores) {
            textPaint.color = fs.color
            textPaint.alpha = (fs.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawText(fs.text, fs.x, fs.y, textPaint)
        }
        textPaint.alpha = 255
    }

    private fun drawTopHUDAndPlayerStations(canvas: Canvas) {
        val badgeW = (width * 0.38f).coerceIn(120f * density, 146f * density)
        val badgeH = 38f * density
        val topY = topSafeY
        val bottomY = bottomSafeY

        // 1. Center Timer Pill (Own dedicated row at timerY, never cramming with player badges)
        val timerW = 86f * density
        val timerH = 30f * density
        drawTimerBadge(canvas, (width - timerW) / 2f, timerY, timerW, timerH)

        // 2. Player Corner Badges
        when (players.size) {
            2 -> {
                // Top row: Player 2 on Top-Right
                drawSinglePlayerStation(canvas, players[1], width - badgeW - 14f * density, topY, badgeW, badgeH)

                // Bottom row: Player 1 centered
                drawSinglePlayerStation(canvas, players[0], (width - badgeW) / 2f, bottomY, badgeW, badgeH)
            }
            3 -> {
                // Top row: P2 on Left, P3 on Right (spacious, wide gap between them)
                drawSinglePlayerStation(canvas, players[1], 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[2], width - badgeW - 14f * density, topY, badgeW, badgeH)

                // Bottom row: P1 centered
                drawSinglePlayerStation(canvas, players[0], (width - badgeW) / 2f, bottomY, badgeW, badgeH)
            }
            4 -> {
                // Top row: P2 on Left, P3 on Right
                drawSinglePlayerStation(canvas, players[1], 14f * density, topY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[2], width - badgeW - 14f * density, topY, badgeW, badgeH)

                // Bottom row: P1 on Left, P4 on Right
                drawSinglePlayerStation(canvas, players[0], 14f * density, bottomY, badgeW, badgeH)
                drawSinglePlayerStation(canvas, players[3], width - badgeW - 14f * density, bottomY, badgeW, badgeH)
            }
            else -> {
                // 1 Player: Bot on Top-Right, P1 at Bottom
                if (players.size > 1) {
                    drawSinglePlayerStation(canvas, players[1], width - badgeW - 14f * density, topY, badgeW, badgeH)
                }
                drawSinglePlayerStation(canvas, players[0], (width - badgeW) / 2f, bottomY, badgeW, badgeH)
            }
        }
    }

    private fun drawTimerBadge(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val rect = RectF(x, y, x + w, y + h)

        // Glass background with neon cyan/red outline
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B152E")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        val isUrgent = roundTimeRemaining <= 10f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = if (isUrgent) Color.parseColor("#FF3B30") else Color.parseColor("#00E5FF")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        // Time text: ⏱️ 45s
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 13f * density
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = if (isUrgent) Color.parseColor("#FF3B30") else Color.parseColor("#00E5FF")
        textPaint.isFakeBoldText = true
        val timeSec = ceil(roundTimeRemaining).toInt()
        canvas.drawText("⏱️ ${timeSec}s", rect.centerX(), rect.centerY() + 4.5f * density, textPaint)
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

        // Glass background with player neon color border
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#221A3B")
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = player.id.color
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        // Player Initial Circle
        val iconR = h * 0.34f
        val iconCx = x + iconR + 8f * density
        val iconCy = y + h * 0.5f

        paint.style = Paint.Style.FILL
        paint.color = player.id.color
        canvas.drawCircle(iconCx, iconCy, iconR, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10.5f * density
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        val initial = if (player.isBot) "🤖" else player.id.displayName.take(2).uppercase()
        canvas.drawText(initial, iconCx, iconCy + 3.8f * density, textPaint)

        // Player Name & Score
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 10f * density
        textPaint.color = Color.parseColor("#B0A4D0")
        val pName = getPlayerDisplayName(player)
        canvas.drawText(pName, iconCx + iconR + 6f * density, y + h * 0.40f, textPaint)

        val count = molesWhacked[player.id] ?: 0
        textPaint.textSize = 12.5f * density
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        canvas.drawText("$count 🐹", iconCx + iconR + 6f * density, y + h * 0.82f, textPaint)
    }

    override fun getPlayerScoreString(player: Player): String {
        val moles = molesWhacked[player.id] ?: 0
        return "$moles ${if (moles == 1) "Mole" else "Moles"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return molesWhacked[player.id] ?: 0
    }
}
