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
 * Pizza Memory Party Game Engine
 * Reverse-engineered & enhanced from 41_PIZZA in reference APK.
 *
 * Game Mechanics:
 * 1. MEMORIZE PHASE (3.5s):
 *    - An order ticket appears in the center showing the customer's pizza recipe
 *      (e.g., 2x Pepperoni, 2x Cheese, 1x Mushroom, 1x Basil - total 6 toppings).
 * 2. BAKING / ASSEMBLE PHASE:
 *    - Order ticket flips away; central 6 ingredient bowls appear.
 *    - Players race to tap ingredient bowls to place toppings onto their pizza crust (max 6 toppings).
 *    - Players can tap a placed topping on their pizza to remove it.
 *    - When satisfied, player hits their brass Service Bell (🛎️) to lock in their pizza!
 * 3. EVALUATION PHASE:
 *    - Order ticket reveals again.
 *    - The player who rang first with the 100% correct recipe wins the round (+1 point).
 *    - If incorrect, other players can still ring or the closest accurate pizza wins.
 * 4. 5 ROUNDS MATCH:
 *    - Central HUD shows "ROUND X/5" and player scores.
 *    - After round 5, final victory popup is displayed with starblast celebration.
 *
 * Full support for 1, 2, 3, and 4 players with proper opposite rotations (180° for top players).
 */
