package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class ColorBoardSpace(
    val row: Int,
    val col: Int,
    val rect: RectF = RectF(),
    var playerOwner: PlayerId? = null,
    var pieceCount: Int = 0,
    var criticalMass: Int = 4,
    var pulseAnim: Float = 0f
) {
    val isEmpty: Boolean get() = pieceCount == 0 || playerOwner == null
    val isReadyToExpand: Boolean get() = pieceCount >= criticalMass
    val isCritical: Boolean get() = !isEmpty && pieceCount == criticalMass - 1
}

data class FlyingCollapsePiece(
    val player: PlayerId,
    val startX: Float,
    val startY: Float,
    val targetX: Float,
    val targetY: Float,
    val targetRow: Int,
    val targetCol: Int,
    var progress: Float = 0f,
    val duration: Float = 0.15f
)

/**
 * Funtoza Cosmic Neon Color Wars Engine (Chain Reaction / Collapse)
 * Re-architected to match Funtoza's signature dark glassmorphic cyber-neon aesthetic.
 *
 * Rules & Mechanics:
 * - 5x5 board with dynamic critical masses:
 *   - 4 Corners: Critical mass = 2 (explodes on 2nd dot)
 *   - 12 Edges: Critical mass = 3 (explodes on 3rd dot)
 *   - 9 Interior: Critical mass = 4 (explodes on 4th dot)
 * - Players alternate turns tapping an empty tile or upgrading their own tile (+1 dot).
 * - Tiles reaching critical mass EXPLODE into orthogonal neighbors!
 * - Captured tiles flip to the attacker's color and trigger massive cascading chain reactions!
 * - Last player standing (or controlling all pieces) wins the match!
 */
class ColorWarsEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // 5x5 Grid Board
    private val numRows = 5
    private val numCols = 5
    private val board = Array(numRows) { r ->
        Array(numCols) { c ->
            val mass = calculateCriticalMass(r, c)
            ColorBoardSpace(row = r, col = c, criticalMass = mass)
        }
    }

    // Turn & Player state
    private var currentPlayerIndex = 0
    private val turnsTaken = IntArray(players.size)
    private var isChainReactionActive = false
    private var botThinkingTimer = -1f
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null

    // Flying animations for explosions
    private val flyingPieces = mutableListOf<FlyingCollapsePiece>()
    private val pendingExplosionQueue = mutableListOf<ColorBoardSpace>()
    private var cascadeWaveCount = 0
    private var isRoundEnding = false

    // Geometry
    private val boardCardBounds = RectF()
    private val boardInnerBounds = RectF()
    private var tileSize = 0f
    private var tileSpacing = 0f
    private var ambientAnimTimer = 0f

    // Funtoza Cosmic Neon Palette
    private val bgCosmicVoid = Color.parseColor("#090B16") // Deep space black-blue
    private val boardCardBg = Color.parseColor("#111424")  // Frosted chassis card
    private val boardCardBorder = Color.parseColor("#262D4A")
    private val tileNeutralBg = Color.parseColor("#181D33") // Cyber dark tile
    private val tileNeutralBorder = Color.parseColor("#283152")

    // Preallocated drawing tools (zero runtime GC allocation)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val tempRect = RectF()
    private val tempPath = Path()

    init {
        if (totalPlayers == 1 && players.size == 1) {
            players.add(Player(id = PlayerId.PLAYER_2, isBot = true))
        }
    }

    private fun calculateCriticalMass(r: Int, c: Int): Int {
        val isTopOrBottom = (r == 0 || r == numRows - 1)
        val isLeftOrRight = (c == 0 || c == numCols - 1)
        return when {
            isTopOrBottom && isLeftOrRight -> 2 // Corner (2 neighbors)
            isTopOrBottom || isLeftOrRight -> 3 // Edge (3 neighbors)
            else -> 4                           // Center (4 neighbors)
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)

        val isPortrait = h > w
        // Avoid overlapping with Top HUD Layout (btnPause & tvCurrentGameTitle take top 72dp)
        val topSafeArea = if (isPortrait) 78f * density else 64f * density
        val bottomSafeArea = if (isPortrait) 68f * density else 56f * density

        val availableH = h - topSafeArea - bottomSafeArea
        val availableW = w * 0.92f

        val boardSide = min(availableW, availableH * 0.88f)
        tileSpacing = 8f * density
        val totalSpacing = tileSpacing * (numCols - 1)
        val boardPadding = 12f * density

        val innerSide = boardSide - (boardPadding * 2)
        tileSize = (innerSide - totalSpacing) / numCols

        val actualInnerW = tileSize * numCols + totalSpacing
        val actualInnerH = tileSize * numRows + totalSpacing

        val boardLeft = (w - (actualInnerW + boardPadding * 2)) * 0.5f
        val boardTop = topSafeArea + (availableH - (actualInnerH + boardPadding * 2)) * 0.5f

        boardCardBounds.set(
            boardLeft,
            boardTop,
            boardLeft + actualInnerW + boardPadding * 2,
            boardTop + actualInnerH + boardPadding * 2
        )

        boardInnerBounds.set(
            boardCardBounds.left + boardPadding,
            boardCardBounds.top + boardPadding,
            boardCardBounds.right - boardPadding,
            boardCardBounds.bottom - boardPadding
        )

        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val left = boardInnerBounds.left + c * (tileSize + tileSpacing)
                val top = boardInnerBounds.top + r * (tileSize + tileSpacing)
                board[r][c].rect.set(left, top, left + tileSize, top + tileSize)
            }
        }
    }

    override fun initGame() {
        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                board[r][c].playerOwner = null
                board[r][c].pieceCount = 0
                board[r][c].pulseAnim = 0f
            }
        }

        flyingPieces.clear()
        pendingExplosionQueue.clear()
        cascadeWaveCount = 0
        turnsTaken.fill(0)
        currentPlayerIndex = 0
        isChainReactionActive = false
        isRoundEnding = false
        isGameOver = false
        roundEndDelayTimer = -1f
        pendingWinner = null
        ambientAnimTimer = 0f

        players.forEach {
            it.isAlive = true
            it.score = 0
        }

        checkBotTurnTrigger()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isRoundEnding || isChainReactionActive || flyingPieces.isNotEmpty()) {
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val curPlayer = players[currentPlayerIndex]
            if (curPlayer.isBot) return true // Ignore touches during bot turns

            val x = event.x
            val y = event.y

            // Check if user tapped a valid board space
            for (r in 0 until numRows) {
                for (c in 0 until numCols) {
                    val space = board[r][c]
                    if (space.rect.contains(x, y)) {
                        tryMakeMove(space, curPlayer)
                        return true
                    }
                }
            }
        }
        return true
    }

    private fun tryMakeMove(space: ColorBoardSpace, player: Player): Boolean {
        if (isGameOver || isRoundEnding || isChainReactionActive || flyingPieces.isNotEmpty()) {
            return false
        }

        // Space must be empty or owned by current player
        if (!space.isEmpty && space.playerOwner != player.id) {
            soundManager.play(SoundManager.SoundType.BOUNCE)
            triggerVibration(18)
            return false
        }

        // Apply move
        space.playerOwner = player.id
        space.pieceCount++
        space.pulseAnim = 1.0f

        turnsTaken[currentPlayerIndex]++
        cascadeWaveCount = 0
        soundManager.play(SoundManager.SoundType.TAP)
        triggerVibration(30)

        // Check if move triggered an expansion
        if (space.isReadyToExpand) {
            pendingExplosionQueue.add(space)
            isChainReactionActive = true
            triggerNextExplosionWave()
        } else {
            advanceToNextTurn()
        }

        return true
    }

    private fun triggerNextExplosionWave() {
        if (isGameOver || isRoundEnding) {
            flyingPieces.clear()
            pendingExplosionQueue.clear()
            isChainReactionActive = false
            return
        }

        // Immediate check: If the match is already won (e.g. opponent wiped out), finish instantly!
        if (checkVictoryCondition()) {
            return
        }

        cascadeWaveCount++
        if (cascadeWaveCount > 18) {
            // Safety cap: stabilize any remaining overflow pieces and finish the wave
            stabilizeBoard()
            pendingExplosionQueue.clear()
            flyingPieces.clear()
            isChainReactionActive = false
            checkEndgameOrAdvance()
            return
        }

        if (pendingExplosionQueue.isEmpty()) {
            isChainReactionActive = false
            checkEndgameOrAdvance()
            return
        }

        // Pop all ready spaces in current cascade generation
        val currentWave = ArrayList(pendingExplosionQueue)
        pendingExplosionQueue.clear()

        var hasExploded = false

        for (space in currentWave) {
            if (!space.isReadyToExpand) continue

            val owner = space.playerOwner ?: continue
            val mass = space.criticalMass

            // Space loses pieces
            space.pieceCount -= mass
            if (space.pieceCount <= 0) {
                space.pieceCount = 0
                space.playerOwner = null
            }
            space.pulseAnim = 1.25f
            hasExploded = true

            // Send 1 piece to each orthogonal neighbor
            val neighbors = getOrthogonalNeighbors(space.row, space.col)
            for (neighbor in neighbors) {
                flyingPieces.add(
                    FlyingCollapsePiece(
                        player = owner,
                        startX = space.rect.centerX(),
                        startY = space.rect.centerY(),
                        targetX = neighbor.rect.centerX(),
                        targetY = neighbor.rect.centerY(),
                        targetRow = neighbor.row,
                        targetCol = neighbor.col
                    )
                )
            }

            // High-octane neon explosion sparks
            particles.spawnExplosion(space.rect.centerX(), space.rect.centerY(), owner.color, 18)
        }

        if (hasExploded) {
            soundManager.play(SoundManager.SoundType.BOUNCE)
            triggerVibration(45)
        }
    }

    private fun stabilizeBoard() {
        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val sp = board[r][c]
                if (sp.pieceCount >= sp.criticalMass) {
                    sp.pieceCount = sp.criticalMass - 1
                }
            }
        }
    }

    private fun getOrthogonalNeighbors(r: Int, c: Int): List<ColorBoardSpace> {
        val list = mutableListOf<ColorBoardSpace>()
        if (r > 0) list.add(board[r - 1][c])
        if (r < numRows - 1) list.add(board[r + 1][c])
        if (c > 0) list.add(board[r][c - 1])
        if (c < numCols - 1) list.add(board[r][c + 1])
        return list
    }

    override fun update(deltaTime: Float) {
        val dt = deltaTime.coerceIn(0.001f, 0.05f)
        ambientAnimTimer += dt

        // Update space pulse animations
        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val sp = board[r][c]
                if (sp.pulseAnim > 0f) {
                    sp.pulseAnim = (sp.pulseAnim - dt * 3.8f).coerceAtLeast(0f)
                }
            }
        }

        particles.update()

        // Round completion countdown before showing winner popup
        if (roundEndDelayTimer >= 0f) {
            roundEndDelayTimer -= dt
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                isRoundEnding = false
                endRound(pendingWinner)
            }
            return
        }

        if (isGameOver || isRoundEnding) return

        // Animate flying pieces during chain reaction
        if (flyingPieces.isNotEmpty()) {
            val iter = flyingPieces.iterator()
            var arrivedAny = false

            while (iter.hasNext()) {
                val fp = iter.next()
                fp.progress += dt / fp.duration
                if (fp.progress >= 1.0f) {
                    // Piece landed on target space!
                    val target = board[fp.targetRow][fp.targetCol]
                    target.playerOwner = fp.player
                    target.pieceCount++
                    target.pulseAnim = 0.90f

                    // If target now needs to expand, queue it
                    if (target.isReadyToExpand && !pendingExplosionQueue.contains(target)) {
                        pendingExplosionQueue.add(target)
                    }

                    iter.remove()
                    arrivedAny = true
                }
            }

            if (arrivedAny && flyingPieces.isEmpty()) {
                // If the game has been won by wiping out all opponents, stop immediately!
                if (checkVictoryCondition()) {
                    return
                }
                // All pieces of current wave have landed: trigger next cascade wave!
                triggerNextExplosionWave()
            }
            return
        }

        // Bot AI Thinking Delay
        if (botThinkingTimer > 0f) {
            botThinkingTimer -= dt
            if (botThinkingTimer <= 0f) {
                executeBotTurn()
            }
        }
    }

    private fun checkVictoryCondition(): Boolean {
        if (isGameOver || isRoundEnding) return true
        updatePlayerScores()

        val allPlayersStarted = turnsTaken.all { it >= 1 }
        if (!allPlayersStarted) return false

        if (isTeamMode) {
            val team1Tiles = getPlayerTileCount(PlayerId.PLAYER_1) + getPlayerTileCount(PlayerId.PLAYER_2)
            val team2Tiles = getPlayerTileCount(PlayerId.PLAYER_3) + getPlayerTileCount(PlayerId.PLAYER_4)

            if (team1Tiles == 0 || team2Tiles == 0) {
                isRoundEnding = true
                isChainReactionActive = false
                flyingPieces.clear()
                pendingExplosionQueue.clear()
                stabilizeBoard()

                winningTeam = if (team1Tiles > 0) 1 else 2
                pendingWinner = if (winningTeam == 1) players[0] else players.getOrElse(2) { players[0] }
                soundManager.play(SoundManager.SoundType.VICTORY)
                particles.spawnConfetti(width, height, players.map { it.id.color }, 100)
                roundEndDelayTimer = 0.85f
                return true
            }
        } else {
            val activePlayers = players.filter { getPlayerTileCount(it.id) > 0 }
            if (activePlayers.size <= 1) {
                isRoundEnding = true
                isChainReactionActive = false
                flyingPieces.clear()
                pendingExplosionQueue.clear()
                stabilizeBoard()

                pendingWinner = activePlayers.firstOrNull() ?: players[currentPlayerIndex]
                soundManager.play(SoundManager.SoundType.VICTORY)
                particles.spawnConfetti(width, height, players.map { it.id.color }, 100)
                roundEndDelayTimer = 0.85f
                return true
            }
        }
        return false
    }

    private fun checkEndgameOrAdvance() {
        if (checkVictoryCondition()) {
            return
        }
        advanceToNextTurn()
    }

    private fun advanceToNextTurn() {
        val total = players.size
        var nextIdx = (currentPlayerIndex + 1) % total

        // Skip players who have started and have 0 pieces
        if (turnsTaken.all { it >= 1 }) {
            var searchCount = 0
            while (searchCount < total && getPlayerTileCount(players[nextIdx].id) == 0) {
                nextIdx = (nextIdx + 1) % total
                searchCount++
            }
        }

        currentPlayerIndex = nextIdx
        checkBotTurnTrigger()
    }

    private fun checkBotTurnTrigger() {
        if (!isGameOver && !isRoundEnding && players[currentPlayerIndex].isBot) {
            botThinkingTimer = when (botDifficulty) {
                0 -> 0.65f + random.nextFloat() * 0.30f // Relaxed
                1 -> 0.45f + random.nextFloat() * 0.20f // Medium
                else -> 0.30f + random.nextFloat() * 0.15f // Fast
            }
        }
    }

    private fun executeBotTurn() {
        if (isGameOver || isRoundEnding || isChainReactionActive) return
        val botPlayer = players[currentPlayerIndex]

        val chosenSpace = selectBestSpaceForBot(botPlayer)
        if (chosenSpace != null) {
            tryMakeMove(chosenSpace, botPlayer)
        }
    }

    private fun selectBestSpaceForBot(bot: Player): ColorBoardSpace? {
        val myId = bot.id
        val validSpaces = mutableListOf<ColorBoardSpace>()

        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val sp = board[r][c]
                if (sp.isEmpty || sp.playerOwner == myId) {
                    validSpaces.add(sp)
                }
            }
        }

        if (validSpaces.isEmpty()) return null

        when (botDifficulty) {
            0 -> {
                // EASY: Pick random valid space
                return validSpaces[random.nextInt(validSpaces.size)]
            }
            1 -> {
                // MEDIUM: Prefer spaces that trigger expansions or take corners
                val scored = validSpaces.map { space ->
                    var score = 0f
                    if (space.criticalMass == 2) score += 40f
                    else if (space.criticalMass == 3) score += 20f

                    if (space.pieceCount == space.criticalMass - 1) score += 50f

                    Pair(space, score + random.nextFloat() * 10f)
                }
                return scored.maxByOrNull { it.second }?.first
            }
            else -> {
                // HARD:
                val scored = validSpaces.map { space ->
                    var score = 0f

                    // 1. Critical Mass explosion advantage
                    if (space.pieceCount == space.criticalMass - 1) {
                        score += 100f
                        val neighbors = getOrthogonalNeighbors(space.row, space.col)
                        for (nb in neighbors) {
                            if (!nb.isEmpty && nb.playerOwner != myId) {
                                score += 65f + nb.pieceCount * 25f
                            }
                        }
                    }

                    // 2. Structural safety: Corners > Edges > Center
                    when (space.criticalMass) {
                        2 -> score += 50f // Corner
                        3 -> score += 25f // Edge
                        4 -> score += 10f // Center
                    }

                    // 3. Threat penalty
                    val neighbors = getOrthogonalNeighbors(space.row, space.col)
                    for (nb in neighbors) {
                        if (!nb.isEmpty && nb.playerOwner != myId && nb.pieceCount == nb.criticalMass - 1) {
                            score -= 85f
                        }
                    }

                    Pair(space, score + random.nextFloat() * 5f)
                }
                return scored.maxByOrNull { it.second }?.first
            }
        }
    }

    private fun getPlayerTileCount(playerId: PlayerId): Int {
        var count = 0
        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                if (board[r][c].playerOwner == playerId) {
                    count++
                }
            }
        }
        return count
    }

    private fun updatePlayerScores() {
        players.forEach { p ->
            p.score = getPlayerTileCount(p.id)
        }
    }

    // ================= RENDERING =================

    override fun render(canvas: Canvas) {
        // 1. Deep Cosmic Void Background
        canvas.drawColor(bgCosmicVoid)

        // Subtle ambient neon grid dust
        renderAmbientStars(canvas)

        // 2. Board Glass Chassis Card
        renderBoardChassis(canvas)

        // 3. 5x5 Cyber Tiles & Tokens
        renderBoardTiles(canvas)

        // 4. Flying Pieces during Chain Reaction
        renderFlyingPieces(canvas)

        // 5. Splatter Particles
        particles.draw(canvas)

        // 6. Modern Player Score Badges (re-positioned safely outside Top Pause Button)
        renderScoreBadges(canvas)

        // 7. Active Turn Banner
        renderTurnBanner(canvas)
    }

    private fun renderAmbientStars(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B2238")
        val cols = 7
        val rows = 12
        val stepX = width / cols
        val stepY = height / rows

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                val sx = (i + 0.5f) * stepX
                val sy = (j + 0.5f) * stepY
                canvas.drawCircle(sx, sy, 1.2f * density, paint)
            }
        }
    }

    private fun renderBoardChassis(canvas: Canvas) {
        val cornerR = 20f * density

        // Card Drop Shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#44000000")
        tempRect.set(
            boardCardBounds.left,
            boardCardBounds.top + 6f * density,
            boardCardBounds.right,
            boardCardBounds.bottom + 6f * density
        )
        canvas.drawRoundRect(tempRect, cornerR, cornerR, paint)

        // Card Glass Body
        paint.color = boardCardBg
        canvas.drawRoundRect(boardCardBounds, cornerR, cornerR, paint)

        // Neon Outer Stroke
        val curPlayerColor = players[currentPlayerIndex].id.color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = boardCardBorder
        canvas.drawRoundRect(boardCardBounds, cornerR, cornerR, paint)

        // Glowing Corner Brackets
        renderCornerBrackets(canvas, boardCardBounds, curPlayerColor)
    }

    private fun renderCornerBrackets(canvas: Canvas, r: RectF, glowColor: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * density
        paint.color = glowColor
        paint.alpha = 140

        val arm = 16f * density
        val offset = 6f * density

        // Top-Left Bracket
        canvas.drawLine(r.left + offset, r.top + offset + arm, r.left + offset, r.top + offset, paint)
        canvas.drawLine(r.left + offset, r.top + offset, r.left + offset + arm, r.top + offset, paint)

        // Top-Right Bracket
        canvas.drawLine(r.right - offset - arm, r.top + offset, r.right - offset, r.top + offset, paint)
        canvas.drawLine(r.right - offset, r.top + offset, r.right - offset, r.top + offset + arm, paint)

        // Bottom-Left Bracket
        canvas.drawLine(r.left + offset, r.bottom - offset - arm, r.left + offset, r.bottom - offset, paint)
        canvas.drawLine(r.left + offset, r.bottom - offset, r.left + offset + arm, r.bottom - offset, paint)

        // Bottom-Right Bracket
        canvas.drawLine(r.right - offset - arm, r.bottom - offset, r.right - offset, r.bottom - offset, paint)
        canvas.drawLine(r.right - offset, r.bottom - offset, r.right - offset, r.bottom - offset - arm, paint)

        paint.alpha = 255
    }

    private fun renderBoardTiles(canvas: Canvas) {
        val cornerR = 10f * density
        val pulse = 0.5f + 0.5f * sin(ambientAnimTimer * 6.0).toFloat()

        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val space = board[r][c]
                val rect = space.rect

                // 1. Tile Background
                paint.style = Paint.Style.FILL
                if (space.isEmpty) {
                    paint.color = tileNeutralBg
                } else {
                    // Translucent cyber tint of owner's color
                    val ownerColor = space.playerOwner!!.color
                    paint.color = getPlayerNeonGlowColor(ownerColor, 40)
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, paint)

                // 2. Tile Border
                paint.style = Paint.Style.STROKE
                if (space.isEmpty) {
                    paint.strokeWidth = 1.2f * density
                    paint.color = tileNeutralBorder
                } else {
                    val ownerColor = space.playerOwner!!.color
                    if (space.isCritical) {
                        // Critical Mass Warning Heartbeat
                        paint.strokeWidth = (2.2f + pulse * 1.0f) * density
                        paint.color = ownerColor
                    } else {
                        paint.strokeWidth = 1.8f * density
                        paint.color = ownerColor
                        paint.alpha = 180
                    }
                }
                canvas.drawRoundRect(rect, cornerR, cornerR, paint)
                paint.alpha = 255

                // 3. Render Pieces inside space
                if (!space.isEmpty) {
                    renderSpacePiece(canvas, space)
                }
            }
        }
    }

    private fun renderSpacePiece(canvas: Canvas, space: ColorBoardSpace) {
        val cx = space.rect.centerX()
        val cy = space.rect.centerY()
        val count = space.pieceCount
        val color = space.playerOwner!!.color

        val popScale = 1f + space.pulseAnim * 0.22f
        val discRadius = (tileSize * 0.33f) * popScale

        canvas.save()
        canvas.translate(cx, cy)

        // Drop shadow
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#44000000")
        canvas.drawCircle(0f, 3f * density, discRadius, paint)

        // Vibrant 3D Neon Disc
        paint.color = color
        canvas.drawCircle(0f, 0f, discRadius, paint)

        // Specular Top Rim Arc for 3D sphere depth
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f * density
        paint.color = Color.WHITE
        paint.alpha = 110
        canvas.drawCircle(0f, 0f, discRadius - 1.2f * density, paint)
        paint.alpha = 255

        // White Pips / Dots
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        val dotR = discRadius * 0.21f

        when (count) {
            1 -> {
                // 1 Centered Pip
                canvas.drawCircle(0f, 0f, dotR, paint)
            }
            2 -> {
                // 2 Diagonal Pips
                val offset = discRadius * 0.36f
                canvas.drawCircle(-offset, -offset, dotR, paint)
                canvas.drawCircle(offset, offset, dotR, paint)
            }
            3 -> {
                // 3 Triangular Pips
                val offset = discRadius * 0.40f
                canvas.drawCircle(0f, -offset * 0.9f, dotR, paint)
                canvas.drawCircle(-offset, offset * 0.6f, dotR, paint)
                canvas.drawCircle(offset, offset * 0.6f, dotR, paint)
            }
            else -> {
                // 4 Pips
                val offset = discRadius * 0.38f
                canvas.drawCircle(-offset, -offset, dotR, paint)
                canvas.drawCircle(offset, -offset, dotR, paint)
                canvas.drawCircle(-offset, offset, dotR, paint)
                canvas.drawCircle(offset, offset, dotR, paint)
            }
        }

        canvas.restore()
    }

    private fun renderFlyingPieces(canvas: Canvas) {
        flyingPieces.forEach { fp ->
            val curX = fp.startX + (fp.targetX - fp.startX) * fp.progress
            val curY = fp.startY + (fp.targetY - fp.startY) * fp.progress
            val radius = tileSize * 0.26f

            // Shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#44000000")
            canvas.drawCircle(curX, curY + 3.5f * density, radius, paint)

            // Neon Disc
            paint.color = fp.player.color
            canvas.drawCircle(curX, curY, radius, paint)

            // Glow core
            paint.color = Color.WHITE
            canvas.drawCircle(curX, curY, radius * 0.34f, paint)
        }
    }

    private fun renderScoreBadges(canvas: Canvas) {
        val badgeW = 96f * density
        val badgeH = 36f * density
        val sideMargin = 16f * density

        // Safe placement:
        // P2 (Blue) Top-Right (safely clears the pause button in top-left!)
        val p2 = players.getOrElse(1) { players[0] }
        val p2Score = getPlayerTileCount(p2.id)
        val p2Y = boardCardBounds.top - badgeH - 12f * density
        val p2Rect = RectF(width - sideMargin - badgeW, p2Y, width - sideMargin, p2Y + badgeH)
        drawScoreBadge(canvas, p2Rect, p2, p2Score, isTopPlayer = true, isCurrent = currentPlayerIndex == 1)

        // P1 (Red) Bottom-Right
        val p1 = players[0]
        val p1Score = getPlayerTileCount(p1.id)
        val p1Y = boardCardBounds.bottom + 12f * density
        val p1Rect = RectF(width - sideMargin - badgeW, p1Y, width - sideMargin, p1Y + badgeH)
        drawScoreBadge(canvas, p1Rect, p1, p1Score, isTopPlayer = false, isCurrent = currentPlayerIndex == 0)

        // 3rd & 4th players if present
        if (players.size >= 3) {
            val p3 = players[2]
            val p3Score = getPlayerTileCount(p3.id)
            val p3Rect = RectF(sideMargin, p2Y, sideMargin + badgeW, p2Y + badgeH)
            drawScoreBadge(canvas, p3Rect, p3, p3Score, isTopPlayer = true, isCurrent = currentPlayerIndex == 2)
        }
        if (players.size >= 4) {
            val p4 = players[3]
            val p4Score = getPlayerTileCount(p4.id)
            val p4Rect = RectF(sideMargin, p1Y, sideMargin + badgeW, p1Y + badgeH)
            drawScoreBadge(canvas, p4Rect, p4, p4Score, isTopPlayer = false, isCurrent = currentPlayerIndex == 3)
        }
    }

    private fun drawScoreBadge(
        canvas: Canvas,
        rect: RectF,
        player: Player,
        score: Int,
        isTopPlayer: Boolean,
        isCurrent: Boolean
    ) {
        canvas.save()
        if (isTopPlayer) {
            // Rotated 180° for head-to-head opponent across table
            canvas.rotate(180f, rect.centerX(), rect.centerY())
        }

        val cornerR = rect.height() * 0.5f

        // Pill background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#151829")
        canvas.drawRoundRect(rect, cornerR, cornerR, paint)

        // Neon Border
        paint.style = Paint.Style.STROKE
        val pColor = player.id.color
        if (isCurrent) {
            val pulse = 0.5f + 0.5f * sin(ambientAnimTimer * 7.0).toFloat()
            paint.strokeWidth = (2.2f + pulse * 1.2f) * density
            paint.color = pColor
        } else {
            paint.strokeWidth = 1.4f * density
            paint.color = Color.parseColor("#28304C")
        }
        canvas.drawRoundRect(rect, cornerR, cornerR, paint)

        // Player Icon Dot
        paint.style = Paint.Style.FILL
        paint.color = pColor
        val iconR = 8f * density
        val iconX = rect.left + 16f * density
        val iconY = rect.centerY()
        canvas.drawCircle(iconX, iconY, iconR, paint)

        // Score number text
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 17f * density
        textPaint.color = Color.WHITE
        val scoreX = rect.centerX() + 8f * density
        val scoreY = rect.centerY() + 6f * density
        canvas.drawText("$score", scoreX, scoreY, textPaint)

        canvas.restore()
    }

    private fun renderTurnBanner(canvas: Canvas) {
        if (isGameOver || isRoundEnding) return
        val curPlayer = players[currentPlayerIndex]
        val cx = width * 0.5f
        val p1Y = boardCardBounds.bottom + 12f * density
        val badgeH = 36f * density
        val cy = p1Y + badgeH + 22f * density

        val bannerW = 175f * density
        val bannerH = 34f * density
        val r = RectF(cx - bannerW * 0.5f, cy - bannerH * 0.5f, cx + bannerW * 0.5f, cy + bannerH * 0.5f)

        // Translucent glass capsule
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1A1F36")
        canvas.drawRoundRect(r, bannerH * 0.5f, bannerH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = curPlayer.id.color
        paint.alpha = 200
        canvas.drawRoundRect(r, bannerH * 0.5f, bannerH * 0.5f, paint)
        paint.alpha = 255

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 12.5f * density
        textPaint.color = Color.WHITE
        val pName = if (curPlayer.isBot) "${curPlayer.id.displayName} (BOT)" else curPlayer.id.displayName
        val turnStr = if (isChainReactionActive) "CHAIN REACTION! 💥" else "$pName'S TURN"
        canvas.drawText(turnStr, cx, cy + 4.5f * density, textPaint)
    }

    private fun getPlayerNeonGlowColor(baseColor: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
    }

    override fun getPlayerScoreString(player: Player): String {
        val count = getPlayerTileCount(player.id)
        return "$count ${if (count == 1) "Tile" else "Tiles"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return getPlayerTileCount(player.id)
    }

    override fun getTeamScoreString(team: Int): String {
        val count = if (team == 1) {
            getPlayerTileCount(PlayerId.PLAYER_1) + getPlayerTileCount(PlayerId.PLAYER_2)
        } else {
            getPlayerTileCount(PlayerId.PLAYER_3) + getPlayerTileCount(PlayerId.PLAYER_4)
        }
        return "TEAM $team: $count Tiles"
    }

    override fun shouldShowPlayerScoresOnWinnerDialog(): Boolean = true
}
