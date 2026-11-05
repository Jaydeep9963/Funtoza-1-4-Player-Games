package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Memory (Concentration / Card Matching) Minigame Engine
 * Supports 1, 2, 3, and 4 players as well as 2v2 Team Play.
 * Adapted directly from 49_MEMORY in the reference APK (JindoBlu FourPlayers).
 */
class MemoryEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // Grid Dimensions: With Bot: 6x6 Easy, 7x7 Medium, 8x8 Hard
    val gridCols: Int = when {
        totalPlayers == 1 -> when (botDifficulty) {
            0 -> 6 // Easy: 6x6
            1 -> 7 // Medium: 7x7
            else -> 8 // Hard: 8x8
        }
        totalPlayers >= 3 -> 8
        botDifficulty == 0 -> 6
        botDifficulty == 1 -> 7
        else -> 8
    }

    val gridRows: Int = when {
        totalPlayers == 1 -> when (botDifficulty) {
            0 -> 6 // Easy: 6x6
            1 -> 7 // Medium: 7x7
            else -> 8 // Hard: 8x8
        }
        totalPlayers >= 3 -> 8
        botDifficulty == 0 -> 6
        botDifficulty == 1 -> 7
        else -> 8
    }

    val totalCardsCount: Int = gridCols * gridRows
    val isOddGrid: Boolean = (totalCardsCount % 2 != 0)
    val totalPairsCount: Int = totalCardsCount / 2

    // Card Pair Symbols & Neon Theme Colors (32 unique pairs for 8x8 = 64 cards)
    private val pairThemes = listOf(
        Pair("⭐", Color.parseColor("#FFD700")), // Golden Star
        Pair("💎", Color.parseColor("#00E5FF")), // Cyan Diamond
        Pair("🍎", Color.parseColor("#FF3B30")), // Ruby Apple
        Pair("👑", Color.parseColor("#FFB300")), // Royal Crown
        Pair("👻", Color.parseColor("#E0D6FF")), // Phantom Ghost
        Pair("🍕", Color.parseColor("#FF6B00")), // Cosmic Pizza
        Pair("🚗", Color.parseColor("#007AFF")), // Speed Car
        Pair("🐟", Color.parseColor("#00E676")), // Neon Fish
        Pair("🐱", Color.parseColor("#FF4081")), // Cyber Cat
        Pair("🐶", Color.parseColor("#FF9100")), // Loyal Dog
        Pair("🌙", Color.parseColor("#BD00FF")), // Silver Moon
        Pair("☀️", Color.parseColor("#FFEA00")), // Solar Sun
        Pair("🚀", Color.parseColor("#FF2A6D")), // Cosmic Rocket
        Pair("⚡", Color.parseColor("#FFE600")), // Thunder Bolt
        Pair("🍀", Color.parseColor("#4CAF50")), // Lucky Clover
        Pair("🏆", Color.parseColor("#FFC107")), // Trophy Cup
        Pair("🔔", Color.parseColor("#FF9800")), // Golden Bell
        Pair("🍬", Color.parseColor("#E91E63")), // Sweet Candy
        Pair("🍒", Color.parseColor("#D81B60")), // Twin Cherry
        Pair("💣", Color.parseColor("#78909C")), // Retro Bomb
        Pair("⚽", Color.parseColor("#42A5F5")), // Neon Ball
        Pair("🎮", Color.parseColor("#AB47BC")), // Arcade Gamepad
        Pair("🍩", Color.parseColor("#FF8A80")), // Glazed Donut
        Pair("🌈", Color.parseColor("#26C6DA")), // Cyber Rainbow
        Pair("🎁", Color.parseColor("#FF5252")), // Surprise Gift
        Pair("🎸", Color.parseColor("#E040FB")), // Electric Guitar
        Pair("🍦", Color.parseColor("#FF80AB")), // Ice Cream
        Pair("🥑", Color.parseColor("#7CB342")), // Green Avocado
        Pair("🍄", Color.parseColor("#F44336")), // Magic Mushroom
        Pair("🛸", Color.parseColor("#00B0FF")), // Alien UFO
        Pair("🏀", Color.parseColor("#FF6D00")), // Basketball
        Pair("🔑", Color.parseColor("#FFD600"))  // Golden Key
    )

    data class MemoryCard(
        val id: Int,
        val pairId: Int,
        val emoji: String,
        val themeColor: Int,
        var isFlipped: Boolean = false,
        var isMatched: Boolean = false,
        var owner: PlayerId? = null,
        val rect: RectF = RectF(),
        var flipProgress: Float = 0f, // 0 = face down, 1 = face up
        var targetFlip: Float = 0f,
        var matchScale: Float = 1f,
        var isFlying: Boolean = false,
        var flyProgress: Float = 0f,
        val startPos: PointF = PointF(),
        val endPos: PointF = PointF()
    )

    private val cards = mutableListOf<MemoryCard>()
    private val pairsClaimed = mutableMapOf<PlayerId, Int>()

    // Bot AI Memory: cardId -> pairId
    private val botMemory = mutableMapOf<Int, Int>()

    // Turn Management
    private var currentTurnIndex = 0
    private var botThinkTimer = 0f
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null

    // Match Evaluation State
    private var firstSelectedCard: MemoryCard? = null
    private var secondSelectedCard: MemoryCard? = null
    private var mismatchRevealTimer = 0f
    private var matchEvaluateTimer = 0f

    // UI Feedback
    private var feedbackMessage = ""
    private var feedbackTimer = 0f
    private var pulseTimer = 0f

    // Board Geometry
    private val boardRect = RectF()
    private var cardWidth = 0f
    private var cardHeight = 0f
    private var cardSpacing = 0f

    // Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun initGame() {
        cards.clear()
        pairsClaimed.clear()
        botMemory.clear()

        isGameOver = false
        currentTurnIndex = 0
        roundEndDelayTimer = -1f
        pendingWinner = null
        firstSelectedCard = null
        secondSelectedCard = null
        mismatchRevealTimer = 0f
        matchEvaluateTimer = 0f
        feedbackMessage = ""
        feedbackTimer = 0f

        players.forEach { p ->
            pairsClaimed[p.id] = 0
            p.score = 0
        }

        // Generate matching pairs
        val pairIndices = mutableListOf<Int>()
        for (i in 0 until totalPairsCount) {
            val themeIndex = i % pairThemes.size
            pairIndices.add(themeIndex)
            pairIndices.add(themeIndex)
        }
        pairIndices.shuffle(random)

        val centerIndex = if (isOddGrid) (gridRows / 2) * gridCols + (gridCols / 2) else -1

        // Create Cards
        var pairPointer = 0
        for (i in 0 until totalCardsCount) {
            if (i == centerIndex) {
                // Special Lucky Star bonus card in the center of 7x7
                cards.add(
                    MemoryCard(
                        id = i,
                        pairId = -1,
                        emoji = "🌟",
                        themeColor = Color.parseColor("#FFD700")
                    )
                )
            } else {
                val themeIdx = pairIndices[pairPointer++]
                val theme = pairThemes[themeIdx]
                cards.add(
                    MemoryCard(
                        id = i,
                        pairId = themeIdx,
                        emoji = theme.first,
                        themeColor = theme.second
                    )
                )
            }
        }

        val firstP = getCurrentPlayer()
        if (firstP != null && firstP.isBot) {
            botThinkTimer = getBotDelay()
        }

        if (width > 0f && height > 0f) {
            computeLayoutGeometry()
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        computeLayoutGeometry()
    }

    override fun calculateControlZones() {
        controlZones.clear()
        computeLayoutGeometry()
    }

    private fun computeLayoutGeometry() {
        if (width <= 0f || height <= 0f) return

        val hudTop = 114f * density // Generous breathing room below the top title and turn banner
        val hudBottom = 86f * density
        val availableHeight = height - hudTop - hudBottom
        val availableWidth = width - 16f * density

        cardSpacing = when {
            gridCols >= 8 -> 3.5f * density
            gridCols >= 7 -> 4.5f * density
            else -> 6f * density
        }
        val boardPadding = 6f * density

        // Compute aspect-correct card size for 8 cols x 8 rows
        val maxAvailableW = availableWidth - boardPadding * 2f
        val maxAvailableH = availableHeight - boardPadding * 2f

        val rawCardW = (maxAvailableW - (gridCols - 1) * cardSpacing) / gridCols
        val rawCardH = (maxAvailableH - (gridRows - 1) * cardSpacing) / gridRows

        val cellSize = min(rawCardW, rawCardH)
        cardWidth = cellSize
        cardHeight = cellSize * 1.12f
        if (cardHeight > rawCardH) {
            cardHeight = rawCardH
            cardWidth = rawCardH / 1.12f
        }

        val totalCardsW = gridCols * cardWidth + (gridCols - 1) * cardSpacing
        val totalCardsH = gridRows * cardHeight + (gridRows - 1) * cardSpacing

        val boardW = totalCardsW + boardPadding * 2f
        val boardH = totalCardsH + boardPadding * 2f

        val boardX = (width - boardW) / 2f
        val boardY = hudTop + (availableHeight - boardH) / 2f
        boardRect.set(boardX, boardY, boardX + boardW, boardY + boardH)

        // Layout Card Rectangles inside padded boardRect
        cards.forEachIndexed { i, card ->
            val col = i % gridCols
            val row = i / gridCols

            val cx = boardRect.left + boardPadding + col * (cardWidth + cardSpacing)
            val cy = boardRect.top + boardPadding + row * (cardHeight + cardSpacing)
            card.rect.set(cx, cy, cx + cardWidth, cy + cardHeight)
        }
    }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        return players[currentTurnIndex % players.size]
    }

    private fun getBotDelay(): Float {
        return when (botDifficulty) {
            0 -> 1.0f
            2 -> 0.55f
            else -> 0.75f
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

        // Update Card Flip Animations
        cards.forEach { c ->
            if (c.flipProgress != c.targetFlip) {
                val step = deltaTime * 4.5f
                if (c.flipProgress < c.targetFlip) {
                    c.flipProgress = min(c.targetFlip, c.flipProgress + step)
                } else {
                    c.flipProgress = max(c.targetFlip, c.flipProgress - step)
                }
            }

            if (c.matchScale > 1f) {
                c.matchScale = max(1f, c.matchScale - deltaTime * 1.8f)
            }

            // Flying matched cards to player tray
            if (c.isFlying) {
                c.flyProgress = min(1f, c.flyProgress + deltaTime * 2.2f)
                val curX = c.startPos.x + (c.endPos.x - c.startPos.x) * c.flyProgress
                val curY = c.startPos.y + (c.endPos.y - c.startPos.y) * c.flyProgress
                val shrink = 1f - c.flyProgress * 0.7f
                val hw = (cardWidth * shrink) / 2f
                val hh = (cardHeight * shrink) / 2f
                c.rect.set(curX - hw, curY - hh, curX + hw, curY + hh)
            }
        }

        // Mismatch View Window: Keep cards visible so players can memorize them
        if (mismatchRevealTimer > 0f) {
            mismatchRevealTimer -= deltaTime
            if (mismatchRevealTimer <= 0f) {
                // Flip both back down
                firstSelectedCard?.isFlipped = false
                firstSelectedCard?.targetFlip = 0f
                secondSelectedCard?.isFlipped = false
                secondSelectedCard?.targetFlip = 0f

                firstSelectedCard = null
                secondSelectedCard = null

                advanceTurn()
            }
            return
        }

        // Match Evaluation Delay: Once second card is flipped
        if (matchEvaluateTimer > 0f) {
            matchEvaluateTimer -= deltaTime
            if (matchEvaluateTimer <= 0f) {
                evaluateChosenCards()
            }
            return
        }

        // Bot AI Thinking
        val curP = getCurrentPlayer()
        if (curP != null && curP.isBot && firstSelectedCard == null && mismatchRevealTimer <= 0f && matchEvaluateTimer <= 0f) {
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f) {
                botPickFirstCard(curP)
            }
        } else if (curP != null && curP.isBot && firstSelectedCard != null && secondSelectedCard == null && mismatchRevealTimer <= 0f && matchEvaluateTimer <= 0f) {
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f) {
                botPickSecondCard(curP)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || mismatchRevealTimer > 0f || matchEvaluateTimer > 0f || roundEndDelayTimer >= 0f) return true

        val curP = getCurrentPlayer() ?: return true
        if (curP.isBot) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val tx = event.x
            val ty = event.y

            // Find clicked card
            val clickedCard = cards.firstOrNull {
                !it.isMatched && !it.isFlipped && it.rect.contains(tx, ty)
            } ?: return true

            handleCardSelection(clickedCard, curP)
        }

        return true
    }

    private fun handleCardSelection(card: MemoryCard, player: Player) {
        if (card.isMatched || card.isFlipped) return

        card.isFlipped = true
        card.targetFlip = 1f
        soundManager.play(SoundManager.SoundType.TAP, 1.0f)
        triggerVibration(25L)

        // Special Lucky Star in 7x7 grid: Instant Bonus Point!
        if (card.pairId == -1) {
            card.isMatched = true
            card.owner = player.id
            card.matchScale = 1.4f

            soundManager.play(SoundManager.SoundType.GOAL, 1.0f)
            triggerVibration(75L)

            particles.spawnExplosion(card.rect.centerX(), card.rect.centerY(), card.themeColor, 26)

            val destPoint = getPlayerScoreBadgePosition(player.id)
            card.startPos.set(card.rect.centerX(), card.rect.centerY())
            card.endPos.set(destPoint.x, destPoint.y)
            card.isFlying = true

            pairsClaimed[player.id] = (pairsClaimed[player.id] ?: 0) + 1
            player.score = pairsClaimed[player.id] ?: 0

            feedbackMessage = "🌟 LUCKY STAR BONUS! (+1 POINT)"
            feedbackTimer = 1.5f

            checkEndgame()

            if (!isGameOver && player.isBot) {
                botThinkTimer = getBotDelay()
            }
            return
        }

        // Bot observes flipped card and potentially remembers it!
        observeCardByBot(card)

        if (firstSelectedCard == null) {
            firstSelectedCard = card
            if (player.isBot) {
                botThinkTimer = getBotDelay()
            }
        } else if (secondSelectedCard == null && card != firstSelectedCard) {
            secondSelectedCard = card
            matchEvaluateTimer = 0.45f
        }
    }

    private fun observeCardByBot(card: MemoryCard) {
        val memoryChance = when (botDifficulty) {
            0 -> 0.45f // Easy: forgets often
            2 -> 0.95f // Hard: near photographic memory
            else -> 0.75f // Medium: good memory
        }
        if (random.nextFloat() <= memoryChance) {
            botMemory[card.id] = card.pairId
        }
    }

    private fun evaluateChosenCards() {
        val c1 = firstSelectedCard ?: return
        val c2 = secondSelectedCard ?: return
        val curP = getCurrentPlayer() ?: return

        if (c1.pairId == c2.pairId) {
            // MATCH!
            soundManager.play(SoundManager.SoundType.GOAL, 1.0f)
            triggerVibration(75L)

            c1.isMatched = true
            c2.isMatched = true
            c1.owner = curP.id
            c2.owner = curP.id

            c1.matchScale = 1.3f
            c2.matchScale = 1.3f

            // Particle Celebrations
            particles.spawnExplosion(c1.rect.centerX(), c1.rect.centerY(), c1.themeColor, 18)
            particles.spawnExplosion(c2.rect.centerX(), c2.rect.centerY(), c2.themeColor, 18)

            // Setup Flying Animation to Player Score Badge
            val destPoint = getPlayerScoreBadgePosition(curP.id)
            c1.startPos.set(c1.rect.centerX(), c1.rect.centerY())
            c1.endPos.set(destPoint.x, destPoint.y)
            c1.isFlying = true

            c2.startPos.set(c2.rect.centerX(), c2.rect.centerY())
            c2.endPos.set(destPoint.x, destPoint.y)
            c2.isFlying = true

            // Clean up from bot memory
            botMemory.remove(c1.id)
            botMemory.remove(c2.id)

            // Update Score
            pairsClaimed[curP.id] = (pairsClaimed[curP.id] ?: 0) + 1
            curP.score = pairsClaimed[curP.id] ?: 0

            feedbackMessage = "🔥 MATCH! DRAW AGAIN 🎯"
            feedbackTimer = 1.2f

            firstSelectedCard = null
            secondSelectedCard = null

            checkEndgame()

            // Bonus turn: Same player plays again!
            if (!isGameOver && curP.isBot) {
                botThinkTimer = getBotDelay()
            }
        } else {
            // MISMATCH: Show cards for a brief moment, then flip down
            soundManager.play(SoundManager.SoundType.BOUNCE, 0.6f)
            triggerVibration(35L)
            mismatchRevealTimer = 0.95f
        }
    }

    private fun getPlayerScoreBadgePosition(playerId: PlayerId): PointF {
        val hudY = height - 50f * density
        val count = players.size
        val index = players.indexOfFirst { it.id == playerId }
        val cardW = (width - 32f * density - (count - 1) * 8f * density) / count
        val cx = 16f * density + index * (cardW + 8f * density) + cardW / 2f
        return PointF(cx, hudY)
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
        val matchedCount = cards.count { it.isMatched }
        if (matchedCount >= totalCardsCount) {
            if (roundEndDelayTimer >= 0f || isGameOver) return

            soundManager.play(SoundManager.SoundType.VICTORY, 1.0f)
            triggerVibration(100L)

            if (isTeamMode) {
                val t1Score = (pairsClaimed[PlayerId.PLAYER_1] ?: 0) + (pairsClaimed[PlayerId.PLAYER_2] ?: 0)
                val t2Score = (pairsClaimed[PlayerId.PLAYER_3] ?: 0) + (pairsClaimed[PlayerId.PLAYER_4] ?: 0)
                winningTeam = if (t1Score >= t2Score) 1 else 2
                val winTeam = winningTeam ?: 1
                val winner = if (winTeam == 1) players[0] else players[2]
                val winScore = if (winTeam == 1) t1Score else t2Score

                feedbackMessage = "🏆 TEAM $winTeam WINS! ($winScore PAIRS)"
                feedbackTimer = 2.5f

                val teamColor = if (winTeam == 1) Color.parseColor("#FF3B30") else Color.parseColor("#007AFF")
                particles.spawnConfetti(width, height, listOf(teamColor, Color.parseColor("#FFD54F"), Color.WHITE))

                pendingWinner = winner
                roundEndDelayTimer = 1.0f
                return
            }

            val maxPairs = players.maxOf { pairsClaimed[it.id] ?: 0 }
            val winners = players.filter { (pairsClaimed[it.id] ?: 0) == maxPairs }
            val winner = winners.firstOrNull() ?: players.first()

            val winnerName = if (winners.size > 1) {
                "TIE GAME!"
            } else {
                "${getPlayerDisplayName(winner).uppercase()} WINS!"
            }
            val winScore = pairsClaimed[winner.id] ?: 0
            feedbackMessage = "🏆 $winnerName ($winScore PAIRS)"
            feedbackTimer = 2.5f

            particles.spawnConfetti(width, height, listOf(winner.id.color, Color.parseColor("#FFD54F"), Color.WHITE))

            pendingWinner = winner
            roundEndDelayTimer = 1.0f
        }
    }

    /**
     * Strategic CPU Bot AI as modeled in reference APK:
     * 1. Check if bot has already memorized a complete matching pair.
     * 2. Otherwise pick an unrevealed card.
     */
    private fun botPickFirstCard(botPlayer: Player) {
        val availableCards = cards.filter { !it.isMatched && !it.isFlipped }
        if (availableCards.isEmpty()) return

        // 1. Look for a known pair in memory
        val memorizedAvailable = botMemory.filter { entry ->
            val card = cards.firstOrNull { it.id == entry.key }
            card != null && !card.isMatched && !card.isFlipped
        }

        // Find any two distinct card IDs with the same pairId
        var chosenCard: MemoryCard? = null
        for ((cId1, pair1) in memorizedAvailable) {
            for ((cId2, pair2) in memorizedAvailable) {
                if (cId1 != cId2 && pair1 == pair2) {
                    chosenCard = cards.firstOrNull { it.id == cId1 }
                    break
                }
            }
            if (chosenCard != null) break
        }

        // 2. If no complete pair is in memory, pick an unrevealed card
        if (chosenCard == null) {
            val unmemorized = availableCards.filter { !botMemory.containsKey(it.id) }
            chosenCard = if (unmemorized.isNotEmpty()) unmemorized.random(random) else availableCards.random(random)
        }

        handleCardSelection(chosenCard, botPlayer)
    }

    private fun botPickSecondCard(botPlayer: Player) {
        val first = firstSelectedCard ?: return
        val availableCards = cards.filter { !it.isMatched && !it.isFlipped && it.id != first.id }
        if (availableCards.isEmpty()) return

        // 1. Does bot know where the match for the first card is?
        var chosenCard: MemoryCard? = null
        val matchEntry = botMemory.entries.firstOrNull {
            it.key != first.id && it.value == first.pairId && cards.any { c -> c.id == it.key && !c.isMatched && !c.isFlipped }
        }

        if (matchEntry != null) {
            chosenCard = cards.firstOrNull { it.id == matchEntry.key }
        }

        // 2. If unknown, pick another random card
        if (chosenCard == null) {
            val unmemorized = availableCards.filter { !botMemory.containsKey(it.id) }
            chosenCard = if (unmemorized.isNotEmpty()) unmemorized.random(random) else availableCards.random(random)
        }

        handleCardSelection(chosenCard, botPlayer)
    }

    override fun render(canvas: Canvas) {
        // Defensive self-heal: guarantee cards and geometry are ready on first frame
        if ((cards.isEmpty() || cards[0].rect.isEmpty) && width > 0f && height > 0f) {
            if (cards.isEmpty()) {
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

        // 2. Board Container Frame
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#100E26")
        canvas.drawRoundRect(boardRect, 18f * density, 18f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = Color.parseColor("#26224E")
        canvas.drawRoundRect(boardRect, 18f * density, 18f * density, paint)

        // 3. Render Memory Cards
        renderCards(canvas)

        // 4. Particle Effects
        particles.draw(canvas)

        // 5. Top HUD & Scoreboard
        renderHUD(canvas)
    }

    private fun renderCards(canvas: Canvas) {
        val curP = getCurrentPlayer()

        cards.forEach { card ->
            // Skip cards that have fully flown away
            if (card.isMatched && card.flyProgress >= 1f) return@forEach

            val cx = card.rect.centerX()
            val cy = card.rect.centerY()

            canvas.save()

            // Pop scale on match
            if (card.matchScale > 1f) {
                canvas.scale(card.matchScale, card.matchScale, cx, cy)
            }

            // 3D Horizontal Flip Scale
            // At progress 0 (face down) scale = 1, at 0.5 scale = 0, at 1.0 (face up) scale = 1
            val flipAngle = card.flipProgress * PI.toFloat()
            val horizontalScale = abs(cos(flipAngle)).coerceIn(0.01f, 1f)
            val isFaceVisible = card.flipProgress >= 0.5f

            canvas.scale(horizontalScale, 1f, cx, cy)

            val cornerRad = min(5f * density, cardWidth * 0.16f)
            val outerStroke = 1.2f * density
            val frontBorderStroke = 1.8f * density

            if (!isFaceVisible) {
                // RENDER CARD BACK (Cosmic Mystery Card with glowing ?)
                // Card Body Fill
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#161330")
                canvas.drawRoundRect(card.rect, cornerRad, cornerRad, paint)

                // Neon Outer Border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = outerStroke
                paint.color = Color.parseColor("#362866")
                canvas.drawRoundRect(card.rect, cornerRad, cornerRad, paint)

                // Geometric Diamond Accent
                val dw = cardWidth * 0.44f
                val dh = cardHeight * 0.44f
                val path = Path().apply {
                    moveTo(cx, cy - dh / 2f)
                    lineTo(cx + dw / 2f, cy)
                    lineTo(cx, cy + dh / 2f)
                    lineTo(cx - dw / 2f, cy)
                    close()
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * density
                paint.color = Color.parseColor("#4B368A")
                canvas.drawPath(path, paint)

                // Glowing Mystery "?" Symbol
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#B094FF")
                paint.textSize = cardWidth * 0.42f
                paint.isFakeBoldText = true
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val textY = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText("?", cx, textY, paint)
                paint.isFakeBoldText = false

            } else {
                // RENDER CARD FRONT (Revealed Symbol)
                // Card Front Fill
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#221D48")
                canvas.drawRoundRect(card.rect, cornerRad, cornerRad, paint)

                // Glowing Theme Border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = frontBorderStroke
                paint.color = card.themeColor
                canvas.drawRoundRect(card.rect, cornerRad, cornerRad, paint)

                // Inner Soft Tint
                paint.style = Paint.Style.FILL
                val r = Color.red(card.themeColor)
                val g = Color.green(card.themeColor)
                val b = Color.blue(card.themeColor)
                paint.color = Color.argb(40, r, g, b)
                canvas.drawRoundRect(card.rect, cornerRad, cornerRad, paint)

                // Center Emoji Symbol
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textSize = min(cardWidth * 0.50f, cardHeight * 0.50f)
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val textY = cy - (fm.ascent + fm.descent) / 2f
                canvas.drawText(card.emoji, cx, textY, paint)
            }

            canvas.restore()
        }
    }

    private fun renderHUD(canvas: Canvas) {
        val curP = getCurrentPlayer()
        val curColor = curP?.id?.color ?: Color.parseColor("#FFD54F")

        // 1. Turn Banner / Status Pill (Positioned gracefully below the top "MEMORY" pill)
        val bannerW = 230f * density
        val bannerH = 30f * density
        val bannerY = 74f * density
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
            val t1Score = (pairsClaimed[PlayerId.PLAYER_1] ?: 0) + (pairsClaimed[PlayerId.PLAYER_2] ?: 0)
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
            canvas.drawText("$t1Score Pairs", t1R.left + 10f * density, t1R.centerY() + 15f * density, paint)

            // Team 2 Card
            val t2Score = (pairsClaimed[PlayerId.PLAYER_3] ?: 0) + (pairsClaimed[PlayerId.PLAYER_4] ?: 0)
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
            canvas.drawText("$t2Score Pairs", t2R.left + 10f * density, t2R.centerY() + 15f * density, paint)
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

                // Color indicator dot
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

                val score = pairsClaimed[p.id] ?: 0
                paint.textSize = 12f * density
                paint.color = p.id.color
                canvas.drawText("$score", cardR.left + 18f * density, cardR.centerY() + 14f * density, paint)
                paint.isFakeBoldText = false
            }
        }
    }

    override fun getPlayerScoreString(player: Player): String {
        val count = pairsClaimed[player.id] ?: 0
        return "$count ${if (count == 1) "Pair" else "Pairs"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return pairsClaimed[player.id] ?: 0
    }

    override fun getTeamScoreString(team: Int): String {
        val count = if (team == 1) {
            (pairsClaimed[PlayerId.PLAYER_1] ?: 0) + (pairsClaimed[PlayerId.PLAYER_2] ?: 0)
        } else {
            (pairsClaimed[PlayerId.PLAYER_3] ?: 0) + (pairsClaimed[PlayerId.PLAYER_4] ?: 0)
        }
        return "$count ${if (count == 1) "Pair" else "Pairs"}"
    }
}