class PizzaMemoryEngine(
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

    enum class GamePhase {
        ORDER_MEMORIZE,
        PIZZA_BAKING,
        ORDER_EVALUATE
    }

    var currentPhase = GamePhase.ORDER_MEMORIZE
    private var memorizeTimer = 3.6f
    private val maxMemorizeTime = 3.6f
    private var evaluateTimer = -1f

    // Pizza Ingredients (from 41_PIZZA in APK)
    enum class PizzaIngredient(
        val displayName: String,
        val emoji: String,
        val color: Int
    ) {
        PEPPERONI("Pepperoni", "🍕", Color.parseColor("#E64A19")),
        MOZZARELLA("Cheese", "🧀", Color.parseColor("#FFD54F")),
        MUSHROOM("Mushroom", "🍄", Color.parseColor("#BCAAA4")),
        TOMATO("Tomato", "🍅", Color.parseColor("#F44336")),
        BASIL("Basil", "🌿", Color.parseColor("#4CAF50")),
        OLIVE("Olive", "🫒", Color.parseColor("#546E7A"))
    }

    // Capacity of toppings per pizza
    val maxToppingsPerPizza = 6

    // Target Recipe for the Round: map of Ingredient -> required count (sums to 6)
    val roundRecipe = mutableMapOf<PizzaIngredient, Int>()

    // Ingredient Bowl in the center
    data class IngredientBowl(
        val ingredient: PizzaIngredient,
        var bounds: RectF = RectF(),
        var animScale: Float = 1.0f
    )

    val ingredientBowls = mutableListOf<IngredientBowl>()

    // Placed Topping on a player's pizza
    data class PlacedTopping(
        val ingredient: PizzaIngredient,
        val slotIndex: Int,
        var relX: Float = 0f, // relative to pizza center
        var relY: Float = 0f,
        var scaleAnim: Float = 0.2f
    )

    // Drag & Drop tracking item
    data class DraggingItem(
        val pointerId: Int,
        val ingredient: PizzaIngredient,
        var currentX: Float,
        var currentY: Float,
        val startX: Float,
        val startY: Float
    )
    val activeDrags = mutableMapOf<Int, DraggingItem>()

    // Player Pizza Station
    class PlayerPizzaStation(
        val player: Player,
        var centerX: Float = 0f,
        var centerY: Float = 0f,
        var radius: Float = 0f,
        var rotationDeg: Float = 0f,
        val toppings: MutableList<PlacedTopping> = mutableListOf(),
        var bellCenter: PointF = PointF(),
        var bellRadius: Float = 0f,
        var isBellRung: Boolean = false,
        var ringOrder: Int = -1, // 1st to ring, 2nd, etc.
        var isCorrect: Boolean = false,
        var accuracyPercent: Int = 0,
        var bellPulseAnim: Float = 1.0f,
        var shakeTimer: Float = 0f,
        var notMatchTimer: Float = 0f
    )

    val playerStations = mutableListOf<PlayerPizzaStation>()
    private var bellsRungCount = 0

    // Bot AI state
    private var botNextActionTimer = 0f
    private val botTargetRecipe = mutableListOf<PizzaIngredient>()
    private var botMistakeIndex = -1

    // Canvas Paints
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Layout areas
    private val orderTicketRect = RectF()
    private val hudRect = RectF()

    init {
        initGame()
    }

    override fun initGame() {
        isGameOver = false
        isRoundEnding = false
        roundEndDelayTimer = -1f
        roundWonTransitionTimer = -1f
        pendingWinner = null
        roundWinningPlayer = null
        currentRound = 1
        playerWins.clear()

        players.forEach {
            it.score = 0
            it.isAlive = true
            playerWins[it.id] = 0
        }

        if (width > 0f && height > 0f) {
            setupStationsLayout()
            startNewRound()
        }
    }

    override fun onSurfaceChanged(w: Float, h: Float) {
        super.onSurfaceChanged(w, h)
        setupStationsLayout()
        startNewRound()
    }

    override fun getPlayerScoreString(player: Player): String {
        val score = playerWins[player.id] ?: player.score
        return "$score / $maxRounds ${if (score == 1) "Round" else "Rounds"}"
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return playerWins[player.id] ?: player.score
    }

    override fun shouldShowPlayerScoresOnWinnerDialog(): Boolean = true

    /**
     * Sizing & Layout for 1, 2, 3, or 4 players:
     * - Clears the top header & pause button with generous safe margin.
     * - Central zone holds either the Order Ticket (Memorize phase) or Ingredient Bowls (Baking phase).
     * - Top players are rotated 180° so opposing players face each other across the phone.
     */
    private fun setupStationsLayout() {
        playerStations.clear()
        ingredientBowls.clear()

        val topSafeArea = 82f * density
        val bottomSafeArea = 32f * density

        val hudH = 36f * density
        val hudCenterY = (topSafeArea + (height - bottomSafeArea)) * 0.5f
        hudRect.set(width * 0.12f, hudCenterY - hudH * 0.5f, width * 0.88f, hudCenterY + hudH * 0.5f)

        val availableH = height - topSafeArea - bottomSafeArea
        val pCount = players.size

        if (pCount <= 2) {
            // 1P or 2P Layout:
            // P1 at bottom (0°), P2 at top (180°)
            val p1 = players[0]
            val p2 = players.getOrElse(1) { Player(PlayerId.PLAYER_2, isBot = true) }

            val stationH = availableH * 0.35f
            val pizzaRadius = min(width * 0.28f, stationH * 0.42f)
            val bellRadius = 26f * density

            // P1 (Bottom)
            val p1CenterY = height - bottomSafeArea - pizzaRadius - 10f * density
            val station1 = PlayerPizzaStation(
                player = p1,
                centerX = width * 0.40f,
                centerY = p1CenterY,
                radius = pizzaRadius,
                rotationDeg = 0f,
                bellCenter = PointF(width * 0.80f, p1CenterY),
                bellRadius = bellRadius
            )

            // P2 (Top, rotated 180°)
            val p2CenterY = topSafeArea + pizzaRadius + 10f * density
            val station2 = PlayerPizzaStation(
                player = p2,
                centerX = width * 0.60f,
                centerY = p2CenterY,
                radius = pizzaRadius,
                rotationDeg = 180f,
                bellCenter = PointF(width * 0.20f, p2CenterY),
                bellRadius = bellRadius
            )

            playerStations.add(station1)
            playerStations.add(station2)

            // Center area for Ticket / Bowls
            val centerTop = p2CenterY + pizzaRadius + 14f * density
            val centerBottom = p1CenterY - pizzaRadius - 14f * density
            orderTicketRect.set(width * 0.10f, centerTop, width * 0.90f, centerBottom)

            setupIngredientBowls(orderTicketRect)

        } else if (pCount == 3) {
            // 3P Layout:
            // Top: P2 (Top-Left, 180°), P3 (Top-Right, 180°)
            // Bottom: P1 (Bottom-Center, 0°)
            val p1 = players[0]
            val p2 = players[1]
            val p3 = players[2]

            val pizzaRadiusTop = min(width * 0.20f, availableH * 0.15f)
            val pizzaRadiusBottom = min(width * 0.26f, availableH * 0.16f)
            val bellRadius = 22f * density

            val topY = topSafeArea + pizzaRadiusTop + 10f * density
            val station2 = PlayerPizzaStation(
                player = p2,
                centerX = width * 0.25f,
                centerY = topY,
                radius = pizzaRadiusTop,
                rotationDeg = 180f,
                bellCenter = PointF(width * 0.25f, topY + pizzaRadiusTop + bellRadius * 1.05f),
                bellRadius = bellRadius
            )

            val station3 = PlayerPizzaStation(
                player = p3,
                centerX = width * 0.75f,
                centerY = topY,
                radius = pizzaRadiusTop,
                rotationDeg = 180f,
                bellCenter = PointF(width * 0.75f, topY + pizzaRadiusTop + bellRadius * 1.05f),
                bellRadius = bellRadius
            )

            val bottomY = height - bottomSafeArea - pizzaRadiusBottom - 10f * density
            val station1 = PlayerPizzaStation(
                player = p1,
                centerX = width * 0.40f,
                centerY = bottomY,
                radius = pizzaRadiusBottom,
                rotationDeg = 0f,
                bellCenter = PointF(width * 0.80f, bottomY),
                bellRadius = bellRadius
            )

            playerStations.add(station1)
            playerStations.add(station2)
            playerStations.add(station3)

            val centerTop = topY + pizzaRadiusTop + bellRadius * 2.2f + 8f * density
            val centerBottom = bottomY - pizzaRadiusBottom - 14f * density
            orderTicketRect.set(width * 0.08f, centerTop, width * 0.92f, centerBottom)

            setupIngredientBowls(orderTicketRect)

        } else {
            // 4P Layout: 4 Corners!
            // Top-Left: P2 (180°), Top-Right: P3 (180°)
            // Bottom-Left: P1 (0°), Bottom-Right: P4 (0°)
            val p1 = players[0]
            val p2 = players[1]
            val p3 = players[2]
            val p4 = players[3]

            val pizzaRadius = min(width * 0.20f, availableH * 0.14f)
            val bellRadius = 22f * density

            val topY = topSafeArea + pizzaRadius + 8f * density
            val bottomY = height - bottomSafeArea - pizzaRadius - 8f * density

            val leftX = width * 0.26f
            val rightX = width * 0.74f

            val station2 = PlayerPizzaStation(
                player = p2,
                centerX = leftX,
                centerY = topY,
                radius = pizzaRadius,
                rotationDeg = 180f,
                bellCenter = PointF(leftX, topY + pizzaRadius + bellRadius * 1.1f),
                bellRadius = bellRadius
            )

            val station3 = PlayerPizzaStation(
                player = p3,
                centerX = rightX,
                centerY = topY,
                radius = pizzaRadius,
                rotationDeg = 180f,
                bellCenter = PointF(rightX, topY + pizzaRadius + bellRadius * 1.1f),
                bellRadius = bellRadius
            )

            val station1 = PlayerPizzaStation(
                player = p1,
                centerX = leftX,
                centerY = bottomY,
                radius = pizzaRadius,
                rotationDeg = 0f,
                bellCenter = PointF(leftX, bottomY - pizzaRadius - bellRadius * 1.1f),
                bellRadius = bellRadius
            )

            val station4 = PlayerPizzaStation(
                player = p4,
                centerX = rightX,
                centerY = bottomY,
                radius = pizzaRadius,
                rotationDeg = 0f,
                bellCenter = PointF(rightX, bottomY - pizzaRadius - bellRadius * 1.1f),
                bellRadius = bellRadius
            )

            playerStations.add(station1)
            playerStations.add(station2)
            playerStations.add(station3)
            playerStations.add(station4)

            val centerTop = topY + pizzaRadius + bellRadius * 2.2f + 6f * density
            val centerBottom = bottomY - pizzaRadius - bellRadius * 2.2f - 6f * density
            orderTicketRect.set(width * 0.08f, centerTop, width * 0.92f, centerBottom)

            setupIngredientBowls(orderTicketRect)
        }
    }

    /**
     * Arrange 6 ingredient bowls in the central area (2 rows x 3 columns)
     * Leaves dedicated clearance for the Central HUD so that top item names are never obscured!
     */
    private fun setupIngredientBowls(rect: RectF) {
        ingredientBowls.clear()
        val allIngredients = PizzaIngredient.values()

        val cols = 3
        val bowlMargin = 6f * density
        val cellW = (rect.width() - bowlMargin * (cols + 1)) / cols

        // Reserve space in the middle for HUD capsule so it NEVER overlaps bowl cards or names!
        val hudHalfH = hudRect.height() * 0.5f + 4f * density
        val cy = hudRect.centerY()

        val topRowTop = rect.top + bowlMargin
        val topRowBottom = cy - hudHalfH

        val bottomRowTop = cy + hudHalfH
        val bottomRowBottom = rect.bottom - bowlMargin

        for (i in allIngredients.indices) {
            val r = i / cols
            val c = i % cols
            val left = rect.left + bowlMargin + c * (cellW + bowlMargin)
            val right = left + cellW

            val top = if (r == 0) topRowTop else bottomRowTop
            val bottom = if (r == 0) topRowBottom else bottomRowBottom

            ingredientBowls.add(
                IngredientBowl(
                    ingredient = allIngredients[i],
                    bounds = RectF(left, top, right, bottom)
                )
            )
        }
    }

    /**
     * Generate a new random customer pizza order for the round
     */
    private fun startNewRound() {
        currentPhase = GamePhase.ORDER_MEMORIZE
        memorizeTimer = maxMemorizeTime
        evaluateTimer = -1f
        bellsRungCount = 0
        roundWinningPlayer = null
        activeDrags.clear()

        // Reset player stations
        playerStations.forEach { station ->
            station.toppings.clear()
            station.isBellRung = false
            station.ringOrder = -1
            station.isCorrect = false
            station.accuracyPercent = 0
            station.bellPulseAnim = 1.0f
            station.shakeTimer = 0f
            station.notMatchTimer = 0f
        }

        // Generate Recipe: pick 2 to 4 unique ingredients, total 3 to 6 toppings
        roundRecipe.clear()
        val allIngredients = PizzaIngredient.values().toList().shuffled(random)

        // Decide total toppings for this round's pizza (3 to 6, supporting 1 1 1, 2 3 1, etc.)
        val targetTotal = when (currentRound) {
            1 -> random.nextInt(3, 5) // 3 or 4 toppings
            2 -> random.nextInt(3, 6) // 3, 4, or 5 toppings
            else -> random.nextInt(3, 7) // 3, 4, 5, or 6 toppings
        }

        // Decide number of unique ingredients (2 to min(4, targetTotal))
        val maxUnique = min(4, targetTotal)
        val numUnique = if (targetTotal == 3) {
            if (random.nextBoolean()) 3 else 2
        } else {
            random.nextInt(2, maxUnique + 1)
        }

        val selected = allIngredients.take(numUnique)
        var remaining = targetTotal

        for (i in selected.indices) {
            if (i == selected.size - 1) {
                roundRecipe[selected[i]] = remaining
            } else {
                val slotsLeftForOthers = selected.size - 1 - i
                val maxForThis = remaining - slotsLeftForOthers
                val count = random.nextInt(1, min(3, maxForThis) + 1)
                roundRecipe[selected[i]] = count
                remaining -= count
            }
        }

        soundManager.play(SoundManager.SoundType.START)
        setupBotAIForRound()
    }

    private fun setupBotAIForRound() {
        botTargetRecipe.clear()
        // Flatten recipe into list of ingredients
        for ((ing, count) in roundRecipe) {
            repeat(count) {
                botTargetRecipe.add(ing)
            }
        }
        botTargetRecipe.shuffle(random)

        // Bot mistake logic based on difficulty
        val mistakeChance = when (botDifficulty) {
            0 -> 0.28f // Easy: 28% chance of placing 1 wrong topping
            1 -> 0.12f // Medium: 12% chance
            else -> 0.03f // Hard: 3% chance
        }

        if (random.nextFloat() < mistakeChance && botTargetRecipe.isNotEmpty()) {
            botMistakeIndex = random.nextInt(botTargetRecipe.size)
            val wrongIng = PizzaIngredient.values().first { it != botTargetRecipe[botMistakeIndex] }
            botTargetRecipe[botMistakeIndex] = wrongIng
        } else {
            botMistakeIndex = -1
        }

        botNextActionTimer = 0.5f
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
                    startNewRound()
                } else {
                    triggerMatchOver()
                }
            }
            return
        }

        // Animate bowl taps
        ingredientBowls.forEach { bowl ->
            if (bowl.animScale > 1.0f) {
                bowl.animScale = max(1.0f, bowl.animScale - dt * 3f)
            }
        }

        // Animate player stations
        playerStations.forEach { station ->
            if (station.shakeTimer > 0f) {
                station.shakeTimer = max(0f, station.shakeTimer - dt)
            }
            if (station.notMatchTimer > 0f) {
                station.notMatchTimer = max(0f, station.notMatchTimer - dt)
            }
            val totalRequired = roundRecipe.values.sum()
            if (station.toppings.size == totalRequired && totalRequired > 0 && !station.isBellRung) {
                station.bellPulseAnim = 1.0f + 0.12f * sin(System.currentTimeMillis() * 0.008f).toFloat()
            } else {
                station.bellPulseAnim = 1.0f
            }
            station.toppings.forEach { t ->
                if (t.scaleAnim < 1.0f) {
                    t.scaleAnim = min(1.0f, t.scaleAnim + dt * 5f)
                }
            }
        }

        // State Machine
        when (currentPhase) {
            GamePhase.ORDER_MEMORIZE -> {
                memorizeTimer -= dt
                if (memorizeTimer <= 0f) {
                    // Transition to Baking Phase
                    currentPhase = GamePhase.PIZZA_BAKING
                    soundManager.play(SoundManager.SoundType.BOUNCE)
                    triggerVibration(40)
                }
            }
            GamePhase.PIZZA_BAKING -> {
                // Update Bot AI
                updateBotAI(dt)
            }
            GamePhase.ORDER_EVALUATE -> {
                if (evaluateTimer > 0f) {
                    evaluateTimer -= dt
                    if (evaluateTimer <= 0f) {
                        finalizeRoundWinner()
                    }
                }
            }
        }
    }

    private fun updateBotAI(dt: Float) {
        val botStation = playerStations.firstOrNull { it.player.isBot && !it.isBellRung } ?: return

        botNextActionTimer -= dt
        if (botNextActionTimer <= 0f) {
            if (botStation.toppings.size < maxToppingsPerPizza && botStation.toppings.size < botTargetRecipe.size) {
                // Bot adds the next topping
                val nextIng = botTargetRecipe[botStation.toppings.size]
                addToppingToStation(botStation, nextIng)

                // Delay to next topping based on difficulty
                val stepDelay = when (botDifficulty) {
                    0 -> 0.75f + random.nextFloat() * 0.35f // Easy
                    1 -> 0.50f + random.nextFloat() * 0.25f // Medium
                    else -> 0.32f + random.nextFloat() * 0.15f // Hard
                }
                botNextActionTimer = stepDelay
            } else if (!botStation.isBellRung) {
                // Bot rings the bell!
                val ringDelay = when (botDifficulty) {
                    0 -> 0.6f + random.nextFloat() * 0.4f
                    1 -> 0.35f + random.nextFloat() * 0.25f
                    else -> 0.15f + random.nextFloat() * 0.15f
                }
                botNextActionTimer = ringDelay
                ringBellForStation(botStation)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || isRoundEnding || roundWonTransitionTimer >= 0f) return true
        if (currentPhase != GamePhase.PIZZA_BAKING) return true

        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pid = event.getPointerId(actionIndex)
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)

                // 1. Check if Service Bell was tapped
                for (station in playerStations) {
                    if (station.player.isBot || station.isBellRung) continue
                    val distToBell = hypot(x - station.bellCenter.x, y - station.bellCenter.y)
                    if (distToBell <= station.bellRadius * 1.55f) {
                        ringBellForStation(station)
                        return true
                    }
                }

                // 2. Check if a placed topping on player's pizza was tapped to remove it
                for (station in playerStations) {
                    if (station.player.isBot || station.isBellRung) continue
                    val distToPizza = hypot(x - station.centerX, y - station.centerY)
                    if (distToPizza <= station.radius * 1.05f) {
                        if (station.toppings.isNotEmpty()) {
                            // Find the specific topping closest to (x, y) where the user clicked
                            var bestIndex = -1
                            var bestDist = Float.MAX_VALUE
                            var bestScreenX = station.centerX
                            var bestScreenY = station.centerY

                            val rad = Math.toRadians(station.rotationDeg.toDouble())
                            val cosA = cos(rad).toFloat()
                            val sinA = sin(rad).toFloat()

                            for (i in station.toppings.indices) {
                                val t = station.toppings[i]
                                val sx = station.centerX + t.relX * cosA - t.relY * sinA
                                val sy = station.centerY + t.relX * sinA + t.relY * cosA
                                val d = hypot(x - sx, y - sy)
                                if (d < bestDist) {
                                    bestDist = d
                                    bestIndex = i
                                    bestScreenX = sx
                                    bestScreenY = sy
                                }
                            }

                            if (bestIndex >= 0 && bestDist <= station.radius * 0.65f) {
                                val removed = station.toppings.removeAt(bestIndex)
                                particles.spawnExplosion(
                                    bestScreenX,
                                    bestScreenY,
                                    removed.ingredient.color,
                                    16
                                )
                                soundManager.play(SoundManager.SoundType.CLICK)
                                triggerVibration(20)
                            }
                        }
                        return true
                    }
                }

                // 3. Check if an Ingredient Bowl was touched to start Drag & Drop
                for (bowl in ingredientBowls) {
                    if (bowl.bounds.contains(x, y)) {
                        bowl.animScale = 1.25f
                        activeDrags[pid] = DraggingItem(
                            pointerId = pid,
                            ingredient = bowl.ingredient,
                            currentX = x,
                            currentY = y,
                            startX = x,
                            startY = y
                        )
                        soundManager.play(SoundManager.SoundType.CLICK)
                        triggerVibration(15)
                        return true
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val drag = activeDrags[pid]
                    if (drag != null) {
                        drag.currentX = event.getX(i)
                        drag.currentY = event.getY(i)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pid = event.getPointerId(actionIndex)
                val drag = activeDrags.remove(pid)
                if (drag != null) {
                    val dropX = event.getX(actionIndex)
                    val dropY = event.getY(actionIndex)

                    val targetStation = resolveDropStation(dropX, dropY, drag.startX, drag.startY)
                    if (targetStation != null && !targetStation.player.isBot && !targetStation.isBellRung) {
                        if (targetStation.toppings.size < maxToppingsPerPizza) {
                            addToppingToStation(targetStation, drag.ingredient)
                            soundManager.play(SoundManager.SoundType.BOUNCE)
                            triggerVibration(30)
                            particles.spawnExplosion(dropX, dropY, drag.ingredient.color, 16)
                        } else {
                            targetStation.shakeTimer = 0.25f
                            soundManager.play(SoundManager.SoundType.CLICK)
                            triggerVibration(15)
                        }
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                activeDrags.clear()
            }
        }
        return true
    }

    /**
     * Resolves which player station an ingredient is dropped into.
     * Supports direct drop on pizza as well as dragging toward player's territory.
     */
    private fun resolveDropStation(dropX: Float, dropY: Float, startX: Float, startY: Float): PlayerPizzaStation? {
        val nonBotStations = playerStations.filter { !it.player.isBot && !it.isBellRung }
        if (nonBotStations.isEmpty()) return null

        // 1. Direct drop onto or near pizza plate (1.35x radius)
        for (station in nonBotStations) {
            val dist = hypot(dropX - station.centerX, dropY - station.centerY)
            if (dist <= station.radius * 1.35f) {
                return station
            }
        }

        // 2. Dragged towards a player's side/quadrant
        val dragDist = hypot(dropX - startX, dropY - startY)
        if (dragDist > 18f * density) {
            val pCount = players.size
            if (pCount <= 2) {
                // Top half (< hudCenterY): Player 2
                // Bottom half (>= hudCenterY): Player 1
                return if (dropY < hudRect.centerY()) {
                    nonBotStations.firstOrNull { it.player.id == PlayerId.PLAYER_2 }
                } else {
                    nonBotStations.firstOrNull { it.player.id == PlayerId.PLAYER_1 }
                }
            } else if (pCount == 3) {
                return if (dropY < hudRect.centerY()) {
                    if (dropX < width * 0.5f) {
                        nonBotStations.firstOrNull { it.player.id == PlayerId.PLAYER_2 }
                    } else {
                        nonBotStations.firstOrNull { it.player.id == PlayerId.PLAYER_3 }
                    }
                } else {
                    nonBotStations.firstOrNull { it.player.id == PlayerId.PLAYER_1 }
                }
            } else {
                val isLeft = dropX < width * 0.5f
                val isTop = dropY < hudRect.centerY()
                val targetId = when {
                    isLeft && isTop -> PlayerId.PLAYER_2
                    !isLeft && isTop -> PlayerId.PLAYER_3
                    isLeft && !isTop -> PlayerId.PLAYER_1
                    else -> PlayerId.PLAYER_4
                }
                return nonBotStations.firstOrNull { it.player.id == targetId }
            }
        }

        return null
    }

    private fun addToppingToStation(station: PlayerPizzaStation, ingredient: PizzaIngredient) {
        if (station.toppings.size >= maxToppingsPerPizza) return

        val occupiedSlots = station.toppings.map { it.slotIndex }.toSet()
        val nextFreeSlot = (0 until maxToppingsPerPizza).firstOrNull { it !in occupiedSlots } ?: station.toppings.size

        // 6 topping slots distributed evenly around the pizza base
        val angle = (nextFreeSlot * (2 * PI / maxToppingsPerPizza) + PI / 6).toFloat()
        val dist = station.radius * 0.48f

        val relX = dist * cos(angle)
        val relY = dist * sin(angle)

        station.toppings.add(
            PlacedTopping(
                ingredient = ingredient,
                slotIndex = nextFreeSlot,
                relX = relX,
                relY = relY,
                scaleAnim = 0.2f
            )
        )
    }

    private fun ringBellForStation(station: PlayerPizzaStation) {
        if (station.isBellRung || currentPhase != GamePhase.PIZZA_BAKING) return

        // Each user is allowed only 1 click on ring per round!
        station.isBellRung = true
        bellsRungCount++
        station.ringOrder = bellsRungCount

        // Calculate accuracy
        calculateStationAccuracy(station)

        if (station.isCorrect) {
            // USER HAS SAME THINGS -> WON IMMEDIATELY! Advance to next round even if other user hasn't rung
            currentPhase = GamePhase.ORDER_EVALUATE
            activeDrags.clear()
            evaluateTimer = -1f

            val roundWin = station.player
            roundWinningPlayer = roundWin
            val newWins = (playerWins[roundWin.id] ?: 0) + 1
            playerWins[roundWin.id] = newWins
            roundWin.score = newWins

            soundManager.play(SoundManager.SoundType.VICTORY)
            triggerVibration(100)
            particles.spawnExplosion(station.centerX, station.centerY, roundWin.id.color, 50)
            particles.spawnExplosion(station.bellCenter.x, station.bellCenter.y, Color.parseColor("#FFD700"), 35)

            // Show victory banner for 2.0s then advance to next round
            roundWonTransitionTimer = 2.0f
        } else {
            // USER IS WRONG -> GAME CONTINUES FOR REMAINING PLAYERS!
            station.shakeTimer = 0.4f
            station.notMatchTimer = 2.5f

            soundManager.play(SoundManager.SoundType.CLICK)
            triggerVibration(50)
            particles.spawnExplosion(station.bellCenter.x, station.bellCenter.y, Color.parseColor("#FF1744"), 25)

            // If ALL users have now rung and all are wrong -> play next round and no one gets any points
            val allRung = playerStations.all { it.isBellRung }
            if (allRung) {
                currentPhase = GamePhase.ORDER_EVALUATE
                activeDrags.clear()
                evaluateTimer = -1f
                roundWinningPlayer = null

                soundManager.play(SoundManager.SoundType.CLICK)
                triggerVibration(80)

                // Show "NO ONE SCORED!" banner for 2.0s then advance to next round
                roundWonTransitionTimer = 2.0f
            }
        }
    }

    private fun calculateStationAccuracy(station: PlayerPizzaStation) {
        val playerCounts = mutableMapOf<PizzaIngredient, Int>()
        station.toppings.forEach { t ->
            playerCounts[t.ingredient] = (playerCounts[t.ingredient] ?: 0) + 1
        }

        val totalRequired = roundRecipe.values.sum().coerceAtLeast(1)
        var isExactMatch = (station.toppings.size == totalRequired)

        var correctToppingsCount = 0
        for ((ing, required) in roundRecipe) {
            val placed = playerCounts[ing] ?: 0
            if (placed != required) {
                isExactMatch = false
            }
            if (required > 0) {
                correctToppingsCount += min(placed, required)
            }
        }

        // Also ensure no extra unrequested ingredients were placed
        for ((ing, placed) in playerCounts) {
            val required = roundRecipe[ing] ?: 0
            if (placed != required) {
                isExactMatch = false
            }
        }

        station.accuracyPercent = (correctToppingsCount * 100) / totalRequired
        station.isCorrect = isExactMatch
    }

    private fun evaluateRound() {
        if (currentPhase == GamePhase.ORDER_EVALUATE) return
        currentPhase = GamePhase.ORDER_EVALUATE

        // Ensure all stations have calculated accuracy
        playerStations.forEach { calculateStationAccuracy(it) }

        // Start short countdown to reveal scores and show winner
        evaluateTimer = 1.8f
    }

    private fun finalizeRoundWinner() {
        evaluateTimer = -1f

        // STRICT LOGIC: ONLY a player whose pizza is 100% CORRECT (isCorrect == true) can score!
        val correctStations = playerStations
            .filter { it.isBellRung && it.isCorrect }
            .sortedBy { it.ringOrder }

        val winnerStation = correctStations.firstOrNull()

        if (winnerStation != null) {
            val roundWin = winnerStation.player
            roundWinningPlayer = roundWin
            val newWins = (playerWins[roundWin.id] ?: 0) + 1
            playerWins[roundWin.id] = newWins
            roundWin.score = newWins

            soundManager.play(SoundManager.SoundType.VICTORY)
            triggerVibration(100)
            particles.spawnExplosion(winnerStation.centerX, winnerStation.centerY, roundWin.id.color, 50)
        } else {
            // Nobody had the exact recipe! NO ONE SCORES
            roundWinningPlayer = null
            soundManager.play(SoundManager.SoundType.CLICK)
            triggerVibration(40)
        }

        roundWonTransitionTimer = 2.2f
    }

    private fun triggerMatchOver() {
        isRoundEnding = true
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

    // ==========================================================
    // RENDER LOGIC
    // ==========================================================

    override fun render(canvas: Canvas) {
        // Deep cosmic void background
        canvas.drawColor(Color.parseColor("#090B16"))

        // Subtle background dots / star grid
        drawSubtleCosmicGrid(canvas)

        // 1. Render Player Pizza Stations & Bells
        playerStations.forEach { station ->
            renderPlayerPizzaStation(canvas, station)
        }

        // 2. Render Central Area (Order Ticket or Ingredient Bowls)
        when (currentPhase) {
            GamePhase.ORDER_MEMORIZE -> {
                renderOrderTicket(canvas, isReveal = false)
            }
            GamePhase.PIZZA_BAKING -> {
                renderIngredientBowls(canvas)
            }
            GamePhase.ORDER_EVALUATE -> {
                renderOrderTicket(canvas, isReveal = true)
            }
        }

        // 3. Central HUD Capsule (Round X/5 and player scores) - ONLY during Baking phase!
        if (currentPhase == GamePhase.PIZZA_BAKING) {
            renderCentralHUD(canvas)
        }

        // 4. Active Drag & Drop Floating Items & Highlights
        renderActiveDrags(canvas)

        // 5. Particles
        particles.draw(canvas)

        // 6. Celebration Banner overlay during round win transition
        if (roundWonTransitionTimer > 0f) {
            renderRoundWonBanner(canvas)
        }
    }

    private fun renderActiveDrags(canvas: Canvas) {
        if (activeDrags.isEmpty()) return

        for (drag in activeDrags.values) {
            // Tether line from source bowl to current touch position
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f * density
            paint.color = Color.argb(120, Color.red(drag.ingredient.color), Color.green(drag.ingredient.color), Color.blue(drag.ingredient.color))
            canvas.drawLine(drag.startX, drag.startY, drag.currentX, drag.currentY, paint)

            // Soft drop shadow under the dragged ingredient
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(80, 0, 0, 0)
            canvas.drawOval(
                drag.currentX - 20f * density,
                drag.currentY + 14f * density,
                drag.currentX + 20f * density,
                drag.currentY + 24f * density,
                paint
            )

            // Halo glow around the floating ingredient
            paint.color = Color.argb(90, Color.red(drag.ingredient.color), Color.green(drag.ingredient.color), Color.blue(drag.ingredient.color))
            canvas.drawCircle(drag.currentX, drag.currentY - 14f * density, 25f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = drag.ingredient.color
            canvas.drawCircle(drag.currentX, drag.currentY - 14f * density, 25f * density, paint)

            // Scaled floating Emoji
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 34f * density
            canvas.drawText(drag.ingredient.emoji, drag.currentX, drag.currentY - 14f * density + 12f * density, textPaint)
        }
    }

    private fun drawSubtleCosmicGrid(canvas: Canvas) {
        paint.color = Color.parseColor("#151928")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        val step = 48f * density
        var gx = 0f
        while (gx < width) {
            var gy = 0f
            while (gy < height) {
                canvas.drawCircle(gx, gy, 1.2f * density, paint)
                gy += step
            }
            gx += step
        }
    }

    private fun renderCentralHUD(canvas: Canvas) {
        val cx = hudRect.centerX()
        val cy = hudRect.centerY()
        val h = hudRect.height()

        // Frosted HUD capsule
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#111528")
        canvas.drawRoundRect(hudRect, h * 0.5f, h * 0.5f, paint)

        // Glowing border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.color = Color.parseColor("#1F2744")
        canvas.drawRoundRect(hudRect, h * 0.5f, h * 0.5f, paint)

        // Round title pill
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText("ROUND $currentRound/$maxRounds", hudRect.left + 16f * density, cy + 4f * density, textPaint)

        // Player Scores pills on right
        val rightEdge = hudRect.right - 14f * density
        var curX = rightEdge
        val pList = playerStations.map { it.player }

        pList.reversed().forEach { p ->
            val wins = playerWins[p.id] ?: p.score
            val tagText = "P${p.id.id}: $wins"
            val tagW = textPaint.measureText(tagText) + 12f * density

            val pillRect = RectF(curX - tagW, cy - 10f * density, curX, cy + 10f * density)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(45, Color.red(p.id.color), Color.green(p.id.color), Color.blue(p.id.color))
            canvas.drawRoundRect(pillRect, 8f * density, 8f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * density
            paint.color = p.id.color
            canvas.drawRoundRect(pillRect, 8f * density, 8f * density, paint)

            textPaint.color = p.id.color
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(tagText, pillRect.centerX(), cy + 3.5f * density, textPaint)

            curX -= (tagW + 6f * density)
        }
    }

    /**
     * Render each player's pizza board, dough, placed toppings, and bell
     */
    private fun renderPlayerPizzaStation(canvas: Canvas, station: PlayerPizzaStation) {
        val cx = station.centerX
        val cy = station.centerY
        val r = station.radius
        val pColor = station.player.id.color

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(station.rotationDeg)

        // Apply shake if present
        if (station.shakeTimer > 0f) {
            val shakeOffsetX = (random.nextFloat() - 0.5f) * 8f * density
            canvas.translate(shakeOffsetX, 0f)
        }

        // 1. Dark Board / Plate
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1B1F33")
        canvas.drawCircle(0f, 0f, r, paint)

        // Glowing border matching player color (or bright green when hovering during drag & drop)
        paint.style = Paint.Style.STROKE
        val isHovered = activeDrags.values.any { resolveDropStation(it.currentX, it.currentY, it.startX, it.startY) == station }
        if (isHovered) {
            paint.strokeWidth = 4.5f * density
            paint.color = Color.parseColor("#00E676")
        } else {
            paint.strokeWidth = 2.5f * density
            paint.color = pColor
        }
        canvas.drawCircle(0f, 0f, r, paint)

        // 2. Pizza Dough Crust (Golden Brown)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#D4A373")
        canvas.drawCircle(0f, 0f, r * 0.88f, paint)

        // Inner Sauce / Melted Cheese Base
        paint.color = Color.parseColor("#FAEDCD")
        canvas.drawCircle(0f, 0f, r * 0.74f, paint)

        paint.color = Color.parseColor("#E76F51")
        paint.alpha = 180
        canvas.drawCircle(0f, 0f, r * 0.65f, paint)
        paint.alpha = 255

        // 3. Render 6 Placed Toppings
        station.toppings.forEach { placed ->
            val scale = placed.scaleAnim
            canvas.save()
            canvas.translate(placed.relX, placed.relY)
            canvas.scale(scale, scale)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = r * 0.32f
            canvas.drawText(placed.ingredient.emoji, 0f, textPaint.textSize * 0.36f, textPaint)
            canvas.restore()
        }

        // 4. Player Name / Status Tag
        renderStationTag(canvas, station, r)

        canvas.restore()

        // 5. Service Bell (drawn in screen space)
        renderServiceBell(canvas, station)
    }

    private fun renderStationTag(canvas: Canvas, station: PlayerPizzaStation, radius: Float) {
        val p = station.player
        val label = if (p.isBot) "BOT" else getPlayerDisplayName(p).uppercase()
        val tagY = radius + 14f * density

        textPaint.textSize = 10f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val tagW = textPaint.measureText(label) + 16f * density
        val tagH = 18f * density

        val tagRect = RectF(-tagW * 0.5f, tagY - tagH * 0.5f, tagW * 0.5f, tagY + tagH * 0.5f)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#111528")
        canvas.drawRoundRect(tagRect, tagH * 0.5f, tagH * 0.5f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = p.id.color
        canvas.drawRoundRect(tagRect, tagH * 0.5f, tagH * 0.5f, paint)

        textPaint.color = p.id.color
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, 0f, tagY + 3.5f * density, textPaint)

        // Show accuracy or RUNG state in evaluate phase
        if (currentPhase == GamePhase.ORDER_EVALUATE && station.isBellRung) {
            val isWinner = (station.player == roundWinningPlayer)
            val resultText = if (isWinner) "WINNER 🏆" else "FINISHED"
            val evalY = -radius - 8f * density

            textPaint.textSize = 10.5f * density
            textPaint.color = if (isWinner) Color.parseColor("#00E676") else Color.parseColor("#8E99A8")
            canvas.drawText(resultText, 0f, evalY, textPaint)
        } else if (station.notMatchTimer > 0f) {
            // "NOT MATCH" popup pill!
            val evalY = -radius - 10f * density
            textPaint.textSize = 11f * density
            textPaint.typeface = Typeface.DEFAULT_BOLD
            val msg = "❌ NOT MATCH!"
            val badgeW = textPaint.measureText(msg) + 16f * density
            val badgeH = 20f * density
            val badgeRect = RectF(-badgeW * 0.5f, evalY - badgeH * 0.5f, badgeW * 0.5f, evalY + badgeH * 0.5f)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#E6210008")
            canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = Color.parseColor("#FF1744")
            canvas.drawRoundRect(badgeRect, badgeH * 0.5f, badgeH * 0.5f, paint)

            textPaint.color = Color.parseColor("#FF5252")
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(msg, 0f, evalY + 4f * density, textPaint)
        }
    }

    private fun renderServiceBell(canvas: Canvas, station: PlayerPizzaStation) {
        val bx = station.bellCenter.x
        val by = station.bellCenter.y
        val br = station.bellRadius
        val isRung = station.isBellRung
        val isCorrect = station.isCorrect
        val isNotMatch = station.notMatchTimer > 0f
        val scale = station.bellPulseAnim

        canvas.save()
        canvas.translate(bx, by)
        canvas.scale(scale, scale)

        // Outer glow
        paint.style = Paint.Style.FILL
        paint.color = when {
            isRung && isCorrect -> Color.parseColor("#43A047")
            isRung && !isCorrect -> Color.parseColor("#D32F2F")
            isNotMatch -> Color.parseColor("#D32F2F")
            else -> Color.argb(40, 255, 215, 0)
        }
        canvas.drawCircle(0f, 0f, br * 1.15f, paint)

        // Brass Bell Base
        paint.color = when {
            isRung && isCorrect -> Color.parseColor("#2E7D32")
            isRung && !isCorrect -> Color.parseColor("#C62828")
            isNotMatch -> Color.parseColor("#C62828")
            else -> Color.parseColor("#FFA000")
        }
        canvas.drawCircle(0f, 0f, br, paint)

        // Shiny highlight
        paint.color = when {
            isRung && isCorrect -> Color.parseColor("#81C784")
            isRung && !isCorrect -> Color.parseColor("#EF5350")
            isNotMatch -> Color.parseColor("#EF5350")
            else -> Color.parseColor("#FFD54F")
        }
        canvas.drawCircle(-br * 0.2f, -br * 0.2f, br * 0.55f, paint)

        // Bell emoji or Ring indicator
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = br * 0.95f
        val icon = when {
            isRung && isCorrect -> "✅"
            isRung && !isCorrect -> "❌"
            isNotMatch -> "❌"
            else -> "🛎️"
        }
        canvas.drawText(icon, 0f, textPaint.textSize * 0.36f, textPaint)

        // Label below bell
        textPaint.textSize = 8.5f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        when {
            isRung && isCorrect -> {
                textPaint.color = Color.parseColor("#00E676")
                canvas.drawText("CORRECT", 0f, br + 10f * density, textPaint)
            }
            isRung && !isCorrect -> {
                textPaint.color = Color.parseColor("#FF5252")
                canvas.drawText("WRONG", 0f, br + 10f * density, textPaint)
            }
            else -> {
                textPaint.color = Color.parseColor("#FFD54F")
                canvas.drawText("RING", 0f, br + 10f * density, textPaint)
            }
        }

        canvas.restore()
    }

    /**
     * Render the 6 central ingredient bowls during the Baking phase
     */
    private fun renderIngredientBowls(canvas: Canvas) {
        ingredientBowls.forEach { bowl ->
            val b = bowl.bounds
            val scale = bowl.animScale
            val cx = b.centerX()
            val cy = b.centerY()

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scale, scale)

            val drawRect = RectF(-b.width() * 0.5f, -b.height() * 0.5f, b.width() * 0.5f, b.height() * 0.5f)

            // Bowl Card Background
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#151A2E")
            canvas.drawRoundRect(drawRect, 14f * density, 14f * density, paint)

            // Glowing ingredient colored border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f * density
            paint.color = bowl.ingredient.color
            canvas.drawRoundRect(drawRect, 14f * density, 14f * density, paint)

            // Emoji (centered comfortably above label)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = min(b.width(), b.height()) * 0.38f
            canvas.drawText(bowl.ingredient.emoji, 0f, -2f * density, textPaint)

            // Ingredient Name (crisp, bold white label near bottom of bowl card)
            textPaint.textSize = 10f * density
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.color = Color.WHITE
            canvas.drawText(bowl.ingredient.displayName, 0f, drawRect.bottom - 8f * density, textPaint)

            canvas.restore()
        }
    }

    /**
     * Render the Customer Order Ticket (Parchment Card)
     * Shows countdown during Memorize phase; shows Recipe vs Results during Evaluate phase.
     */
    private fun renderOrderTicket(canvas: Canvas, isReveal: Boolean) {
        val rect = orderTicketRect

        // Frosted Order Card
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#13172C")
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint)

        // Neon Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.0f * density
        paint.color = if (isReveal) Color.parseColor("#00E5FF") else Color.parseColor("#FF9800")
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint)

        // Ticket Header
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 13f * density
        textPaint.color = if (isReveal) Color.parseColor("#00E5FF") else Color.parseColor("#FFD54F")

        val titleText = if (isReveal) "ORDER RECIPE REVEAL" else "MEMORIZE THE ORDER! 🍕"
        canvas.drawText(titleText, rect.centerX(), rect.top + 22f * density, textPaint)

        // Timer bar during Memorize phase
        if (!isReveal) {
            val barW = rect.width() * 0.70f
            val barH = 5f * density
            val barLeft = rect.centerX() - barW * 0.5f
            val barTop = rect.top + 30f * density
            val progress = (memorizeTimer / maxMemorizeTime).coerceIn(0f, 1f)

            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#222842")
            canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + barW, barTop + barH), barH * 0.5f, barH * 0.5f, paint)

            paint.color = Color.parseColor("#FF9800")
            canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + barW * progress, barTop + barH), barH * 0.5f, barH * 0.5f, paint)
        }

        // Recipe Items Grid
        val items = roundRecipe.entries.toList()
        val numItems = items.size
        val rowH = 28f * density
        val startY = rect.top + (if (!isReveal) 50f else 38f) * density

        for (i in 0 until numItems) {
            val entry = items[i]
            val ing = entry.key
            val count = entry.value

            val itemY = startY + i * rowH

            // Ingredient Emoji + Name
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 15f * density
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.color = Color.WHITE
            canvas.drawText("${ing.emoji}  ${ing.displayName}", rect.left + 24f * density, itemY + 5f * density, textPaint)

            // Required Quantity badge
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 15f * density
            textPaint.color = Color.parseColor("#FFD54F")
            canvas.drawText("x $count", rect.right - 24f * density, itemY + 5f * density, textPaint)
        }

        // Round & Score pill at the bottom of the ticket (cleanly separated, never overlapping items)
        val p1Win = playerWins[PlayerId.PLAYER_1] ?: 0
        val p2Win = playerWins[PlayerId.PLAYER_2] ?: 0
        val roundScoreText = if (players.size == 2) "ROUND $currentRound/$maxRounds   •   P1: $p1Win  |  P2: $p2Win" else "ROUND $currentRound/$maxRounds"
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 11f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.parseColor("#00E5FF")
        canvas.drawText(roundScoreText, rect.centerX(), rect.bottom - 28f * density, textPaint)

        // Subtitle prompt
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 9.5f * density
        textPaint.typeface = Typeface.DEFAULT
        textPaint.color = Color.parseColor("#8E99A8")
        val bottomMsg = if (!isReveal) {
            "Remember toppings & count!"
        } else {
            val winnerName = roundWinningPlayer?.let { getPlayerDisplayName(it).uppercase() }
            if (winnerName != null) "🏆 $winnerName won the round!" else "❌ No one scored!"
        }
        canvas.drawText(bottomMsg, rect.centerX(), rect.bottom - 12f * density, textPaint)
    }

    private fun renderRoundWonBanner(canvas: Canvas) {
        val bannerW = width * 0.82f
        val bannerH = 50f * density
        val bannerY = height * 0.5f

        val rect = RectF(
            width * 0.5f - bannerW * 0.5f,
            bannerY - bannerH * 0.5f,
            width * 0.5f + bannerW * 0.5f,
            bannerY + bannerH * 0.5f
        )

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CC0D1122")
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint)

        val winColor = roundWinningPlayer?.id?.color ?: Color.parseColor("#FF5252")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = winColor
        canvas.drawRoundRect(rect, 16f * density, 16f * density, paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 14f * density
        textPaint.color = winColor

        if (roundWinningPlayer != null) {
            val winnerName = getPlayerDisplayName(roundWinningPlayer!!).uppercase()
            canvas.drawText("🏆 $winnerName WON!", rect.centerX(), bannerY + 5f * density, textPaint)
        } else {
            canvas.drawText("❌ NO ONE SCORED!", rect.centerX(), bannerY + 5f * density, textPaint)
        }
    }
}

