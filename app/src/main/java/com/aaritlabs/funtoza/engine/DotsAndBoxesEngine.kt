package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dots & Boxes Minigame Engine
 * Supports 1, 2, 3, and 4 players as well as 2v2 Team Play.
 * Adapted directly from the classic mechanics of reference APK (57_DOTSBOXES).
 */
class DotsAndBoxesEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // Board Dimensions: Dynamic by botDifficulty and totalPlayers
    // With Bot: 7x7 Easy, 8x8 Medium, 9x9 Hard; All other player modes: 9x9
    val gridCols: Int = when {
        totalPlayers == 1 -> when (botDifficulty) {
            0 -> 7 // Easy: 7x7
            1 -> 8 // Medium: 8x8
            else -> 9 // Hard: 9x9
        }
        else -> 9 // All other player counts (2P, 3P, 4P, Team): 9x9
    }
    val gridRows: Int = when {
        totalPlayers == 1 -> when (botDifficulty) {
            0 -> 7 // Easy: 7x7
            1 -> 8 // Medium: 8x8
            else -> 9 // Hard: 9x9
        }
        else -> 9 // All other player counts (2P, 3P, 4P, Team): 9x9
    }
    val dotCols: Int = gridCols + 1
    val dotRows: Int = gridRows + 1

    enum class LineType { HORIZONTAL, VERTICAL }

    data class BoxLine(
        val type: LineType,
        val col: Int,
        val row: Int,
        var owner: PlayerId? = null,
        var animProgress: Float = 1f,
        val touchRect: RectF = RectF()
    )

    data class BoxCell(
        val col: Int,
        val row: Int,
        var owner: PlayerId? = null,
        var pulseScale: Float = 0f,
        val rect: RectF = RectF()
    )

    // Lines & Boxes
    private val horizontalLines = mutableListOf<BoxLine>()
    private val verticalLines = mutableListOf<BoxLine>()
    private val boxes = mutableListOf<BoxCell>()
    private val dotPositions = mutableMapOf<Pair<Int, Int>, PointF>()

    // Score Tracking
    private val boxesClaimed = mutableMapOf<PlayerId, Int>()

    // Turn Management
    private var currentTurnIndex = 0
    private var turnTransitionTimer = 0f
    private var botThinkTimer = 0f
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null

    // UI Feedback
    private var feedbackMessage = ""
    private var feedbackTimer = 0f
    private var pulseTimer = 0f

    // Board Geometry
    private val boardRect = RectF()
    private var cellWidth = 0f
    private var cellHeight = 0f

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun initGame() {
        horizontalLines.clear()
        verticalLines.clear()
        boxes.clear()
        dotPositions.clear()
        boxesClaimed.clear()

        isGameOver = false
        currentTurnIndex = 0
        turnTransitionTimer = 0f
        roundEndDelayTimer = -1f
        pendingWinner = null
        feedbackMessage = ""
        feedbackTimer = 0f

        players.forEach { p ->
            boxesClaimed[p.id] = 0
            p.score = 0
        }

        // 1. Create Horizontal Lines: (col in 0 until gridCols, row in 0 until dotRows)
        for (r in 0 until dotRows) {
            for (c in 0 until gridCols) {
                horizontalLines.add(BoxLine(LineType.HORIZONTAL, c, r))
            }
        }

        // 2. Create Vertical Lines: (col in 0 until dotCols, row in 0 until gridRows)
        for (r in 0 until gridRows) {
            for (c in 0 until dotCols) {
                verticalLines.add(BoxLine(LineType.VERTICAL, c, r))
            }
        }

        // 3. Create Boxes: (col in 0 until gridCols, row in 0 until gridRows)
        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                boxes.add(BoxCell(c, r))
            }
        }

        val firstP = getCurrentPlayer()
        if (firstP != null && firstP.isBot) {
            botThinkTimer = getBotDelay()
        }

        // Recompute layout immediately if dimensions are already available
        if (width > 0f && height > 0f) {
            computeLayoutGeometry()
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        // Ensure lines, dots, and boxes hitboxes are always computed once surface is sized
        computeLayoutGeometry()
    }

    override fun calculateControlZones() {
        controlZones.clear()
        computeLayoutGeometry()
    }

    private fun computeLayoutGeometry() {
        if (width <= 0f || height <= 0f) return

        val hudTop = 126f * density // Clears the top title and turn banner with generous spacing
        val hudBottom = 86f * density
        val availableHeight = height - hudTop - hudBottom
        val availableWidth = width - 24f * density

        // Compute aspect cell size to fit gridCols x gridRows
        val maxCellW = availableWidth / gridCols
        val maxCellH = availableHeight / gridRows
        val cellSize = min(maxCellW, maxCellH)

        cellWidth = cellSize
        cellHeight = cellSize

        val boardW = gridCols * cellWidth
        val boardH = gridRows * cellHeight

        val boardX = (width - boardW) / 2f
        val boardY = hudTop + (availableHeight - boardH) / 2f

        boardRect.set(boardX, boardY, boardX + boardW, boardY + boardH)

        // Compute Dot Positions
        dotPositions.clear()
        for (r in 0 until dotRows) {
            for (c in 0 until dotCols) {
                val dx = boardRect.left + c * cellWidth
                val dy = boardRect.top + r * cellHeight
                dotPositions[Pair(c, r)] = PointF(dx, dy)
            }
        }

        // Compute Box Rectangles
        boxes.forEach { b ->
            val bx = boardRect.left + b.col * cellWidth
            val by = boardRect.top + b.row * cellHeight
            b.rect.set(bx, by, bx + cellWidth, by + cellHeight)
        }

        // Compute Line Touch Hitboxes (generous touch hit area)
        val hitPadding = min(18f * density, cellWidth * 0.40f)
        horizontalLines.forEach { l ->
            val p1 = dotPositions[Pair(l.col, l.row)] ?: return@forEach
            val p2 = dotPositions[Pair(l.col + 1, l.row)] ?: return@forEach
            l.touchRect.set(
                p1.x,
                p1.y - hitPadding,
                p2.x,
                p1.y + hitPadding
            )
        }

        verticalLines.forEach { l ->
            val p1 = dotPositions[Pair(l.col, l.row)] ?: return@forEach
            val p2 = dotPositions[Pair(l.col, l.row + 1)] ?: return@forEach
            l.touchRect.set(
                p1.x - hitPadding,
                p1.y,
                p1.x + hitPadding,
                p2.y
            )
        }
    }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        return players[currentTurnIndex % players.size]
    }

    private fun getBotDelay(): Float {
        return when (botDifficulty) {
            0 -> 1.0f
            2 -> 0.45f
            else -> 0.7f
        }
    }

    override fun update(deltaTime: Float) {
        pulseTimer += deltaTime
        particles.update()

        if (feedbackTimer > 0f) {
            feedbackTimer -= deltaTime
        }

        // Round Over Countdown to trigger popup
        if (roundEndDelayTimer > 0f) {
            roundEndDelayTimer -= deltaTime
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                endRound(pendingWinner)
            }
            return
        }

        if (isGameOver) return

        // Update Box Claim Pulse Animations
        boxes.forEach { b ->
            if (b.pulseScale > 1f) {
                b.pulseScale = max(1f, b.pulseScale - deltaTime * 1.8f)
            }
        }

        // Turn Transition Timer
        if (turnTransitionTimer > 0f) {
            turnTransitionTimer -= deltaTime
            if (turnTransitionTimer <= 0f) {
                advanceTurn()
            }
            return
        }

        // Bot AI Action
        val curP = getCurrentPlayer()
        if (curP != null && curP.isBot) {
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f) {
                botExecuteMove(curP)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || turnTransitionTimer > 0f || roundEndDelayTimer >= 0f) return true

        val curP = getCurrentPlayer() ?: return true
        if (curP.isBot) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val tx = event.x
            val ty = event.y

            // Find closest unowned line within touch threshold
            var closestLine: BoxLine? = null
            val maxTouchDist = min(26f * density, cellWidth * 0.45f)
            var closestDist = maxTouchDist

            for (l in horizontalLines) {
                if (l.owner == null && l.touchRect.contains(tx, ty)) {
                    val p1 = dotPositions[Pair(l.col, l.row)] ?: continue
                    val p2 = dotPositions[Pair(l.col + 1, l.row)] ?: continue
                    val lineCenterY = p1.y
                    val dist = abs(ty - lineCenterY)
                    if (dist < closestDist) {
                        closestDist = dist
                        closestLine = l
                    }
                }
            }

            for (l in verticalLines) {
                if (l.owner == null && l.touchRect.contains(tx, ty)) {
                    val p1 = dotPositions[Pair(l.col, l.row)] ?: continue
                    val lineCenterX = p1.x
                    val dist = abs(tx - lineCenterX)
                    if (dist < closestDist) {
                        closestDist = dist
                        closestLine = l
                    }
                }
            }

            if (closestLine != null) {
                claimLine(closestLine, curP)
            }
        }

        return true
    }

    private fun claimLine(line: BoxLine, player: Player) {
        line.owner = player.id
        soundManager.play(SoundManager.SoundType.TAP, 1.0f)
        triggerVibration(25L)

        val center = getLineCenter(line)
        particles.spawnExplosion(center.x, center.y, player.id.color, 12)

        // Check if any box was completed by this move!
        val completedCount = checkForCompletedBoxes(line, player)

        if (completedCount > 0) {
            soundManager.play(SoundManager.SoundType.GOAL, 0.95f)
            triggerVibration(65L)

            // Update player score
            boxesClaimed[player.id] = (boxesClaimed[player.id] ?: 0) + completedCount
            player.score = boxesClaimed[player.id] ?: 0

            feedbackMessage = if (completedCount == 2) "🔥 DOUBLE BOX! DRAW AGAIN 🎯" else "💥 BOX CLAIMED! DRAW AGAIN 🎯"
            feedbackTimer = 1.2f

            checkEndgame()

            if (!isGameOver) {
                // Same player gets another turn to draw!
                if (player.isBot) {
                    botThinkTimer = getBotDelay()
                }
            }
        } else {
            // No box completed: turn passes to next player!
            turnTransitionTimer = 0.35f
        }
    }

    private fun getLineCenter(line: BoxLine): PointF {
        return if (line.type == LineType.HORIZONTAL) {
            val p1 = dotPositions[Pair(line.col, line.row)] ?: PointF()
            val p2 = dotPositions[Pair(line.col + 1, line.row)] ?: PointF()
            PointF((p1.x + p2.x) / 2f, p1.y)
        } else {
            val p1 = dotPositions[Pair(line.col, line.row)] ?: PointF()
            val p2 = dotPositions[Pair(line.col, line.row + 1)] ?: PointF()
            PointF(p1.x, (p1.y + p2.y) / 2f)
        }
    }

    private fun checkForCompletedBoxes(line: BoxLine, player: Player): Int {
        var completedCount = 0

        val boxesToCheck = mutableListOf<BoxCell>()
        if (line.type == LineType.HORIZONTAL) {
            // Box above
            if (line.row > 0) {
                boxes.firstOrNull { it.col == line.col && it.row == line.row - 1 }?.let { boxesToCheck.add(it) }
            }
            // Box below
            if (line.row < gridRows) {
                boxes.firstOrNull { it.col == line.col && it.row == line.row }?.let { boxesToCheck.add(it) }
            }
        } else {
            // Box to left
            if (line.col > 0) {
                boxes.firstOrNull { it.col == line.col - 1 && it.row == line.row }?.let { boxesToCheck.add(it) }
            }
            // Box to right
            if (line.col < gridCols) {
                boxes.firstOrNull { it.col == line.col && it.row == line.row }?.let { boxesToCheck.add(it) }
            }
        }

        for (box in boxesToCheck) {
            if (box.owner == null && isBoxCompleted(box)) {
                box.owner = player.id
                box.pulseScale = 1.35f
                completedCount++

                // Explode particles inside box
                particles.spawnExplosion(box.rect.centerX(), box.rect.centerY(), player.id.color, 24)
            }
        }

        return completedCount
    }

    private fun isBoxCompleted(box: BoxCell): Boolean {
        val top = getHorizontalLine(box.col, box.row)
        val bottom = getHorizontalLine(box.col, box.row + 1)
        val left = getVerticalLine(box.col, box.row)
        val right = getVerticalLine(box.col + 1, box.row)

        return (top?.owner != null && bottom?.owner != null && left?.owner != null && right?.owner != null)
    }

    private fun getBoxLinesDrawnCount(box: BoxCell): Int {
        var count = 0
        if (getHorizontalLine(box.col, box.row)?.owner != null) count++
        if (getHorizontalLine(box.col, box.row + 1)?.owner != null) count++
        if (getVerticalLine(box.col, box.row)?.owner != null) count++
        if (getVerticalLine(box.col + 1, box.row)?.owner != null) count++
        return count
    }

    private fun getUnownedLinesOfBox(box: BoxCell): List<BoxLine> {
        val list = mutableListOf<BoxLine>()
        getHorizontalLine(box.col, box.row)?.let { if (it.owner == null) list.add(it) }
        getHorizontalLine(box.col, box.row + 1)?.let { if (it.owner == null) list.add(it) }
        getVerticalLine(box.col, box.row)?.let { if (it.owner == null) list.add(it) }
        getVerticalLine(box.col + 1, box.row)?.let { if (it.owner == null) list.add(it) }
        return list
    }

    private fun getHorizontalLine(c: Int, r: Int): BoxLine? {
        return horizontalLines.firstOrNull { it.col == c && it.row == r }
    }

    private fun getVerticalLine(c: Int, r: Int): BoxLine? {
        return verticalLines.firstOrNull { it.col == c && it.row == r }
    }

    private fun advanceTurn() {
        if (isGameOver) return
        currentTurnIndex = (currentTurnIndex + 1) % players.size
        val nextP = getCurrentPlayer()
        if (nextP != null && nextP.isBot) {
            botThinkTimer = getBotDelay()
        }
    }

    private fun checkEndgame() {
        val totalBoxes = gridCols * gridRows
        val completed = boxes.count { it.owner != null }

        if (completed >= totalBoxes) {
            if (roundEndDelayTimer >= 0f || isGameOver) return

            soundManager.play(SoundManager.SoundType.VICTORY, 1.0f)
            triggerVibration(100L)

            if (isTeamMode) {
                val t1Score = (boxesClaimed[PlayerId.PLAYER_1] ?: 0) + (boxesClaimed[PlayerId.PLAYER_2] ?: 0)
                val t2Score = (boxesClaimed[PlayerId.PLAYER_3] ?: 0) + (boxesClaimed[PlayerId.PLAYER_4] ?: 0)
                winningTeam = if (t1Score >= t2Score) 1 else 2
                val winTeam = winningTeam ?: 1
                val winner = if (winTeam == 1) players[0] else players[2]
                val winScore = if (winTeam == 1) t1Score else t2Score

                feedbackMessage = "🏆 TEAM $winTeam WINS! ($winScore BOXES)"
                feedbackTimer = 2.5f

                val teamColor = if (winTeam == 1) Color.parseColor("#FF3B30") else Color.parseColor("#007AFF")
                particles.spawnConfetti(width, height, listOf(teamColor, Color.parseColor("#FFD54F"), Color.WHITE))

                pendingWinner = winner
                roundEndDelayTimer = 1.0f
                return
            }

            val maxBoxes = players.maxOf { boxesClaimed[it.id] ?: 0 }
            val winners = players.filter { (boxesClaimed[it.id] ?: 0) == maxBoxes }
            val winner = winners.firstOrNull() ?: players.first()

            val winnerName = if (winners.size > 1) {
                "TIE GAME!"
            } else {
                "${getPlayerDisplayName(winner).uppercase()} WINS!"
            }
            val winScore = boxesClaimed[winner.id] ?: 0
            feedbackMessage = "🏆 $winnerName ($winScore BOXES)"
            feedbackTimer = 2.5f

            particles.spawnConfetti(width, height, listOf(winner.id.color, Color.parseColor("#FFD54F"), Color.WHITE))

            pendingWinner = winner
            roundEndDelayTimer = 1.0f
        }
    }

    /**
     * Strategic CPU AI as modeled in the reference APK:
     * 1. Find 3-liner boxes: Claim the 4th line to score immediately.
     * 2. Avoid 2-liner boxes: Avoid picking the 3rd line which gifts a box to opponent.
     * 3. Pick safe random lines (boxes with 0 or 1 lines drawn).
     */
    private fun botExecuteMove(botPlayer: Player) {
        val allAvailableLines = (horizontalLines + verticalLines).filter { it.owner == null }
        if (allAvailableLines.isEmpty()) return

        // 1. Search for 3-liner box to complete
        val threeLinerBoxes = boxes.filter { it.owner == null && getBoxLinesDrawnCount(it) == 3 }
        val findThreeChance = when (botDifficulty) {
            0 -> 0.45f
            2 -> 1.0f
            else -> 0.85f
        }

        if (threeLinerBoxes.isNotEmpty() && random.nextFloat() < findThreeChance) {
            val pickBox = threeLinerBoxes.random(random)
            val missingLine = getUnownedLinesOfBox(pickBox).firstOrNull()
            if (missingLine != null) {
                claimLine(missingLine, botPlayer)
                return
            }
        }

        // 2. Filter safe lines that do NOT create a 3-liner for opponent
        val avoidTwoChance = when (botDifficulty) {
            0 -> 0.20f
            2 -> 0.95f
            else -> 0.75f
        }

        if (random.nextFloat() < avoidTwoChance) {
            val safeLines = allAvailableLines.filter { candidate ->
                // Check if candidate line causes any adjacent box to reach 3 lines
                val adjBoxes = getBoxesAdjacentToLine(candidate)
                adjBoxes.none { getBoxLinesDrawnCount(it) == 2 }
            }

            if (safeLines.isNotEmpty()) {
                val pick = safeLines.random(random)
                claimLine(pick, botPlayer)
                return
            }
        }

        // 3. Fallback: Pick a random available line
        val pick = allAvailableLines.random(random)
        claimLine(pick, botPlayer)
    }

    private fun getBoxesAdjacentToLine(line: BoxLine): List<BoxCell> {
        val list = mutableListOf<BoxCell>()
        if (line.type == LineType.HORIZONTAL) {
            if (line.row > 0) boxes.firstOrNull { it.col == line.col && it.row == line.row - 1 }?.let { list.add(it) }
            if (line.row < gridRows) boxes.firstOrNull { it.col == line.col && it.row == line.row }?.let { list.add(it) }
        } else {
            if (line.col > 0) boxes.firstOrNull { it.col == line.col - 1 && it.row == line.row }?.let { list.add(it) }
            if (line.col < gridCols) boxes.firstOrNull { it.col == line.col && it.row == line.row }?.let { list.add(it) }
        }
        return list
    }

    override fun render(canvas: Canvas) {
        // Defensive self-heal: guarantee lines and dots geometry are ready on first frame
        if ((dotPositions.isEmpty() || horizontalLines.isEmpty()) && width > 0f && height > 0f) {
            if (horizontalLines.isEmpty()) {
                initGame()
            }
            computeLayoutGeometry()
        }

        // 1. Dark Cosmic Nebula Background
        canvas.drawColor(Color.parseColor("#090817"))

        // Ambient cyber grid dots/stars
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#15132D")
        for (i in 0..18) {
            val sx = (i * 73f * density) % width
            val sy = (i * 97f * density) % height
            canvas.drawCircle(sx, sy, 1.5f * density, paint)
        }

        // 2. Board Card Container
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#100E26")
        canvas.drawRoundRect(boardRect, 18f * density, 18f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = Color.parseColor("#26224E")
        canvas.drawRoundRect(boardRect, 18f * density, 18f * density, paint)

        // 3. Render Boxes (Filled Territory)
        renderBoxes(canvas)

        // 4. Render Grid Lines (Unclaimed & Claimed)
        renderLines(canvas)

        // 5. Render Glowing Dots
        renderDots(canvas)

        // 6. Particle Effects
        particles.draw(canvas)

        // 7. Top HUD & Scoreboard
        renderHUD(canvas)
    }

    private fun renderBoxes(canvas: Canvas) {
        boxes.forEach { b ->
            val ownerId = b.owner
            if (ownerId != null) {
                val cx = b.rect.centerX()
                val cy = b.rect.centerY()
                val scale = if (b.pulseScale > 1f) b.pulseScale else 1f

                canvas.save()
                canvas.scale(scale, scale, cx, cy)

                // Fill with translucent player color
                val ownerColor = ownerId.color
                paint.style = Paint.Style.FILL
                val alpha = (85 * scale).toInt().coerceIn(0, 255)
                paint.color = Color.argb(alpha, Color.red(ownerColor), Color.green(ownerColor), Color.blue(ownerColor))
                canvas.drawRoundRect(b.rect, 8f * density, 8f * density, paint)

                // Inner soft border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * density
                paint.color = Color.argb(180, Color.red(ownerColor), Color.green(ownerColor), Color.blue(ownerColor))
                canvas.drawRoundRect(b.rect, 8f * density, 8f * density, paint)

                // Player Icon / Initial in Center
                val ownerP = players.firstOrNull { it.id == ownerId }
                val initial = if (ownerP?.isBot == true) "🤖" else getPlayerDisplayName(ownerP ?: Player(ownerId)).take(1).uppercase()

                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textSize = min(16f * density, cellWidth * 0.38f)
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val textY = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(initial, cx, textY, paint)
                paint.isFakeBoldText = false

                canvas.restore()
            }
        }
    }

    private fun renderLines(canvas: Canvas) {
        val curP = getCurrentPlayer()
        val curColor = curP?.id?.color ?: Color.parseColor("#BD00FF")

        val claimedStroke = min(6.5f * density, cellWidth * 0.13f)
        val centerHighlightStroke = min(2.5f * density, cellWidth * 0.05f)
        val unclaimedStroke = min(2.4f * density, cellWidth * 0.048f)

        // Draw Horizontal Lines
        horizontalLines.forEach { l ->
            val p1 = dotPositions[Pair(l.col, l.row)] ?: return@forEach
            val p2 = dotPositions[Pair(l.col + 1, l.row)] ?: return@forEach

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND

            if (l.owner != null) {
                // Claimed line: Vivid Player Color
                val c = l.owner!!.color
                paint.strokeWidth = claimedStroke
                paint.color = c
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)

                // Glowing center highlight
                paint.strokeWidth = centerHighlightStroke
                paint.color = Color.WHITE
                canvas.drawLine(p1.x + 4f * density, p1.y, p2.x - 4f * density, p2.y, paint)
            } else {
                // Unclaimed line: subtle cosmic track
                paint.strokeWidth = unclaimedStroke
                paint.color = Color.parseColor("#221D45")
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }
        }

        // Draw Vertical Lines
        verticalLines.forEach { l ->
            val p1 = dotPositions[Pair(l.col, l.row)] ?: return@forEach
            val p2 = dotPositions[Pair(l.col, l.row + 1)] ?: return@forEach

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND

            if (l.owner != null) {
                // Claimed line
                val c = l.owner!!.color
                paint.strokeWidth = claimedStroke
                paint.color = c
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)

                // Glowing center highlight
                paint.strokeWidth = centerHighlightStroke
                paint.color = Color.WHITE
                canvas.drawLine(p1.x, p1.y + 4f * density, p2.x, p2.y - 4f * density, paint)
            } else {
                // Unclaimed line
                paint.strokeWidth = unclaimedStroke
                paint.color = Color.parseColor("#221D45")
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
            }
        }
    }

    private fun renderDots(canvas: Canvas) {
        val curP = getCurrentPlayer()
        val pulse = 0.5f + 0.5f * sin(pulseTimer * 5f)

        val dotRadius = min(3.8f * density, cellWidth * 0.085f)
        val haloRadius = dotRadius * 1.55f + pulse * 1.5f * density
        val centerCore = dotRadius * 0.48f

        dotPositions.values.forEach { pt ->
            // Outer Halo
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(45, 189, 0, 255)
            canvas.drawCircle(pt.x, pt.y, haloRadius, paint)

            // Inner Core Dot
            paint.color = Color.parseColor("#E0D6FF")
            canvas.drawCircle(pt.x, pt.y, dotRadius, paint)

            // Center Pinpoint
            paint.color = Color.WHITE
            canvas.drawCircle(pt.x, pt.y, centerCore, paint)
        }
    }

    private fun renderHUD(canvas: Canvas) {
        val curP = getCurrentPlayer()
        val curColor = curP?.id?.color ?: Color.parseColor("#FFD54F")

        // 1. Turn Banner / Status Pill (Cleanly placed safely below the top "DOTS & BOXES" title)
        val bannerW = 230f * density
        val bannerH = 28f * density
        val bannerY = 88f * density
        val bannerR = RectF(width / 2f - bannerW / 2f, bannerY, width / 2f + bannerW / 2f, bannerY + bannerH)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1A1434")
        canvas.drawRoundRect(bannerR, bannerH / 2f, bannerH / 2f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = curColor
        canvas.drawRoundRect(bannerR, bannerH / 2f, bannerH / 2f, paint)

        val bannerText = if (feedbackTimer > 0f && feedbackMessage.isNotBlank()) {
            feedbackMessage
        } else if (curP != null) {
            val name = if (curP.isBot) "ROBI (BOT)" else getPlayerDisplayName(curP).uppercase()
            "👉 $name'S TURN"
        } else "PLAYING"

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 11.5f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val textY = bannerR.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(bannerText, bannerR.centerX(), textY, paint)
        paint.isFakeBoldText = false

        // 2. Scoreboard Badges below board
        renderScoreboard(canvas)
    }

    private fun renderScoreboard(canvas: Canvas) {
        val hudY = height - 76f * density
        val hudH = 50f * density
        val activeP = getCurrentPlayer()

        if (isTeamMode) {
            // Team 1 vs Team 2 Score Badges
            val cardW = (width - 48f * density) / 2f

            // Team 1 Card
            val t1Score = (boxesClaimed[PlayerId.PLAYER_1] ?: 0) + (boxesClaimed[PlayerId.PLAYER_2] ?: 0)
            val isT1Turn = activeP?.id == PlayerId.PLAYER_1 || activeP?.id == PlayerId.PLAYER_2
            val t1R = RectF(16f * density, hudY, 16f * density + cardW, hudY + hudH)

            paint.style = Paint.Style.FILL
            paint.color = if (isT1Turn) Color.parseColor("#321326") else Color.parseColor("#1A1434")
            canvas.drawRoundRect(t1R, 12f * density, 12f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isT1Turn) 2.2f * density else 1f * density
            paint.color = if (isT1Turn) Color.parseColor("#FF3B30") else Color.parseColor("#3A2A60")
            canvas.drawRoundRect(t1R, 12f * density, 12f * density, paint)

            val p1 = getPlayerDisplayName(players.getOrElse(0) { Player(PlayerId.PLAYER_1) })
            val p2 = getPlayerDisplayName(players.getOrElse(1) { Player(PlayerId.PLAYER_2) })
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 11f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("🔴 TEAM 1 ($p1+$p2)", t1R.left + 10f * density, t1R.centerY() - 2f * density, paint)
            paint.textSize = 13f * density
            paint.color = Color.parseColor("#FF3B30")
            canvas.drawText("$t1Score Boxes", t1R.left + 10f * density, t1R.centerY() + 15f * density, paint)

            // Team 2 Card
            val t2Score = (boxesClaimed[PlayerId.PLAYER_3] ?: 0) + (boxesClaimed[PlayerId.PLAYER_4] ?: 0)
            val isT2Turn = activeP?.id == PlayerId.PLAYER_3 || activeP?.id == PlayerId.PLAYER_4
            val t2R = RectF(width - 16f * density - cardW, hudY, width - 16f * density, hudY + hudH)

            paint.style = Paint.Style.FILL
            paint.color = if (isT2Turn) Color.parseColor("#102244") else Color.parseColor("#1A1434")
            canvas.drawRoundRect(t2R, 12f * density, 12f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isT2Turn) 2.2f * density else 1f * density
            paint.color = if (isT2Turn) Color.parseColor("#007AFF") else Color.parseColor("#3A2A60")
            canvas.drawRoundRect(t2R, 12f * density, 12f * density, paint)

            val p3 = getPlayerDisplayName(players.getOrElse(2) { Player(PlayerId.PLAYER_3) })
            val p4 = getPlayerDisplayName(players.getOrElse(3) { Player(PlayerId.PLAYER_4) })
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 11f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("🔵 TEAM 2 ($p3+$p4)", t2R.left + 10f * density, t2R.centerY() - 2f * density, paint)
            paint.textSize = 13f * density
            paint.color = Color.parseColor("#007AFF")
            canvas.drawText("$t2Score Boxes", t2R.left + 10f * density, t2R.centerY() + 15f * density, paint)
            paint.isFakeBoldText = false
        } else {
            // Individual Players Score Badges
            val count = players.size
            val cardW = (width - 32f * density - (count - 1) * 8f * density) / count

            players.forEachIndexed { i, p ->
                val cardX = 16f * density + i * (cardW + 8f * density)
                val cardR = RectF(cardX, hudY, cardX + cardW, hudY + hudH)
                val isTurn = (activeP?.id == p.id)

                paint.style = Paint.Style.FILL
                paint.color = if (isTurn) Color.parseColor("#2A1948") else Color.parseColor("#14102A")
                canvas.drawRoundRect(cardR, 10f * density, 10f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = if (isTurn) 2.2f * density else 1f * density
                paint.color = if (isTurn) p.id.color else Color.parseColor("#2A224E")
                canvas.drawRoundRect(cardR, 10f * density, 10f * density, paint)

                // Dot
                paint.style = Paint.Style.FILL
                paint.color = p.id.color
                canvas.drawCircle(cardR.left + 10f * density, cardR.centerY() - 6f * density, 4f * density, paint)

                // Name & Score
                paint.color = Color.WHITE
                paint.textSize = 10f * density
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.LEFT
                val name = if (p.isBot) "ROBI" else getPlayerDisplayName(p).take(4).uppercase()
                canvas.drawText(name, cardR.left + 18f * density, cardR.centerY() - 2f * density, paint)

                val score = boxesClaimed[p.id] ?: 0
                paint.textSize = 12f * density
                paint.color = p.id.color
                canvas.drawText("$score", cardR.left + 18f * density, cardR.centerY() + 14f * density, paint)
                paint.isFakeBoldText = false
            }
        }
    }

    override fun getPlayerScoreString(player: Player): String {
        val count = boxesClaimed[player.id] ?: 0
        return "$count ${if (count == 1) "Box" else "Boxes"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return boxesClaimed[player.id] ?: 0
    }

    override fun getTeamScoreString(team: Int): String {
        val count = if (team == 1) {
            (boxesClaimed[PlayerId.PLAYER_1] ?: 0) + (boxesClaimed[PlayerId.PLAYER_2] ?: 0)
        } else {
            (boxesClaimed[PlayerId.PLAYER_3] ?: 0) + (boxesClaimed[PlayerId.PLAYER_4] ?: 0)
        }
        return "$count ${if (count == 1) "Box" else "Boxes"}"
    }
}

