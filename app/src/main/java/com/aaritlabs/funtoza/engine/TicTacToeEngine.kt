package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class TicTacToeWinLine(
    val r1: Int,
    val c1: Int,
    val r2: Int,
    val c2: Int,
    val winnerId: PlayerId
)

class TicTacToeEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density

    private val board = Array(3) { arrayOfNulls<PlayerId>(3) }
    private val cellScales = Array(3) { FloatArray(3) { 0f } }

    private var currentTurn: PlayerId = PlayerId.PLAYER_1
    private var winningLine: TicTacToeWinLine? = null
    private var winBeamProgress: Float = 0f
    private var isDrawMatch: Boolean = false
    private var roundEndTimer: Float = 0f
    private var botThinkTimer: Float = 0f
    private var pulseTimer: Float = 0f

    private var boardLeft: Float = 0f
    private var boardTop: Float = 0f
    private var boardSize: Float = 0f
    private var cellSize: Float = 0f

    private var topCardTop: Float = 0f
    private var bottomCardTop: Float = 0f
    private var cardH: Float = 0f
    private var cardW: Float = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    private val colorP1X = Color.parseColor("#FF2A6D") // Neon Hot Pink
    private val colorP2O = Color.parseColor("#00E5FF") // Neon Electric Cyan
    private val colorGridBg = Color.parseColor("#15102A")
    private val colorCellBg = Color.parseColor("#1F173C")
    private val colorCellBorder = Color.parseColor("#352A5E")

    override fun calculateControlZones() {
        controlZones.clear() // No default corner buttons; grid is touched directly
    }

    override fun initGame() {
        for (r in 0..2) {
            for (c in 0..2) {
                board[r][c] = null
                cellScales[r][c] = 0f
            }
        }
        currentTurn = PlayerId.PLAYER_1
        winningLine = null
        winBeamProgress = 0f
        isDrawMatch = false
        roundEndTimer = 0f
        botThinkTimer = 0f
        isGameOver = false
        winner = null
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        computeBoardGeometry()
    }

    private fun computeBoardGeometry() {
        if (width <= 0f || height <= 0f) return

        cardH = 74f * density
        cardW = (width - 32f * density).coerceAtMost(500f * density)

        // Clear top HUD margin so top player card never collides with the pause button & title
        val topHudMargin = 82f * density
        val bottomMargin = 26f * density

        topCardTop = topHudMargin + 8f * density
        val topCardBottom = topCardTop + cardH

        val bottomCardBottom = height - bottomMargin
        bottomCardTop = bottomCardBottom - cardH

        // Available vertical space for the 3x3 board
        val availableMiddleSpace = (bottomCardTop - topCardBottom).coerceAtLeast(120f)
        val maxBoardWidth = width - 36f * density

        boardSize = (availableMiddleSpace - 32f * density).coerceAtMost(maxBoardWidth).coerceAtMost(370f * density)
        boardLeft = (width - boardSize) / 2f
        boardTop = topCardBottom + (availableMiddleSpace - boardSize) / 2f
        cellSize = boardSize / 3f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || winningLine != null || isDrawMatch) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val currentP = players.firstOrNull { it.id == currentTurn } ?: return true
            if (currentP.isBot) return true // Bot's turn, ignore human touches

            val x = event.x
            val y = event.y

            if (cellSize > 0f && x in boardLeft..(boardLeft + boardSize) && y in boardTop..(boardTop + boardSize)) {
                val col = ((x - boardLeft) / cellSize).toInt().coerceIn(0, 2)
                val row = ((y - boardTop) / cellSize).toInt().coerceIn(0, 2)

                if (board[row][col] == null) {
                    executeMove(row, col, currentTurn)
                }
            }
        }
        return true
    }

    private fun executeMove(row: Int, col: Int, playerId: PlayerId) {
        if (board[row][col] != null || isGameOver) return

        board[row][col] = playerId
        cellScales[row][col] = 0.05f

        soundManager.play(SoundManager.SoundType.TAP)
        triggerVibration(35)

        // Check for win
        val win = checkWin(board)
        if (win != null) {
            winningLine = win
            winBeamProgress = 0f
            roundEndTimer = 1.3f
            soundManager.play(SoundManager.SoundType.GOAL)
            triggerVibration(90)

            // Spawn celebration particles across the 3 winning cells
            val (x1, y1) = getCellCenter(win.r1, win.c1)
            val (x2, y2) = getCellCenter(win.r2, win.c2)
            val mx = (x1 + x2) / 2f
            val my = (y1 + y2) / 2f
            val winColor = if (win.winnerId == PlayerId.PLAYER_1) colorP1X else colorP2O
            particles.spawnExplosion(x1, y1, winColor, 25)
            particles.spawnExplosion(mx, my, Color.WHITE, 25)
            particles.spawnExplosion(x2, y2, winColor, 25)
            return
        }

        // Check for draw
        if (isBoardFull(board)) {
            isDrawMatch = true
            roundEndTimer = 1.2f
            soundManager.play(SoundManager.SoundType.BOUNCE)
            triggerVibration(60)
            return
        }

        // Advance turn
        currentTurn = if (currentTurn == PlayerId.PLAYER_1) PlayerId.PLAYER_2 else PlayerId.PLAYER_1

        val nextPlayer = players.firstOrNull { it.id == currentTurn }
        if (nextPlayer != null && nextPlayer.isBot) {
            botThinkTimer = when (botDifficulty) {
                0 -> 0.40f
                2 -> 0.50f
                else -> 0.45f
            }
        }
    }

    override fun update(deltaTime: Float) {
        pulseTimer += deltaTime

        // Animate cell mark pop-in
        for (r in 0..2) {
            for (c in 0..2) {
                if (cellScales[r][c] in 0.001f..0.999f) {
                    cellScales[r][c] = (cellScales[r][c] + deltaTime * 6.5f).coerceAtMost(1f)
                }
            }
        }

        // Animate winning laser
        if (winningLine != null && winBeamProgress < 1f) {
            winBeamProgress = (winBeamProgress + deltaTime * 3.5f).coerceAtMost(1f)
        }

        // Bot move countdown
        if (botThinkTimer > 0f) {
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f && !isGameOver && winningLine == null && !isDrawMatch) {
                makeBotMove()
            }
        }

        // End round delay timer
        if (roundEndTimer > 0f) {
            roundEndTimer -= deltaTime
            if (roundEndTimer <= 0f && !isGameOver) {
                if (winningLine != null) {
                    val winnerPlayer = players.firstOrNull { it.id == winningLine!!.winnerId }
                    endRound(winnerPlayer)
                } else if (isDrawMatch) {
                    endRound(null)
                }
            }
        }

        particles.update()
    }

    private fun makeBotMove() {
        val botPlayer = players.firstOrNull { it.id == currentTurn && it.isBot } ?: return
        val botId = botPlayer.id
        val humanId = if (botId == PlayerId.PLAYER_1) PlayerId.PLAYER_2 else PlayerId.PLAYER_1

        val (r, c) = when (botDifficulty) {
            0 -> getEasyMove(botId, humanId)
            2 -> getHardMove(botId, humanId)
            else -> getMediumMove(botId, humanId)
        }

        if (r in 0..2 && c in 0..2 && board[r][c] == null) {
            executeMove(r, c, botId)
        }
    }

    private fun getEasyMove(botId: PlayerId, humanId: PlayerId): Pair<Int, Int> {
        val emptyCells = getEmptyCells(board)
        if (emptyCells.isEmpty()) return Pair(0, 0)

        // 25% chance to spot winning move
        if (random.nextFloat() < 0.25f) {
            for ((r, c) in emptyCells) {
                board[r][c] = botId
                val win = checkWin(board)
                board[r][c] = null
                if (win != null) return Pair(r, c)
            }
        }
        return emptyCells[random.nextInt(emptyCells.size)]
    }

    private fun getMediumMove(botId: PlayerId, humanId: PlayerId): Pair<Int, Int> {
        val emptyCells = getEmptyCells(board)
        if (emptyCells.isEmpty()) return Pair(0, 0)

        // 1. Can bot win?
        for ((r, c) in emptyCells) {
            board[r][c] = botId
            val win = checkWin(board)
            board[r][c] = null
            if (win != null) return Pair(r, c)
        }

        // 2. Can human win next move? Block them!
        for ((r, c) in emptyCells) {
            board[r][c] = humanId
            val win = checkWin(board)
            board[r][c] = null
            if (win != null) return Pair(r, c)
        }

        // 3. Take center if available
        if (board[1][1] == null && random.nextFloat() < 0.70f) {
            return Pair(1, 1)
        }

        // 4. Take available corner
        val corners = listOf(Pair(0, 0), Pair(0, 2), Pair(2, 0), Pair(2, 2)).filter { board[it.first][it.second] == null }
        if (corners.isNotEmpty()) {
            return corners[random.nextInt(corners.size)]
        }

        // 5. Random empty
        return emptyCells[random.nextInt(emptyCells.size)]
    }

    private fun getHardMove(botId: PlayerId, humanId: PlayerId): Pair<Int, Int> {
        var bestScore = Int.MIN_VALUE
        var bestMove = Pair(-1, -1)

        val emptyCells = getEmptyCells(board)
        if (emptyCells.size == 9) {
            val openings = listOf(Pair(1, 1), Pair(0, 0), Pair(0, 2), Pair(2, 0), Pair(2, 2))
            return openings[random.nextInt(openings.size)]
        }

        for ((r, c) in emptyCells) {
            board[r][c] = botId
            val score = minimax(board, depth = 0, isMaximizing = false, botId = botId, humanId = humanId)
            board[r][c] = null
            if (score > bestScore) {
                bestScore = score
                bestMove = Pair(r, c)
            }
        }
        return if (bestMove.first != -1) bestMove else emptyCells.first()
    }

    private fun minimax(
        b: Array<Array<PlayerId?>>,
        depth: Int,
        isMaximizing: Boolean,
        botId: PlayerId,
        humanId: PlayerId
    ): Int {
        val win = checkWin(b)
        if (win != null) {
            return if (win.winnerId == botId) (10 - depth) else (depth - 10)
        }
        if (isBoardFull(b)) return 0

        val emptyCells = getEmptyCells(b)
        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for ((r, c) in emptyCells) {
                b[r][c] = botId
                val eval = minimax(b, depth + 1, false, botId, humanId)
                b[r][c] = null
                maxEval = maxOf(maxEval, eval)
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for ((r, c) in emptyCells) {
                b[r][c] = humanId
                val eval = minimax(b, depth + 1, true, botId, humanId)
                b[r][c] = null
                minEval = minOf(minEval, eval)
            }
            return minEval
        }
    }

    private fun checkWin(b: Array<Array<PlayerId?>>): TicTacToeWinLine? {
        for (r in 0..2) {
            val p = b[r][0]
            if (p != null && p == b[r][1] && b[r][1] == b[r][2]) {
                return TicTacToeWinLine(r, 0, r, 2, p)
            }
        }
        for (c in 0..2) {
            val p = b[0][c]
            if (p != null && p == b[1][c] && b[1][c] == b[2][c]) {
                return TicTacToeWinLine(0, c, 2, c, p)
            }
        }
        val mid = b[1][1]
        if (mid != null) {
            if (b[0][0] == mid && mid == b[2][2]) {
                return TicTacToeWinLine(0, 0, 2, 2, mid)
            }
            if (b[0][2] == mid && mid == b[2][0]) {
                return TicTacToeWinLine(0, 2, 2, 0, mid)
            }
        }
        return null
    }

    private fun isBoardFull(b: Array<Array<PlayerId?>>): Boolean {
        for (r in 0..2) {
            for (c in 0..2) {
                if (b[r][c] == null) return false
            }
        }
        return true
    }

    private fun getEmptyCells(b: Array<Array<PlayerId?>>): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        for (r in 0..2) {
            for (c in 0..2) {
                if (b[r][c] == null) list.add(Pair(r, c))
            }
        }
        return list
    }

    private fun getCellCenter(r: Int, c: Int): Pair<Float, Float> {
        val x = boardLeft + c * cellSize + cellSize / 2f
        val y = boardTop + r * cellSize + cellSize / 2f
        return Pair(x, y)
    }

    override fun render(canvas: Canvas) {
        if (cellSize <= 0f) computeBoardGeometry()

        // 1. Cosmic Background
        canvas.drawColor(Color.parseColor("#0C091D"))

        // Subtle cosmic dust stars
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1F193B")
        for (i in 0..14) {
            val sx = (i * 73f * density) % width
            val sy = (i * 127f * density) % height
            canvas.drawCircle(sx, sy, 2.5f * density, paint)
        }

        // 2. Player 2 (Top) Status Card
        drawPlayerHeader(canvas, isTop = true)

        // 3. Player 1 (Bottom) Status Card
        drawPlayerHeader(canvas, isTop = false)

        // 4. 3x3 Board Grid Background
        paint.style = Paint.Style.FILL
        paint.color = colorGridBg
        val gridPad = 10f * density
        val gridRect = RectF(boardLeft - gridPad, boardTop - gridPad, boardLeft + boardSize + gridPad, boardTop + boardSize + gridPad)
        canvas.drawRoundRect(gridRect, 24f * density, 24f * density, paint)

        // Grid border glow
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * density
        paint.color = Color.parseColor("#2B2152")
        canvas.drawRoundRect(gridRect, 24f * density, 24f * density, paint)

        // 5. Cells & Symbols
        val cellPad = cellSize * 0.06f
        val symbolInset = cellSize * 0.22f

        for (r in 0..2) {
            for (c in 0..2) {
                val cl = boardLeft + c * cellSize
                val ct = boardTop + r * cellSize
                val cellBox = RectF(cl + cellPad, ct + cellPad, cl + cellSize - cellPad, ct + cellSize - cellPad)

                // Cell card background
                paint.style = Paint.Style.FILL
                paint.color = colorCellBg
                canvas.drawRoundRect(cellBox, 16f * density, 16f * density, paint)

                // Cell subtle border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.8f * density
                paint.color = colorCellBorder
                canvas.drawRoundRect(cellBox, 16f * density, 16f * density, paint)

                // Cell symbol (X or O)
                val mark = board[r][c]
                if (mark != null) {
                    val scale = cellScales[r][c]
                    val (cx, cy) = getCellCenter(r, c)

                    canvas.save()
                    canvas.scale(scale, scale, cx, cy)

                    if (mark == PlayerId.PLAYER_1) {
                        drawNeonX(canvas, cx, cy, symbolInset)
                    } else {
                        drawNeonO(canvas, cx, cy, symbolInset)
                    }

                    canvas.restore()
                }
            }
        }

        // 6. Winning Laser Beam Animation
        winningLine?.let { win ->
            drawWinningLaser(canvas, win)
        }

        // 7. Particle Effects
        particles.draw(canvas)

        // 8. Draw announcement banner when draw occurs
        if (isDrawMatch && winningLine == null) {
            drawDrawBanner(canvas)
        }
    }

    private fun drawNeonX(canvas: Canvas, cx: Float, cy: Float, inset: Float) {
        val span = cellSize / 2f - inset

        // Outer glow
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = cellSize * 0.16f
        paint.color = Color.parseColor("#55FF2A6D")
        canvas.drawLine(cx - span, cy - span, cx + span, cy + span, paint)
        canvas.drawLine(cx + span, cy - span, cx - span, cy + span, paint)

        // Vibrant neon body
        paint.strokeWidth = cellSize * 0.11f
        paint.color = colorP1X
        canvas.drawLine(cx - span, cy - span, cx + span, cy + span, paint)
        canvas.drawLine(cx + span, cy - span, cx - span, cy + span, paint)

        // Inner white laser core
        paint.strokeWidth = cellSize * 0.04f
        paint.color = Color.parseColor("#FFF0F5")
        canvas.drawLine(cx - span, cy - span, cx + span, cy + span, paint)
        canvas.drawLine(cx + span, cy - span, cx - span, cy + span, paint)
    }

    private fun drawNeonO(canvas: Canvas, cx: Float, cy: Float, inset: Float) {
        val radius = cellSize / 2f - inset

        // Outer glow
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = cellSize * 0.16f
        paint.color = Color.parseColor("#5500E5FF")
        canvas.drawCircle(cx, cy, radius, paint)

        // Vibrant neon body
        paint.strokeWidth = cellSize * 0.11f
        paint.color = colorP2O
        canvas.drawCircle(cx, cy, radius, paint)

        // Inner white laser core
        paint.strokeWidth = cellSize * 0.04f
        paint.color = Color.parseColor("#E0FFFF")
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun drawWinningLaser(canvas: Canvas, win: TicTacToeWinLine) {
        val (x1, y1) = getCellCenter(win.r1, win.c1)
        val (x2, y2) = getCellCenter(win.r2, win.c2)

        val dx = x2 - x1
        val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val ext = cellSize * 0.28f
        val startX = x1 - (dx / len) * ext
        val startY = y1 - (dy / len) * ext
        val targetEndX = x2 + (dx / len) * ext
        val targetEndY = y2 + (dy / len) * ext

        val curEndX = startX + (targetEndX - startX) * winBeamProgress
        val curEndY = startY + (targetEndY - startY) * winBeamProgress

        val beamColor = if (win.winnerId == PlayerId.PLAYER_1) colorP1X else colorP2O

        // Wide aura glow
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = cellSize * 0.22f
        paint.color = (beamColor and 0x00FFFFFF) or 0x44000000
        canvas.drawLine(startX, startY, curEndX, curEndY, paint)

        // Vivid laser beam
        paint.strokeWidth = cellSize * 0.12f
        paint.color = beamColor
        canvas.drawLine(startX, startY, curEndX, curEndY, paint)

        // Brilliant white core
        paint.strokeWidth = cellSize * 0.05f
        paint.color = Color.WHITE
        canvas.drawLine(startX, startY, curEndX, curEndY, paint)

        // Spark pulse at the tip
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(curEndX, curEndY, cellSize * 0.08f, paint)
    }

    private fun drawPlayerHeader(canvas: Canvas, isTop: Boolean) {
        val player = if (isTop) players.getOrNull(1) else players.firstOrNull()
        if (player == null) return

        val isTurn = (currentTurn == player.id) && !isGameOver && winningLine == null && !isDrawMatch
        val isBot = player.isBot
        val playerColor = if (player.id == PlayerId.PLAYER_1) colorP1X else colorP2O

        val cardTop = if (isTop) topCardTop else bottomCardTop
        val cardLeft = (width - cardW) / 2f
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

        canvas.save()
        // If 2 human players facing each other across the phone, invert the top card so Player 2 views it right-side up!
        if (isTop && !isBot) {
            canvas.rotate(180f, width / 2f, cardTop + cardH / 2f)
        }

        // 1. Card Glow Halo when active
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

        // 2. Main Glass Card Body (matching Setup Screen dark aesthetic)
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

        // 4. Left Avatar Squircle Badge (Matching 2 Players Setup Dialog)
        val avatarSize = 48f * density
        val avatarL = cardLeft + 12f * density
        val avatarT = cardTop + (cardH - avatarSize) / 2f
        val avatarRect = RectF(avatarL, avatarT, avatarL + avatarSize, avatarT + avatarSize)

        // Solid vibrant avatar fill
        paint.style = Paint.Style.FILL
        paint.color = playerColor
        paint.alpha = 255
        canvas.drawRoundRect(avatarRect, 14f * density, 14f * density, paint)

        // Avatar icon / text inside badge
        paint.style = Paint.Style.FILL
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

        // 5. Turn Indicator Pill on Right
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

        // 6. Player Name & Role Label (Middle section with bounded width)
        paint.textAlign = Paint.Align.LEFT
        val textStartX = avatarL + avatarSize + 12f * density
        val maxTextWidth = pillL - textStartX - 8f * density

        // Top Category Tag (e.g. "PLAYER 1 (X)" or "AI BOT (O)")
        paint.color = playerColor
        paint.textSize = 11f * density
        paint.isFakeBoldText = true
        paint.letterSpacing = 0.05f
        val tagText = when {
            player.id == PlayerId.PLAYER_1 -> "PLAYER 1  •  ✕"
            isBot -> {
                val diffStr = when (botDifficulty) {
                    0 -> "EASY"
                    2 -> "HARD"
                    else -> "MEDIUM"
                }
                "AI BOT ($diffStr)  •  ◯"
            }
            else -> "PLAYER 2  •  ◯"
        }
        val tagBaseline = cardTop + 28f * density
        canvas.drawText(tagText, textStartX, tagBaseline, paint)
        paint.letterSpacing = 0f

        // Bottom Player Name (e.g. "Monika", "Player 2", "Robi the Bot")
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

    private fun drawDrawBanner(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E61B1635")
        val bannerW = boardSize * 0.78f
        val bannerH = 48f * density
        val bannerL = (width - bannerW) / 2f
        val bannerT = boardTop + (boardSize - bannerH) / 2f
        val bannerRect = RectF(bannerL, bannerT, bannerL + bannerW, bannerT + bannerH)
        canvas.drawRoundRect(bannerRect, 18f * density, 18f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.parseColor("#FFCC00")
        canvas.drawRoundRect(bannerRect, 18f * density, 18f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 18f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = bannerRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("⚖️ DRAW MATCH!", bannerRect.centerX(), baseline, paint)
        paint.isFakeBoldText = false
    }
}
