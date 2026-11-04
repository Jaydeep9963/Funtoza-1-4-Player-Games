package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

enum class TokenState {
    IN_BASE,
    ON_BOARD,
    FINISHED
}

data class LudoToken(
    val id: Int, // 0 or 1
    val owner: PlayerId,
    var state: TokenState = TokenState.IN_BASE,
    var step: Int = -1, // -1 in base, 0..50 outer track, 51..55 home stretch, 56 home goal
    var visualPos: PointF = PointF(),
    var isEligible: Boolean = false
)

class LudoEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density

    // Board Geometry (15x15 Ludo grid)
    private val gridCells = 15
    private var boardSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cellSize = 0f

    // Target Blitz styled Layout & Dynamic Dice Buttons
    private var cardH = 50f * density
    private var cardW = 0f
    private var topCardTop = 0f
    private var bottomCardTop = 0f
    private val bottomDiceRect = RectF()
    private val topDiceRect = RectF()

    // 4 Tokens per player for standard classic Ludo
    private val TOKENS_PER_PLAYER = 4
    private val tokens = mutableListOf<LudoToken>()

    // Paths: 52 outer tiles + 5 home stretch + 1 home goal = 57 tiles
    private val playerPaths = mutableMapOf<PlayerId, List<Point>>()
    private val playerBaseSlots = mutableMapOf<PlayerId, List<Point>>()

    // Outer 52 track tiles in clockwise order starting from Red's start tile (1, 8)
    private val outerTrack = listOf(
        // Red horizontal arm moving right
        Point(1, 8), Point(2, 8), Point(3, 8), Point(4, 8), Point(5, 8),
        // Down into bottom-vertical arm
        Point(6, 9), Point(6, 10), Point(6, 11), Point(6, 12), Point(6, 13), Point(6, 14),
        // Bottom center
        Point(7, 14),
        // Up along right side of bottom arm
        Point(8, 14), Point(8, 13), Point(8, 12), Point(8, 11), Point(8, 10), Point(8, 9),
        // Right into right-horizontal arm
        Point(9, 8), Point(10, 8), Point(11, 8), Point(12, 8), Point(13, 8), Point(14, 8),
        // Right center
        Point(14, 7),
        // Left along top side of right arm
        Point(14, 6), Point(13, 6), Point(12, 6), Point(11, 6), Point(10, 6), Point(9, 6),
        // Up into top-vertical arm
        Point(8, 5), Point(8, 4), Point(8, 3), Point(8, 2), Point(8, 1), Point(8, 0),
        // Top center
        Point(7, 0),
        // Down along left side of top arm
        Point(6, 0), Point(6, 1), Point(6, 2), Point(6, 3), Point(6, 4), Point(6, 5),
        // Left into left-horizontal arm
        Point(5, 6), Point(4, 6), Point(3, 6), Point(2, 6), Point(1, 6), Point(0, 6),
        // Left center
        Point(0, 7),
        // Bottom-left corner
        Point(0, 8)
    )

    // Safe track indices where tokens cannot be captured:
    // Starts: 0 (Red), 13 (Yellow), 26 (Blue), 39 (Green).
    // Stars: 8 (Bottom arm), 21 (Right arm), 34 (Top arm), 47 (Left arm).
    private val safeTrackIndices = setOf(0, 8, 13, 21, 26, 34, 39, 47)

    // Turn Management
    private var currentTurnIndex = 0
    private var isRolling = false
    private var rollTimer = 0f
    private var displayedDice = 1
    private var finalDiceValue = 1
    private var consecutiveSixes = 0

    // Token Selection & Movement
    private var waitingForTokenChoice = false
    private var isMoving = false
    private var movingToken: LudoToken? = null
    private val moveQueue = mutableListOf<PointF>()
    private var hopTimer = 0f
    private val hopDuration = 0.16f
    private var hopStartPoint = PointF()
    private var hopEndPoint = PointF()

    // Feedback & Pause Timers
    private var feedbackTimer = 0f
    private var feedbackMessage = ""
    private var botThinkTimer = 0f
    private var pulseTimer = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    init {
        buildPlayerPaths()
    }

    private fun buildPlayerPaths() {
        // Red (P1): Bottom-Left, starts at outerTrack[0]
        playerPaths[PlayerId.PLAYER_1] = buildPathForPlayer(0, listOf(
            Point(1, 7), Point(2, 7), Point(3, 7), Point(4, 7), Point(5, 7)
        ), Point(6, 7))
        playerBaseSlots[PlayerId.PLAYER_1] = listOf(
            Point(1, 10), Point(4, 10), Point(1, 13), Point(4, 13)
        )

        // Yellow (P4): Bottom-Right, starts at outerTrack[13]
        playerPaths[PlayerId.PLAYER_4] = buildPathForPlayer(13, listOf(
            Point(7, 13), Point(7, 12), Point(7, 11), Point(7, 10), Point(7, 9)
        ), Point(7, 8))
        playerBaseSlots[PlayerId.PLAYER_4] = listOf(
            Point(10, 10), Point(13, 10), Point(10, 13), Point(13, 13)
        )

        // Blue (P2): Top-Right, starts at outerTrack[26]
        playerPaths[PlayerId.PLAYER_2] = buildPathForPlayer(26, listOf(
            Point(13, 7), Point(12, 7), Point(11, 7), Point(10, 7), Point(9, 7)
        ), Point(8, 7))
        playerBaseSlots[PlayerId.PLAYER_2] = listOf(
            Point(10, 1), Point(13, 1), Point(10, 4), Point(13, 4)
        )

        // Green (P3): Top-Left, starts at outerTrack[39]
        playerPaths[PlayerId.PLAYER_3] = buildPathForPlayer(39, listOf(
            Point(7, 1), Point(7, 2), Point(7, 3), Point(7, 4), Point(7, 5)
        ), Point(7, 6))
        playerBaseSlots[PlayerId.PLAYER_3] = listOf(
            Point(1, 1), Point(4, 1), Point(1, 4), Point(4, 4)
        )
    }

    private fun buildPathForPlayer(startIndex: Int, homeStretch: List<Point>, homeGoal: Point): List<Point> {
        val list = mutableListOf<Point>()
        // 51 steps along outer track
        for (i in 0..50) {
            val idx = (startIndex + i) % 52
            list.add(outerTrack[idx])
        }
        // 5 steps in colored home stretch
        list.addAll(homeStretch)
        // Step 56: Home Goal
        list.add(homeGoal)
        return list
    }

    override fun initGame() {
        tokens.clear()
        isGameOver = false
        isRolling = false
        isMoving = false
        waitingForTokenChoice = false
        movingToken = null
        moveQueue.clear()
        feedbackTimer = 0f
        feedbackMessage = ""
        currentTurnIndex = 0
        consecutiveSixes = 0
        displayedDice = 1
        finalDiceValue = 1

        // Initialize 2 tokens for each active player in base
        players.forEach { p ->
            p.score = 0
            p.isAlive = true
            for (tId in 0 until TOKENS_PER_PLAYER) {
                tokens.add(
                    LudoToken(
                        id = tId,
                        owner = p.id,
                        state = TokenState.IN_BASE,
                        step = -1
                    )
                )
            }
        }

        recomputeBoardGeometry()

        // Place tokens in their base slot positions
        tokens.forEach { t ->
            t.visualPos = getTokenCenter(t)
        }

        val firstP = getCurrentPlayer()
        if (firstP != null && firstP.isBot) {
            botThinkTimer = getBotDelay()
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        recomputeBoardGeometry()
        tokens.forEach { t ->
            t.visualPos = getTokenCenter(t)
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

        val maxBoardH = middleSpace - (diceH * 2f + 16f * density)
        val maxBoardW = width - 10f * density
        boardSize = min(maxBoardW, maxBoardH).coerceAtLeast(240f * density)
        cellSize = boardSize / 15f

        boardLeft = (width - boardSize) / 2f
        boardTop = (topCardBottom + bottomCardTop - boardSize) / 2f
        val boardBottom = boardTop + boardSize

        // Top Dice Button: centered between top player card and board
        val topDiceY = (topCardBottom + boardTop) / 2f
        topDiceRect.set(width / 2f - diceW / 2f, topDiceY - diceH / 2f, width / 2f + diceW / 2f, topDiceY + diceH / 2f)

        // Bottom Dice Button: centered between board and bottom player card
        val bottomDiceY = (boardBottom + bottomCardTop) / 2f
        bottomDiceRect.set(width / 2f - diceW / 2f, bottomDiceY - diceH / 2f, width / 2f + diceW / 2f, bottomDiceY + diceH / 2f)
    }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        return players[currentTurnIndex % players.size]
    }

    private fun getBotDelay(): Float {
        return when (botDifficulty) {
            0 -> 0.85f
            2 -> 0.42f
            else -> 0.62f
        }
    }

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

    /**
     * Map token state and step to screen coordinates
     */
    private fun getTokenCenter(token: LudoToken): PointF {
        if (token.state == TokenState.IN_BASE) {
            val slots = playerBaseSlots[token.owner] ?: return PointF(boardLeft, boardTop)
            val slot = slots.getOrElse(token.id) { Point(1, 1) }
            val cx = boardLeft + slot.x * cellSize + cellSize / 2f
            val cy = boardTop + slot.y * cellSize + cellSize / 2f
            return PointF(cx, cy)
        }

        val path = playerPaths[token.owner] ?: return PointF(boardLeft, boardTop)
        val myPt = path.getOrElse(token.step.coerceIn(0, path.size - 1)) { Point(7, 7) }

        var cx = boardLeft + myPt.x * cellSize + cellSize / 2f
        var cy = boardTop + myPt.y * cellSize + cellSize / 2f

        // When multiple tokens share the exact same tile on board, cluster them
        val sharedTokens = tokens.filter { other ->
            if (other.state != TokenState.ON_BOARD) return@filter false
            val otherPath = playerPaths[other.owner] ?: return@filter false
            val otherPt = otherPath.getOrElse(other.step.coerceIn(0, otherPath.size - 1)) { Point(-1, -1) }
            otherPt.x == myPt.x && otherPt.y == myPt.y
        }

        if (sharedTokens.size > 1) {
            val idx = sharedTokens.indexOf(token)
            val offset = cellSize * 0.18f
            when (idx) {
                0 -> { cx -= offset; cy -= offset }
                1 -> { cx += offset; cy -= offset }
                2 -> { cx -= offset; cy += offset }
                3 -> { cx += offset; cy += offset }
            }
        }

        return PointF(cx, cy)
    }

    private fun isSharedTile(token: LudoToken): Boolean {
        if (token.state != TokenState.ON_BOARD) return false
        val path = playerPaths[token.owner] ?: return false
        val myPt = path.getOrElse(token.step.coerceIn(0, path.size - 1)) { Point(7, 7) }
        return tokens.count { other ->
            if (other.state != TokenState.ON_BOARD) return@count false
            val otherPath = playerPaths[other.owner] ?: return@count false
            val otherPt = otherPath.getOrElse(other.step.coerceIn(0, otherPath.size - 1)) { Point(-1, -1) }
            otherPt.x == myPt.x && otherPt.y == myPt.y
        } > 1
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isRolling || isMoving || feedbackTimer > 0f) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val activeP = getCurrentPlayer() ?: return true
            if (activeP.isBot) return true // Ignore touches during bot turns

            val tx = event.x
            val ty = event.y

            // Case A: Waiting for player to choose which token to move
            if (waitingForTokenChoice) {
                val eligible = tokens.filter { it.owner == activeP.id && it.isEligible }
                val touchRadius = (cellSize * 1.3f).coerceAtLeast(36f * density)
                val touchedToken = eligible.minByOrNull { t ->
                    hypot(tx - t.visualPos.x, ty - t.visualPos.y)
                }?.takeIf { t ->
                    hypot(tx - t.visualPos.x, ty - t.visualPos.y) <= touchRadius
                }
                if (touchedToken != null) {
                    clearEligibility()
                    waitingForTokenChoice = false
                    startTokenMovement(touchedToken, finalDiceValue)
                    return true
                }
            }

            // Case B: Tap to roll dice
            if (!waitingForTokenChoice) {
                val index = players.indexOf(activeP).coerceAtLeast(0)
                val isBottom = (index == 0 || index == 3)
                val (activeDiceRect, _) = getActiveDiceInfo()

                if (isBottom) {
                    if (activeDiceRect.contains(tx, ty) || ty >= (boardTop + boardSize)) {
                        triggerRoll(activeP)
                    }
                } else {
                    if (activeDiceRect.contains(tx, ty) || ty <= boardTop) {
                        triggerRoll(activeP)
                    }
                }
            }
        }
        return true
    }

    private fun triggerRoll(player: Player) {
        if (isRolling || isMoving || isGameOver || waitingForTokenChoice || feedbackTimer > 0f) return

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

        // 1. Feedback message pause (e.g. "NEED 6 TO OPEN!")
        if (feedbackTimer > 0f) {
            feedbackTimer -= deltaTime
            if (feedbackTimer <= 0f) {
                val activeP = getCurrentPlayer()
                if (activeP != null) {
                    finishTurn(activeP)
                }
            }
            particles.update()
            return
        }

        // 2. Dice Rolling Animation
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

                processDiceRollResult(finalDiceValue)
            }
            particles.update()
            return
        }

        // 3. Step-by-Step Token Hopping Animation
        if (isMoving) {
            hopTimer -= deltaTime
            val frac = (1f - (hopTimer / hopDuration)).coerceIn(0f, 1f)
            val currentT = movingToken

            if (currentT != null) {
                val cx = hopStartPoint.x + (hopEndPoint.x - hopStartPoint.x) * frac
                // Natural jump arc
                val arc = sin(frac * Math.PI).toFloat() * (14f * density)
                val cy = (hopStartPoint.y + (hopEndPoint.y - hopStartPoint.y) * frac) - arc
                currentT.visualPos.set(cx, cy)
            }

            if (hopTimer <= 0f) {
                val activeP = getCurrentPlayer()
                if (currentT != null && moveQueue.isNotEmpty()) {
                    val nextPoint = moveQueue.removeAt(0)
                    soundManager.play(SoundManager.SoundType.TAP, 0.75f)
                    triggerVibration(15)

                    // Step dust
                    particles.spawnExplosion(nextPoint.x, nextPoint.y, currentT.owner.color, 6)

                    if (moveQueue.isNotEmpty()) {
                        hopStartPoint.set(nextPoint)
                        hopEndPoint.set(moveQueue.first())
                        hopTimer = hopDuration
                    } else {
                        // Finished walking!
                        isMoving = false
                        currentT.visualPos.set(nextPoint)
                        onTokenLanded(currentT)
                    }
                } else {
                    isMoving = false
                }
            }
            particles.update()
            return
        }

        // 4. Bot Automation
        val activeP = getCurrentPlayer()
        if (activeP != null && activeP.isBot && !isRolling && !isMoving && feedbackTimer <= 0f) {
            if (waitingForTokenChoice) {
                botThinkTimer -= deltaTime
                if (botThinkTimer <= 0f) {
                    botChooseTokenMove()
                }
            } else {
                botThinkTimer -= deltaTime
                if (botThinkTimer <= 0f) {
                    triggerRoll(activeP)
                }
            }
        }

        particles.update()
    }

    private fun processDiceRollResult(roll: Int) {
        val activeP = getCurrentPlayer() ?: return
        val playerTokens = tokens.filter { it.owner == activeP.id }

        // Find all legal moves for this roll
        val movable = playerTokens.filter { canTokenMove(it, roll) }

        if (movable.isEmpty()) {
            // Cannot move any token
            val allInBase = playerTokens.all { it.state == TokenState.IN_BASE }
            feedbackMessage = if (allInBase) "NEED 6 TO OPEN!" else "CANNOT MOVE!"
            feedbackTimer = 0.85f
            soundManager.play(SoundManager.SoundType.BOUNCE, 0.6f)
            triggerVibration(25)
            return
        }

        if (movable.size == 1) {
            // Only 1 token can move, move automatically!
            startTokenMovement(movable[0], roll)
        } else {
            // Player has multiple choices!
            if (activeP.isBot) {
                waitingForTokenChoice = true
                botThinkTimer = getBotDelay() * 0.8f
            } else {
                waitingForTokenChoice = true
                clearEligibility()
                movable.forEach { it.isEligible = true }
                soundManager.play(SoundManager.SoundType.TAP, 0.8f)
            }
        }
    }

    private fun canTokenMove(token: LudoToken, roll: Int): Boolean {
        if (token.state == TokenState.FINISHED) return false
        if (token.state == TokenState.IN_BASE) return roll == 6
        // On board: must not overshoot Home (step 56)
        return (token.step + roll) <= 56
    }

    private fun clearEligibility() {
        tokens.forEach { it.isEligible = false }
    }

    private fun startTokenMovement(token: LudoToken, roll: Int) {
        clearEligibility()
        waitingForTokenChoice = false
        movingToken = token
        moveQueue.clear()

        val path = playerPaths[token.owner] ?: return

        if (token.state == TokenState.IN_BASE) {
            // Exit base onto Start Tile (step 0)
            token.state = TokenState.ON_BOARD
            token.step = 0
            val startCenter = getTokenCenter(token)
            moveQueue.add(startCenter)
        } else {
            // Walk forward step-by-step
            val fromStep = token.step
            val targetStep = fromStep + roll
            token.step = targetStep

            for (s in (fromStep + 1)..targetStep) {
                val pt = path.getOrElse(s) { Point(7, 7) }
                val cx = boardLeft + pt.x * cellSize + cellSize / 2f
                val cy = boardTop + pt.y * cellSize + cellSize / 2f
                moveQueue.add(PointF(cx, cy))
            }
        }

        if (moveQueue.isNotEmpty()) {
            isMoving = true
            hopStartPoint.set(token.visualPos)
            hopEndPoint.set(moveQueue.first())
            hopTimer = hopDuration
        } else {
            onTokenLanded(token)
        }
    }

    private fun onTokenLanded(token: LudoToken) {
        var earnedBonusRoll = false

        // 1. Check Win / Home Goal (Step 56)
        if (token.step == 56) {
            token.state = TokenState.FINISHED
            val homePos = getTokenCenter(token)
            particles.spawnExplosion(homePos.x, homePos.y, token.owner.color, 35)
            soundManager.play(SoundManager.SoundType.GOAL, 0.9f)
            triggerVibration(60)
            earnedBonusRoll = true

            // Check if player won (all 2 tokens finished)
            val finishedCount = tokens.count { it.owner == token.owner && it.state == TokenState.FINISHED }
            if (finishedCount == TOKENS_PER_PLAYER) {
                val p = players.firstOrNull { it.id == token.owner }
                if (p != null) {
                    celebrateWin(p)
                    return
                }
            }
        }

        // 2. Check Capture of Opponents on Outer Track (Steps 0..50)
        if (token.state == TokenState.ON_BOARD && token.step <= 50) {
            val path = playerPaths[token.owner]
            if (path != null) {
                val landGridPt = path[token.step]
                // Check if this outer track tile is safe
                val outerIdx = outerTrack.indexOf(landGridPt)
                val isSafe = outerIdx in safeTrackIndices

                if (!isSafe && outerIdx != -1) {
                    val captured = tokens.firstOrNull { other ->
                        other.owner != token.owner && other.state == TokenState.ON_BOARD && other.step <= 50 &&
                        playerPaths[other.owner]?.getOrNull(other.step) == landGridPt
                    }
                    if (captured != null) {
                        // Capture! Send opponent back to base
                        captured.state = TokenState.IN_BASE
                        captured.step = -1
                        captured.visualPos = getTokenCenter(captured)

                        val hitCenter = getTokenCenter(token)
                        particles.spawnExplosion(hitCenter.x, hitCenter.y, token.owner.color, 45)
                        soundManager.play(SoundManager.SoundType.EXPLOSION, 0.9f)
                        triggerVibration(90)
                        earnedBonusRoll = true
                    }
                }
            }
        }

        val activeP = getCurrentPlayer() ?: return
        finishTurn(activeP, earnedBonusRoll)
    }

    private fun finishTurn(player: Player, bonusFromCaptureOrHome: Boolean = false) {
        // Rolling a 6 or capturing/finishing earns another turn (max 2 consecutive extra rolls)
        val earnsExtraRoll = (finalDiceValue == 6 || bonusFromCaptureOrHome) && consecutiveSixes < 2

        if (earnsExtraRoll) {
            consecutiveSixes++
            soundManager.play(SoundManager.SoundType.TAP, 1.0f)
            if (player.isBot) {
                botThinkTimer = getBotDelay()
            }
        } else {
            consecutiveSixes = 0
            currentTurnIndex = (currentTurnIndex + 1) % players.size

            val nextP = getCurrentPlayer()
            if (nextP != null && nextP.isBot) {
                botThinkTimer = getBotDelay()
            }
        }
    }

    private fun botChooseTokenMove() {
        val activeP = getCurrentPlayer() ?: return
        val eligible = tokens.filter { it.owner == activeP.id && canTokenMove(it, finalDiceValue) }
        if (eligible.isEmpty()) return

        // Strategic AI Evaluation
        var bestToken = eligible[0]
        var bestScore = -9999

        eligible.forEach { t ->
            var score = 0
            val path = playerPaths[t.owner] ?: return@forEach

            // 1. Can capture opponent? Highest priority (+1000)
            if (t.state == TokenState.ON_BOARD && (t.step + finalDiceValue) <= 50) {
                val landPt = path[t.step + finalDiceValue]
                val outerIdx = outerTrack.indexOf(landPt)
                if (outerIdx != -1 && outerIdx !in safeTrackIndices) {
                    val opponentOnTile = tokens.any { other ->
                        other.owner != t.owner && other.state == TokenState.ON_BOARD &&
                        playerPaths[other.owner]?.getOrNull(other.step) == landPt
                    }
                    if (opponentOnTile) score += 1000
                }
            }

            // 2. Reaches Home? (+500)
            if (t.step + finalDiceValue == 56) {
                score += 500
            }

            // 3. Opening token with a 6? (+300)
            if (t.state == TokenState.IN_BASE && finalDiceValue == 6) {
                score += 300
            }

            // 4. Progress furthest token (+step * 2)
            score += t.step * 2

            if (score > bestScore) {
                bestScore = score
                bestToken = t
            }
        }

        clearEligibility()
        waitingForTokenChoice = false
        startTokenMovement(bestToken, finalDiceValue)
    }

    private fun celebrateWin(player: Player) {
        val cx = boardLeft + boardSize / 2f
        val cy = boardTop + boardSize / 2f
        particles.spawnExplosion(cx, cy, Color.parseColor("#FFD54F"), 60)
        soundManager.play(SoundManager.SoundType.VICTORY)
        triggerVibration(120)
        endRound(player)
    }

    override fun render(canvas: Canvas) {
        // 1. Cosmic Neon Arena Background
        canvas.drawColor(Color.parseColor("#0C091D"))

        // Ambient cosmic stars
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1634")
        for (i in 0..15) {
            val sx = (i * 89f * density) % width
            val sy = (i * 143f * density) % height
            canvas.drawCircle(sx, sy, 2.5f * density, paint)
        }

        // 2. Player Status Cards (Target Blitz style)
        drawPlayerStatusCards(canvas)

        // 3. Render 15x15 Ludo Board
        drawLudoBoard(canvas)

        // 4. Render 3D Puck Tokens
        drawTokens(canvas)

        // 5. Render Interactive Dynamic Dice Controls at active player's side
        drawDiceControls(canvas)

        // 6. Particles
        particles.draw(canvas)
    }

    /**
     * Render the official 15x15 Ludo Board in Dark Cosmic Neon styling
     */
    private fun drawLudoBoard(canvas: Canvas) {
        // Board Outer Glowing Frame
        val frameRect = RectF(
            boardLeft - 4f * density,
            boardTop - 4f * density,
            boardLeft + boardSize + 4f * density,
            boardTop + boardSize + 4f * density
        )
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density
        paint.color = Color.parseColor("#3B2E68")
        canvas.drawRoundRect(frameRect, 14f * density, 14f * density, paint)

        // Board Base Fill
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#120D26")
        canvas.drawRect(RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize), paint)

        // 1. Draw 4 Corner Yards (6x6 cells each)
        drawCornerYard(canvas, 0, 9, 6, 6, PlayerId.PLAYER_1) // Red (Bottom-Left)
        drawCornerYard(canvas, 9, 9, 6, 6, PlayerId.PLAYER_4) // Yellow (Bottom-Right)
        drawCornerYard(canvas, 9, 0, 6, 6, PlayerId.PLAYER_2) // Blue (Top-Right)
        drawCornerYard(canvas, 0, 0, 6, 6, PlayerId.PLAYER_3) // Green (Top-Left)

        // 2. Draw 52 Outer Track Grid Tiles
        outerTrack.forEachIndexed { idx, pt ->
            val tileR = RectF(
                boardLeft + pt.x * cellSize,
                boardTop + pt.y * cellSize,
                boardLeft + (pt.x + 1) * cellSize,
                boardTop + (pt.y + 1) * cellSize
            )

            paint.style = Paint.Style.FILL
            when (idx) {
                0 -> paint.color = PlayerId.PLAYER_1.color // Red start
                13 -> paint.color = PlayerId.PLAYER_4.color // Yellow start
                26 -> paint.color = PlayerId.PLAYER_2.color // Blue start
                39 -> paint.color = PlayerId.PLAYER_3.color // Green start
                else -> paint.color = Color.parseColor("#1C153A")
            }
            canvas.drawRect(tileR, paint)

            // Inner border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * density
            paint.color = Color.parseColor("#2C2254")
            canvas.drawRect(tileR, paint)

            // Star icon ⭐ on safe tiles
            if (idx in safeTrackIndices) {
                paint.style = Paint.Style.FILL
                paint.color = if (idx in listOf(0, 13, 26, 39)) Color.WHITE else Color.parseColor("#FFD54F")
                paint.textSize = cellSize * 0.45f
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val baseline = tileR.centerY() - (fm.ascent + fm.descent) / 2f
                canvas.drawText("⭐", tileR.centerX(), baseline, paint)
            }
        }

        // 3. Draw 4 Colored Home Stretches (5 tiles each leading to center)
        drawHomeStretch(canvas, listOf(Point(1, 7), Point(2, 7), Point(3, 7), Point(4, 7), Point(5, 7)), PlayerId.PLAYER_1.color)
        drawHomeStretch(canvas, listOf(Point(7, 13), Point(7, 12), Point(7, 11), Point(7, 10), Point(7, 9)), PlayerId.PLAYER_4.color)
        drawHomeStretch(canvas, listOf(Point(13, 7), Point(12, 7), Point(11, 7), Point(10, 7), Point(9, 7)), PlayerId.PLAYER_2.color)
        drawHomeStretch(canvas, listOf(Point(7, 1), Point(7, 2), Point(7, 3), Point(7, 4), Point(7, 5)), PlayerId.PLAYER_3.color)

        // 4. Center Home Goal Triangle (3x3 cells in center: cols 6..8, rows 6..8)
        drawCenterHomeGoal(canvas)
    }

    private fun drawCornerYard(canvas: Canvas, startCol: Int, startRow: Int, colSpan: Int, rowSpan: Int, playerId: PlayerId) {
        val r = RectF(
            boardLeft + startCol * cellSize,
            boardTop + startRow * cellSize,
            boardLeft + (startCol + colSpan) * cellSize,
            boardTop + (startRow + rowSpan) * cellSize
        )

        // Yard Solid Background
        paint.style = Paint.Style.FILL
        paint.color = (playerId.color and 0x00FFFFFF) or 0x24000000
        canvas.drawRect(r, paint)

        // Yard Outer Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.2f * density
        paint.color = playerId.color
        canvas.drawRect(r, paint)

        // Inner white/neon squircle box with generous room for spaced tokens
        val pad = cellSize * 0.45f
        val innerBox = RectF(r.left + pad, r.top + pad, r.right - pad, r.bottom - pad)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#150F28")
        canvas.drawRoundRect(innerBox, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = (playerId.color and 0x00FFFFFF) or 0x66000000
        canvas.drawRoundRect(innerBox, 14f * density, 14f * density, paint)

        // Token pedestal circles in base - widely spaced out
        val slots = playerBaseSlots[playerId] ?: return
        slots.forEach { slot ->
            val cx = boardLeft + slot.x * cellSize + cellSize / 2f
            val cy = boardTop + slot.y * cellSize + cellSize / 2f
            val pedR = cellSize * 0.54f

            // Soft base glow
            paint.style = Paint.Style.FILL
            paint.color = (playerId.color and 0x00FFFFFF) or 0x22000000
            canvas.drawCircle(cx, cy, pedR + 2f * density, paint)

            paint.color = (playerId.color and 0x00FFFFFF) or 0x44000000
            canvas.drawCircle(cx, cy, pedR, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = playerId.color
            canvas.drawCircle(cx, cy, pedR, paint)
        }
    }

    private fun drawHomeStretch(canvas: Canvas, points: List<Point>, color: Int) {
        points.forEach { pt ->
            val tileR = RectF(
                boardLeft + pt.x * cellSize,
                boardTop + pt.y * cellSize,
                boardLeft + (pt.x + 1) * cellSize,
                boardTop + (pt.y + 1) * cellSize
            )
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRect(tileR, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * density
            paint.color = Color.parseColor("#22183D")
            canvas.drawRect(tileR, paint)
        }
    }

    private fun drawCenterHomeGoal(canvas: Canvas) {
        val cx = boardLeft + 7.5f * cellSize
        val cy = boardTop + 7.5f * cellSize
        val tlX = boardLeft + 6f * cellSize
        val tlY = boardTop + 6f * cellSize
        val brX = boardLeft + 9f * cellSize
        val brY = boardTop + 9f * cellSize

        // 4 Converging Triangles
        paint.style = Paint.Style.FILL

        // Left triangle (Red)
        paint.color = PlayerId.PLAYER_1.color
        val pRed = Path().apply {
            moveTo(tlX, tlY)
            lineTo(cx, cy)
            lineTo(tlX, brY)
            close()
        }
        canvas.drawPath(pRed, paint)

        // Bottom triangle (Yellow)
        paint.color = PlayerId.PLAYER_4.color
        val pYellow = Path().apply {
            moveTo(tlX, brY)
            lineTo(cx, cy)
            lineTo(brX, brY)
            close()
        }
        canvas.drawPath(pYellow, paint)

        // Right triangle (Blue)
        paint.color = PlayerId.PLAYER_2.color
        val pBlue = Path().apply {
            moveTo(brX, brY)
            lineTo(cx, cy)
            lineTo(brX, tlY)
            close()
        }
        canvas.drawPath(pBlue, paint)

        // Top triangle (Green)
        paint.color = PlayerId.PLAYER_3.color
        val pGreen = Path().apply {
            moveTo(brX, tlY)
            lineTo(cx, cy)
            lineTo(tlX, tlY)
            close()
        }
        canvas.drawPath(pGreen, paint)

        // Center Gold Crown / Star Trophy
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FFD54F")
        paint.textSize = cellSize * 0.9f
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText("👑", cx, baseline, paint)
    }

    /**
     * Render 3D Puck Tokens
     */
    private fun drawTokens(canvas: Canvas) {
        val pulse = 0.5f + 0.5f * sin(pulseTimer * 6f)

        tokens.forEach { t ->
            val isEligibleToChoose = t.isEligible && waitingForTokenChoice
            val bounceY = if (isEligibleToChoose) -abs(sin(pulseTimer * 8f)) * 5f * density else 0f
            val pos = PointF(t.visualPos.x, t.visualPos.y + bounceY)
            val isShared = isSharedTile(t)
            val puckR = if (isShared) cellSize * 0.32f else cellSize * 0.44f

            // If token is eligible to move, draw vibrant pulsating golden aura & downward pointer arrow
            if (isEligibleToChoose) {
                // 1. Pulsing golden aura halo
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (2.5f + pulse * 2.2f) * density
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawCircle(pos.x, pos.y, puckR + (4f + pulse * 5f) * density, paint)

                // 2. Soft translucent gold radial glow
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#33FFD54F")
                canvas.drawCircle(pos.x, pos.y, puckR + (4f + pulse * 5f) * density, paint)

                // 3. Floating neon pointer arrow right above token
                paint.color = Color.parseColor("#FFD54F")
                val arrowTipY = pos.y - puckR - (5f + pulse * 3f) * density
                val arrowH = 7f * density
                val arrowW = 4.5f * density
                val arrowPath = Path().apply {
                    moveTo(pos.x, arrowTipY)
                    lineTo(pos.x - arrowW, arrowTipY - arrowH)
                    lineTo(pos.x + arrowW, arrowTipY - arrowH)
                    close()
                }
                canvas.drawPath(arrowPath, paint)
            }

            // 1. Base Drop Shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#60000000")
            canvas.drawOval(RectF(pos.x - puckR, pos.y + puckR * 0.2f, pos.x + puckR, pos.y + puckR * 0.95f), paint)

            // 2. Dark 3D lower thickness rim
            paint.color = t.owner.darkColor
            canvas.drawOval(RectF(pos.x - puckR, pos.y - puckR * 0.35f, pos.x + puckR, pos.y + puckR * 0.85f), paint)

            // 3. Outer Puck Body
            paint.color = t.owner.color
            canvas.drawCircle(pos.x, pos.y, puckR, paint)

            // 4. Metallic Rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = if (isEligibleToChoose) Color.parseColor("#FFF9C4") else Color.WHITE
            canvas.drawCircle(pos.x, pos.y, puckR, paint)

            // 5. Specular highlight crescent on top-left of the 3D puck
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#66FFFFFF")
            canvas.drawCircle(pos.x - puckR * 0.28f, pos.y - puckR * 0.28f, puckR * 0.35f, paint)

            // 6. Token Number (1..4) in center
            paint.color = Color.WHITE
            paint.textSize = if (isShared) 7f * density else 8.5f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = pos.y - (fm.ascent + fm.descent) / 2f
            canvas.drawText("${t.id + 1}", pos.x, baseline, paint)
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
        val strokeColor = if (waitingForTokenChoice || isRolling) Color.parseColor("#FFD54F") else activeP.id.color

        // Button Container Pill
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1336")
        canvas.drawRoundRect(diceRect, 22f * density, 22f * density, paint)

        // Subtle glowing fill
        val glowColor = if (waitingForTokenChoice) 0x33FFD54F.toInt() else ((activeP.id.color and 0x00FFFFFF) or 0x24000000)
        paint.color = glowColor
        canvas.drawRoundRect(diceRect, 22f * density, 22f * density, paint)

        // Pulsing glowing border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (waitingForTokenChoice) (2.5f + pulse * 1.5f) * density else (2f + pulse * 1.2f) * density
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
        paint.textSize = if (waitingForTokenChoice) 11.5f * density else 12.5f * density
        paint.isFakeBoldText = true
        paint.color = if (waitingForTokenChoice) Color.parseColor("#FFD54F") else Color.WHITE

        val btnText = when {
            feedbackTimer > 0f -> feedbackMessage
            waitingForTokenChoice -> "SELECT TOKEN 👆"
            isRolling -> "ROLLING..."
            isMoving -> "MOVING..."
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
        val finished = tokens.count { it.owner == player.id && it.state == TokenState.FINISHED }
        canvas.drawText("HOME $finished/4", avatarX + avatarSize / 2f + 6f * density, rect.centerY() + 11f * density, paint)
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

        // Current Finished Pill on the Right
        val finished = tokens.count { it.owner == player.id && it.state == TokenState.FINISHED }
        val pillW = 88f * density
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
        canvas.drawText("HOME $finished/4", pillR.centerX(), pillBaseline, paint)
        paint.isFakeBoldText = false

        canvas.restore()
    }

    override fun getPlayerScoreString(player: Player): String {
        val finished = tokens.count { it.owner == player.id && it.state == TokenState.FINISHED }
        return "Home $finished/4"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return tokens.count { it.owner == player.id && it.state == TokenState.FINISHED }
    }
}

