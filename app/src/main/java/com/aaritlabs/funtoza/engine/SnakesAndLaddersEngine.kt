package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class BoardLadder(val start: Int, val end: Int)

data class BoardSnake(
    val head: Int,
    val tail: Int,
    val color: Int,
    val bellyColor: Int,
    val waveAmpFactor: Float = 1.0f
)

class SnakesAndLaddersEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density

    // Board configuration: 10 x 10 grid (1 to 100)
    private val totalTiles = 100
    private var boardSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cellSize = 0f

    // 10 Non-overlapping Ladders perfectly matching the reference image layout
    private val ladders = listOf(
        BoardLadder(3, 19),   // Bottom left ladder
        BoardLadder(8, 28),   // Bottom right ladder
        BoardLadder(21, 39),  // Mid-lower left diagonal ladder
        BoardLadder(26, 47),  // Center diagonal ladder (clean, goes up-right from 26 to 47)
        BoardLadder(31, 50),  // Mid-right vertical ladder
        BoardLadder(37, 44),  // Center-left short ladder (37 to 44)
        BoardLadder(53, 74),  // Upper right diagonal ladder (53 to 74)
        BoardLadder(65, 85),  // Upper center vertical ladder (column 5)
        BoardLadder(82, 98),  // Upper left diagonal ladder (82 to 98)
        BoardLadder(89, 92)   // Upper right short ladder (89 to 92)
    )

    // 10 Non-overlapping Expressive Cartoon Snakes matching the reference image
    private val snakes = listOf(
        // 1. Cute Cyan Snake Top-Left (99 -> 81)
        BoardSnake(99, 81, Color.parseColor("#00E5FF"), Color.parseColor("#B0F5FF"), 0.85f),
        // 2. Coral Red Snake Upper-Mid (97 -> 57) - Sinuous wave down column 4
        BoardSnake(97, 57, Color.parseColor("#FF3B5C"), Color.parseColor("#FFA3B2"), 1.0f),
        // 3. Bright Emerald Green Snake (95 -> 75) - Column 6
        BoardSnake(95, 75, Color.parseColor("#00E676"), Color.parseColor("#A3FFD1"), 0.85f),
        // 4. Golden Yellow Snake (93 -> 73) - Column 8
        BoardSnake(93, 73, Color.parseColor("#FFD600"), Color.parseColor("#FFF4A8"), 0.85f),
        // 5. Electric Azure Snake Far-Right (72 -> 51)
        BoardSnake(72, 51, Color.parseColor("#00B0FF"), Color.parseColor("#A6E4FF"), 0.85f),
        // 6. Amber Orange Snake Mid-Left (60 -> 41)
        BoardSnake(60, 41, Color.parseColor("#FF9100"), Color.parseColor("#FFD9A8"), 0.85f),
        // 7. Vivid Teal Snake Center (56 -> 36) - Column 5 (between ladders 37->44 and 26->47)
        BoardSnake(56, 36, Color.parseColor("#1DE9B6"), Color.parseColor("#B4FDF1"), 0.85f),
        // 8. Sunset Orange Snake Mid-Right (49 -> 29)
        BoardSnake(49, 29, Color.parseColor("#FF6D00"), Color.parseColor("#FFC89E"), 0.85f),
        // 9. Cyan Blue Snake Lower-Left (23 -> 4)
        BoardSnake(23, 4, Color.parseColor("#00B0FF"), Color.parseColor("#B0F5FF"), 0.85f),
        // 10. Neon Purple Snake Bottom (15 -> 5)
        BoardSnake(15, 5, Color.parseColor("#D500F9"), Color.parseColor("#F6B5FF"), 0.85f)
    )

    // Player State
    private val playerPositions = mutableMapOf<PlayerId, Int>()
    private val playerVisualPositions = mutableMapOf<PlayerId, PointF>()

    // Turn Management
    private var currentTurnIndex = 0
    private var isRolling = false
    private var rollTimer = 0f
    private var displayedDice = 1
    private var finalDiceValue = 1
    private var consecutiveSixes = 0

    // Movement & Animation
    private var isMoving = false
    private val moveQueue = mutableListOf<Int>()
    private var hopTimer = 0f
    private val hopDuration = 0.16f
    private var hopStartPoint = PointF()
    private var hopEndPoint = PointF()
    private var overshootWaitTimer = 0f
    private var overshootMessage = ""

    // Slide animation (Ladder / Snake)
    private var isSliding = false
    private var slideTimer = 0f
    private val slideDuration = 0.55f
    private var slideStartTile = 0
    private var slideEndTile = 0
    private var isSlidingUp = true

    // Bot AI & Timers
    private var botThinkTimer = 0f
    private var pulseTimer = 0f

    // Target Blitz styled Player Card & Dynamic Dice Layout Geometry
    private var cardH = 50f * density
    private var cardW = 0f
    private var topCardTop = 0f
    private var bottomCardTop = 0f

    // Dynamic Dice buttons for User Side interaction
    private val bottomDiceRect = RectF()
    private val topDiceRect = RectF()
    private val finishedPlayers = mutableSetOf<PlayerId>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    override fun initGame() {
        playerPositions.clear()
        playerVisualPositions.clear()
        finishedPlayers.clear()
        moveQueue.clear()
        isGameOver = false
        isRolling = false
        isMoving = false
        isSliding = false
        overshootWaitTimer = 0f
        overshootMessage = ""
        currentTurnIndex = 0
        consecutiveSixes = 0
        displayedDice = 1
        finalDiceValue = 1

        players.forEach {
            playerPositions[it.id] = 1
            it.isAlive = true
            it.score = 0
        }

        recomputeBoardGeometry()

        players.forEach {
            playerVisualPositions[it.id] = getTileCenter(1, it.id)
        }

        val firstP = getCurrentPlayer()
        if (firstP != null && firstP.isBot) {
            botThinkTimer = getBotDelay()
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        recomputeBoardGeometry()
        players.forEach {
            val tile = playerPositions[it.id] ?: 1
            playerVisualPositions[it.id] = getTileCenter(tile, it.id)
        }
    }

    private fun recomputeBoardGeometry() {
        if (width <= 0f || height <= 0f) return

        // 1. Target Blitz HUD and Card spacing
        val topHudMargin = 72f * density
        val bottomMargin = 12f * density
        cardH = if (totalPlayers <= 2) 48f * density else 42f * density
        cardW = (width - 20f * density).coerceAtMost(500f * density)

        topCardTop = topHudMargin + 2f * density
        val topCardBottom = topCardTop + cardH

        val bottomCardBottom = height - bottomMargin
        bottomCardTop = bottomCardBottom - cardH

        // 2. Middle area available for board and top/bottom dice buttons
        val middleSpace = bottomCardTop - topCardBottom
        val diceH = 44f * density
        val diceW = 158f * density

        // Available vertical space for the board
        val maxBoardH = middleSpace - (diceH * 2f + 16f * density)
        val maxBoardW = width - 10f * density
        boardSize = min(maxBoardW, maxBoardH).coerceAtLeast(240f * density)
        cellSize = boardSize / 10f

        boardLeft = (width - boardSize) / 2f
        boardTop = (topCardBottom + bottomCardTop - boardSize) / 2f
        val boardBottom = boardTop + boardSize

        // Top Dice Button: centered vertically between top player card and top of board
        val topDiceY = (topCardBottom + boardTop) / 2f
        topDiceRect.set(width / 2f - diceW / 2f, topDiceY - diceH / 2f, width / 2f + diceW / 2f, topDiceY + diceH / 2f)

        // Bottom Dice Button: centered vertically between bottom of board and bottom player card
        val bottomDiceY = (boardBottom + bottomCardTop) / 2f
        bottomDiceRect.set(width / 2f - diceW / 2f, bottomDiceY - diceH / 2f, width / 2f + diceW / 2f, bottomDiceY + diceH / 2f)
    }

    /**
     * Get active dice button location and orientation angle:
     * - Bottom players (P1, P4): on the bottom side facing P1 (0°)
     * - Top players (P2, P3): on the top side facing P2/P3 (180° for humans, 0° for Bot)
     */
    private fun getActiveDiceInfo(): Pair<RectF, Float> {
        val activeP = getCurrentPlayer() ?: return Pair(bottomDiceRect, 0f)
        val index = players.indexOf(activeP).coerceAtLeast(0)
        val isBottom = (index == 0 || index == 3)

        return if (isBottom) {
            Pair(bottomDiceRect, 0f)
        } else {
            val rot = if (activeP.isBot) 0f else 180f
            Pair(topDiceRect, rot)
        }
    }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        return players[currentTurnIndex % players.size]
    }

    private fun getBotDelay(): Float {
        return when (botDifficulty) {
            0 -> 0.85f
            2 -> 0.45f
            else -> 0.65f
        }
    }

    /**
     * Maps tile number 1..100 to screen coordinates (X, Y)
     * Row 1 (1..10) is bottom, zig-zagging to Row 10 (91..100) at top-left.
     */
    private fun getTileCenter(tileNum: Int, playerId: PlayerId? = null): PointF {
        val clamped = tileNum.coerceIn(1, 100)
        val rowFromBottom = (clamped - 1) / 10 // 0..9
        val isLeftToRight = (rowFromBottom % 2 == 0)
        val col = if (isLeftToRight) {
            (clamped - 1) % 10
        } else {
            9 - ((clamped - 1) % 10)
        }

        var cx = boardLeft + col * cellSize + cellSize / 2f
        var cy = boardTop + (9 - rowFromBottom) * cellSize + cellSize / 2f

        // When multiple players are on the same tile, offset pucks in a 2x2 cluster
        if (playerId != null && totalPlayers > 1) {
            val offset = cellSize * 0.18f
            when (playerId) {
                PlayerId.PLAYER_1 -> { cx -= offset; cy += offset }
                PlayerId.PLAYER_2 -> { cx += offset; cy -= offset }
                PlayerId.PLAYER_3 -> { cx -= offset; cy -= offset }
                PlayerId.PLAYER_4 -> { cx += offset; cy += offset }
            }
        }

        return PointF(cx, cy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isRolling || isMoving || isSliding || overshootWaitTimer > 0f) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val activeP = getCurrentPlayer() ?: return true
            if (activeP.isBot) return true // Ignore touches during bot turns

            val tx = event.x
            val ty = event.y
            val index = players.indexOf(activeP).coerceAtLeast(0)
            val isBottom = (index == 0 || index == 3)

            val (activeDiceRect, _) = getActiveDiceInfo()

            if (isBottom) {
                // Bottom player can tap either the dice button or anywhere in the bottom area
                if (activeDiceRect.contains(tx, ty) || ty >= (boardTop + boardSize)) {
                    triggerRoll(activeP)
                }
            } else {
                // Top player can tap either the top dice button or anywhere in the top area
                if (activeDiceRect.contains(tx, ty) || ty <= boardTop) {
                    triggerRoll(activeP)
                }
            }
        }
        return true
    }

    private fun triggerRoll(player: Player) {
        if (isRolling || isMoving || isSliding || isGameOver || overshootWaitTimer > 0f) return

        isRolling = true
        rollTimer = 0.55f
        soundManager.play(SoundManager.SoundType.TAP, 0.9f)
        triggerVibration(25)
    }

    override fun update(deltaTime: Float) {
        pulseTimer += deltaTime

        if (isGameOver) {
            particles.update()
            return
        }

        // Handle pause when roll overshoots (no bounce back - user waits for exact roll)
        if (overshootWaitTimer > 0f) {
            overshootWaitTimer -= deltaTime
            if (overshootWaitTimer <= 0f) {
                val activeP = getCurrentPlayer()
                if (activeP != null) {
                    finishTurn(activeP)
                }
            }
            particles.update()
            return
        }

        // 1. Handle Dice Rolling Animation
        if (isRolling) {
            rollTimer -= deltaTime
            displayedDice = random.nextInt(1, 7)
            if (random.nextFloat() < 0.35f) {
                soundManager.play(SoundManager.SoundType.TAP, 0.4f)
            }

            if (rollTimer <= 0f) {
                isRolling = false
                finalDiceValue = random.nextInt(1, 7)
                displayedDice = finalDiceValue
                soundManager.play(SoundManager.SoundType.BOUNCE, 1.0f)
                triggerVibration(45)

                startPawnMovement(finalDiceValue)
            }
            particles.update()
            return
        }

        // 2. Handle Step-by-Step Pawn Hopping Animation
        if (isMoving) {
            hopTimer -= deltaTime
            val frac = (1f - (hopTimer / hopDuration)).coerceIn(0f, 1f)

            val activeP = getCurrentPlayer()
            if (activeP != null) {
                val currentVisual = playerVisualPositions[activeP.id] ?: PointF()
                currentVisual.x = hopStartPoint.x + (hopEndPoint.x - hopStartPoint.x) * frac
                // Arc jump upward
                val hopArc = sin(frac * Math.PI).toFloat() * (16f * density)
                currentVisual.y = (hopStartPoint.y + (hopEndPoint.y - hopStartPoint.y) * frac) - hopArc
            }

            if (hopTimer <= 0f) {
                val activeP = getCurrentPlayer()
                if (activeP != null && moveQueue.isNotEmpty()) {
                    val nextTile = moveQueue.removeAt(0)
                    playerPositions[activeP.id] = nextTile
                    soundManager.play(SoundManager.SoundType.TAP, 0.7f)
                    triggerVibration(15)

                    // Spawn small step dust
                    val endCenter = getTileCenter(nextTile, activeP.id)
                    particles.spawnExplosion(endCenter.x, endCenter.y, activeP.id.color, 8)

                    if (moveQueue.isNotEmpty()) {
                        hopStartPoint = PointF(endCenter.x, endCenter.y)
                        val followingTile = moveQueue.first()
                        hopEndPoint = getTileCenter(followingTile, activeP.id)
                        hopTimer = hopDuration
                    } else {
                        // Finished walking steps! Check ladder / snake or win!
                        isMoving = false
                        playerVisualPositions[activeP.id] = endCenter
                        onPawnLanded(activeP, nextTile)
                    }
                } else {
                    isMoving = false
                }
            }
            particles.update()
            return
        }

        // 3. Handle Ladder Climbing or Snake Sliding Animation
        if (isSliding) {
            slideTimer -= deltaTime
            val frac = (1f - (slideTimer / slideDuration)).coerceIn(0f, 1f)

            val activeP = getCurrentPlayer()
            if (activeP != null) {
                val pStart = getTileCenter(slideStartTile, activeP.id)
                val pEnd = getTileCenter(slideEndTile, activeP.id)
                val currentVisual = playerVisualPositions[activeP.id] ?: PointF()

                currentVisual.x = pStart.x + (pEnd.x - pStart.x) * frac
                currentVisual.y = pStart.y + (pEnd.y - pStart.y) * frac
            }

            if (slideTimer <= 0f) {
                isSliding = false
                val activeP = getCurrentPlayer()
                if (activeP != null) {
                    playerPositions[activeP.id] = slideEndTile
                    playerVisualPositions[activeP.id] = getTileCenter(slideEndTile, activeP.id)

                    // Check win after sliding (e.g. from ladder)
                    if (slideEndTile == totalTiles) {
                        if (handleTile100Landed(activeP)) return
                        return
                    }

                    finishTurn(activeP)
                }
            }
            particles.update()
            return
        }

        // 4. Bot Turn Automation
        val activeP = getCurrentPlayer()
        if (activeP != null && activeP.isBot && !isRolling && !isMoving && !isSliding) {
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f) {
                triggerRoll(activeP)
            }
        }

        particles.update()
    }

    private fun startPawnMovement(steps: Int) {
        val activeP = getCurrentPlayer() ?: return
        val currentTile = playerPositions[activeP.id] ?: 1

        moveQueue.clear()

        val targetTile = currentTile + steps
        if (targetTile <= totalTiles) {
            for (t in (currentTile + 1)..targetTile) {
                moveQueue.add(t)
            }
        } else {
            // Do NOT bounce back!
            // When targetTile > 100 (e.g. on 99 rolling 2..6, on 96 rolling 5..6):
            // The pawn stays in place and waits for the exact number to win.
            moveQueue.clear()
        }

        if (moveQueue.isNotEmpty()) {
            isMoving = true
            val firstTarget = moveQueue.first()
            hopStartPoint = getTileCenter(currentTile, activeP.id)
            hopEndPoint = getTileCenter(firstTarget, activeP.id)
            hopTimer = hopDuration
        } else {
            // Overshot: can't move, show informative message and wait briefly
            val needed = totalTiles - currentTile
            overshootMessage = "NEED $needed TO WIN!"
            overshootWaitTimer = 0.85f
            soundManager.play(SoundManager.SoundType.BOUNCE, 0.6f)
            triggerVibration(30)
        }
    }

    private fun handleTile100Landed(player: Player): Boolean {
        if (isTeamMode) {
            finishedPlayers.add(player.id)
            val t1Both = finishedPlayers.contains(PlayerId.PLAYER_1) && finishedPlayers.contains(PlayerId.PLAYER_2)
            val t2Both = finishedPlayers.contains(PlayerId.PLAYER_3) && finishedPlayers.contains(PlayerId.PLAYER_4)
            if (t1Both) {
                winningTeam = 1
                celebrateWin(players[0])
                return true
            } else if (t2Both) {
                winningTeam = 2
                celebrateWin(players[2])
                return true
            }
            overshootMessage = "🌟 ${getPlayerDisplayName(player).uppercase()} AT 100! TEAMMATE TO WIN"
            overshootWaitTimer = 1.3f
            soundManager.play(SoundManager.SoundType.GOAL, 0.9f)
            triggerVibration(60)
            finishTurn(player)
            return false
        } else {
            celebrateWin(player)
            return true
        }
    }

    private fun onPawnLanded(player: Player, tile: Int) {
        // 1. Check Win
        if (tile == totalTiles) {
            if (handleTile100Landed(player)) return
            return
        }

        // 2. Check Ladder
        val ladder = ladders.firstOrNull { it.start == tile }
        if (ladder != null) {
            isSliding = true
            isSlidingUp = true
            slideStartTile = ladder.start
            slideEndTile = ladder.end
            slideTimer = slideDuration
            soundManager.play(SoundManager.SoundType.SHOOT, 0.9f)
            triggerVibration(60)

            val ladderStart = getTileCenter(ladder.start, player.id)
            particles.spawnExplosion(ladderStart.x, ladderStart.y, Color.parseColor("#FFD54F"), 30)
            return
        }

        // 3. Check Snake
        val snake = snakes.firstOrNull { it.head == tile }
        if (snake != null) {
            isSliding = true
            isSlidingUp = false
            slideStartTile = snake.head
            slideEndTile = snake.tail
            slideTimer = slideDuration
            soundManager.play(SoundManager.SoundType.BOUNCE, 0.9f)
            triggerVibration(70)

            val snakeHead = getTileCenter(snake.head, player.id)
            particles.spawnExplosion(snakeHead.x, snakeHead.y, snake.color, 30)
            return
        }

        // Regular landing, finish turn
        finishTurn(player)
    }

    private fun finishTurn(player: Player) {
        // Rolling a 6 gives an extra turn! (max 2 extra turns, unless player already at 100)
        if (finalDiceValue == 6 && consecutiveSixes < 2 && (!isTeamMode || !finishedPlayers.contains(player.id))) {
            consecutiveSixes++
            soundManager.play(SoundManager.SoundType.TAP, 1.0f)
            if (player.isBot) {
                botThinkTimer = getBotDelay()
            }
        } else {
            consecutiveSixes = 0
            advanceToNextActivePlayer()
        }
    }

    private fun advanceToNextActivePlayer() {
        for (i in 1..players.size) {
            currentTurnIndex = (currentTurnIndex + 1) % players.size
            val nextP = getCurrentPlayer()
            if (nextP != null) {
                if (isTeamMode && finishedPlayers.contains(nextP.id)) {
                    continue
                }
                if (nextP.isBot) {
                    botThinkTimer = getBotDelay()
                }
                break
            }
        }
    }

    override fun getTeamScoreString(team: Int): String {
        val (pA, pB) = if (team == 1) Pair(PlayerId.PLAYER_1, PlayerId.PLAYER_2) else Pair(PlayerId.PLAYER_3, PlayerId.PLAYER_4)
        val posA = playerPositions[pA] ?: 1
        val posB = playerPositions[pB] ?: 1
        val bothWon = finishedPlayers.contains(pA) && finishedPlayers.contains(pB)
        return if (bothWon) "BOTH AT 100 🏆" else "Pips: $posA & $posB"
    }

    private fun celebrateWin(player: Player) {
        val center = getTileCenter(totalTiles, player.id)
        particles.spawnExplosion(center.x, center.y, Color.parseColor("#FFD54F"), 60)
        soundManager.play(SoundManager.SoundType.VICTORY)
        triggerVibration(120)
        endRound(player)
    }

    override fun render(canvas: Canvas) {
        // 1. Cosmic Neon Background
        canvas.drawColor(Color.parseColor("#0C091D"))

        // Ambient cosmic stars
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1634")
        for (i in 0..15) {
            val sx = (i * 89f * density) % width
            val sy = (i * 143f * density) % height
            canvas.drawCircle(sx, sy, 2.5f * density, paint)
        }

        // 2. Player Status HUD (Top & Bottom)
        drawPlayerStatusCards(canvas)

        // 3. Render 10x10 Checkered Board matching the reference image layout
        drawBoardGrid(canvas)

        // 4. Render Ladders with authentic wooden rails & rungs
        drawLadders(canvas)

        // 5. Render Expressive Cartoon Snakes with Drop Shadows & Cute Eyes
        drawSnakes(canvas)

        // 6. Render 3D Puck Pawns (like in the bottom-left of reference image)
        drawPlayerPawns(canvas)

        // 7. Render Interactive Dice Controls
        drawDiceControls(canvas)

        // 8. Particles
        particles.draw(canvas)
    }

    /**
     * Draw 10x10 Checkered Board in Dark Cosmic theme with direction chevrons (<, >) and Star 100
     */
    private fun drawBoardGrid(canvas: Canvas) {
        // Outer dark frame with neon glow
        val frameRect = RectF(
            boardLeft - 4f * density,
            boardTop - 4f * density,
            boardLeft + boardSize + 4f * density,
            boardTop + boardSize + 4f * density
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density
        paint.color = Color.parseColor("#4A3778")
        canvas.drawRoundRect(frameRect, 14f * density, 14f * density, paint)

        // Draw checkered tiles
        for (tileNum in 1..totalTiles) {
            val p = getTileCenter(tileNum)
            val tileRect = RectF(
                p.x - cellSize / 2f,
                p.y - cellSize / 2f,
                p.x + cellSize / 2f,
                p.y + cellSize / 2f
            )

            val rowFromBottom = (tileNum - 1) / 10
            val col = if (rowFromBottom % 2 == 0) (tileNum - 1) % 10 else 9 - ((tileNum - 1) % 10)
            val isEven = (rowFromBottom + col) % 2 == 0

            paint.style = Paint.Style.FILL
            when (tileNum) {
                100 -> paint.color = Color.parseColor("#342718") // Gold Trophy Goal
                1 -> paint.color = Color.parseColor("#122A26") // Neon Start
                else -> paint.color = if (isEven) Color.parseColor("#1E173C") else Color.parseColor("#140F2A")
            }
            canvas.drawRect(tileRect, paint)

            // Inner border line
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * density
            paint.color = when (tileNum) {
                100 -> Color.parseColor("#FFD54F")
                1 -> Color.parseColor("#00E5FF")
                else -> Color.parseColor("#291E47")
            }
            canvas.drawRect(tileRect, paint)

            // Tile Number Text (in top-left / center)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.textSize = 8.5f * density
            paint.isFakeBoldText = true

            when (tileNum) {
                100 -> {
                    paint.color = Color.parseColor("#FFD54F")
                    canvas.drawText("⭐ 100", tileRect.left + 3f * density, tileRect.top + 10f * density, paint)
                }
                1 -> {
                    paint.color = Color.parseColor("#00E5FF")
                    canvas.drawText("START", tileRect.left + 3f * density, tileRect.top + 10f * density, paint)
                }
                else -> {
                    paint.color = Color.parseColor("#8E80BE")
                    canvas.drawText("$tileNum", tileRect.left + 3f * density, tileRect.top + 10f * density, paint)
                }
            }

            // Direction Chevrons (< and >) on row edges matching the reference image!
            if (col == 0 || col == 9) {
                val isRowLeftToRight = (rowFromBottom % 2 == 0)
                val chevron = if (isRowLeftToRight) "›" else "‹"
                paint.color = Color.parseColor("#5A4788")
                paint.textSize = 13f * density
                paint.textAlign = Paint.Align.CENTER
                val chevX = if (col == 0) tileRect.left + 6f * density else tileRect.right - 6f * density
                val chevY = tileRect.centerY() + 4f * density
                canvas.drawText(chevron, chevX, chevY, paint)
            }
            paint.isFakeBoldText = false
        }
    }

    /**
     * Draw ladders with authentic wooden rails, drop shadows, and rungs (like reference image)
     */
    private fun drawLadders(canvas: Canvas) {
        ladders.forEach { ladder ->
            val p1 = getTileCenter(ladder.start)
            val p2 = getTileCenter(ladder.end)

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dist = hypot(dx, dy)
            if (dist <= 0f) return@forEach

            val nx = -dy / dist
            val ny = dx / dist
            val railOffset = cellSize * 0.18f

            // Rails extend slightly past the rungs
            val ext = 5f * density
            val ux = dx / dist
            val uy = dy / dist

            val p1ExtX = p1.x - ux * ext
            val p1ExtY = p1.y - uy * ext
            val p2ExtX = p2.x + ux * ext
            val p2ExtY = p2.y + uy * ext

            val r1x = p1ExtX + nx * railOffset
            val r1y = p1ExtY + ny * railOffset
            val r2x = p2ExtX + nx * railOffset
            val r2y = p2ExtY + ny * railOffset

            val l1x = p1ExtX - nx * railOffset
            val l1y = p1ExtY - ny * railOffset
            val l2x = p2ExtX - nx * railOffset
            val l2y = p2ExtY - ny * railOffset

            // 1. Drop shadow beneath ladder
            val shadowOff = 3f * density
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.2f * density
            paint.color = Color.parseColor("#66000000")
            canvas.drawLine(r1x + shadowOff, r1y + shadowOff, r2x + shadowOff, r2y + shadowOff, paint)
            canvas.drawLine(l1x + shadowOff, l1y + shadowOff, l2x + shadowOff, l2y + shadowOff, paint)

            // 2. Dark wooden rail outline
            paint.strokeWidth = 3.6f * density
            paint.color = Color.parseColor("#221208")
            canvas.drawLine(r1x, r1y, r2x, r2y, paint)
            canvas.drawLine(l1x, l1y, l2x, l2y, paint)

            // 3. Inner golden wood rail
            paint.strokeWidth = 2.2f * density
            paint.color = Color.parseColor("#D4943E") // Rich warm wood
            canvas.drawLine(r1x, r1y, r2x, r2y, paint)
            canvas.drawLine(l1x, l1y, l2x, l2y, paint)

            // 4. Rungs
            val numRungs = (dist / (cellSize * 0.38f)).toInt().coerceIn(3, 8)
            for (i in 1..numRungs) {
                val t = i.toFloat() / (numRungs + 1)
                val rx = (p1.x + nx * railOffset) + (dx * t)
                val ry = (p1.y + ny * railOffset) + (dy * t)
                val lx = (p1.x - nx * railOffset) + (dx * t)
                val ly = (p1.y - ny * railOffset) + (dy * t)

                // Rung shadow
                paint.strokeWidth = 2.4f * density
                paint.color = Color.parseColor("#55000000")
                canvas.drawLine(rx + shadowOff, ry + shadowOff, lx + shadowOff, ly + shadowOff, paint)

                // Rung outline
                paint.strokeWidth = 2.8f * density
                paint.color = Color.parseColor("#241308")
                canvas.drawLine(rx, ry, lx, ly, paint)

                // Rung body
                paint.strokeWidth = 1.6f * density
                paint.color = Color.parseColor("#F8E0A0")
                canvas.drawLine(rx, ry, lx, ly, paint)
            }
        }
    }

    /**
     * Draw expressive cartoon snakes with wavy tapering bodies, drop shadows, cartoon eyes and red tongue!
     * Perfectly matches the cartoon illustration style in the reference image.
     */
    private fun drawSnakes(canvas: Canvas) {
        snakes.forEach { snake ->
            val pHead = getTileCenter(snake.head)
            val pTail = getTileCenter(snake.tail)

            val dx = pTail.x - pHead.x
            val dy = pTail.y - pHead.y
            val dist = hypot(dx, dy)
            if (dist <= 0f) return@forEach

            val nx = -dy / dist
            val ny = dx / dist

            // Smooth cartoon wavy S-curve with natural sinuous ripples
            val numSteps = 40
            val curvePoints = ArrayList<PointF>(numSteps + 1)
            val numCycles = if (snake.head == 97) 1.5 else 1.0
            val maxWave = cellSize * 0.28f * snake.waveAmpFactor

            for (i in 0..numSteps) {
                val t = i.toFloat() / numSteps
                val bx = pHead.x + dx * t
                val by = pHead.y + dy * t

                // Sine wave displacement perpendicular to direction vector
                val waveOffset = sin(t * Math.PI * 2.0 * numCycles).toFloat() * maxWave

                val px = bx + nx * waveOffset
                val py = by + ny * waveOffset
                curvePoints.add(PointF(px, py))
            }

            val startW = cellSize * 0.32f // Neck width (~10-11dp)
            val endW = cellSize * 0.08f   // Pointy tail width (~2.5dp)
            val shadowOffset = 3.5f * density

            paint.strokeCap = Paint.Cap.ROUND
            paint.style = Paint.Style.STROKE

            // 1. Soft Drop Shadow underneath tapering body
            paint.color = Color.parseColor("#45000000")
            for (i in 0 until numSteps) {
                val t = i.toFloat() / numSteps
                val w = (startW + (endW - startW) * t) + 2.5f * density
                paint.strokeWidth = w
                val p1 = curvePoints[i]
                val p2 = curvePoints[i + 1]
                canvas.drawLine(p1.x + shadowOffset, p1.y + shadowOffset, p2.x + shadowOffset, p2.y + shadowOffset, paint)
            }

            // 2. Crisp Black Cartoon Ink Outline
            paint.color = Color.parseColor("#10081C")
            for (i in 0 until numSteps) {
                val t = i.toFloat() / numSteps
                val w = (startW + (endW - startW) * t) + 2.4f * density
                paint.strokeWidth = w
                val p1 = curvePoints[i]
                val p2 = curvePoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }

            // 3. Vibrant Colored Snake Body
            paint.color = snake.color
            for (i in 0 until numSteps) {
                val t = i.toFloat() / numSteps
                val w = startW + (endW - startW) * t
                paint.strokeWidth = w
                val p1 = curvePoints[i]
                val p2 = curvePoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }

            // 4. Lighter Belly Stripe / Highlight along the center
            paint.color = snake.bellyColor
            for (i in 0 until numSteps) {
                val t = i.toFloat() / numSteps
                val w = (startW + (endW - startW) * t) * 0.36f
                paint.strokeWidth = w
                val p1 = curvePoints[i]
                val p2 = curvePoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }

            // 5. Cartoon Head oriented along initial wave direction
            val headDx = if (curvePoints.size > 2) curvePoints[0].x - curvePoints[2].x else -dx
            val headDy = if (curvePoints.size > 2) curvePoints[0].y - curvePoints[2].y else -dy
            val headAngle = atan2(headDy, headDx)
            val headDeg = Math.toDegrees(headAngle.toDouble()).toFloat()
            val cartoonDeg = (headDeg % 360f).coerceIn(-25f, 25f)

            val headW = cellSize * 0.36f
            val headH = cellSize * 0.42f

            canvas.save()
            canvas.translate(pHead.x, pHead.y)
            canvas.rotate(cartoonDeg)

            // Head Shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#45000000")
            canvas.drawOval(RectF(-headW / 2f + shadowOffset, -headH / 2f + shadowOffset, headW / 2f + shadowOffset, headH / 2f + shadowOffset), paint)

            // Forked Red Tongue sticking out from the top of the head
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 1.8f * density
            paint.color = Color.parseColor("#FF1744")
            val tongueStartY = -headH / 2f + 1f * density
            val tongueTipY = tongueStartY - 8f * density
            canvas.drawLine(0f, tongueStartY, 0f, tongueTipY, paint)
            canvas.drawLine(0f, tongueTipY, -3.5f * density, tongueTipY - 3.5f * density, paint)
            canvas.drawLine(0f, tongueTipY, 3.5f * density, tongueTipY - 3.5f * density, paint)

            // Head Black Cartoon Ink Border
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#10081C")
            canvas.drawOval(RectF(-headW / 2f - 1.4f * density, -headH / 2f - 1.4f * density, headW / 2f + 1.4f * density, headH / 2f + 1.4f * density), paint)

            // Head Vibrant Fill
            paint.color = snake.color
            canvas.drawOval(RectF(-headW / 2f, -headH / 2f, headW / 2f, headH / 2f), paint)

            // BIG EXPRESSIVE CARTOON EYES (White with Black Outline and Pupils)
            val eyeSpacing = headW * 0.28f
            val eyeY = -headH * 0.16f
            val eyeR = 4.2f * density

            // White Eyeballs
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(-eyeSpacing, eyeY, eyeR, paint)
            canvas.drawCircle(eyeSpacing, eyeY, eyeR, paint)

            // Black Eye Outlines
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = Color.BLACK
            canvas.drawCircle(-eyeSpacing, eyeY, eyeR, paint)
            canvas.drawCircle(eyeSpacing, eyeY, eyeR, paint)

            // Black Pupils (looking cute / slightly up-forward)
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            val pupilR = 2.2f * density
            val pupilOffsetY = -0.8f * density
            canvas.drawCircle(-eyeSpacing, eyeY + pupilOffsetY, pupilR, paint)
            canvas.drawCircle(eyeSpacing, eyeY + pupilOffsetY, pupilR, paint)

            // White Specular Gleam in Pupils
            paint.color = Color.WHITE
            val gleamR = 0.8f * density
            canvas.drawCircle(-eyeSpacing - 0.7f * density, eyeY + pupilOffsetY - 0.7f * density, gleamR, paint)
            canvas.drawCircle(eyeSpacing - 0.7f * density, eyeY + pupilOffsetY - 0.7f * density, gleamR, paint)

            canvas.restore()
        }
    }

    /**
     * Draw 3D Puck Tokens matching the red & blue pucks in the reference image at Tile 1
     */
    private fun drawPlayerPawns(canvas: Canvas) {
        players.forEach { p ->
            val pos = playerVisualPositions[p.id] ?: return@forEach
            val puckR = cellSize * 0.32f

            // 1. Base Drop Shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#70000000")
            canvas.drawOval(RectF(pos.x - puckR, pos.y + puckR * 0.3f, pos.x + puckR, pos.y + puckR * 1.0f), paint)

            // 2. Dark lower bevel rim (3D thickness)
            paint.color = p.id.darkColor
            canvas.drawOval(RectF(pos.x - puckR, pos.y - puckR * 0.4f, pos.x + puckR, pos.y + puckR * 0.85f), paint)

            // 3. Outer Puck Body
            paint.color = p.id.color
            canvas.drawCircle(pos.x, pos.y, puckR, paint)

            // 4. Metallic White Rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = Color.WHITE
            canvas.drawCircle(pos.x, pos.y, puckR, paint)

            // 5. Specular highlight crescent on top-left of the 3D puck
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#66FFFFFF")
            canvas.drawCircle(pos.x - puckR * 0.28f, pos.y - puckR * 0.28f, puckR * 0.35f, paint)

            // 6. Player Label in center
            paint.color = Color.WHITE
            paint.textSize = 8.5f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = pos.y - (fm.ascent + fm.descent) / 2f
            val label = if (p.isBot) "🤖" else "${p.id.id}"
            canvas.drawText(label, pos.x, baseline, paint)
            paint.isFakeBoldText = false
        }
    }

    /**
     * Draw interactive dice button positioned dynamically at the active user's side (Target Blitz style)
     */
    private fun drawDiceControls(canvas: Canvas) {
        val activeP = getCurrentPlayer() ?: return
        val (diceRect, rotDeg) = getActiveDiceInfo()

        canvas.save()
        if (rotDeg != 0f) {
            canvas.rotate(rotDeg, diceRect.centerX(), diceRect.centerY())
        }

        val pulse = 0.5f + 0.5f * sin(pulseTimer * 4.5f)
        val strokeColor = if (isRolling) Color.parseColor("#FFD54F") else activeP.id.color

        // Button Container
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1336")
        canvas.drawRoundRect(diceRect, 22f * density, 22f * density, paint)

        // Subtle glowing fill
        paint.color = (activeP.id.color and 0x00FFFFFF) or 0x24000000
        canvas.drawRoundRect(diceRect, 22f * density, 22f * density, paint)

        // Pulsing glowing border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (2f + pulse * 1.2f) * density
        paint.color = strokeColor
        canvas.drawRoundRect(diceRect, 22f * density, 22f * density, paint)

        // 3D Dice Cube inside button
        val diceSize = 30f * density
        val diceX = diceRect.left + 22f * density
        val diceY = diceRect.centerY()
        val cubeRect = RectF(diceX - diceSize / 2f, diceY - diceSize / 2f, diceX + diceSize / 2f, diceY + diceSize / 2f)

        canvas.save()
        if (isRolling) {
            val shake = sin(rollTimer * 40f) * 10f
            canvas.rotate(shake, cubeRect.centerX(), cubeRect.centerY())
        }

        // Dice 3D shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#40000000")
        canvas.drawRoundRect(RectF(cubeRect.left + 2f * density, cubeRect.top + 2f * density, cubeRect.right + 2f * density, cubeRect.bottom + 2f * density), 8f * density, 8f * density, paint)

        // Dice White Body
        paint.color = Color.WHITE
        canvas.drawRoundRect(cubeRect, 8f * density, 8f * density, paint)

        // Inner soft bevel
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = Color.parseColor("#E0D6F5")
        canvas.drawRoundRect(cubeRect, 8f * density, 8f * density, paint)

        // Dice Pips
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#180F30")
        drawDicePips(canvas, cubeRect, displayedDice)
        canvas.restore()

        // Button Action Text
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 12.5f * density
        paint.isFakeBoldText = true
        paint.color = Color.WHITE

        val btnText = when {
            overshootWaitTimer > 0f -> overshootMessage.ifEmpty { "NEED EXACT ROLL!" }
            isRolling -> "ROLLING..."
            isMoving || isSliding -> "MOVING..."
            activeP.isBot -> "🤖 BOT'S TURN"
            else -> "TAP TO ROLL 🎲"
        }
        val fm = paint.fontMetrics
        val baseline = diceRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(btnText, diceX + diceSize / 2f + 12f * density, baseline, paint)
        paint.isFakeBoldText = false

        canvas.restore()
    }

    private fun drawDicePips(canvas: Canvas, r: RectF, value: Int) {
        val cx = r.centerX()
        val cy = r.centerY()
        val dotR = 2.4f * density
        val o = r.width() * 0.25f

        fun drawDot(x: Float, y: Float) = canvas.drawCircle(x, y, dotR, paint)

        when (value) {
            1 -> drawDot(cx, cy)
            2 -> { drawDot(cx - o, cy - o); drawDot(cx + o, cy + o) }
            3 -> { drawDot(cx - o, cy - o); drawDot(cx, cy); drawDot(cx + o, cy + o) }
            4 -> {
                drawDot(cx - o, cy - o); drawDot(cx + o, cy - o)
                drawDot(cx - o, cy + o); drawDot(cx + o, cy + o)
            }
            5 -> {
                drawDot(cx - o, cy - o); drawDot(cx + o, cy - o)
                drawDot(cx, cy)
                drawDot(cx - o, cy + o); drawDot(cx + o, cy + o)
            }
            6 -> {
                drawDot(cx - o, cy - o); drawDot(cx + o, cy - o)
                drawDot(cx - o, cy); drawDot(cx + o, cy)
                drawDot(cx - o, cy + o); drawDot(cx + o, cy + o)
            }
        }
    }

    /**
     * Draw sleek player cards matching Target Blitz layout & 180° orientation for human Player 2/3
     */
    private fun drawPlayerStatusCards(canvas: Canvas) {
        val activeP = getCurrentPlayer()

        if (totalPlayers <= 2) {
            val cardLeft = (width - cardW) / 2f

            // 1. Top Card: Player 2 (or Bot)
            val p2 = players.getOrNull(1)
            if (p2 != null) {
                val rectP2 = RectF(cardLeft, topCardTop, cardLeft + cardW, topCardTop + cardH)
                drawStatusCard(canvas, p2, rectP2, (p2 == activeP), isTopPlayer = true)
            }

            // 2. Bottom Card: Player 1
            val p1 = players.getOrNull(0)
            if (p1 != null) {
                val rectP1 = RectF(cardLeft, bottomCardTop, cardLeft + cardW, bottomCardTop + cardH)
                drawStatusCard(canvas, p1, rectP1, (p1 == activeP), isTopPlayer = false)
            }
        } else {
            // 3P & 4P Multiplayer Badges
            val badgeH = 44f * density
            val badgeW = (width - 36f * density) / 2f

            // Top: P3 (Left) & P2 (Right) - Rotated 180° for players across
            val p3 = players.getOrNull(2)
            if (p3 != null) {
                val rectP3 = RectF(14f * density, topCardTop, 14f * density + badgeW, topCardTop + badgeH)
                drawMiniBadge(canvas, p3, rectP3, (p3 == activeP), isTopPlayer = true)
            }
            val p2 = players.getOrNull(1)
            if (p2 != null) {
                val rectP2 = RectF(width - 14f * density - badgeW, topCardTop, width - 14f * density, topCardTop + badgeH)
                drawMiniBadge(canvas, p2, rectP2, (p2 == activeP), isTopPlayer = true)
            }

            // Bottom: P1 (Left or Center) & P4 (Right)
            val p1 = players.getOrNull(0)
            val p4 = players.getOrNull(3)
            if (p4 != null && p1 != null) {
                val rectP1 = RectF(14f * density, bottomCardTop, 14f * density + badgeW, bottomCardTop + badgeH)
                drawMiniBadge(canvas, p1, rectP1, (p1 == activeP), isTopPlayer = false)
                val rectP4 = RectF(width - 14f * density - badgeW, bottomCardTop, width - 14f * density, bottomCardTop + badgeH)
                drawMiniBadge(canvas, p4, rectP4, (p4 == activeP), isTopPlayer = false)
            } else if (p1 != null) {
                val singleW = (width - 40f * density).coerceAtMost(240f * density)
                val rectP1 = RectF((width - singleW) / 2f, bottomCardTop, (width + singleW) / 2f, bottomCardTop + badgeH)
                drawMiniBadge(canvas, p1, rectP1, (p1 == activeP), isTopPlayer = false)
            }
        }
    }

    private fun drawMiniBadge(canvas: Canvas, player: Player, rect: RectF, isTurn: Boolean, isTopPlayer: Boolean) {
        canvas.save()
        if (isTopPlayer && !player.isBot) {
            canvas.rotate(180f, rect.centerX(), rect.centerY())
        }

        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) Color.parseColor("#22183D") else Color.parseColor("#150F28")
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isTurn) 2f * density else 1f * density
        paint.color = if (isTurn) player.id.color else Color.parseColor("#2F2454")
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        val avatarSize = 24f * density
        val avatarX = rect.left + 8f * density + avatarSize / 2f
        val avatarY = rect.centerY()

        paint.style = Paint.Style.FILL
        paint.color = player.id.color
        canvas.drawCircle(avatarX, avatarY, avatarSize / 2f, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val avBaseline = avatarY - (fm.ascent + fm.descent) / 2f
        canvas.drawText("P${player.id.id}", avatarX, avBaseline, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        paint.textSize = 11f * density
        val nameBaseline = rect.centerY() - 2f * density
        var name = getPlayerDisplayName(player)
        if (name.length > 7) name = name.substring(0, 6) + "…"
        canvas.drawText(name, avatarX + avatarSize / 2f + 6f * density, nameBaseline, paint)

        paint.textSize = 9.5f * density
        paint.color = if (isTurn) Color.parseColor("#FFD54F") else Color.parseColor("#8E82B4")
        val pos = playerPositions[player.id] ?: 1
        canvas.drawText("TILE $pos", avatarX + avatarSize / 2f + 6f * density, rect.centerY() + 11f * density, paint)
        paint.isFakeBoldText = false

        canvas.restore()
    }

    private fun drawStatusCard(canvas: Canvas, player: Player, rect: RectF, isTurn: Boolean, isTopPlayer: Boolean) {
        canvas.save()
        if (isTopPlayer && !player.isBot) {
            canvas.rotate(180f, rect.centerX(), rect.centerY())
        }

        // Card Background
        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) Color.parseColor("#22183D") else Color.parseColor("#150F28")
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isTurn) 2.2f * density else 1f * density
        paint.color = if (isTurn) player.id.color else Color.parseColor("#2F2454")
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)

        // Left Avatar Squircle Badge
        val avatarSize = 32f * density
        val avatarX = rect.left + 12f * density + avatarSize / 2f
        val avatarY = rect.centerY()
        val avatarRect = RectF(avatarX - avatarSize / 2f, avatarY - avatarSize / 2f, avatarX + avatarSize / 2f, avatarY + avatarSize / 2f)

        paint.style = Paint.Style.FILL
        paint.color = player.id.color
        canvas.drawRoundRect(avatarRect, 10f * density, 10f * density, paint)

        paint.color = Color.WHITE
        paint.textSize = 12f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val avBaseline = avatarY - (fm.ascent + fm.descent) / 2f
        val label = if (player.isBot) "🤖" else "P${player.id.id}"
        canvas.drawText(label, avatarX, avBaseline, paint)

        // Player Name & Turn info
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        paint.textSize = 13.5f * density
        val nameBaseline = rect.centerY() - 2f * density
        val name = if (player.isBot) "Robi the Bot" else getPlayerDisplayName(player)
        canvas.drawText(name, avatarRect.right + 10f * density, nameBaseline, paint)

        // Role / Status subtitle under name
        paint.textSize = 9.5f * density
        paint.color = if (isTurn) player.id.color else Color.parseColor("#8E82B4")
        val statusSubtitle = if (isTurn) (if (player.isBot) "BOT THINKING..." else "YOUR TURN") else "WAITING"
        canvas.drawText(statusSubtitle, avatarRect.right + 10f * density, rect.centerY() + 11f * density, paint)

        // Current Tile Position Pill on the Right
        val pos = playerPositions[player.id] ?: 1
        val pillW = 84f * density
        val pillH = 26f * density
        val pillR = RectF(rect.right - pillW - 10f * density, rect.centerY() - pillH / 2f, rect.right - 10f * density, rect.centerY() + pillH / 2f)

        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) (player.id.color and 0x00FFFFFF) or 0x33000000 else Color.parseColor("#1F1836")
        canvas.drawRoundRect(pillR, 13f * density, 13f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = if (isTurn) player.id.color else Color.parseColor("#3B2E68")
        canvas.drawRoundRect(pillR, 13f * density, 13f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) Color.parseColor("#FFD54F") else Color.parseColor("#CDC4EB")
        paint.textSize = 11.5f * density
        paint.textAlign = Paint.Align.CENTER
        val pfm = paint.fontMetrics
        val pillBaseline = pillR.centerY() - (pfm.ascent + pfm.descent) / 2f
        canvas.drawText("TILE $pos", pillR.centerX(), pillBaseline, paint)
        paint.isFakeBoldText = false

        canvas.restore()
    }

    override fun getPlayerScoreString(player: Player): String {
        val pos = playerPositions[player.id] ?: 1
        return "Tile $pos"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return playerPositions[player.id] ?: 1
    }
}
