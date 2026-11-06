package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

/**
 * Find Match (Spot It / Dobble) Party Game Engine
 * Reverse-engineered & enhanced from 144_FIND in reference APK.
 *
 * Full support for 1, 2, 3, and 4 players:
 * - 1 Player: Player 1 (Bottom, Red) vs Bot (Top, Blue) -> Exactly 2 circles
 * - 2 Players: Player 1 (Bottom, Red) vs Player 2 (Top, Blue) -> Exactly 2 circles
 * - 3 Players: Player 1 (Bottom), Player 2 (Top-Left), Player 3 (Top-Right) -> Exactly 3 circles
 * - 4 Players: P1 (Bottom-Left), P2 (Top-Left), P3 (Top-Right), P4 (Bottom-Right) -> Exactly 4 circles
 *
 * Exact Dobble Rule across all players:
 * - In every round, exactly ONE universal target symbol is common to ALL players' circles!
 * - All other symbols on every circle are distinct unique distractors!
 * - First player to spot and tap the matching symbol on their circle wins the round!
 *
 * Match Structure:
 * - Exactly 5 intense rounds ("make 5 round then show won popup")
 * - Central HUD showing "ROUND X/5" and live player scores without header overlap.
 * - Lockout cooldown & ❌ penalty on wrong taps.
 * - Starburst particle celebration, zoom pulse & grey-out on correct matches.
 */
class FindMatchEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())

    // 5 Rounds Match
    val maxRounds = 5
    var currentRound = 1
    private var isRoundEnding = false
    private var roundEndDelayTimer = -1f
    private var roundWonTransitionTimer = -1f
    private var pendingWinner: Player? = null
    private var roundWinningPlayer: Player? = null
    private val playerWins = mutableMapOf<PlayerId, Int>()

    // Symbol Item Definition
    data class MatchItem(
        val id: Int,
        val name: String,
        val emoji: String,
        val themeColor: Int
    )

    data class PlacedItem(
        val item: MatchItem,
        var relX: Float, // relative to disc center (-1..1)
        var relY: Float,
        var screenX: Float = 0f,
        var screenY: Float = 0f,
        var touchRadius: Float = 0f,
        var baseScale: Float = 1.0f,
        var rotationDeg: Float = 0f,
        var animScale: Float = 1.0f,
        var isGreyedOut: Boolean = false
    )

    class PlayerDisc(
        val player: Player?,
        var centerX: Float = 0f,
        var centerY: Float = 0f,
        var radius: Float = 0f,
        var rotationDeg: Float = 0f,
        val items: MutableList<PlacedItem> = mutableListOf(),
        var commonItem: MatchItem? = null,
        var cooldownTimer: Float = 0f,
        var entranceSlide: Float = 1.0f
    )

    // Symbol Library (52 colorful, recognizable symbols)
    private val symbolPool = listOf(
        MatchItem(1, "Star", "⭐", Color.parseColor("#FFD700")),
        MatchItem(2, "Diamond", "💎", Color.parseColor("#00E5FF")),
        MatchItem(3, "Fire", "🔥", Color.parseColor("#FF5722")),
        MatchItem(4, "Rocket", "🚀", Color.parseColor("#FF3B30")),
        MatchItem(5, "Ghost", "👻", Color.parseColor("#E0D6FF")),
        MatchItem(6, "Lightning", "⚡", Color.parseColor("#FFEB3B")),
        MatchItem(7, "Crown", "👑", Color.parseColor("#FFB300")),
        MatchItem(8, "Pizza", "🍕", Color.parseColor("#FF9800")),
        MatchItem(9, "Heart", "❤️", Color.parseColor("#FF1744")),
        MatchItem(10, "Bomb", "💣", Color.parseColor("#B0BEC5")),
        MatchItem(11, "Cat", "🐱", Color.parseColor("#FF80AB")),
        MatchItem(12, "Car", "🚗", Color.parseColor("#2979FF")),
        MatchItem(13, "Soccer", "⚽", Color.parseColor("#FFFFFF")),
        MatchItem(14, "Ice Cream", "🍦", Color.parseColor("#FFE082")),
        MatchItem(15, "Apple", "🍎", Color.parseColor("#F44336")),
        MatchItem(16, "Burger", "🍔", Color.parseColor("#FFA000")),
        MatchItem(17, "Gift", "🎁", Color.parseColor("#E91E63")),
        MatchItem(18, "Bell", "🔔", Color.parseColor("#FFD54F")),
        MatchItem(19, "Key", "🔑", Color.parseColor("#FFE082")),
        MatchItem(20, "Anchor", "⚓", Color.parseColor("#80D8FF")),
        MatchItem(21, "Sunglasses", "🕶️", Color.parseColor("#90CAF9")),
        MatchItem(22, "Dice", "🎲", Color.parseColor("#FF5252")),
        MatchItem(23, "Donut", "🍩", Color.parseColor("#F48FB1")),
        MatchItem(24, "Frog", "🐸", Color.parseColor("#00E676")),
        MatchItem(25, "Lollipop", "🍭", Color.parseColor("#FF4081")),
        MatchItem(26, "Rainbow", "🌈", Color.parseColor("#B388FF")),
        MatchItem(27, "Trophy", "🏆", Color.parseColor("#FFC107")),
        MatchItem(28, "Watermelon", "🍉", Color.parseColor("#FF5252")),
        MatchItem(29, "Avocado", "🥑", Color.parseColor("#76FF03")),
        MatchItem(30, "Crab", "🦀", Color.parseColor("#FF6E40")),
        MatchItem(31, "Butterfly", "🦋", Color.parseColor("#40C4FF")),
        MatchItem(32, "Mushroom", "🍄", Color.parseColor("#FF5252")),
        MatchItem(33, "Balloon", "🎈", Color.parseColor("#FF1744")),
        MatchItem(34, "Target", "🎯", Color.parseColor("#FF3D00")),
        MatchItem(35, "Alien", "👽", Color.parseColor("#69F0AE")),
        MatchItem(36, "Clover", "🍀", Color.parseColor("#00E676")),
        MatchItem(37, "Sun", "☀️", Color.parseColor("#FFD600")),
        MatchItem(38, "Moon", "🌙", Color.parseColor("#FFF59D")),
        MatchItem(39, "Guitar", "🎸", Color.parseColor("#FFAB40")),
        MatchItem(40, "Dolphin", "🐬", Color.parseColor("#18FFFF")),
        MatchItem(41, "Palm", "🌴", Color.parseColor("#00E676")),
        MatchItem(42, "Strawberry", "🍓", Color.parseColor("#FF1744")),
        MatchItem(43, "Banana", "🍌", Color.parseColor("#FFEE58")),
        MatchItem(44, "Octopus", "🐙", Color.parseColor("#EA80FC")),
        MatchItem(45, "Bee", "🐝", Color.parseColor("#FFD600")),
        MatchItem(46, "Ladybug", "🐞", Color.parseColor("#FF3D00")),
        MatchItem(47, "Clock", "⏰", Color.parseColor("#FF5252")),
        MatchItem(48, "Shield", "🛡️", Color.parseColor("#82B1FF")),
        MatchItem(49, "Robot", "🤖", Color.parseColor("#CFD8DC")),
        MatchItem(50, "Gem", "🔮", Color.parseColor("#D500F9")),
        MatchItem(51, "Cherry", "🍒", Color.parseColor("#FF1744")),
        MatchItem(52, "Compass", "🧭", Color.parseColor("#FF9100"))
    )

    // Discs in play (Strictly 2, 3, or 4 circles matching the player count)
    private val activeDiscs = mutableListOf<PlayerDisc>()

    // Bot AI state
    private var botReactionCountdown = -1f
    private var botTargetDisc: PlayerDisc? = null
    private var botTargetItem: PlacedItem? = null
    private var botWillMiss = false

    // Funtoza Dark Neon Theme Colors
    private val bgCosmicVoid = Color.parseColor("#090B16")
    private val discChassisBg = Color.parseColor("#101426")
    private val discChassisBorder = Color.parseColor("#252E4D")
    private val hudGlassBg = Color.parseColor("#14182E")
    private val hudGlassBorder = Color.parseColor("#2E385E")

    // Drawing tools
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun calculateControlZones() {
        controlZones.clear() // Custom canvas disc touch zones
    }

    override fun initGame() {
        currentRound = 1
        isGameOver = false
        isRoundEnding = false
        roundEndDelayTimer = -1f
        roundWonTransitionTimer = -1f
        pendingWinner = null
        roundWinningPlayer = null
        playerWins.clear()

        players.forEach {
            it.score = 0
            it.isAlive = true
        }

        if (width > 0f && height > 0f) {
            setupDiscsLayout()
            generateRound()
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        setupDiscsLayout()
        generateRound()
    }

    /**
     * Sizing & Layout for 1, 2, 3, or 4 players:
     * - Clears the top header & pause button with generous safe margin.
     * - Clears the bottom navigation area.
     * - Centers the HUD capsule in the vertical middle between top & bottom circles.
     * - Exactly N circles for N players (2 circles for 2P, 3 circles for 3P, 4 circles for 4P).
     */
    private fun setupDiscsLayout() {
        activeDiscs.clear()

        val topSafeArea = 84f * density
        val bottomSafeArea = 34f * density

        val hudH = 38f * density
        val hudCenterY = (topSafeArea + (height - bottomSafeArea)) * 0.5f

        val halfH = (hudCenterY - hudH * 0.5f - 14f * density) - topSafeArea
        val topHalfCenterY = topSafeArea + halfH * 0.50f
        val bottomHalfCenterY = (height - bottomSafeArea) - halfH * 0.50f

        val pCount = players.size

        if (pCount <= 2) {
            // Exactly 2 Large Discs (Player 1 at Bottom, Player 2 at Top)
            val p1 = players[0]
            val p2 = players.getOrElse(1) { Player(PlayerId.PLAYER_2, isBot = true) }

            val discRadius = min(width * 0.38f, halfH * 0.44f)

            val disc1 = PlayerDisc(
                player = p1,
                centerX = width * 0.5f,
                centerY = bottomHalfCenterY,
                radius = discRadius,
                rotationDeg = 0f
            )

            val disc2 = PlayerDisc(
                player = p2,
                centerX = width * 0.5f,
                centerY = topHalfCenterY,
                radius = discRadius,
                rotationDeg = 180f
            )

            activeDiscs.add(disc1)
            activeDiscs.add(disc2)
        } else if (pCount == 3) {
            // Exactly 3 Discs (NO center disc!):
            // Top row: Player 2 (Left, 180°) and Player 3 (Right, 180°)
            // Bottom: Player 1 (Center, 0°)
            val col1X = width * 0.27f
            val col2X = width * 0.73f
            val discRadiusTop = min(width * 0.215f, halfH * 0.44f)
            val discRadiusBottom = min(width * 0.28f, halfH * 0.44f)

            // P2 Top-Left (facing 180°)
            val disc2 = PlayerDisc(
                player = players[1],
                centerX = col1X,
                centerY = topHalfCenterY,
                radius = discRadiusTop,
                rotationDeg = 180f
            )

            // P3 Top-Right (facing 180°)
            val disc3 = PlayerDisc(
                player = players[2],
                centerX = col2X,
                centerY = topHalfCenterY,
                radius = discRadiusTop,
                rotationDeg = 180f
            )

            // P1 Bottom (facing 0°)
            val disc1 = PlayerDisc(
                player = players[0],
                centerX = width * 0.50f,
                centerY = bottomHalfCenterY,
                radius = discRadiusBottom,
                rotationDeg = 0f
            )

            activeDiscs.add(disc1)
            activeDiscs.add(disc2)
            activeDiscs.add(disc3)
        } else {
            // Exactly 4 Discs (NO center disc! ONLY 4 circles in 4 corners):
            // Top row: Player 2 (Left, 180°) and Player 3 (Right, 180°)
            // Bottom row: Player 1 (Left, 0°) and Player 4 (Right, 0°)
            val col1X = width * 0.27f
            val col2X = width * 0.73f
            val cornerRadius = min(width * 0.215f, halfH * 0.44f)

            // P2: Top-Left (facing 180°)
            val disc2 = PlayerDisc(
                player = players[1],
                centerX = col1X,
                centerY = topHalfCenterY,
                radius = cornerRadius,
                rotationDeg = 180f
            )

            // P3: Top-Right (facing 180°)
            val disc3 = PlayerDisc(
                player = players[2],
                centerX = col2X,
                centerY = topHalfCenterY,
                radius = cornerRadius,
                rotationDeg = 180f
            )

            // P1: Bottom-Left (facing 0°)
            val disc1 = PlayerDisc(
                player = players[0],
                centerX = col1X,
                centerY = bottomHalfCenterY,
                radius = cornerRadius,
                rotationDeg = 0f
            )

            // P4: Bottom-Right (facing 0°)
            val disc4 = PlayerDisc(
                player = players[3],
                centerX = col2X,
                centerY = bottomHalfCenterY,
                radius = cornerRadius,
                rotationDeg = 0f
            )

            activeDiscs.add(disc1)
            activeDiscs.add(disc2)
            activeDiscs.add(disc3)
            activeDiscs.add(disc4)
        }
    }

    /**
     * Generates a round where exactly ONE universal target symbol is common to ALL circles!
     * All remaining items on every circle are distinct unique distractors.
     */
    private fun generateRound() {
        val shuffledPool = symbolPool.shuffled(random).toMutableList()

        // 1 Universal Common Target Item that is present on ALL circles!
        val universalCommon = shuffledPool.removeAt(0)
        val itemsCount = 8

        // Each player disc receives the universal common item + 7 distinct unique distractors
        for (disc in activeDiscs) {
            val pItems = mutableListOf(universalCommon)
            for (i in 0 until (itemsCount - 1)) {
                if (shuffledPool.isNotEmpty()) {
                    pItems.add(shuffledPool.removeAt(0))
                } else {
                    val fallback = symbolPool.filter { it.id != universalCommon.id && !pItems.contains(it) }
                    pItems.add(fallback.random(random))
                }
            }
            populateDiscItems(disc, pItems, universalCommon)
        }

        // Reset disc state & trigger entrance animation
        activeDiscs.forEach {
            it.cooldownTimer = 0f
            it.entranceSlide = 0f
        }

        roundWinningPlayer = null
        roundWonTransitionTimer = -1f

        // Reset Bot AI
        setupBotAIForRound()
    }

    /**
     * Arranges 8 items on a circular disc:
     * - 1 item at the center
     * - 7 items in a ring around the center with jitter & tier sizing
     */
    private fun populateDiscItems(disc: PlayerDisc, items: List<MatchItem>, common: MatchItem) {
        disc.items.clear()
        disc.commonItem = common

        val shuffled = items.shuffled(random)
        val r = disc.radius
        val centerRatio = 0.58f

        // Scales matching APK: Small (0.82), Medium (1.02), Large (1.25)
        val tiers = listOf(0.82f, 1.02f, 1.25f)

        shuffled.forEachIndexed { i, item ->
            val scale = tiers.random(random)
            val rot = (random.nextFloat() * 80f - 40f) // -40..+40 deg

            val relX: Float
            val relY: Float

            if (i == 0) {
                // Center item with slight offset
                relX = (random.nextFloat() * 0.16f - 0.08f) * r
                relY = (random.nextFloat() * 0.16f - 0.08f) * r
            } else {
                // 7 Ring items
                val ringIndex = i - 1
                val baseAngle = (2.0 * PI * ringIndex / 7.0)
                val angleJitter = (random.nextFloat() * 0.26 - 0.13)
                val angle = baseAngle + angleJitter
                val distJitter = (random.nextFloat() * 0.14 - 0.07) * r
                val dist = r * centerRatio + distJitter

                relX = (cos(angle) * dist).toFloat()
                relY = (sin(angle) * dist).toFloat()
            }

            val touchRad = r * 0.19f * scale

            val placed = PlacedItem(
                item = item,
                relX = relX,
                relY = relY,
                touchRadius = touchRad,
                baseScale = scale,
                rotationDeg = rot,
                animScale = 1.0f,
                isGreyedOut = false
            )
            updateScreenCoordinates(disc, placed)
            disc.items.add(placed)
        }
    }

    private fun updateScreenCoordinates(disc: PlayerDisc, placed: PlacedItem) {
        val rad = Math.toRadians(disc.rotationDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val rotX = placed.relX * cosA - placed.relY * sinA
        val rotY = placed.relX * sinA + placed.relY * cosA
        placed.screenX = disc.centerX + rotX
        placed.screenY = disc.centerY + rotY
    }

    private fun setupBotAIForRound() {
        val botPlayer = players.firstOrNull { it.isBot } ?: return
        val botDisc = activeDiscs.firstOrNull { it.player?.id == botPlayer.id } ?: return
        val common = botDisc.commonItem ?: return
        val targetItem = botDisc.items.firstOrNull { it.item.id == common.id } ?: return

        botTargetDisc = botDisc
        botTargetItem = targetItem

        // Delay & accuracy based on bot difficulty
        when (botDifficulty) {
            0 -> {
                // Easy: 3.4s - 4.8s delay, 22% chance of tapping wrong
                botReactionCountdown = 3.4f + random.nextFloat() * 1.4f
                botWillMiss = random.nextFloat() < 0.22f
            }
            1 -> {
                // Medium: 2.2f - 3.1s delay, 9% chance of tapping wrong
                botReactionCountdown = 2.2f + random.nextFloat() * 0.9f
                botWillMiss = random.nextFloat() < 0.09f
            }
            else -> {
                // Hard: 1.35f - 1.85s delay, 2% miss
                botReactionCountdown = 1.35f + random.nextFloat() * 0.5f
                botWillMiss = random.nextFloat() < 0.02f
            }
        }
    }

    override fun update(deltaTime: Float) {
        val dt = deltaTime.coerceIn(0.001f, 0.05f)
        particles.update()

        // Match end victory popup delay
        if (roundEndDelayTimer >= 0f) {
            roundEndDelayTimer -= dt
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                isRoundEnding = false
                pendingWinner?.let {
                    it.score = max(0, (playerWins[it.id] ?: 1) - 1)
                }
                endRound(pendingWinner)
                pendingWinner?.let {
                    it.score = playerWins[it.id] ?: 1
                }
            }
            return
        }

        if (isGameOver || isRoundEnding) return

        // Round won celebration countdown
        if (roundWonTransitionTimer >= 0f) {
            roundWonTransitionTimer -= dt
            if (roundWonTransitionTimer <= 0f) {
                roundWonTransitionTimer = -1f
                if (currentRound < maxRounds) {
                    currentRound++
                    generateRound()
                } else {
                    // 5 Rounds completed! Crown the player with highest score
                    triggerMatchOver()
                }
            }
            return
        }

        // Update disc entrance animations & cooldowns
        activeDiscs.forEach { disc ->
            if (disc.entranceSlide < 1f) {
                disc.entranceSlide = min(1f, disc.entranceSlide + dt * 4.5f)
            }
            if (disc.cooldownTimer > 0f) {
                disc.cooldownTimer = max(0f, disc.cooldownTimer - dt)
            }
        }

        // Bot AI Action
        if (roundWonTransitionTimer < 0f && botReactionCountdown > 0f) {
            botReactionCountdown -= dt
            if (botReactionCountdown <= 0f) {
                executeBotAction()
            }
        }
    }

    private fun executeBotAction() {
        val bDisc = botTargetDisc ?: return
        val bItem = botTargetItem ?: return
        if (bDisc.cooldownTimer > 0f || roundWonTransitionTimer >= 0f) return

        if (botWillMiss) {
            // Bot taps a wrong item
            val wrongItem = bDisc.items.firstOrNull { it.item.id != bDisc.commonItem?.id }
            if (wrongItem != null) {
                triggerWrongSelection(bDisc)
                // Bot recovers after penalty
                botReactionCountdown = 1.4f + random.nextFloat() * 1.0f
                botWillMiss = false
            }
        } else {
            // Bot correctly taps the common item!
            triggerCorrectSelection(bDisc, bItem)
        }
    }

    private fun triggerMatchOver() {
        isRoundEnding = true
        // Find player with highest score
        val sortedPlayers = players.sortedByDescending { it.score }
        val topScore = sortedPlayers.firstOrNull()?.score ?: 0
        val winners = sortedPlayers.filter { it.score == topScore }

        val matchWinner = if (winners.size == 1) winners.first() else null
        pendingWinner = matchWinner

        soundManager.play(SoundManager.SoundType.VICTORY)
        triggerVibration(120)
        particles.spawnExplosion(width * 0.5f, height * 0.5f, matchWinner?.id?.color ?: Color.parseColor("#FFD700"), 75)

        roundEndDelayTimer = 1.8f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isRoundEnding || roundWonTransitionTimer >= 0f) return true

        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                handleTouch(x, y)
            }
        }
        return true
    }

    private fun handleTouch(x: Float, y: Float) {
        // Find which player disc was tapped
        for (disc in activeDiscs) {
            // Bot discs cannot be touched by humans
            if (disc.player?.isBot == true) continue

            val distToDisc = hypot(x - disc.centerX, y - disc.centerY)
            if (distToDisc <= disc.radius * 1.08f) {
                // Player tapped inside their disc!
                if (disc.cooldownTimer > 0f) {
                    // Player is in cooldown lockout!
                    soundManager.play(SoundManager.SoundType.BOUNCE)
                    triggerVibration(25)
                    return
                }

                // Check which item was tapped
                var tappedItem: PlacedItem? = null
                for (item in disc.items) {
                    val distToItem = hypot(x - item.screenX, y - item.screenY)
                    if (distToItem <= item.touchRadius * 1.35f) {
                        tappedItem = item
                        break
                    }
                }

                if (tappedItem != null) {
                    if (tappedItem.item.id == disc.commonItem?.id) {
                        // Correct match tapped!
                        triggerCorrectSelection(disc, tappedItem)
                    } else {
                        // Wrong item tapped!
                        triggerWrongSelection(disc)
                    }
                }
                return
            }
        }
    }

    private fun triggerCorrectSelection(disc: PlayerDisc, item: PlacedItem) {
        val player = disc.player ?: return
        roundWinningPlayer = player
        playerWins[player.id] = (playerWins[player.id] ?: 0) + 1
        player.score = playerWins[player.id] ?: 1

        soundManager.play(SoundManager.SoundType.GOAL)
        triggerVibration(60)

        // Spawn sparkling stars celebration in player neon color
        particles.spawnExplosion(item.screenX, item.screenY, player.id.color, 50)

        // Expand matching item on all discs; grey out all distractors
        activeDiscs.forEach { d ->
            d.items.forEach { itm ->
                if (itm.item.id == disc.commonItem?.id) {
                    itm.animScale = 1.4f
                    itm.isGreyedOut = false
                } else {
                    itm.isGreyedOut = true
                }
            }
        }

        roundWonTransitionTimer = 1.35f
    }

    private fun triggerWrongSelection(disc: PlayerDisc) {
        disc.cooldownTimer = 1.3f
        soundManager.play(SoundManager.SoundType.BOUNCE)
        triggerVibration(40)
    }

    override fun render(canvas: Canvas) {
        // 1. Deep Cosmic Void Background
        canvas.drawColor(bgCosmicVoid)
        renderAmbientGrid(canvas)

        // 2. Render Discs (Exactly 2, 3, or 4 circles)
        activeDiscs.forEach { renderDisc(canvas, it) }

        // 3. Render Center HUD Capsule (Round & Scores)
        renderHUD(canvas)

        // 4. Round Won Announcement Banner
        if (roundWonTransitionTimer > 0f) {
            renderRoundWonBanner(canvas)
        }

        // 5. Particles
        particles.draw(canvas)
    }

    private fun renderAmbientGrid(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#151A2E")
        val cols = 8
        val rows = 16
        val stepX = width / cols
        val stepY = height / rows

        for (i in 0 until cols) {
            for (j in 0 until rows) {
                canvas.drawCircle(i * stepX + stepX * 0.5f, j * stepY + stepY * 0.5f, 1.4f * density, paint)
            }
        }
    }

    private fun renderDisc(canvas: Canvas, disc: PlayerDisc) {
        val cx = disc.centerX
        val cy = disc.centerY
        val r = disc.radius * (0.85f + 0.15f * disc.entranceSlide)
        val player = disc.player
        val borderColor = player?.id?.color ?: Color.WHITE

        // 1. Frosted Circular Chassis
        paint.style = Paint.Style.FILL
        paint.color = discChassisBg
        canvas.drawCircle(cx, cy, r, paint)

        // 2. Glowing Neon Ring Border
        paint.style = Paint.Style.STROKE
        val isCooldown = disc.cooldownTimer > 0f
        if (isCooldown) {
            paint.strokeWidth = 2.5f * density
            paint.color = Color.parseColor("#FF1744")
        } else {
            paint.strokeWidth = 2.0f * density
            paint.color = borderColor
        }
        canvas.drawCircle(cx, cy, r, paint)

        // Inner subtle rim
        paint.strokeWidth = 1.0f * density
        paint.color = discChassisBorder
        canvas.drawCircle(cx, cy, r - 3.5f * density, paint)

        // 3. Render Placed Items inside Disc
        disc.items.forEach { placed ->
            updateScreenCoordinates(disc, placed)
            renderPlacedItem(canvas, disc, placed)
        }

        // 4. Player Indicator Pill at Disc Rim
        if (player != null) {
            renderPlayerTag(canvas, disc, player)
        }

        // 5. Wrong Selection ❌ Lockout Overlay
        if (disc.cooldownTimer > 0f) {
            renderCooldownOverlay(canvas, disc)
        }
    }

    private fun renderPlacedItem(canvas: Canvas, disc: PlayerDisc, placed: PlacedItem) {
        val sx = placed.screenX
        val sy = placed.screenY
        val scale = placed.baseScale * placed.animScale

        canvas.save()
        canvas.translate(sx, sy)
        canvas.rotate(disc.rotationDeg + placed.rotationDeg)
        canvas.scale(scale, scale)

        val textSize = disc.radius * 0.32f

        // Matching Highlight Ring
        if (placed.animScale > 1.05f) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(90, 0, 229, 255)
            canvas.drawCircle(0f, 0f, textSize * 0.65f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * density
            paint.color = Color.parseColor("#00E5FF")
            canvas.drawCircle(0f, 0f, textSize * 0.65f, paint)
        }

        // Symbol Emoji
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = textSize
        textPaint.alpha = if (placed.isGreyedOut) 60 else 255

        val fm = textPaint.fontMetrics
        val textY = -(fm.ascent + fm.descent) / 2f
        canvas.drawText(placed.item.emoji, 0f, textY, textPaint)

        textPaint.alpha = 255
        canvas.restore()
    }

    private fun renderPlayerTag(canvas: Canvas, disc: PlayerDisc, player: Player) {
        canvas.save()
        val tagW = 74f * density
        val tagH = 20f * density
        val isFlipped = (disc.rotationDeg > 90f)

        // For top player (facing top), tag is right above their disc rim (towards the top edge, safely below header)
        // For bottom player (facing bottom), tag is right below their disc rim (towards the bottom edge)
        val tagY = if (isFlipped) {
            max(84f * density + tagH * 0.5f, disc.centerY - disc.radius - 9f * density)
        } else {
            min(height - 18f * density - tagH * 0.5f, disc.centerY + disc.radius + 9f * density)
        }

        val tagRect = RectF(
            disc.centerX - tagW * 0.5f,
            tagY - tagH * 0.5f,
            disc.centerX + tagW * 0.5f,
            tagY + tagH * 0.5f
        )

        // Glass capsule
        paint.style = Paint.Style.FILL
        paint.color = hudGlassBg
        canvas.drawRoundRect(tagRect, tagH * 0.5f, tagH * 0.5f, paint)

        // Neon border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = player.id.color
        canvas.drawRoundRect(tagRect, tagH * 0.5f, tagH * 0.5f, paint)

        // Player Tag Text
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 9.5f * density
        textPaint.color = Color.WHITE
        val ty = tagRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        val pName = if (player.isBot) "BOT" else getPlayerDisplayName(player).uppercase()
        canvas.drawText("$pName (${player.score})", tagRect.centerX(), ty, textPaint)

        canvas.restore()
    }

    private fun renderCooldownOverlay(canvas: Canvas, disc: PlayerDisc) {
        val cx = disc.centerX
        val cy = disc.centerY
        val r = disc.radius

        // Dim overlay
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(140, 20, 10, 15)
        canvas.drawCircle(cx, cy, r, paint)

        // Bold Glowing ❌ Cross
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = disc.radius * 0.44f
        textPaint.color = Color.parseColor("#FF1744")
        val ty = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("❌", cx, ty, textPaint)

        // Lock text
        textPaint.textSize = 12f * density
        textPaint.color = Color.WHITE
        canvas.drawText("LOCKED!", cx, cy + disc.radius * 0.32f, textPaint)
    }

    private fun renderHUD(canvas: Canvas) {
        val topSafeArea = 84f * density
        val bottomSafeArea = 34f * density
        val hudCenterY = (topSafeArea + (height - bottomSafeArea)) * 0.5f

        val hudW = min(width * 0.90f, 330f * density)
        val hudH = 38f * density
        val hudX = (width - hudW) / 2f
        val hudY = hudCenterY - hudH * 0.5f

        val hudRect = RectF(hudX, hudY, hudX + hudW, hudY + hudH)

        // Frosted Glass Chassis
        paint.style = Paint.Style.FILL
        paint.color = hudGlassBg
        canvas.drawRoundRect(hudRect, 19f * density, 19f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.3f * density
        paint.color = hudGlassBorder
        canvas.drawRoundRect(hudRect, 19f * density, 19f * density, paint)

        // Left: Round Capsule
        val roundBadgeW = 84f * density
        val roundBadgeRect = RectF(
            hudRect.left + 5f * density,
            hudRect.top + 5f * density,
            hudRect.left + 5f * density + roundBadgeW,
            hudRect.bottom - 5f * density
        )
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B223E")
        canvas.drawRoundRect(roundBadgeRect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(roundBadgeRect, 14f * density, 14f * density, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f * density
        textPaint.color = Color.parseColor("#00E5FF")
        var ty = roundBadgeRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("ROUND $currentRound/$maxRounds", roundBadgeRect.centerX(), ty, textPaint)

        // Right: Score Pills for each active player
        val scoresLeft = roundBadgeRect.right + 10f * density
        val availScoreW = hudRect.right - 6f * density - scoresLeft
        val pCount = players.size
        val pillSpacing = 5f * density
        val pillW = (availScoreW - (pCount - 1) * pillSpacing) / pCount
        val pillH = hudRect.height() - 12f * density

        val maxScore = players.maxOfOrNull { it.score } ?: 0

        players.forEachIndexed { i, p ->
            val pl = scoresLeft + i * (pillW + pillSpacing)
            val pt = hudRect.top + 6f * density
            val pillRect = RectF(pl, pt, pl + pillW, pt + pillH)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#161A2D")
            canvas.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = p.id.color
            canvas.drawRoundRect(pillRect, pillH * 0.5f, pillH * 0.5f, paint)

            textPaint.textSize = 11.5f * density
            textPaint.color = Color.WHITE
            ty = pillRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f

            val crown = if (p.score == maxScore && maxScore > 0) "👑 " else ""
            canvas.drawText("$crown${p.score}", pillRect.centerX(), ty, textPaint)
        }
    }

    private fun renderRoundWonBanner(canvas: Canvas) {
        val winner = roundWinningPlayer ?: return
        val bannerW = min(width * 0.82f, 300f * density)
        val bannerH = 46f * density
        val bannerX = (width - bannerW) * 0.5f
        val bannerY = height * 0.50f - bannerH * 0.5f
        val rect = RectF(bannerX, bannerY, bannerX + bannerW, bannerY + bannerH)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E60A0D1A")
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = winner.id.color
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f * density
        textPaint.color = Color.WHITE
        val ty = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
        val wName = if (winner.isBot) "BOT" else getPlayerDisplayName(winner).uppercase()
        canvas.drawText("🎉 $wName SCORED! (+1)", rect.centerX(), ty, textPaint)
    }

    override fun getPlayerScoreString(player: Player): String {
        val wins = playerWins[player.id] ?: 0
        return "$wins / $maxRounds ${if (wins == 1) "Round" else "Rounds"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return playerWins[player.id] ?: 0
    }

    override fun shouldShowPlayerScoresOnWinnerDialog(): Boolean = true
}
