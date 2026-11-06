package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

enum class OopsPawnLocation {
    IN_START,
    ON_RING,
    IN_LANE,
    IN_CASTLE
}

data class OopsPawn(
    val id: Int,
    val owner: PlayerId,
    var location: OopsPawnLocation = OopsPawnLocation.IN_START,
    var ringIndex: Int = -1,     // 0..59 on outer ring
    var laneIndex: Int = -1,     // 0..4 in colored safe lane
    var visualPos: PointF = PointF(),
    var isEligible: Boolean = false
)

enum class OopsCardType {
    FORWARD,
    BACKWARD,
    SWITCH,
    OOPS
}

data class OopsCard(
    val type: OopsCardType,
    val value: Int,
    val title: String,
    val subtitle: String,
    val badge: String
)

data class OopsSlide(
    val owner: PlayerId,
    val startIndex: Int,
    val length: Int // e.g. 3 or 4 tiles
) {
    val endIndex: Int get() = (startIndex + length) % 60

    fun containsIndex(idx: Int): Boolean {
        for (i in 0..length) {
            if ((startIndex + i) % 60 == idx) return true
        }
        return false
    }

    fun isStart(idx: Int): Boolean = idx == startIndex
}

enum class OopsPhase {
    WAITING_DRAW,
    CARD_REVEAL,
    CHOOSING_11_OPTION,
    CHOOSING_10_OPTION,
    CHOOSING_PAWN,
    CHOOSING_SWITCH_OWN,
    CHOOSING_SWITCH_OPPONENT,
    CHOOSING_OOPS_TARGET,
    ANIMATING,
    TURN_ENDING
}

class OopsEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val PAWNS_PER_PLAYER = 4

    // 16x16 Grid Board Geometry
    private val gridCells = 16
    private var boardSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var cellSize = 0f

    // Target Blitz HUD geometry
    private var cardH = 48f * density
    private var cardW = 0f
    private var topCardTop = 0f
    private var bottomCardTop = 0f

    // Dynamic Action / Draw button
    private val bottomActionRect = RectF()
    private val topActionRect = RectF()

    // Interactive Card Choice Buttons (e.g. Card 11 or Card 10 options)
    private val choiceBtn1Rect = RectF()
    private val choiceBtn2Rect = RectF()
    private var chosenMoveDelta: Int? = null

    // 60-tile Outer Ring Coordinates (0..59 clockwise)
    private val ringGridPoints = mutableListOf<Point>()

    // Safe Lanes per player (5 tiles each, heading inward)
    private val safeLanes = mutableMapOf<PlayerId, List<Point>>()

    // Castle Goals per player
    private val castleGoals = mutableMapOf<PlayerId, Point>()

    // Start Base pedestal slots per player (3 pawns each)
    private val startSlots = mutableMapOf<PlayerId, List<Point>>()

    // Start ring entry index for each player (clockwise around perimeter)
    private val startRingEntry = mapOf(
        PlayerId.PLAYER_1 to 4,   // Bottom side (moves Left)
        PlayerId.PLAYER_3 to 19,  // Left side (moves Up)
        PlayerId.PLAYER_2 to 34,  // Top side (moves Right)
        PlayerId.PLAYER_4 to 49   // Right side (moves Down)
    )

    // Safe lane entry index for each player (where they turn into lane)
    private val safeLaneEntry = mapOf(
        PlayerId.PLAYER_1 to 2,
        PlayerId.PLAYER_3 to 17,
        PlayerId.PLAYER_2 to 32,
        PlayerId.PLAYER_4 to 47
    )

    // 8 Slides in clockwise perimeter order (2 per side: 1 short of length 3, 1 long of length 4)
    private val slides = listOf(
        // Bottom (Red / P1)
        OopsSlide(PlayerId.PLAYER_1, 1, 3),
        OopsSlide(PlayerId.PLAYER_1, 9, 4),
        // Left (Green / P3)
        OopsSlide(PlayerId.PLAYER_3, 16, 3),
        OopsSlide(PlayerId.PLAYER_3, 24, 4),
        // Top (Blue / P2)
        OopsSlide(PlayerId.PLAYER_2, 31, 3),
        OopsSlide(PlayerId.PLAYER_2, 39, 4),
        // Right (Yellow / P4)
        OopsSlide(PlayerId.PLAYER_4, 46, 3),
        OopsSlide(PlayerId.PLAYER_4, 54, 4)
    )

    // Game State & Components
    private val pawns = mutableListOf<OopsPawn>()
    private val drawDeck = mutableListOf<OopsCard>()
    private val discardPile = mutableListOf<OopsCard>()

    private var currentTurnIndex = 0
    private var phase = OopsPhase.WAITING_DRAW
    private var currentCard: OopsCard? = null

    // Feedback & Messages
    private var feedbackMessage = ""
    private var feedbackTimer = 0f

    // Movement Animation
    private var movingPawn: OopsPawn? = null
    private val movePath = mutableListOf<PointF>()
    private var hopTimer = 0f
    private val hopDuration = 0.14f
    private var hopStart = PointF()
    private var hopEnd = PointF()
    private var pendingKnockbacks = mutableListOf<OopsPawn>()
    private var pendingSlide: OopsSlide? = null
    private var onAnimationComplete: (() -> Unit)? = null

    // Interaction Selection State
    private var selectedOwnPawn: OopsPawn? = null

    // Timers
    private var botThinkTimer = 0f
    private var pulseTimer = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    init {
        buildGridCoordinates()
    }

    private fun buildGridCoordinates() {
        ringGridPoints.clear()

        // 1. Bottom row (Right to Left): tiles 0..14 (moves LEFT clockwise)
        for (x in 15 downTo 1) ringGridPoints.add(Point(x, 15))
        // 2. Left col (Bottom to Top): tiles 15..29 (moves UP clockwise)
        for (y in 15 downTo 1) ringGridPoints.add(Point(0, y))
        // 3. Top row (Left to Right): tiles 30..44 (moves RIGHT clockwise)
        for (x in 0..14) ringGridPoints.add(Point(x, 0))
        // 4. Right col (Top to Bottom): tiles 45..59 (moves DOWN clockwise)
        for (y in 0..14) ringGridPoints.add(Point(15, y))

        // Safe Lanes (5 steps inward towards Castle)
        safeLanes[PlayerId.PLAYER_1] = (14 downTo 10).map { y -> Point(13, y) }
        safeLanes[PlayerId.PLAYER_3] = (1..5).map { x -> Point(x, 13) }
        safeLanes[PlayerId.PLAYER_2] = (1..5).map { y -> Point(2, y) }
        safeLanes[PlayerId.PLAYER_4] = (14 downTo 10).map { x -> Point(x, 2) }

        // Castle Goals
        castleGoals[PlayerId.PLAYER_1] = Point(13, 9)
        castleGoals[PlayerId.PLAYER_3] = Point(6, 13)
        castleGoals[PlayerId.PLAYER_2] = Point(2, 6)
        castleGoals[PlayerId.PLAYER_4] = Point(9, 2)

        // Start Base Pedestals (4 pawns per player in a clean 2x2 grid, separated from running space & safe lanes)
        startSlots[PlayerId.PLAYER_1] = listOf(Point(10, 12), Point(11, 12), Point(10, 13), Point(11, 13))
        startSlots[PlayerId.PLAYER_3] = listOf(Point(2, 10), Point(3, 10), Point(2, 11), Point(3, 11))
        startSlots[PlayerId.PLAYER_2] = listOf(Point(4, 2), Point(5, 2), Point(4, 3), Point(5, 3))
        startSlots[PlayerId.PLAYER_4] = listOf(Point(12, 4), Point(13, 4), Point(12, 5), Point(13, 5))
    }

    override fun initGame() {
        pawns.clear()
        isGameOver = false
        phase = OopsPhase.WAITING_DRAW
        currentTurnIndex = 0
        currentCard = null
        selectedOwnPawn = null
        chosenMoveDelta = null
        movingPawn = null
        pendingSlide = null
        pendingKnockbacks.clear()
        movePath.clear()
        feedbackTimer = 0f
        feedbackMessage = ""

        // Initialize 3 pawns for each active player
        players.forEach { p ->
            p.score = 0
            p.isAlive = true
            for (i in 0 until PAWNS_PER_PLAYER) {
                pawns.add(OopsPawn(id = i, owner = p.id))
            }
        }

        buildCardDeck()
        recomputeBoardGeometry()

        pawns.forEach { pawn ->
            pawn.visualPos = getPawnSlotPoint(pawn)
        }

        val firstP = getCurrentPlayer()
        if (firstP != null && firstP.isBot) {
            botThinkTimer = getBotDelay()
        }
    }

    private fun buildCardDeck() {
        drawDeck.clear()
        discardPile.clear()

        val fullDeck = mutableListOf<OopsCard>()

        // 1 (5x): Out of Start OR +1
        repeat(5) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 1, "1", "+1 OR START", "START / +1")) }
        // 2 (4x): Out of Start OR +2 (DRAW AGAIN!)
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 2, "2", "+2 OR START (DRAW AGAIN)", "DRAW AGAIN!")) }
        // 3 (4x): +3
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 3, "3", "+3 FORWARD", "+3")) }
        // 4 (4x): -4 BACKWARD
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.BACKWARD, 4, "4", "-4 BACKWARD ↩️", "-4 BACK")) }
        // 5 (4x): +5
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 5, "5", "+5 FORWARD", "+5")) }
        // 7 (4x): +7
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 7, "7", "+7 FORWARD", "+7")) }
        // 8 (4x): +8
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 8, "8", "+8 FORWARD", "+8")) }
        // 10 (4x): +10 OR -1
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 10, "10", "+10 OR -1", "+10 / -1")) }
        // 11 (4x): +11 OR SWAP
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.SWITCH, 11, "11", "+11 OR SWAP 🔀", "SWAP / +11")) }
        // 12 (4x): +12
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.FORWARD, 12, "12", "+12 FORWARD", "+12")) }
        // OOPS! (4x): Take opponent spot from Start!
        repeat(4) { fullDeck.add(OopsCard(OopsCardType.OOPS, 0, "OOPS!", "BUMP TO START 💥", "OOPS!")) }

        fullDeck.shuffle(random)
        drawDeck.addAll(fullDeck)
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        recomputeBoardGeometry()
        pawns.forEach { pawn ->
            pawn.visualPos = getPawnSlotPoint(pawn)
        }
    }

    private fun recomputeBoardGeometry() {
        if (width <= 0f || height <= 0f) return

        val topHudMargin = 72f * density
        val bottomMargin = 12f * density
        cardH = if (totalPlayers <= 2) 48f * density else 42f * density
        cardW = (width - 20f * density).coerceAtMost(500f * density)

        topCardTop = topHudMargin + 2f * density
        val topCardBottom = topCardTop + cardH

        val bottomCardBottom = height - bottomMargin
        bottomCardTop = bottomCardBottom - cardH

        val middleSpace = bottomCardTop - topCardBottom
        val actionH = 44f * density
        val actionW = 168f * density

        // Board expands dynamically to fill almost the entire width
        val maxBoardH = middleSpace - (actionH * 2f + 16f * density)
        val maxBoardW = width - 10f * density
        boardSize = min(maxBoardW, maxBoardH).coerceAtLeast(240f * density)
        cellSize = boardSize / gridCells.toFloat()

        boardLeft = (width - boardSize) / 2f
        boardTop = (topCardBottom + bottomCardTop - boardSize) / 2f
        val boardBottom = boardTop + boardSize

        // Top & Bottom Action Buttons
        val topActionY = (topCardBottom + boardTop) / 2f
        topActionRect.set(width / 2f - actionW / 2f, topActionY - actionH / 2f, width / 2f + actionW / 2f, topActionY + actionH / 2f)

        val bottomActionY = (boardBottom + bottomCardTop) / 2f
        bottomActionRect.set(width / 2f - actionW / 2f, bottomActionY - actionH / 2f, width / 2f + actionW / 2f, bottomActionY + actionH / 2f)
    }

    private val turnOrder: List<PlayerId>
        get() = when (totalPlayers) {
            4 -> listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_3, PlayerId.PLAYER_2, PlayerId.PLAYER_4)
            3 -> listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_3, PlayerId.PLAYER_2)
            else -> listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2)
        }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        val order = turnOrder
        val targetId = order[currentTurnIndex % order.size]
        return players.firstOrNull { it.id == targetId }
    }

    private fun isPlayerAtBottom(player: Player): Boolean {
        return player.id == PlayerId.PLAYER_1 || player.id == PlayerId.PLAYER_4
    }

    private fun getBotDelay(): Float = when (botDifficulty) {
        0 -> 0.85f
        2 -> 0.42f
        else -> 0.62f
    }

    private fun getActiveActionInfo(): Pair<RectF, Float> {
        val activeP = getCurrentPlayer() ?: return Pair(bottomActionRect, 0f)
        val isBottom = isPlayerAtBottom(activeP)
        return if (isBottom) {
            Pair(bottomActionRect, 0f)
        } else {
            val rot = if (activeP.isBot) 0f else 180f
            Pair(topActionRect, rot)
        }
    }

    /**
     * Map pawn location to screen coordinates (X, Y)
     */
    private fun getPawnSlotPoint(pawn: OopsPawn): PointF {
        val pt = when (pawn.location) {
            OopsPawnLocation.IN_START -> {
                val slots = startSlots[pawn.owner] ?: return PointF(boardLeft, boardTop)
                slots.getOrElse(pawn.id) { Point(1, 1) }
            }
            OopsPawnLocation.ON_RING -> {
                ringGridPoints.getOrElse(pawn.ringIndex.coerceIn(0, 59)) { Point(0, 0) }
            }
            OopsPawnLocation.IN_LANE -> {
                val lane = safeLanes[pawn.owner] ?: return PointF(boardLeft, boardTop)
                lane.getOrElse(pawn.laneIndex.coerceIn(0, 4)) { Point(0, 0) }
            }
            OopsPawnLocation.IN_CASTLE -> {
                castleGoals[pawn.owner] ?: return PointF(boardLeft, boardTop)
            }
        }

        var cx = boardLeft + pt.x * cellSize + cellSize / 2f
        var cy = boardTop + pt.y * cellSize + cellSize / 2f

        // Overlapping cluster offset for multiple pawns sharing Castle or Star
        if (pawn.location == OopsPawnLocation.IN_CASTLE) {
            val offset = cellSize * 0.18f
            when (pawn.id) {
                0 -> { cx -= offset; cy -= offset }
                1 -> { cx += offset; cy -= offset }
                2 -> { cx -= offset; cy += offset }
                3 -> { cx += offset; cy += offset }
            }
        }

        return PointF(cx, cy)
    }

    override fun update(deltaTime: Float) {
        pulseTimer += deltaTime

        if (feedbackTimer > 0f) {
            feedbackTimer -= deltaTime
            if (feedbackTimer <= 0f) {
                if (phase == OopsPhase.CARD_REVEAL) {
                    finishTurn()
                }
            }
            particles.update()
            return
        }

        // Hop Movement Animation
        if (phase == OopsPhase.ANIMATING && movingPawn != null) {
            hopTimer -= deltaTime
            val p = movingPawn!!
            val t = (1f - (hopTimer / hopDuration)).coerceIn(0f, 1f)

            // Parabolic hop
            val curX = hopStart.x + (hopEnd.x - hopStart.x) * t
            val curY = hopStart.y + (hopEnd.y - hopStart.y) * t
            val hopH = sin(t * Math.PI).toFloat() * 10f * density
            p.visualPos.set(curX, curY - hopH)

            if (hopTimer <= 0f) {
                p.visualPos.set(hopEnd.x, hopEnd.y)
                soundManager.play(SoundManager.SoundType.TAP, 0.6f)

                if (movePath.isNotEmpty()) {
                    hopStart.set(hopEnd.x, hopEnd.y)
                    hopEnd = movePath.removeAt(0)
                    hopTimer = hopDuration
                } else {
                    movingPawn = null
                    val cb = onAnimationComplete
                    onAnimationComplete = null
                    cb?.invoke()

                    // Check slide / sweep trigger
                    if (pendingSlide != null) {
                        applySlideSweep(p, pendingSlide!!)
                        pendingSlide = null
                    } else {
                        // Apply pending knockbacks
                        resolveKnockbacks()
                        checkWinOrFinishTurn()
                    }
                }
            }
            particles.update()
            return
        }

        // Bot Automation
        val activeP = getCurrentPlayer()
        if (activeP != null && activeP.isBot && !isGameOver && feedbackTimer <= 0f) {
            botThinkTimer -= deltaTime
            if (botThinkTimer <= 0f) {
                when (phase) {
                    OopsPhase.WAITING_DRAW -> triggerDrawCard()
                    OopsPhase.CHOOSING_11_OPTION,
                    OopsPhase.CHOOSING_10_OPTION,
                    OopsPhase.CHOOSING_SWITCH_OWN,
                    OopsPhase.CHOOSING_PAWN,
                    OopsPhase.CHOOSING_SWITCH_OPPONENT,
                    OopsPhase.CHOOSING_OOPS_TARGET -> botExecuteBestMove()
                    else -> {}
                }
            }
        }

        particles.update()
    }

    private fun triggerDrawCard() {
        if (phase != OopsPhase.WAITING_DRAW || isGameOver) return

        if (drawDeck.isEmpty()) {
            drawDeck.addAll(discardPile.shuffled(random))
            discardPile.clear()
        }

        if (drawDeck.isEmpty()) {
            buildCardDeck()
        }

        val card = drawDeck.removeAt(0)
        currentCard = card
        discardPile.add(card)

        soundManager.play(SoundManager.SoundType.TAP, 0.9f)
        triggerVibration(40)

        phase = OopsPhase.CARD_REVEAL
        processDrawnCard(card)
    }

    private fun processDrawnCard(card: OopsCard) {
        val activeP = getCurrentPlayer() ?: return
        val myPawns = pawns.filter { it.owner == activeP.id }

        when (card.type) {
            OopsCardType.OOPS -> {
                // Must have at least 1 pawn in Start, and at least 1 opponent pawn on ring
                val pawnInStart = myPawns.firstOrNull { it.location == OopsPawnLocation.IN_START }
                val opponentOnRing = pawns.filter { 
                    val isEnemy = if (isTeamMode) getPlayerTeam(it.owner) != getPlayerTeam(activeP.id) else it.owner != activeP.id
                    isEnemy && it.location == OopsPawnLocation.ON_RING 
                }

                if (pawnInStart != null && opponentOnRing.isNotEmpty()) {
                    clearEligibility()
                    opponentOnRing.forEach { it.isEligible = true }
                    phase = OopsPhase.CHOOSING_OOPS_TARGET
                    selectedOwnPawn = pawnInStart
                    if (activeP.isBot) botThinkTimer = getBotDelay()
                } else {
                    if (pawnInStart == null) {
                        noMovesToast("NO PAWNS IN START FOR OOPS!")
                    } else {
                        noMovesToast("NO OPPONENT TARGETS ON RING!")
                    }
                }
            }

            OopsCardType.SWITCH -> {
                // Card 11: ALWAYS present choice (+11 OR SWAP) if any legal action exists!
                val ownOnRing = myPawns.filter { it.location == OopsPawnLocation.ON_RING }
                val opponentsOnRing = pawns.filter { 
                    val isEnemy = if (isTeamMode) getPlayerTeam(it.owner) != getPlayerTeam(activeP.id) else it.owner != activeP.id
                    isEnemy && it.location == OopsPawnLocation.ON_RING 
                }
                val canSwap = ownOnRing.isNotEmpty() && opponentsOnRing.isNotEmpty()
                val canMove11 = myPawns.any { canPawnMoveForward(it, 11) }

                if (canSwap || canMove11) {
                    clearEligibility()
                    phase = OopsPhase.CHOOSING_11_OPTION
                    if (activeP.isBot) botThinkTimer = getBotDelay()
                } else {
                    if (myPawns.all { it.location == OopsPawnLocation.IN_START }) {
                        noMovesToast("NEEDS 1 OR 2 TO LEAVE START!")
                    } else {
                        noMovesToast("NO LEGAL MOVES FOR 11 OR SWAP!")
                    }
                }
            }

            OopsCardType.BACKWARD -> {
                val movable = myPawns.filter { canPawnMoveBackward(it, card.value) }
                if (movable.isNotEmpty()) {
                    clearEligibility()
                    movable.forEach { it.isEligible = true }
                    phase = OopsPhase.CHOOSING_PAWN
                    if (activeP.isBot) botThinkTimer = getBotDelay()
                } else {
                    if (myPawns.all { it.location == OopsPawnLocation.IN_START }) {
                        noMovesToast("NEEDS 1 OR 2 TO LEAVE START!")
                    } else {
                        noMovesToast("NO LEGAL MOVES!")
                    }
                }
            }

            OopsCardType.FORWARD -> {
                if (card.value == 10) {
                    // Card 10: ALWAYS present choice (+10 OR -1) if any legal action exists!
                    val canMove10 = myPawns.any { canPawnMoveForward(it, 10) }
                    val canMoveMinus1 = myPawns.any { canPawnMoveBackward(it, 1) }

                    if (canMove10 || canMoveMinus1) {
                        clearEligibility()
                        phase = OopsPhase.CHOOSING_10_OPTION
                        if (activeP.isBot) botThinkTimer = getBotDelay()
                    } else {
                        if (myPawns.all { it.location == OopsPawnLocation.IN_START }) {
                            noMovesToast("NEEDS 1 OR 2 TO LEAVE START!")
                        } else {
                            noMovesToast("NO LEGAL MOVES FOR 10 OR -1!")
                        }
                    }
                } else {
                    val movable = myPawns.filter { canPawnMoveForward(it, card.value) }
                    if (movable.isNotEmpty()) {
                        clearEligibility()
                        movable.forEach { it.isEligible = true }
                        phase = OopsPhase.CHOOSING_PAWN
                        if (activeP.isBot) botThinkTimer = getBotDelay()
                    } else {
                        if (myPawns.all { it.location == OopsPawnLocation.IN_START }) {
                            noMovesToast("NEEDS 1 OR 2 TO LEAVE START!")
                        } else {
                            noMovesToast("NO LEGAL MOVES!")
                        }
                    }
                }
            }
        }
    }

    private fun noMovesToast(reason: String = "NO MOVES POSSIBLE!") {
        feedbackMessage = reason
        feedbackTimer = 1.3f
        soundManager.play(SoundManager.SoundType.BOUNCE, 0.6f)
        triggerVibration(25)
    }

    private fun canPawnMoveForward(pawn: OopsPawn, steps: Int): Boolean {
        if (pawn.location == OopsPawnLocation.IN_CASTLE) return false

        // Card 1 and Card 2 can leave Start!
        if (pawn.location == OopsPawnLocation.IN_START) {
            if (steps == 1 || steps == 2) {
                val entryIdx = startRingEntry[pawn.owner] ?: return false
                // Cannot place on self
                val occupant = pawns.firstOrNull { it.location == OopsPawnLocation.ON_RING && it.ringIndex == entryIdx }
                return occupant == null || occupant.owner != pawn.owner
            }
            return false
        }

        if (pawn.location == OopsPawnLocation.IN_LANE) {
            val targetLane = pawn.laneIndex + steps
            if (targetLane == 5) return true // Exact hit to Castle!
            if (targetLane < 5) {
                // Must not be occupied by self
                val occupant = pawns.firstOrNull {
                    it.owner == pawn.owner && it.location == OopsPawnLocation.IN_LANE && it.laneIndex == targetLane
                }
                return occupant == null
            }
            return false // Cannot overshoot Castle
        }

        if (pawn.location == OopsPawnLocation.ON_RING) {
            val target = calculateForwardTarget(pawn, steps) ?: return false
            // Check self occupancy at target
            when (target.first) {
                OopsPawnLocation.ON_RING -> {
                    val occupant = pawns.firstOrNull { it.location == OopsPawnLocation.ON_RING && it.ringIndex == target.second }
                    return occupant == null || occupant.owner != pawn.owner
                }
                OopsPawnLocation.IN_LANE -> {
                    val occupant = pawns.firstOrNull { it.owner == pawn.owner && it.location == OopsPawnLocation.IN_LANE && it.laneIndex == target.second }
                    return occupant == null
                }
                OopsPawnLocation.IN_CASTLE -> return true
                else -> return false
            }
        }
        return false
    }

    private fun canPawnMoveBackward(pawn: OopsPawn, steps: Int): Boolean {
        if (pawn.location != OopsPawnLocation.ON_RING) return false
        val newIdx = (pawn.ringIndex - steps + 60) % 60
        val occupant = pawns.firstOrNull { it.location == OopsPawnLocation.ON_RING && it.ringIndex == newIdx }
        return occupant == null || occupant.owner != pawn.owner
    }

    private fun calculateForwardTarget(pawn: OopsPawn, steps: Int): Pair<OopsPawnLocation, Int>? {
        val entry = safeLaneEntry[pawn.owner] ?: return null
        var cur = pawn.ringIndex

        // Count distance to safe lane entry
        var distToEntry = 0
        var temp = cur
        while (temp != entry && distToEntry < 60) {
            temp = (temp + 1) % 60
            distToEntry++
        }

        if (steps <= distToEntry) {
            return Pair(OopsPawnLocation.ON_RING, (cur + steps) % 60)
        } else {
            val stepsIntoLane = steps - distToEntry - 1
            if (stepsIntoLane < 5) {
                return Pair(OopsPawnLocation.IN_LANE, stepsIntoLane)
            } else if (stepsIntoLane == 5) {
                return Pair(OopsPawnLocation.IN_CASTLE, 0)
            }
            return null // Overshot Castle
        }
    }

    private fun executePawnMove(pawn: OopsPawn, card: OopsCard) {
        clearEligibility()

        if (card.type == OopsCardType.OOPS) {
            // Already handled in OOPS target selection
            return
        }

        if (card.type == OopsCardType.SWITCH && selectedOwnPawn != null) {
            // Handled in Switch target selection
            return
        }

        // Leaving Start onto ring
        if (pawn.location == OopsPawnLocation.IN_START) {
            val entryIdx = startRingEntry[pawn.owner] ?: 0
            val targetPt = getPointForLocation(OopsPawnLocation.ON_RING, entryIdx, pawn.owner)
            startPawnAnimation(pawn, listOf(targetPt)) {
                pawn.location = OopsPawnLocation.ON_RING
                pawn.ringIndex = entryIdx
                checkKnockoutAt(entryIdx, pawn)
            }
            return
        }

        val isBackward = (chosenMoveDelta != null && chosenMoveDelta!! < 0) || card.type == OopsCardType.BACKWARD
        val moveSteps = if (chosenMoveDelta != null) abs(chosenMoveDelta!!) else card.value

        // Backward Move
        if (isBackward) {
            val path = mutableListOf<PointF>()
            var cur = pawn.ringIndex
            for (i in 1..moveSteps) {
                cur = (cur - 1 + 60) % 60
                path.add(getPointForLocation(OopsPawnLocation.ON_RING, cur, pawn.owner))
            }
            startPawnAnimation(pawn, path) {
                pawn.ringIndex = cur
                checkSlideAndKnockout(pawn)
            }
            return
        }

        // Forward Move
        if (pawn.location == OopsPawnLocation.IN_LANE) {
            val targetLane = pawn.laneIndex + moveSteps
            val path = mutableListOf<PointF>()
            val lanePts = safeLanes[pawn.owner] ?: return
            for (i in (pawn.laneIndex + 1)..targetLane) {
                if (i < 5) {
                    val pt = lanePts[i]
                    path.add(PointF(boardLeft + pt.x * cellSize + cellSize / 2f, boardTop + pt.y * cellSize + cellSize / 2f))
                } else if (i == 5) {
                    val pt = castleGoals[pawn.owner] ?: Point(0, 0)
                    path.add(PointF(boardLeft + pt.x * cellSize + cellSize / 2f, boardTop + pt.y * cellSize + cellSize / 2f))
                }
            }
            startPawnAnimation(pawn, path) {
                if (targetLane == 5) {
                    pawn.location = OopsPawnLocation.IN_CASTLE
                    soundManager.play(SoundManager.SoundType.GOAL, 0.9f)
                    val c = getPawnSlotPoint(pawn)
                    particles.spawnExplosion(c.x, c.y, pawn.owner.color, 40)
                } else {
                    pawn.laneIndex = targetLane
                }
            }
            return
        }

        if (pawn.location == OopsPawnLocation.ON_RING) {
            val target = calculateForwardTarget(pawn, moveSteps) ?: return
            val path = mutableListOf<PointF>()
            val entry = safeLaneEntry[pawn.owner] ?: 0

            var cur = pawn.ringIndex
            var remaining = moveSteps

            while (remaining > 0 && cur != entry) {
                cur = (cur + 1) % 60
                path.add(getPointForLocation(OopsPawnLocation.ON_RING, cur, pawn.owner))
                remaining--
            }

            if (remaining > 0) {
                // Moving into safe lane
                val lanePts = safeLanes[pawn.owner] ?: return
                var laneStep = 0
                while (remaining > 0) {
                    if (laneStep < 5) {
                        val pt = lanePts[laneStep]
                        path.add(PointF(boardLeft + pt.x * cellSize + cellSize / 2f, boardTop + pt.y * cellSize + cellSize / 2f))
                    } else if (laneStep == 5) {
                        val pt = castleGoals[pawn.owner] ?: Point(0, 0)
                        path.add(PointF(boardLeft + pt.x * cellSize + cellSize / 2f, boardTop + pt.y * cellSize + cellSize / 2f))
                    }
                    laneStep++
                    remaining--
                }
            }

            startPawnAnimation(pawn, path) {
                when (target.first) {
                    OopsPawnLocation.ON_RING -> {
                        pawn.ringIndex = target.second
                        checkSlideAndKnockout(pawn)
                    }
                    OopsPawnLocation.IN_LANE -> {
                        pawn.location = OopsPawnLocation.IN_LANE
                        pawn.laneIndex = target.second
                    }
                    OopsPawnLocation.IN_CASTLE -> {
                        pawn.location = OopsPawnLocation.IN_CASTLE
                        soundManager.play(SoundManager.SoundType.GOAL, 0.9f)
                        val c = getPawnSlotPoint(pawn)
                        particles.spawnExplosion(c.x, c.y, pawn.owner.color, 45)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun getPointForLocation(loc: OopsPawnLocation, index: Int, owner: PlayerId): PointF {
        val pt = when (loc) {
            OopsPawnLocation.ON_RING -> ringGridPoints[index.coerceIn(0, 59)]
            OopsPawnLocation.IN_LANE -> (safeLanes[owner] ?: listOf())[index.coerceIn(0, 4)]
            OopsPawnLocation.IN_CASTLE -> castleGoals[owner] ?: Point(0, 0)
            OopsPawnLocation.IN_START -> (startSlots[owner] ?: listOf())[0]
        }
        return PointF(boardLeft + pt.x * cellSize + cellSize / 2f, boardTop + pt.y * cellSize + cellSize / 2f)
    }

    private fun startPawnAnimation(pawn: OopsPawn, path: List<PointF>, onComplete: () -> Unit) {
        if (path.isEmpty()) {
            onComplete()
            return
        }
        phase = OopsPhase.ANIMATING
        movingPawn = pawn
        movePath.clear()
        movePath.addAll(path)
        onAnimationComplete = onComplete

        hopStart.set(pawn.visualPos.x, pawn.visualPos.y)
        hopEnd = movePath.removeAt(0)
        hopTimer = hopDuration

        soundManager.play(SoundManager.SoundType.TAP, 0.8f)
    }

    private fun checkSlideAndKnockout(pawn: OopsPawn) {
        if (pawn.location != OopsPawnLocation.ON_RING) return

        // Check if pawn landed on an opponent slide start
        val slide = slides.firstOrNull { it.isStart(pawn.ringIndex) && it.owner != pawn.owner }
        if (slide != null) {
            pendingSlide = slide
        } else {
            checkKnockoutAt(pawn.ringIndex, pawn)
        }
    }

    private fun applySlideSweep(pawn: OopsPawn, slide: OopsSlide) {
        // Collect slide path
        val path = mutableListOf<PointF>()
        for (i in 1..slide.length) {
            val idx = (slide.startIndex + i) % 60
            path.add(getPointForLocation(OopsPawnLocation.ON_RING, idx, pawn.owner))
        }

        // Knock back any pawn in the slide path
        for (i in 0..slide.length) {
            val idx = (slide.startIndex + i) % 60
            val victims = pawns.filter { it.location == OopsPawnLocation.ON_RING && it.ringIndex == idx && it != pawn }
            pendingKnockbacks.addAll(victims)
        }

        soundManager.play(SoundManager.SoundType.SHOOT, 0.9f)
        feedbackMessage = "💨 SLIDE SWEEP!"
        feedbackTimer = 0.5f

        startPawnAnimation(pawn, path) {
            pawn.ringIndex = slide.endIndex
            resolveKnockbacks()
            checkWinOrFinishTurn()
        }
    }

    private fun checkKnockoutAt(ringIdx: Int, attacker: OopsPawn) {
        val opponent = pawns.firstOrNull { it.location == OopsPawnLocation.ON_RING && it.ringIndex == ringIdx && it != attacker }
        if (opponent != null) {
            val isEnemy = if (isTeamMode) getPlayerTeam(opponent.owner) != getPlayerTeam(attacker.owner) else opponent.owner != attacker.owner
            if (isEnemy) {
                pendingKnockbacks.add(opponent)
            }
        }
    }

    private fun resolveKnockbacks() {
        if (pendingKnockbacks.isNotEmpty()) {
            pendingKnockbacks.forEach { victim ->
                victim.location = OopsPawnLocation.IN_START
                victim.visualPos = getPawnSlotPoint(victim)
                val pos = victim.visualPos
                particles.spawnExplosion(pos.x, pos.y, victim.owner.color, 35)
            }
            pendingKnockbacks.clear()
            soundManager.play(SoundManager.SoundType.EXPLOSION, 0.9f)
            triggerVibration(80)
            feedbackMessage = "💥 OOPS!"
            feedbackTimer = 0.65f
        }
    }

    private fun isPlayerFinished(pId: PlayerId): Boolean {
        return pawns.count { it.owner == pId && it.location == OopsPawnLocation.IN_CASTLE } >= PAWNS_PER_PLAYER
    }

    private fun checkWinOrFinishTurn() {
        val activeP = getCurrentPlayer() ?: return
        val castleCount = pawns.count { it.owner == activeP.id && it.location == OopsPawnLocation.IN_CASTLE }

        if (castleCount >= PAWNS_PER_PLAYER) {
            // Victory!
            val c = castleGoals[activeP.id] ?: Point(0, 0)
            val cx = boardLeft + c.x * cellSize + cellSize / 2f
            val cy = boardTop + c.y * cellSize + cellSize / 2f
            particles.spawnExplosion(cx, cy, Color.parseColor("#FFD54F"), 60)
            soundManager.play(SoundManager.SoundType.VICTORY)
            triggerVibration(120)

            if (isTeamMode) {
                val t1Both = isPlayerFinished(PlayerId.PLAYER_1) && isPlayerFinished(PlayerId.PLAYER_2)
                val t2Both = isPlayerFinished(PlayerId.PLAYER_3) && isPlayerFinished(PlayerId.PLAYER_4)
                if (t1Both) {
                    winningTeam = 1
                    endRound(players[0])
                    return
                } else if (t2Both) {
                    winningTeam = 2
                    endRound(players[2])
                    return
                }
                feedbackMessage = "🌟 ${getPlayerDisplayName(activeP).uppercase()} ALL HOME! TEAMMATE TO WIN"
                feedbackTimer = 1.3f
                finishTurn()
                return
            } else {
                endRound(activeP)
                return
            }
        }

        // If card was 2, draw again! (unless player has already finished)
        if (currentCard?.value == 2 && (!isTeamMode || !isPlayerFinished(activeP.id))) {
            feedbackMessage = "DRAW AGAIN!"
            feedbackTimer = 0.65f
            phase = OopsPhase.WAITING_DRAW
            if (activeP.isBot) botThinkTimer = getBotDelay()
        } else {
            finishTurn()
        }
    }

    private fun finishTurn() {
        clearEligibility()
        selectedOwnPawn = null
        chosenMoveDelta = null
        currentCard = null
        phase = OopsPhase.WAITING_DRAW
        advanceToNextActivePlayer()
    }

    private fun advanceToNextActivePlayer() {
        val order = turnOrder
        for (i in 1..order.size) {
            currentTurnIndex = (currentTurnIndex + 1) % order.size
            val nextP = getCurrentPlayer()
            if (nextP != null) {
                if (isTeamMode && isPlayerFinished(nextP.id)) {
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
        val cA = pawns.count { it.owner == pA && it.location == OopsPawnLocation.IN_CASTLE }
        val cB = pawns.count { it.owner == pB && it.location == OopsPawnLocation.IN_CASTLE }
        val bothWon = (cA >= PAWNS_PER_PLAYER && cB >= PAWNS_PER_PLAYER)
        return if (bothWon) "BOTH IN CASTLE 🏆" else "${cA + cB}/${PAWNS_PER_PLAYER * 2} in Castle"
    }

    private fun clearEligibility() {
        pawns.forEach { it.isEligible = false }
    }

    private fun botExecuteBestMove() {
        val activeP = getCurrentPlayer() ?: return
        val card = currentCard ?: return
        val myPawns = pawns.filter { it.owner == activeP.id }

        if (phase == OopsPhase.CHOOSING_11_OPTION) {
            val ownOnRing = myPawns.filter { it.location == OopsPawnLocation.ON_RING }
            val opponentsOnRing = pawns.filter { it.owner != activeP.id && it.location == OopsPawnLocation.ON_RING }
            val movable11 = myPawns.filter { canPawnMoveForward(it, 11) }

            val bestOpponent = opponentsOnRing.maxByOrNull { it.ringIndex }
            val worstOwn = ownOnRing.minByOrNull { it.ringIndex }

            // If swapping gains more than 8 tiles or steals a high spot, swap!
            if (bestOpponent != null && worstOwn != null && (bestOpponent.ringIndex > worstOwn.ringIndex + 8)) {
                selectedOwnPawn = worstOwn
                clearEligibility()
                opponentsOnRing.forEach { it.isEligible = true }
                phase = OopsPhase.CHOOSING_SWITCH_OPPONENT
                botThinkTimer = getBotDelay() * 0.7f
            } else if (movable11.isNotEmpty()) {
                chosenMoveDelta = 11
                clearEligibility()
                movable11.forEach { it.isEligible = true }
                phase = OopsPhase.CHOOSING_PAWN
                botThinkTimer = getBotDelay() * 0.7f
            } else if (bestOpponent != null && worstOwn != null) {
                selectedOwnPawn = worstOwn
                clearEligibility()
                opponentsOnRing.forEach { it.isEligible = true }
                phase = OopsPhase.CHOOSING_SWITCH_OPPONENT
                botThinkTimer = getBotDelay() * 0.7f
            }
            return
        }

        if (phase == OopsPhase.CHOOSING_10_OPTION) {
            val movable10 = myPawns.filter { canPawnMoveForward(it, 10) }
            val movableMinus1 = myPawns.filter { canPawnMoveBackward(it, 1) }

            val safeEntry = safeLaneEntry[activeP.id] ?: 0
            val bestMinus1 = movableMinus1.firstOrNull { pawn ->
                val targetRing = (pawn.ringIndex - 1 + 60) % 60
                targetRing == safeEntry || (targetRing + 1) % 60 == safeEntry
            }

            if (bestMinus1 != null) {
                chosenMoveDelta = -1
                clearEligibility()
                movableMinus1.forEach { it.isEligible = true }
                phase = OopsPhase.CHOOSING_PAWN
                botThinkTimer = getBotDelay() * 0.7f
            } else if (movable10.isNotEmpty()) {
                chosenMoveDelta = 10
                clearEligibility()
                movable10.forEach { it.isEligible = true }
                phase = OopsPhase.CHOOSING_PAWN
                botThinkTimer = getBotDelay() * 0.7f
            } else if (movableMinus1.isNotEmpty()) {
                chosenMoveDelta = -1
                clearEligibility()
                movableMinus1.forEach { it.isEligible = true }
                phase = OopsPhase.CHOOSING_PAWN
                botThinkTimer = getBotDelay() * 0.7f
            }
            return
        }

        if (phase == OopsPhase.CHOOSING_SWITCH_OWN) {
            val ownOnRing = myPawns.filter { it.location == OopsPawnLocation.ON_RING }
            selectedOwnPawn = ownOnRing.minByOrNull { it.ringIndex } ?: ownOnRing.firstOrNull()
            clearEligibility()
            val opponentsOnRing = pawns.filter { it.owner != activeP.id && it.location == OopsPawnLocation.ON_RING }
            opponentsOnRing.forEach { it.isEligible = true }
            phase = OopsPhase.CHOOSING_SWITCH_OPPONENT
            botThinkTimer = getBotDelay() * 0.7f
            return
        }

        if (phase == OopsPhase.CHOOSING_OOPS_TARGET) {
            val eligibleOpponents = pawns.filter { it.isEligible }
            // Pick opponent closest to their Castle or on high ring
            val victim = eligibleOpponents.maxByOrNull { it.ringIndex } ?: eligibleOpponents.firstOrNull()
            if (victim != null && selectedOwnPawn != null) {
                executeOopsAction(selectedOwnPawn!!, victim)
            }
            return
        }

        if (phase == OopsPhase.CHOOSING_SWITCH_OPPONENT) {
            val eligibleOpponents = pawns.filter { it.isEligible }
            val victim = eligibleOpponents.maxByOrNull { it.ringIndex } ?: eligibleOpponents.firstOrNull()
            if (victim != null && selectedOwnPawn != null) {
                executeSwitchAction(selectedOwnPawn!!, victim)
            }
            return
        }

        if (phase == OopsPhase.CHOOSING_PAWN) {
            val eligible = myPawns.filter { it.isEligible }
            if (eligible.isEmpty()) {
                finishTurn()
                return
            }

            // Score candidate moves
            val bestPawn = eligible.maxByOrNull { p ->
                var score = 0
                if (p.location == OopsPawnLocation.IN_START) score += 30
                val target = calculateForwardTarget(p, card.value)
                if (target?.first == OopsPawnLocation.IN_CASTLE) score += 100
                if (target?.first == OopsPawnLocation.IN_LANE) score += 60
                if (target?.first == OopsPawnLocation.ON_RING) {
                    val slide = slides.firstOrNull { it.isStart(target.second) && it.owner != p.owner }
                    if (slide != null) score += 40
                    val victim = pawns.firstOrNull { it.location == OopsPawnLocation.ON_RING && it.ringIndex == target.second && it.owner != p.owner }
                    if (victim != null) score += 50
                }
                score
            } ?: eligible.first()

            executePawnMove(bestPawn, card)
        }
    }

    private fun executeOopsAction(ownPawn: OopsPawn, victim: OopsPawn) {
        clearEligibility()
        val targetIdx = victim.ringIndex

        victim.location = OopsPawnLocation.IN_START
        victim.visualPos = getPawnSlotPoint(victim)
        particles.spawnExplosion(victim.visualPos.x, victim.visualPos.y, victim.owner.color, 35)

        ownPawn.location = OopsPawnLocation.ON_RING
        ownPawn.ringIndex = targetIdx
        val targetPt = getPointForLocation(OopsPawnLocation.ON_RING, targetIdx, ownPawn.owner)

        soundManager.play(SoundManager.SoundType.EXPLOSION, 0.9f)
        triggerVibration(80)
        feedbackMessage = "💥 OOPS! TAKEN!"
        feedbackTimer = 0.65f

        startPawnAnimation(ownPawn, listOf(targetPt)) {
            checkWinOrFinishTurn()
        }
    }

    private fun executeSwitchAction(ownPawn: OopsPawn, opponentPawn: OopsPawn) {
        clearEligibility()
        val ownIdx = ownPawn.ringIndex
        val oppIdx = opponentPawn.ringIndex

        ownPawn.ringIndex = oppIdx
        opponentPawn.ringIndex = ownIdx

        val ownTarget = getPointForLocation(OopsPawnLocation.ON_RING, oppIdx, ownPawn.owner)
        val oppTarget = getPointForLocation(OopsPawnLocation.ON_RING, ownIdx, opponentPawn.owner)

        soundManager.play(SoundManager.SoundType.SHOOT, 0.9f)
        feedbackMessage = "🔀 SWAPPED!"
        feedbackTimer = 0.6f

        opponentPawn.visualPos.set(oppTarget.x, oppTarget.y)
        startPawnAnimation(ownPawn, listOf(ownTarget)) {
            checkSlideAndKnockout(ownPawn)
            checkWinOrFinishTurn()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || feedbackTimer > 0f) return true

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val activeP = getCurrentPlayer() ?: return true
            if (activeP.isBot) return true

            val tx = event.x
            val ty = event.y

            // 1. Tapping deck or action button to draw card
            if (phase == OopsPhase.WAITING_DRAW) {
                val (actionRect, _) = getActiveActionInfo()
                val deckRect = getDeckRect()
                if (actionRect.contains(tx, ty) || deckRect.contains(tx, ty)) {
                    triggerDrawCard()
                    return true
                }
            }

            // 2. Choosing Card 11 or Card 10 Option
            if (phase == OopsPhase.CHOOSING_11_OPTION || phase == OopsPhase.CHOOSING_10_OPTION) {
                val deckRect = getDeckRect()
                val activeIndex = players.indexOf(activeP)
                val isTopPlayer = (activeIndex in listOf(1, 2)) && (totalPlayers > 1)
                val rotDeg = if (isTopPlayer && !activeP.isBot) 180f else 0f

                var touchX = tx
                var touchY = ty
                if (rotDeg != 0f) {
                    val angleRad = Math.toRadians(-rotDeg.toDouble())
                    val cosA = cos(angleRad).toFloat()
                    val sinA = sin(angleRad).toFloat()
                    val dx = tx - deckRect.centerX()
                    val dy = ty - deckRect.centerY()
                    touchX = deckRect.centerX() + dx * cosA - dy * sinA
                    touchY = deckRect.centerY() + dx * sinA + dy * cosA
                }

                val myPawns = pawns.filter { it.owner == activeP.id }
                val hitPad = 6f * density
                val btn1Hit = RectF(choiceBtn1Rect).apply { inset(-hitPad, -hitPad) }
                val btn2Hit = RectF(choiceBtn2Rect).apply { inset(-hitPad, -hitPad) }

                if (phase == OopsPhase.CHOOSING_11_OPTION) {
                    if (btn1Hit.contains(touchX, touchY)) {
                        val movable = myPawns.filter { canPawnMoveForward(it, 11) }
                        if (movable.isNotEmpty()) {
                            soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                            chosenMoveDelta = 11
                            clearEligibility()
                            movable.forEach { it.isEligible = true }
                            phase = OopsPhase.CHOOSING_PAWN
                        } else {
                            noMovesToast("CANNOT MOVE 11 SPACES!")
                        }
                        return true
                    }

                    if (btn2Hit.contains(touchX, touchY)) {
                        soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                        val ownOnRing = myPawns.filter { it.location == OopsPawnLocation.ON_RING }
                        val opponentsOnRing = pawns.filter { 
                    val isEnemy = if (isTeamMode) getPlayerTeam(it.owner) != getPlayerTeam(activeP.id) else it.owner != activeP.id
                    isEnemy && it.location == OopsPawnLocation.ON_RING 
                }
                        if (ownOnRing.isNotEmpty() && opponentsOnRing.isNotEmpty()) {
                            if (ownOnRing.size == 1) {
                                selectedOwnPawn = ownOnRing.first()
                                clearEligibility()
                                opponentsOnRing.forEach { it.isEligible = true }
                                phase = OopsPhase.CHOOSING_SWITCH_OPPONENT
                            } else {
                                clearEligibility()
                                ownOnRing.forEach { it.isEligible = true }
                                phase = OopsPhase.CHOOSING_SWITCH_OWN
                            }
                        } else if (ownOnRing.isEmpty()) {
                            noMovesToast("NEED A PAWN ON TRACK TO SWAP!")
                        } else {
                            noMovesToast("NO OPPONENT TARGETS ON TRACK!")
                        }
                        return true
                    }
                } else if (phase == OopsPhase.CHOOSING_10_OPTION) {
                    if (btn1Hit.contains(touchX, touchY)) {
                        val movable = myPawns.filter { canPawnMoveForward(it, 10) }
                        if (movable.isNotEmpty()) {
                            soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                            chosenMoveDelta = 10
                            clearEligibility()
                            movable.forEach { it.isEligible = true }
                            phase = OopsPhase.CHOOSING_PAWN
                        } else {
                            noMovesToast("CANNOT MOVE 10 SPACES!")
                        }
                        return true
                    }

                    if (btn2Hit.contains(touchX, touchY)) {
                        val movable = myPawns.filter { canPawnMoveBackward(it, 1) }
                        if (movable.isNotEmpty()) {
                            soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                            chosenMoveDelta = -1
                            clearEligibility()
                            movable.forEach { it.isEligible = true }
                            phase = OopsPhase.CHOOSING_PAWN
                        } else {
                            noMovesToast("NEED A PAWN ON TRACK TO MOVE -1!")
                        }
                        return true
                    }
                }
            }

            // 3. Choosing Oops Target
            if (phase == OopsPhase.CHOOSING_OOPS_TARGET) {
                val eligibleOpponents = pawns.filter { it.isEligible }
                val touchR = (cellSize * 1.3f).coerceAtLeast(36f * density)
                val touched = eligibleOpponents.minByOrNull { hypot(tx - it.visualPos.x, ty - it.visualPos.y) }
                    ?.takeIf { hypot(tx - it.visualPos.x, ty - it.visualPos.y) <= touchR }
                if (touched != null && selectedOwnPawn != null) {
                    executeOopsAction(selectedOwnPawn!!, touched)
                    return true
                }
            }

            // 4. Choosing which own pawn to swap (if multiple own pawns on ring)
            if (phase == OopsPhase.CHOOSING_SWITCH_OWN) {
                val eligible = pawns.filter { it.owner == activeP.id && it.isEligible }
                val touchR = (cellSize * 1.3f).coerceAtLeast(36f * density)
                val touched = eligible.minByOrNull { hypot(tx - it.visualPos.x, ty - it.visualPos.y) }
                    ?.takeIf { hypot(tx - it.visualPos.x, ty - it.visualPos.y) <= touchR }
                if (touched != null) {
                    selectedOwnPawn = touched
                    clearEligibility()
                    val opponentsOnRing = pawns.filter { 
                    val isEnemy = if (isTeamMode) getPlayerTeam(it.owner) != getPlayerTeam(activeP.id) else it.owner != activeP.id
                    isEnemy && it.location == OopsPawnLocation.ON_RING 
                }
                    opponentsOnRing.forEach { it.isEligible = true }
                    phase = OopsPhase.CHOOSING_SWITCH_OPPONENT
                    soundManager.play(SoundManager.SoundType.TAP, 0.8f)
                    return true
                }
            }

            // 5. Choosing Switch Opponent
            if (phase == OopsPhase.CHOOSING_SWITCH_OPPONENT) {
                val eligibleOpponents = pawns.filter { it.isEligible }
                val touchR = (cellSize * 1.3f).coerceAtLeast(36f * density)
                val touched = eligibleOpponents.minByOrNull { hypot(tx - it.visualPos.x, ty - it.visualPos.y) }
                    ?.takeIf { hypot(tx - it.visualPos.x, ty - it.visualPos.y) <= touchR }
                if (touched != null && selectedOwnPawn != null) {
                    executeSwitchAction(selectedOwnPawn!!, touched)
                    return true
                }
            }

            // 6. Choosing which of own pawns to move forward / backward
            if (phase == OopsPhase.CHOOSING_PAWN) {
                val eligible = pawns.filter { it.owner == activeP.id && it.isEligible }
                val touchR = (cellSize * 1.3f).coerceAtLeast(36f * density)
                val touched = eligible.minByOrNull { hypot(tx - it.visualPos.x, ty - it.visualPos.y) }
                    ?.takeIf { hypot(tx - it.visualPos.x, ty - it.visualPos.y) <= touchR }

                if (touched != null) {
                    val card = currentCard ?: return true
                    executePawnMove(touched, card)
                    return true
                }
            }
        }
        return true
    }

    private fun getDeckRect(): RectF {
        val cx = boardLeft + boardSize / 2f
        val cy = boardTop + boardSize / 2f
        val w = cellSize * 3.6f
        val h = cellSize * 4.4f
        return RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    private fun drawFittedText(
        canvas: Canvas,
        text: String,
        cx: Float,
        cy: Float,
        maxW: Float,
        baseSizePx: Float,
        isBold: Boolean = false,
        textColor: Int = Color.WHITE
    ) {
        paint.style = Paint.Style.FILL
        paint.color = textColor
        paint.isFakeBoldText = isBold
        paint.textAlign = Paint.Align.CENTER
        var curSize = baseSizePx
        paint.textSize = curSize
        val measured = paint.measureText(text)
        if (measured > maxW && measured > 0f) {
            curSize = (curSize * (maxW / measured)).coerceAtLeast(6f * density)
            paint.textSize = curSize
        }
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, cx, baseline, paint)
        paint.isFakeBoldText = false
    }

    private fun getPawnDestinationPoint(pawn: OopsPawn, card: OopsCard): PointF? {
        if (pawn.location == OopsPawnLocation.IN_START) {
            if (card.value == 1 || card.value == 2) {
                val entryIdx = startRingEntry[pawn.owner] ?: return null
                return getPointForLocation(OopsPawnLocation.ON_RING, entryIdx, pawn.owner)
            }
            return null
        }
        val isBackward = (chosenMoveDelta != null && chosenMoveDelta!! < 0) || card.type == OopsCardType.BACKWARD
        val steps = if (chosenMoveDelta != null) abs(chosenMoveDelta!!) else card.value
        if (isBackward) {
            val newIdx = (pawn.ringIndex - steps + 60) % 60
            return getPointForLocation(OopsPawnLocation.ON_RING, newIdx, pawn.owner)
        }
        if (pawn.location == OopsPawnLocation.IN_LANE) {
            val targetLane = pawn.laneIndex + steps
            if (targetLane <= 4) return getPointForLocation(OopsPawnLocation.IN_LANE, targetLane, pawn.owner)
            if (targetLane == 5) return getPointForLocation(OopsPawnLocation.IN_CASTLE, 0, pawn.owner)
            return null
        }
        if (pawn.location == OopsPawnLocation.ON_RING) {
            val target = calculateForwardTarget(pawn, steps) ?: return null
            return getPointForLocation(target.first, target.second, pawn.owner)
        }
        return null
    }

    override fun render(canvas: Canvas) {
        // 1. Cosmic Dark Background
        canvas.drawColor(Color.parseColor("#0C091D"))

        // Ambient stars
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1634")
        for (i in 0..15) {
            val sx = (i * 89f * density) % width
            val sy = (i * 143f * density) % height
            canvas.drawCircle(sx, sy, 2.5f * density, paint)
        }

        // 2. Target Blitz Player Status HUD
        drawPlayerStatusCards(canvas)

        // 3. Render 16x16 Oops Board (Ring, Lanes, Castle, Start Bases, Slides)
        drawOopsBoard(canvas)

        // 4. Central 3D Card Deck & Drawn Card Flip
        drawCardDeckArena(canvas)

        // 5. 3D Pawns with Pulsing Selection Aura and Pointer Arrows
        drawPawns(canvas)

        // 6. Interactive Player Action Pill Button
        drawActionControls(canvas)

        // 7. Toast Feedback Banner (if active)
        drawFeedbackBanner(canvas)

        // 8. Particles
        particles.draw(canvas)
    }

    private fun drawOopsBoard(canvas: Canvas) {
        // Outer board frame
        val frameR = RectF(boardLeft - 4f * density, boardTop - 4f * density, boardLeft + boardSize + 4f * density, boardTop + boardSize + 4f * density)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f * density
        paint.color = Color.parseColor("#4A3778")
        canvas.drawRoundRect(frameR, 14f * density, 14f * density, paint)

        val pulse = 0.5f + 0.5f * sin(pulseTimer * 5f)

        // 1. Draw 60 Ring Tiles with vibrant colors and step dots
        ringGridPoints.forEachIndexed { idx, pt ->
            val tileR = RectF(boardLeft + pt.x * cellSize, boardTop + pt.y * cellSize, boardLeft + (pt.x + 1) * cellSize, boardTop + (pt.y + 1) * cellSize)

            val slide = slides.firstOrNull { it.containsIndex(idx) }
            val isSlideStart = slide != null && slide.isStart(idx)
            val entryPlayer = startRingEntry.entries.firstOrNull { it.value == idx }?.key
            val safeEntryPlayer = safeLaneEntry.entries.firstOrNull { it.value == idx }?.key

            // Distinct checkered fill
            paint.style = Paint.Style.FILL
            when {
                slide != null -> paint.color = (slide.owner.color and 0x00FFFFFF) or (if (isSlideStart) 0x66000000 else 0x38000000)
                entryPlayer != null -> paint.color = (entryPlayer.color and 0x00FFFFFF) or 0x4D000000
                safeEntryPlayer != null -> paint.color = (safeEntryPlayer.color and 0x00FFFFFF) or 0x3D000000
                else -> paint.color = if (idx % 2 == 0) Color.parseColor("#261B48") else Color.parseColor("#1C1338")
            }
            canvas.drawRect(tileR, paint)

            // Crisp neon border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = when {
                slide != null -> slide.owner.color
                entryPlayer != null -> entryPlayer.color
                safeEntryPlayer != null -> safeEntryPlayer.color
                else -> Color.parseColor("#5A4788")
            }
            canvas.drawRect(tileR, paint)

            // Center markers on tiles
            if (slide != null) {
                if (isSlideStart) {
                    paint.style = Paint.Style.FILL
                    paint.color = slide.owner.color
                    canvas.drawCircle(tileR.centerX(), tileR.centerY(), cellSize * 0.24f, paint)
                }
            } else if (entryPlayer != null) {
                val arrow = when (entryPlayer) {
                    PlayerId.PLAYER_1 -> "←" // Bottom moves LEFT
                    PlayerId.PLAYER_3 -> "↑" // Left moves UP
                    PlayerId.PLAYER_2 -> "→" // Top moves RIGHT
                    PlayerId.PLAYER_4 -> "↓" // Right moves DOWN
                }
                paint.style = Paint.Style.FILL
                paint.color = entryPlayer.color
                paint.textSize = cellSize * 0.5f
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val baseline = tileR.centerY() - (fm.ascent + fm.descent) / 2f
                canvas.drawText(arrow, tileR.centerX(), baseline, paint)
            } else if (safeEntryPlayer != null) {
                val arrow = when (safeEntryPlayer) {
                    PlayerId.PLAYER_1 -> "▲" // Turns UP inward
                    PlayerId.PLAYER_3 -> "▶" // Turns RIGHT inward
                    PlayerId.PLAYER_2 -> "▼" // Turns DOWN inward
                    PlayerId.PLAYER_4 -> "◀" // Turns LEFT inward
                }
                paint.style = Paint.Style.FILL
                paint.color = safeEntryPlayer.color
                paint.textSize = cellSize * 0.5f
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val baseline = tileR.centerY() - (fm.ascent + fm.descent) / 2f
                canvas.drawText(arrow, tileR.centerX(), baseline, paint)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#7D66B3")
                canvas.drawCircle(tileR.centerX(), tileR.centerY(), 1.8f * density, paint)
            }
        }

        // 2. Draw Safe Lanes & Castles
        players.forEach { p ->
            val lanePts = safeLanes[p.id] ?: return@forEach
            lanePts.forEachIndexed { step, pt ->
                val tileR = RectF(boardLeft + pt.x * cellSize, boardTop + pt.y * cellSize, boardLeft + (pt.x + 1) * cellSize, boardTop + (pt.y + 1) * cellSize)
                paint.style = Paint.Style.FILL
                paint.color = (p.id.color and 0x00FFFFFF) or 0x4D000000
                canvas.drawRect(tileR, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * density
                paint.color = p.id.color
                canvas.drawRect(tileR, paint)

                // Lane Step Number
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.textSize = cellSize * 0.45f
                paint.textAlign = Paint.Align.CENTER
                val fm = paint.fontMetrics
                val baseline = tileR.centerY() - (fm.ascent + fm.descent) / 2f
                canvas.drawText("${step + 1}", tileR.centerX(), baseline, paint)
            }

            // Castle Goal
            val c = castleGoals[p.id] ?: return@forEach
            val cR = RectF(boardLeft + c.x * cellSize, boardTop + c.y * cellSize, boardLeft + (c.x + 1) * cellSize, boardTop + (c.y + 1) * cellSize)
            paint.style = Paint.Style.FILL
            paint.color = p.id.color
            canvas.drawRoundRect(cR, 6f * density, 6f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = Color.WHITE
            canvas.drawRoundRect(cR, 6f * density, 6f * density, paint)

            // Castle Trophy Icon
            paint.style = Paint.Style.FILL
            paint.textSize = cellSize * 0.65f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = cR.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText("⭐", cR.centerX(), baseline, paint)
        }

        // 3. Draw Start Bases with dedicated "START" Base Plates (2x2 grid)
        players.forEach { p ->
            val slots = startSlots[p.id] ?: return@forEach
            val minX = slots.minOf { it.x }
            val maxX = slots.maxOf { it.x }
            val minY = slots.minOf { it.y }
            val maxY = slots.maxOf { it.y }

            val pad = cellSize * 0.22f
            val topLabelGap = 8f * density
            val plateR = RectF(
                boardLeft + minX * cellSize - pad,
                boardTop + minY * cellSize - pad - topLabelGap,
                boardLeft + (maxX + 1) * cellSize + pad,
                boardTop + (maxY + 1) * cellSize + pad
            )

            // Plate Fill & Border
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#180F30")
            canvas.drawRoundRect(plateR, 10f * density, 10f * density, paint)

            paint.color = (p.id.color and 0x00FFFFFF) or 0x22000000
            canvas.drawRoundRect(plateR, 10f * density, 10f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.4f * density
            paint.color = p.id.color
            canvas.drawRoundRect(plateR, 10f * density, 10f * density, paint)

            // Label "START"
            paint.style = Paint.Style.FILL
            paint.textSize = 7.5f * density
            paint.color = p.id.color
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("START", plateR.centerX(), plateR.top + 8.5f * density, paint)
            paint.isFakeBoldText = false

            // Pedestals in clean 2x2 spacing
            slots.forEach { pt ->
                val cx = boardLeft + pt.x * cellSize + cellSize / 2f
                val cy = boardTop + pt.y * cellSize + cellSize / 2f
                val r = cellSize * 0.36f

                paint.style = Paint.Style.FILL
                paint.color = (p.id.color and 0x00FFFFFF) or 0x44000000
                canvas.drawCircle(cx, cy, r, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * density
                paint.color = p.id.color
                canvas.drawCircle(cx, cy, r, paint)
            }
        }

        // 4. Highlight Destination Tile(s) when choosing a pawn to move
        if (phase == OopsPhase.CHOOSING_PAWN && currentCard != null) {
            val eligible = pawns.filter { it.isEligible }
            eligible.forEach { p ->
                val dest = getPawnDestinationPoint(p, currentCard!!)
                if (dest != null) {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#38FFD54F")
                    canvas.drawCircle(dest.x, dest.y, cellSize * 0.38f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = (2.2f + pulse * 1.5f) * density
                    paint.color = Color.parseColor("#FFD54F")
                    canvas.drawCircle(dest.x, dest.y, cellSize * 0.38f, paint)
                }
            }
        }
    }

    private fun drawCardDeckArena(canvas: Canvas) {
        val deckRect = getDeckRect()
        val pulse = 0.5f + 0.5f * sin(pulseTimer * 5f)
        val activeP = getCurrentPlayer()

        if (phase == OopsPhase.WAITING_DRAW || currentCard == null) {
            // Draw Deck (Card Back Visual)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#150F28")
            canvas.drawRoundRect(deckRect, 14f * density, 14f * density, paint)

            // Pulsing Deck Border in active player's color
            paint.style = Paint.Style.STROKE
            val borderC = activeP?.id?.color ?: Color.parseColor("#FFD54F")
            paint.strokeWidth = (2.5f + pulse * 2f) * density
            paint.color = borderC
            canvas.drawRoundRect(deckRect, 14f * density, 14f * density, paint)

            // Inner Card Pattern
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#261B46")
            val innerR = RectF(deckRect.left + 5f * density, deckRect.top + 5f * density, deckRect.right - 5f * density, deckRect.bottom - 5f * density)
            canvas.drawRoundRect(innerR, 10f * density, 10f * density, paint)

            paint.textSize = cellSize * 0.95f
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = deckRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText("🃏", deckRect.centerX(), baseline - 8f * density, paint)

            drawFittedText(
                canvas,
                "TAP DECK",
                deckRect.centerX(),
                deckRect.centerY() + 16f * density,
                maxW = deckRect.width() - 16f * density,
                baseSizePx = 9.5f * density,
                isBold = true,
                textColor = Color.WHITE
            )
            return
        }

        // Drawn Card Face Visual (Only drawn when card is revealed, NO background box underneath!)
        val card = currentCard!!
        val activeIndex = if (activeP != null) players.indexOf(activeP) else 0
        val isTopPlayer = (activeIndex in listOf(1, 2)) && (totalPlayers > 1)
        val rotDeg = if (isTopPlayer && activeP?.isBot != true) 180f else 0f

        canvas.save()
        if (rotDeg != 0f) {
            canvas.rotate(rotDeg, deckRect.centerX(), deckRect.centerY())
        }

        // Glowing Card Face
        val cardColor = when (card.type) {
            OopsCardType.OOPS -> Color.parseColor("#FF2A6D")
            OopsCardType.SWITCH -> Color.parseColor("#00E5FF")
            OopsCardType.BACKWARD -> Color.parseColor("#FF9100")
            OopsCardType.FORWARD -> Color.parseColor("#FFD54F")
        }

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#261B4A")
        canvas.drawRoundRect(deckRect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * density
        paint.color = cardColor
        canvas.drawRoundRect(deckRect, 14f * density, 14f * density, paint)

        val contentMaxW = deckRect.width() - 12f * density

        if (phase == OopsPhase.CHOOSING_11_OPTION || phase == OopsPhase.CHOOSING_10_OPTION) {
            // 1. Top Badge Pill
            val badgeText = if (phase == OopsPhase.CHOOSING_11_OPTION) "SWAP / +11" else "+10 / -1"
            val badgeH = 14f * density
            val badgeY = deckRect.top + 12f * density
            val badgePillW = min(contentMaxW, 80f * density)
            val badgeR = RectF(deckRect.centerX() - badgePillW / 2f, badgeY - badgeH / 2f, deckRect.centerX() + badgePillW / 2f, badgeY + badgeH / 2f)
            paint.style = Paint.Style.FILL
            paint.color = (cardColor and 0x00FFFFFF) or 0x33000000
            canvas.drawRoundRect(badgeR, 7f * density, 7f * density, paint)
            drawFittedText(canvas, badgeText, deckRect.centerX(), badgeY, badgePillW - 6f * density, 7f * density, isBold = true, textColor = cardColor)

            // 2. Compact Card Number in Upper Space (NO OVERLAP with buttons!)
            val titleY = deckRect.top + 33f * density
            val titleText = if (phase == OopsPhase.CHOOSING_11_OPTION) "11" else "10"
            drawFittedText(canvas, titleText, deckRect.centerX(), titleY, contentMaxW, cellSize * 0.70f, isBold = true, textColor = cardColor)

            // 3. Clear Choice Instruction Header
            val subY = deckRect.top + 47f * density
            drawFittedText(canvas, "CHOOSE ACTION:", deckRect.centerX(), subY, contentMaxW, 6.8f * density, isBold = true, textColor = Color.parseColor("#B3A7D6"))

            // 4. Two Choice Buttons
            val btnW = min(contentMaxW, 118f * density)
            val btnH = 20f * density
            val btn1Y = deckRect.top + 67f * density
            val btn2Y = deckRect.top + 92f * density

            choiceBtn1Rect.set(deckRect.centerX() - btnW / 2f, btn1Y - btnH / 2f, deckRect.centerX() + btnW / 2f, btn1Y + btnH / 2f)
            choiceBtn2Rect.set(deckRect.centerX() - btnW / 2f, btn2Y - btnH / 2f, deckRect.centerX() + btnW / 2f, btn2Y + btnH / 2f)

            if (phase == OopsPhase.CHOOSING_11_OPTION) {
                // Button 1: MOVE +11
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#33FFD54F")
                canvas.drawRoundRect(choiceBtn1Rect, 10f * density, 10f * density, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * density
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawRoundRect(choiceBtn1Rect, 10f * density, 10f * density, paint)
                drawFittedText(canvas, "🏃 MOVE +11", deckRect.centerX(), btn1Y, btnW - 6f * density, 7.2f * density, isBold = true, textColor = Color.parseColor("#FFD54F"))

                // Button 2: SWAP PLACES
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#3300E5FF")
                canvas.drawRoundRect(choiceBtn2Rect, 10f * density, 10f * density, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * density
                paint.color = Color.parseColor("#00E5FF")
                canvas.drawRoundRect(choiceBtn2Rect, 10f * density, 10f * density, paint)
                drawFittedText(canvas, "🔀 SWAP PLACES", deckRect.centerX(), btn2Y, btnW - 6f * density, 7.2f * density, isBold = true, textColor = Color.parseColor("#00E5FF"))
            } else {
                // Button 1: MOVE +10
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#33FFD54F")
                canvas.drawRoundRect(choiceBtn1Rect, 10f * density, 10f * density, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * density
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawRoundRect(choiceBtn1Rect, 10f * density, 10f * density, paint)
                drawFittedText(canvas, "🏃 MOVE +10", deckRect.centerX(), btn1Y, btnW - 6f * density, 7.2f * density, isBold = true, textColor = Color.parseColor("#FFD54F"))

                // Button 2: MOVE -1
                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#33FF9100")
                canvas.drawRoundRect(choiceBtn2Rect, 10f * density, 10f * density, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * density
                paint.color = Color.parseColor("#FF9100")
                canvas.drawRoundRect(choiceBtn2Rect, 10f * density, 10f * density, paint)
                drawFittedText(canvas, "↩️ MOVE -1", deckRect.centerX(), btn2Y, btnW - 6f * density, 7.2f * density, isBold = true, textColor = Color.parseColor("#FF9100"))
            }
        } else {
            // 1. Top Badge Pill
            val badgeText = card.badge
            val badgeH = 15f * density
            val badgeY = deckRect.top + 13f * density
            val badgePillW = min(contentMaxW, 76f * density)
            val badgeR = RectF(deckRect.centerX() - badgePillW / 2f, badgeY - badgeH / 2f, deckRect.centerX() + badgePillW / 2f, badgeY + badgeH / 2f)
            paint.style = Paint.Style.FILL
            paint.color = (cardColor and 0x00FFFFFF) or 0x33000000
            canvas.drawRoundRect(badgeR, 7.5f * density, 7.5f * density, paint)
            drawFittedText(canvas, badgeText, deckRect.centerX(), badgeY, badgePillW - 6f * density, 7f * density, isBold = true, textColor = cardColor)

            // 2. Big Card Value / Title
            val titleY = deckRect.centerY() - 5f * density
            val titleBaseSize = if (card.type == OopsCardType.OOPS) cellSize * 0.78f else cellSize * 0.95f
            drawFittedText(canvas, card.title, deckRect.centerX(), titleY, contentMaxW, titleBaseSize, isBold = true, textColor = cardColor)

            // 3. Subtitle / Action explanation (cleanly fitted inside card boundaries)
            val subY = deckRect.centerY() + 14f * density
            val displaySub = if (chosenMoveDelta != null) {
                if (chosenMoveDelta!! > 0) "+${chosenMoveDelta} FORWARD" else "${chosenMoveDelta} BACKWARD ↩️"
            } else {
                card.subtitle
            }
            drawFittedText(canvas, displaySub, deckRect.centerX(), subY, contentMaxW, 7.5f * density, isBold = false, textColor = Color.WHITE)

            // 4. Prompt Pill when choosing pawn/target
            val promptText = when (phase) {
                OopsPhase.CHOOSING_PAWN -> "TAP PAWN 👆"
                OopsPhase.CHOOSING_SWITCH_OWN -> "CHOOSE OWN PAWN 👆"
                OopsPhase.CHOOSING_OOPS_TARGET -> "TAP TARGET 💥"
                OopsPhase.CHOOSING_SWITCH_OPPONENT -> "TAP TARGET 🔀"
                else -> null
            }

            if (promptText != null) {
                val promptColor = when (phase) {
                    OopsPhase.CHOOSING_OOPS_TARGET -> Color.parseColor("#FF2A6D")
                    OopsPhase.CHOOSING_SWITCH_OPPONENT -> Color.parseColor("#00E5FF")
                    OopsPhase.CHOOSING_SWITCH_OWN -> Color.parseColor("#00E5FF")
                    else -> Color.parseColor("#FFD54F")
                }
                val promptY = deckRect.bottom - 11f * density
                val promptPillH = 16f * density
                val promptPillW = min(contentMaxW, 95f * density)
                val promptR = RectF(deckRect.centerX() - promptPillW / 2f, promptY - promptPillH / 2f, deckRect.centerX() + promptPillW / 2f, promptY + promptPillH / 2f)

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#160E2E")
                canvas.drawRoundRect(promptR, 8f * density, 8f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * density
                paint.color = promptColor
                canvas.drawRoundRect(promptR, 8f * density, 8f * density, paint)

                drawFittedText(canvas, promptText, deckRect.centerX(), promptY, promptPillW - 6f * density, 7f * density, isBold = true, textColor = promptColor)
            }
        }

        canvas.restore()
    }

    private fun drawPawns(canvas: Canvas) {
        val pulse = 0.5f + 0.5f * sin(pulseTimer * 6f)

        pawns.forEach { p ->
            val isEligible = p.isEligible
            val bounceY = if (isEligible) -abs(sin(pulseTimer * 8f)) * 5f * density else 0f
            val pos = PointF(p.visualPos.x, p.visualPos.y + bounceY)
            val puckR = if (p.location == OopsPawnLocation.IN_CASTLE) cellSize * 0.22f else cellSize * 0.36f

            // Eligible Pulsing Glow Halo and Pointer Arrow
            if (isEligible) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = (2.5f + pulse * 2f) * density
                paint.color = Color.parseColor("#FFD54F")
                canvas.drawCircle(pos.x, pos.y, puckR + (4f + pulse * 5f) * density, paint)

                paint.style = Paint.Style.FILL
                paint.color = Color.parseColor("#33FFD54F")
                canvas.drawCircle(pos.x, pos.y, puckR + (4f + pulse * 5f) * density, paint)

                // Pointer Arrow
                paint.color = Color.parseColor("#FFD54F")
                val arrowTipY = pos.y - puckR - (5f + pulse * 3f) * density
                val arrowPath = Path().apply {
                    moveTo(pos.x, arrowTipY)
                    lineTo(pos.x - 4f * density, arrowTipY - 6f * density)
                    lineTo(pos.x + 4f * density, arrowTipY - 6f * density)
                    close()
                }
                canvas.drawPath(arrowPath, paint)
            }

            // 1. Drop shadow
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#60000000")
            canvas.drawOval(RectF(pos.x - puckR, pos.y + puckR * 0.2f, pos.x + puckR, pos.y + puckR * 0.95f), paint)

            // 2. Lower 3D Bevel
            paint.color = p.owner.darkColor
            canvas.drawOval(RectF(pos.x - puckR, pos.y - puckR * 0.35f, pos.x + puckR, pos.y + puckR * 0.85f), paint)

            // 3. Body
            paint.color = p.owner.color
            canvas.drawCircle(pos.x, pos.y, puckR, paint)

            // 4. Rim
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.6f * density
            paint.color = if (isEligible) Color.parseColor("#FFF9C4") else Color.WHITE
            canvas.drawCircle(pos.x, pos.y, puckR, paint)

            // 5. Specular highlight
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#66FFFFFF")
            canvas.drawCircle(pos.x - puckR * 0.28f, pos.y - puckR * 0.28f, puckR * 0.35f, paint)

            // 6. Pawn Number (1..4)
            paint.color = Color.WHITE
            paint.textSize = if (p.location == OopsPawnLocation.IN_CASTLE) 6.5f * density else 8.5f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            val fm = paint.fontMetrics
            val baseline = pos.y - (fm.ascent + fm.descent) / 2f
            canvas.drawText("${p.id + 1}", pos.x, baseline, paint)
            paint.isFakeBoldText = false
        }
    }

    private fun drawActionControls(canvas: Canvas) {
        val activeP = getCurrentPlayer() ?: return
        val (actionRect, rotDeg) = getActiveActionInfo()

        canvas.save()
        if (rotDeg != 0f) {
            canvas.rotate(rotDeg, actionRect.centerX(), actionRect.centerY())
        }

        val pulse = 0.5f + 0.5f * sin(pulseTimer * 4.5f)

        // Container Pill
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1336")
        canvas.drawRoundRect(actionRect, 22f * density, 22f * density, paint)

        // Glowing border
        paint.style = Paint.Style.STROKE
        val isChoosing = phase in listOf(
            OopsPhase.CHOOSING_11_OPTION,
            OopsPhase.CHOOSING_10_OPTION,
            OopsPhase.CHOOSING_PAWN,
            OopsPhase.CHOOSING_SWITCH_OWN,
            OopsPhase.CHOOSING_OOPS_TARGET,
            OopsPhase.CHOOSING_SWITCH_OPPONENT
        )
        paint.strokeWidth = if (isChoosing) (2.5f + pulse * 1.5f) * density else 1.8f * density
        paint.color = if (isChoosing) Color.parseColor("#FFD54F") else activeP.id.color
        canvas.drawRoundRect(actionRect, 22f * density, 22f * density, paint)

        // Action Text
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f * density
        paint.isFakeBoldText = true
        paint.color = if (isChoosing) Color.parseColor("#FFD54F") else Color.WHITE

        val btnText = when (phase) {
            OopsPhase.WAITING_DRAW -> if (activeP.isBot) "🤖 BOT DRAWING..." else "DRAW CARD 🃏"
            OopsPhase.CARD_REVEAL -> currentCard?.title ?: "CARD DRAWN"
            OopsPhase.CHOOSING_11_OPTION -> "CHOOSE: +11 OR SWAP 👆"
            OopsPhase.CHOOSING_10_OPTION -> "CHOOSE: +10 OR -1 👆"
            OopsPhase.CHOOSING_PAWN -> "CHOOSE PAWN 👆"
            OopsPhase.CHOOSING_SWITCH_OWN -> "CHOOSE YOUR PAWN 👆"
            OopsPhase.CHOOSING_OOPS_TARGET -> "💥 CHOOSE TARGET!"
            OopsPhase.CHOOSING_SWITCH_OPPONENT -> "🔀 CHOOSE TARGET!"
            OopsPhase.ANIMATING -> "MOVING..."
            OopsPhase.TURN_ENDING -> "NEXT TURN..."
        }

        val fm = paint.fontMetrics
        val baseline = actionRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(btnText, actionRect.centerX(), baseline, paint)
        paint.isFakeBoldText = false

        canvas.restore()
    }

    private fun drawFeedbackBanner(canvas: Canvas) {
        if (feedbackTimer <= 0f || feedbackMessage.isEmpty()) return

        paint.textSize = 12f * density
        val textW = paint.measureText(feedbackMessage)
        val bannerW = (textW + 36f * density).coerceAtLeast(160f * density).coerceAtMost(width - 24f * density)
        val bannerH = 34f * density
        val cx = width / 2f
        val cy = boardTop - 22f * density
        val r = RectF(cx - bannerW / 2f, cy - bannerH / 2f, cx + bannerW / 2f, cy + bannerH / 2f)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#EE150F28")
        canvas.drawRoundRect(r, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = Color.parseColor("#FFD54F")
        canvas.drawRoundRect(r, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 11.5f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(feedbackMessage, cx, baseline, paint)
        paint.isFakeBoldText = false
    }

    private fun drawPlayerStatusCards(canvas: Canvas) {
        val activeP = getCurrentPlayer()

        if (totalPlayers <= 2) {
            val cardLeft = (width - cardW) / 2f
            val p2 = players.getOrNull(1)
            if (p2 != null) {
                val r2 = RectF(cardLeft, topCardTop, cardLeft + cardW, topCardTop + cardH)
                drawStatusCard(canvas, p2, r2, (p2 == activeP), isTopPlayer = true)
            }
            val p1 = players.getOrNull(0)
            if (p1 != null) {
                val r1 = RectF(cardLeft, bottomCardTop, cardLeft + cardW, bottomCardTop + cardH)
                drawStatusCard(canvas, p1, r1, (p1 == activeP), isTopPlayer = false)
            }
        } else {
            val gap = 8f * density
            val halfW = (cardW - gap) / 2f
            val left1 = (width - cardW) / 2f
            val left2 = left1 + halfW + gap

            val p3 = players.getOrNull(2)
            val p2 = players.getOrNull(1)
            if (p3 != null) drawStatusCard(canvas, p3, RectF(left1, topCardTop, left1 + halfW, topCardTop + cardH), (p3 == activeP), isTopPlayer = true)
            if (p2 != null) drawStatusCard(canvas, p2, RectF(left2, topCardTop, left2 + halfW, topCardTop + cardH), (p2 == activeP), isTopPlayer = true)

            val p1 = players.getOrNull(0)
            val p4 = players.getOrNull(3)
            if (p1 != null) drawStatusCard(canvas, p1, RectF(left1, bottomCardTop, left1 + halfW, bottomCardTop + cardH), (p1 == activeP), isTopPlayer = false)
            if (p4 != null) drawStatusCard(canvas, p4, RectF(left2, bottomCardTop, left2 + halfW, bottomCardTop + cardH), (p4 == activeP), isTopPlayer = false)
        }
    }

    private fun drawStatusCard(canvas: Canvas, player: Player, rect: RectF, isTurn: Boolean, isTopPlayer: Boolean) {
        canvas.save()
        if (isTopPlayer && !player.isBot) {
            canvas.rotate(180f, rect.centerX(), rect.centerY())
        }

        // Card container
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#150F28")
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)

        // Turn border
        paint.style = Paint.Style.STROKE
        val pulse = 0.5f + 0.5f * sin(pulseTimer * 5f)
        paint.strokeWidth = if (isTurn) (2f + pulse * 1.5f) * density else 1f * density
        paint.color = if (isTurn) player.id.color else Color.parseColor("#342852")
        canvas.drawRoundRect(rect, 14f * density, 14f * density, paint)

        // Avatar icon
        val avR = 14f * density
        val avCx = rect.left + 22f * density
        val avCy = rect.centerY()
        paint.style = Paint.Style.FILL
        paint.color = player.id.color
        canvas.drawCircle(avCx, avCy, avR, paint)

        paint.color = Color.WHITE
        paint.textSize = 10f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val afm = paint.fontMetrics
        val avBase = avCy - (afm.ascent + afm.descent) / 2f
        canvas.drawText(if (player.isBot) "🤖" else "${player.id.id}", avCx, avBase, paint)

        // Player Name & Status
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 12f * density
        val name = if (player.isBot) "Robi the Bot" else getPlayerDisplayName(player)
        canvas.drawText(name, avCx + avR + 8f * density, rect.centerY() - 2f * density, paint)

        paint.textSize = 9f * density
        paint.color = if (isTurn) Color.parseColor("#00E5FF") else Color.parseColor("#8E80BE")
        val status = if (isTurn) "YOUR TURN" else "WAITING"
        canvas.drawText(status, avCx + avR + 8f * density, rect.centerY() + 11f * density, paint)

        // Castle Goal count pill
        val castleCount = pawns.count { it.owner == player.id && it.location == OopsPawnLocation.IN_CASTLE }
        val pillW = 82f * density
        val pillH = 24f * density
        val pillR = RectF(rect.right - pillW - 8f * density, rect.centerY() - pillH / 2f, rect.right - 8f * density, rect.centerY() + pillH / 2f)

        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) (player.id.color and 0x00FFFFFF) or 0x33000000 else Color.parseColor("#1F1836")
        canvas.drawRoundRect(pillR, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = if (isTurn) player.id.color else Color.parseColor("#3B2E68")
        canvas.drawRoundRect(pillR, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = if (isTurn) Color.parseColor("#FFD54F") else Color.parseColor("#CDC4EB")
        paint.textSize = 10.5f * density
        paint.textAlign = Paint.Align.CENTER
        val pfm = paint.fontMetrics
        val pillBase = pillR.centerY() - (pfm.ascent + pfm.descent) / 2f
        canvas.drawText("CASTLE $castleCount/$PAWNS_PER_PLAYER", pillR.centerX(), pillBase, paint)

        paint.isFakeBoldText = false
        canvas.restore()
    }

    override fun getPlayerScoreString(player: Player): String {
        val castleCount = pawns.count { it.owner == player.id && it.location == OopsPawnLocation.IN_CASTLE }
        return "Castle $castleCount/$PAWNS_PER_PLAYER"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return pawns.count { it.owner == player.id && it.location == OopsPawnLocation.IN_CASTLE }
    }
}

