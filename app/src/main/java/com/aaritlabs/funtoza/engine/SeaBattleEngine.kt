package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

enum class SeaCellState {
    FOG,
    WATER_MISS,
    SHIP_HIT,
    SHIP_SUNK
}

data class SeaShip(
    val id: Int,
    val name: String,
    val size: Int,
    var startX: Int = -1,
    var startY: Int = -1,
    var isVertical: Boolean = false,
    var hits: Int = 0,
    var claimedBy: PlayerId? = null
) {
    val isSunk: Boolean get() = hits >= size

    fun occupies(x: Int, y: Int): Boolean {
        if (startX == -1 || startY == -1) return false
        return if (isVertical) {
            x == startX && y in startY until (startY + size)
        } else {
            y == startY && x in startX until (startX + size)
        }
    }

    fun getCells(): List<Point> {
        val list = mutableListOf<Point>()
        if (startX == -1 || startY == -1) return list
        for (i in 0 until size) {
            if (isVertical) list.add(Point(startX, startY + i))
            else list.add(Point(startX + i, startY))
        }
        return list
    }
}

class SeaBoard(
    val ownerId: PlayerId?,
    val gridCols: Int = 7,
    val gridRows: Int = 7
) {
    val grid = Array(gridRows) { Array(gridCols) { SeaCellState.FOG } }
    val ships = mutableListOf<SeaShip>()
    var isReady = false
    var isEliminated = false

    fun reset() {
        for (y in 0 until gridRows) {
            for (x in 0 until gridCols) {
                grid[y][x] = SeaCellState.FOG
            }
        }
        ships.clear()
        isReady = false
        isEliminated = false
    }

    fun allShipsSunk(): Boolean = ships.isNotEmpty() && ships.all { it.isSunk }

    fun canPlaceShip(size: Int, x: Int, y: Int, isVertical: Boolean, ignoreShip: SeaShip? = null): Boolean {
        val endX = if (isVertical) x else x + size - 1
        val endY = if (isVertical) y + size - 1 else y

        if (x < 0 || y < 0 || endX >= gridCols || endY >= gridRows) return false

        // 1-cell distance gap rule
        for (ship in ships) {
            if (ship === ignoreShip) continue
            for (pt in ship.getCells()) {
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val checkX = pt.x + dx
                        val checkY = pt.y + dy
                        if (isVertical) {
                            if (x == checkX && checkY in y..endY) return false
                        } else {
                            if (y == checkY && checkX in x..endX) return false
                        }
                    }
                }
            }
        }
        return true
    }

    fun rotateShip(ship: SeaShip): Boolean {
        val newVert = !ship.isVertical
        val size = ship.size
        val x = ship.startX
        val y = ship.startY

        if (canPlaceShip(size, x, y, newVert, ignoreShip = ship)) {
            ship.isVertical = newVert
            return true
        }
        val maxX = if (newVert) gridCols - 1 else gridCols - size
        val maxY = if (newVert) gridRows - size else gridRows - 1
        val clampedX = x.coerceIn(0, max(0, maxX))
        val clampedY = y.coerceIn(0, max(0, maxY))
        if (canPlaceShip(size, clampedX, clampedY, newVert, ignoreShip = ship)) {
            ship.startX = clampedX
            ship.startY = clampedY
            ship.isVertical = newVert
            return true
        }
        return false
    }

    fun randomizeShips(random: Random) {
        ships.clear()
        val shipDefs = listOf(
            Pair("Carrier", 4),
            Pair("Battleship", 3),
            Pair("Cruiser", 2),
            Pair("Submarine", 2),
            Pair("Patrol Boat", 1)
        )

        var shipId = 0
        for ((name, size) in shipDefs) {
            var placed = false
            var attempts = 0
            while (!placed && attempts < 400) {
                attempts++
                val isVert = random.nextBoolean()
                val maxX = if (isVert) gridCols - 1 else gridCols - size
                val maxY = if (isVert) gridRows - size else gridRows - 1
                if (maxX < 0 || maxY < 0) continue

                val x = random.nextInt(maxX + 1)
                val y = random.nextInt(maxY + 1)

                if (canPlaceShip(size, x, y, isVert)) {
                    val ship = SeaShip(shipId++, name, size, x, y, isVert)
                    ships.add(ship)
                    placed = true
                }
            }
        }
        isReady = true
    }

    fun randomizeSharedHuntShips(random: Random) {
        ships.clear()
        val shipDefs = listOf(
            Pair("Carrier", 4),
            Pair("Battleship", 3),
            Pair("Battleship", 3),
            Pair("Cruiser", 2),
            Pair("Cruiser", 2),
            Pair("Submarine", 2),
            Pair("Submarine", 2),
            Pair("Patrol Boat", 1),
            Pair("Patrol Boat", 1)
        )

        var shipId = 0
        for ((name, size) in shipDefs) {
            var placed = false
            var attempts = 0
            while (!placed && attempts < 500) {
                attempts++
                val isVert = random.nextBoolean()
                val maxX = if (isVert) gridCols - 1 else gridCols - size
                val maxY = if (isVert) gridRows - size else gridRows - 1
                if (maxX < 0 || maxY < 0) continue

                val x = random.nextInt(maxX + 1)
                val y = random.nextInt(maxY + 1)

                if (canPlaceShip(size, x, y, isVert)) {
                    val ship = SeaShip(shipId++, name, size, x, y, isVert)
                    ships.add(ship)
                    placed = true
                }
            }
        }
        isReady = true
    }
}

data class CannonballFlight(
    val fromPt: PointF,
    val toPt: PointF,
    val targetOwner: PlayerId?,
    val targetX: Int,
    val targetY: Int,
    val isHit: Boolean,
    var progress: Float = 0f,
    val duration: Float = 0.44f
)

data class WaterSplashEffect(
    val cx: Float,
    val cy: Float,
    var radius: Float = 0f,
    val maxRadius: Float = 24f,
    var alpha: Float = 1f
)

enum class SeaPhase {
    PLACEMENT,
    BATTLE,
    ROUND_OVER
}

class SeaBattleEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager,
    private val botDifficulty: Int = 1
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val density: Float = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1-2 Players: Dual Boards
    private val boards = mutableMapOf<PlayerId, SeaBoard>()
    private var phase = SeaPhase.PLACEMENT
    private var currentTurnIndex = 0

    // 3-4 Players: Shared Fleet Hunt Board (Screenshot 2)
    private val sharedBoard = SeaBoard(null, gridCols = 7, gridRows = 11)
    private val sharedGridRect = RectF()
    private val sharedInventoryRect = RectF()
    private val sharedScoreStripRect = RectF()

    // Sequential Placement State (1-2 Players)
    private var placementPlayerIndex = 0
    private var showPassDeviceScreen = false
    private var botDeployTimer = 0f

    // Dragging & Placement interaction
    private var draggingShip: SeaShip? = null
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f
    private var isDragging = false

    // Deployment UI Rects (1-2 Players)
    private val deployGridRect = RectF()
    private val deployRandomBtnRect = RectF()
    private val deploySaveBtnRect = RectF()
    private val passReadyBtnRect = RectF()

    // Dual Battle UI Rects (1-2 Players)
    private val enemyGridRect = RectF()
    private val friendlyGridRect = RectF()
    private val fleetStatusBarRect = RectF()
    private val cannonTurretRect = RectF()

    // Cannon & FX
    private var activeCannonball: CannonballFlight? = null
    private val splashes = mutableListOf<WaterSplashEffect>()
    private var cannonAngle = -90f
    private var cannonRecoil = 0f

    // Feedback & Timers
    private var feedbackMessage: String? = null
    private var feedbackTimer = 0f
    private var botThinkTimer = 0f
    private var radarWaveTimer = 0f
    private var turnTransitionDelay = 0f

    // Ships Claimed Tracking (3-4 Players) & Game End Timer
    private val shipsClaimedCount = mutableMapOf<PlayerId, Int>()
    private var roundEndDelayTimer = -1f
    private var pendingWinner: Player? = null

    // Bot AI
    private val botHits = mutableListOf<Point>()
    private val botTargetQueue = mutableListOf<Point>()

    init {
        val gSize = 7
        players.forEach { p ->
            boards[p.id] = SeaBoard(p.id, gSize, gSize)
        }
    }

    override fun initGame() {
        isGameOver = false
        currentTurnIndex = 0
        activeCannonball = null
        splashes.clear()
        botHits.clear()
        botTargetQueue.clear()
        feedbackMessage = null
        feedbackTimer = 0f
        cannonAngle = -90f
        cannonRecoil = 0f
        roundEndDelayTimer = -1f
        pendingWinner = null
        shipsClaimedCount.clear()
        players.forEach {
            it.score = 0
            shipsClaimedCount[it.id] = 0
        }

        if (totalPlayers >= 3) {
            // 3-4 Players: Instant Shared Fleet Hunt Arena (Screenshot 2)
            phase = SeaPhase.BATTLE
            sharedBoard.reset()
            sharedBoard.randomizeSharedHuntShips(random)
            val firstP = getCurrentPlayer()
            if (firstP != null && firstP.isBot) {
                botThinkTimer = getBotDelay()
            }
        } else {
            // 1-2 Players: Sequential Secret Placement & Dual Battle
            phase = SeaPhase.PLACEMENT
            placementPlayerIndex = 0
            showPassDeviceScreen = false
            botDeployTimer = 0f

            boards.values.forEach { b ->
                b.reset()
                b.randomizeShips(random)
                b.isReady = false
            }

            val firstP = players.getOrNull(0)
            if (firstP != null && firstP.isBot) {
                botDeployTimer = 1.0f
            }
        }
    }

    override fun update(deltaTime: Float) {
        radarWaveTimer += deltaTime
        if (radarWaveTimer > 4f) radarWaveTimer -= 4f

        if (cannonRecoil > 0f) {
            cannonRecoil = (cannonRecoil - deltaTime * 4.5f).coerceAtLeast(0f)
        }

        if (feedbackTimer > 0f) {
            feedbackTimer -= deltaTime
            if (feedbackTimer <= 0f) feedbackMessage = null
        }

        // Round Over Popup Timer
        if (roundEndDelayTimer > 0f) {
            roundEndDelayTimer -= deltaTime
            if (roundEndDelayTimer <= 0f) {
                roundEndDelayTimer = -1f
                endRound(pendingWinner)
            }
        }

        // Placement Bot timer (1-2P)
        if (totalPlayers <= 2 && phase == SeaPhase.PLACEMENT) {
            if (botDeployTimer > 0f) {
                botDeployTimer -= deltaTime
                if (botDeployTimer <= 0f) {
                    val p = players.getOrNull(placementPlayerIndex)
                    if (p != null) {
                        boards[p.id]?.randomizeShips(random)
                        boards[p.id]?.isReady = true
                    }
                    advancePlacement()
                }
            }
        }

        // Water splash ripple animation
        val splashIt = splashes.iterator()
        while (splashIt.hasNext()) {
            val s = splashIt.next()
            s.radius += deltaTime * 48f * density
            s.alpha -= deltaTime * 1.8f
            if (s.alpha <= 0f) splashIt.remove()
        }

        // Active Cannonball flight
        if (activeCannonball != null) {
            val cb = activeCannonball!!
            cb.progress += deltaTime / cb.duration
            if (cb.progress >= 1f) {
                resolveImpact(cb)
                activeCannonball = null
            }
            particles.update()
            return
        }

        // Turn transition delay after shot
        if (turnTransitionDelay > 0f) {
            turnTransitionDelay -= deltaTime
            if (turnTransitionDelay <= 0f) {
                advanceTurn()
            }
            particles.update()
            return
        }

        // Battle Bot execution
        if (phase == SeaPhase.BATTLE && !isGameOver) {
            val currentP = getCurrentPlayer()
            if (currentP != null && currentP.isBot) {
                botThinkTimer -= deltaTime
                if (botThinkTimer <= 0f) {
                    botExecuteTurn(currentP)
                }
            }
        }

        particles.update()
    }

    private fun getCurrentPlayer(): Player? {
        if (players.isEmpty()) return null
        return players.getOrNull(currentTurnIndex % players.size)
    }

    private fun advancePlacement() {
        placementPlayerIndex++
        if (placementPlayerIndex >= players.size) {
            // All players finished deployment -> START BATTLE!
            phase = SeaPhase.BATTLE
            currentTurnIndex = 0
            feedbackMessage = "⚔️ COMMENCE BATTLE! FIRE AT WILL!"
            feedbackTimer = 1.6f
            soundManager.play(SoundManager.SoundType.COUNTDOWN, 0.95f)
            triggerVibration(60L)

            val firstP = getCurrentPlayer()
            if (firstP != null && firstP.isBot) {
                botThinkTimer = getBotDelay()
            }
        } else {
            val nextP = players[placementPlayerIndex]
            if (nextP.isBot) {
                botDeployTimer = 1.1f
            } else {
                showPassDeviceScreen = true
                soundManager.play(SoundManager.SoundType.TAP, 0.8f)
            }
        }
    }

    private fun resolveImpact(cb: CannonballFlight) {
        if (totalPlayers >= 3) {
            resolveImpactShared(cb)
            return
        }

        val targetBoard = boards[cb.targetOwner] ?: return
        val x = cb.targetX
        val y = cb.targetY
        val targetPt = cb.toPt

        val shipHit = targetBoard.ships.firstOrNull { it.occupies(x, y) }
        if (shipHit != null) {
            targetBoard.grid[y][x] = SeaCellState.SHIP_HIT
            shipHit.hits++
            soundManager.play(SoundManager.SoundType.EXPLOSION, 0.95f)
            triggerVibration(55L)
            particles.spawnExplosion(targetPt.x, targetPt.y, Color.parseColor("#FF5722"), 32)

            if (shipHit.isSunk) {
                shipHit.getCells().forEach { pt ->
                    targetBoard.grid[pt.y][pt.x] = SeaCellState.SHIP_SUNK
                }

                soundManager.play(SoundManager.SoundType.GOAL, 0.9f)
                triggerVibration(85L)
                feedbackMessage = "💥 ${shipHit.name.uppercase()} DESTROYED!"
                feedbackTimer = 1.4f

                botHits.removeAll { shipHit.occupies(it.x, it.y) }
                botTargetQueue.removeAll { shipHit.occupies(it.x, it.y) }

                checkWinCondition()
                if (!isGameOver) {
                    feedbackMessage = "DIRECT HIT! SHOOT AGAIN 🎯"
                    feedbackTimer = 1.1f
                    val curP = getCurrentPlayer()
                    if (curP != null && curP.isBot) botThinkTimer = 0.8f
                }
            } else {
                feedbackMessage = "DIRECT HIT! SHOOT AGAIN 🎯"
                feedbackTimer = 1.0f
                if (getCurrentPlayer()?.isBot == true) {
                    botHits.add(Point(x, y))
                    queueAdjacentTargets(targetBoard, x, y)
                    botThinkTimer = 0.8f
                }
            }
        } else {
            targetBoard.grid[y][x] = SeaCellState.WATER_MISS
            splashes.add(WaterSplashEffect(targetPt.x, targetPt.y, maxRadius = 24f * density))
            soundManager.play(SoundManager.SoundType.BOUNCE, 0.7f)
            triggerVibration(25L)
            particles.spawnExplosion(targetPt.x, targetPt.y, Color.parseColor("#00E5FF"), 16)

            feedbackMessage = "SPLASH! WATER MISS 🌊"
            feedbackTimer = 0.9f
            turnTransitionDelay = 0.7f
        }
    }

    private fun resolveImpactShared(cb: CannonballFlight) {
        val x = cb.targetX
        val y = cb.targetY
        val targetPt = cb.toPt
        val curP = getCurrentPlayer() ?: return

        val shipHit = sharedBoard.ships.firstOrNull { it.occupies(x, y) }
        if (shipHit != null) {
            sharedBoard.grid[y][x] = SeaCellState.SHIP_HIT
            shipHit.hits++
            soundManager.play(SoundManager.SoundType.EXPLOSION, 0.95f)
            triggerVibration(55L)
            particles.spawnExplosion(targetPt.x, targetPt.y, Color.parseColor("#FF5722"), 32)

            if (shipHit.isSunk) {
                // Sunk the last box! Claim this ship in current player's color!
                shipHit.claimedBy = curP.id
                shipsClaimedCount[curP.id] = (shipsClaimedCount[curP.id] ?: 0) + 1
                curP.score = shipsClaimedCount[curP.id] ?: 0
                shipHit.getCells().forEach { pt ->
                    sharedBoard.grid[pt.y][pt.x] = SeaCellState.SHIP_SUNK
                }
                soundManager.play(SoundManager.SoundType.GOAL, 0.95f)
                triggerVibration(85L)
                feedbackMessage = "💥 ${getPlayerDisplayName(curP).uppercase()} SANK ${shipHit.name.uppercase()}! SHOOT AGAIN 🎯"
                feedbackTimer = 1.4f

                botHits.removeAll { shipHit.occupies(it.x, it.y) }
                botTargetQueue.removeAll { shipHit.occupies(it.x, it.y) }

                checkSharedWinCondition()
                if (!isGameOver) {
                    // Same player gets another turn!
                    if (curP.isBot) botThinkTimer = 0.8f
                }
            } else {
                feedbackMessage = "DIRECT HIT! SHOOT AGAIN 🎯"
                feedbackTimer = 1.1f
                if (curP.isBot) {
                    botHits.add(Point(x, y))
                    queueAdjacentTargetsShared(x, y)
                    botThinkTimer = 0.8f
                }
                // Same player gets another turn!
            }
        } else {
            sharedBoard.grid[y][x] = SeaCellState.WATER_MISS
            splashes.add(WaterSplashEffect(targetPt.x, targetPt.y, maxRadius = 24f * density))
            soundManager.play(SoundManager.SoundType.BOUNCE, 0.7f)
            triggerVibration(25L)
            particles.spawnExplosion(targetPt.x, targetPt.y, Color.parseColor("#00E5FF"), 16)

            feedbackMessage = "SPLASH! WATER MISS 🌊"
            feedbackTimer = 0.9f

            // Only on miss does the turn advance to the next player!
            if (!isGameOver) {
                turnTransitionDelay = 0.7f
            }
        }
    }

    private fun queueAdjacentTargets(board: SeaBoard, x: Int, y: Int) {
        val dirs = listOf(Point(0, -1), Point(0, 1), Point(-1, 0), Point(1, 0))
        for (d in dirs) {
            val nx = x + d.x
            val ny = y + d.y
            if (nx in 0 until board.gridCols && ny in 0 until board.gridRows) {
                if (board.grid[ny][nx] == SeaCellState.FOG && !botTargetQueue.contains(Point(nx, ny))) {
                    botTargetQueue.add(0, Point(nx, ny))
                }
            }
        }
    }

    private fun queueAdjacentTargetsShared(x: Int, y: Int) {
        val dirs = listOf(Point(0, -1), Point(0, 1), Point(-1, 0), Point(1, 0))
        for (d in dirs) {
            val nx = x + d.x
            val ny = y + d.y
            if (nx in 0 until sharedBoard.gridCols && ny in 0 until sharedBoard.gridRows) {
                if (sharedBoard.grid[ny][nx] == SeaCellState.FOG && !botTargetQueue.contains(Point(nx, ny))) {
                    botTargetQueue.add(0, Point(nx, ny))
                }
            }
        }
    }

    private fun advanceTurn() {
        if (isGameOver) return

        if (totalPlayers >= 3) {
            // Round-robin 1 by 1 chance to all players
            currentTurnIndex = (currentTurnIndex + 1) % players.size
            val nextP = getCurrentPlayer()
            if (nextP != null && nextP.isBot) {
                botThinkTimer = getBotDelay()
            }
            return
        }

        val alivePlayers = players.filter { p ->
            val b = boards[p.id]
            b != null && !b.isEliminated
        }
        if (alivePlayers.isEmpty()) return

        var nextIndex = (currentTurnIndex + 1) % players.size
        while (boards[players[nextIndex].id]?.isEliminated == true) {
            nextIndex = (nextIndex + 1) % players.size
        }
        currentTurnIndex = nextIndex

        val nextP = getCurrentPlayer()
        if (nextP != null && nextP.isBot) {
            botThinkTimer = getBotDelay()
        }
    }

    private fun getBotDelay(): Float {
        return when (botDifficulty) {
            0 -> 1.0f
            2 -> 0.5f
            else -> 0.75f
        }
    }

    private fun checkWinCondition() {
        players.forEach { p ->
            val b = boards[p.id]
            if (b != null && !b.isEliminated && b.allShipsSunk()) {
                b.isEliminated = true
            }
        }

        val livingPlayers = players.filter { p ->
            val b = boards[p.id]
            b != null && !b.isEliminated
        }

        if (livingPlayers.size <= 1) {
            if (roundEndDelayTimer >= 0f || isGameOver) return
            val winner = livingPlayers.firstOrNull() ?: players.firstOrNull()
            phase = SeaPhase.ROUND_OVER
            val winnerName = if (winner != null) getPlayerDisplayName(winner) else "WINNER"
            feedbackMessage = "🏆 $winnerName SANK THE ENEMY FLEET!"
            feedbackTimer = 2.5f

            if (winner != null) {
                particles.spawnConfetti(width, height, listOf(winner.id.color, Color.parseColor("#FFD54F"), Color.WHITE))
            }
            pendingWinner = winner
            roundEndDelayTimer = 1.0f
        }
    }

    private fun checkSharedWinCondition() {
        if (sharedBoard.ships.all { it.isSunk }) {
            if (roundEndDelayTimer >= 0f || isGameOver) return
            phase = SeaPhase.ROUND_OVER

            if (isTeamMode) {
                val t1Ships = (shipsClaimedCount[PlayerId.PLAYER_1] ?: 0) + (shipsClaimedCount[PlayerId.PLAYER_2] ?: 0)
                val t2Ships = (shipsClaimedCount[PlayerId.PLAYER_3] ?: 0) + (shipsClaimedCount[PlayerId.PLAYER_4] ?: 0)
                winningTeam = if (t1Ships >= t2Ships) 1 else 2
                val winner = if (winningTeam == 1) players[0] else players[2]
                val winShips = if (winningTeam == 1) t1Ships else t2Ships
                feedbackMessage = "🏆 TEAM $winningTeam WINS! ($winShips SHIPS)"
                feedbackTimer = 2.5f

                soundManager.play(SoundManager.SoundType.VICTORY, 1f)
                triggerVibration(100L)
                val teamColor = if (winningTeam == 1) Color.parseColor("#FF3B30") else Color.parseColor("#007AFF")
                particles.spawnConfetti(width, height, listOf(teamColor, Color.parseColor("#FFD54F"), Color.WHITE))
                pendingWinner = winner
                roundEndDelayTimer = 1.0f
                return
            }

            val maxScore = players.maxOf { shipsClaimedCount[it.id] ?: 0 }
            val winners = players.filter { (shipsClaimedCount[it.id] ?: 0) == maxScore }
            val winner = winners.firstOrNull() ?: players.first()

            val winnerName = if (winners.size > 1) {
                "TIE GAME!"
            } else {
                "${getPlayerDisplayName(winner).uppercase()} WINS!"
            }
            val winnerShips = shipsClaimedCount[winner.id] ?: 0
            feedbackMessage = "🏆 $winnerName ($winnerShips SHIPS)"
            feedbackTimer = 2.5f

            soundManager.play(SoundManager.SoundType.VICTORY, 1f)
            triggerVibration(100L)
            particles.spawnConfetti(width, height, listOf(winner.id.color, Color.parseColor("#FFD54F"), Color.WHITE))
            pendingWinner = winner
            roundEndDelayTimer = 1.0f
        }
    }

    private fun botExecuteTurn(botPlayer: Player) {
        if (totalPlayers >= 3) {
            botExecuteTurnShared(botPlayer)
            return
        }

        val targets = players.filter { it.id != botPlayer.id && boards[it.id]?.isEliminated == false }
        if (targets.isEmpty()) return
        val targetPlayer = targets.random(random)
        val targetBoard = boards[targetPlayer.id] ?: return

        var targetX = -1
        var targetY = -1

        while (botTargetQueue.isNotEmpty()) {
            val pt = botTargetQueue.removeAt(0)
            if (pt.x in 0 until targetBoard.gridCols && pt.y in 0 until targetBoard.gridRows) {
                if (targetBoard.grid[pt.y][pt.x] == SeaCellState.FOG) {
                    targetX = pt.x
                    targetY = pt.y
                    break
                }
            }
        }

        if (targetX == -1) {
            val parityPoints = mutableListOf<Point>()
            val remainingFog = mutableListOf<Point>()

            for (y in 0 until targetBoard.gridRows) {
                for (x in 0 until targetBoard.gridCols) {
                    if (targetBoard.grid[y][x] == SeaCellState.FOG) {
                        remainingFog.add(Point(x, y))
                        if ((x + y) % 2 == 0) parityPoints.add(Point(x, y))
                    }
                }
            }

            val pick = if (parityPoints.isNotEmpty() && botDifficulty > 0) {
                parityPoints.random(random)
            } else if (remainingFog.isNotEmpty()) {
                remainingFog.random(random)
            } else null

            if (pick != null) {
                targetX = pick.x
                targetY = pick.y
            }
        }

        if (targetX != -1 && targetY != -1) {
            fireCannon(botPlayer.id, targetPlayer.id, targetX, targetY)
        }
    }

    private fun botExecuteTurnShared(botPlayer: Player) {
        while (botTargetQueue.isNotEmpty()) {
            val pt = botTargetQueue.removeAt(0)
            if (pt.x in 0 until sharedBoard.gridCols && pt.y in 0 until sharedBoard.gridRows) {
                if (sharedBoard.grid[pt.y][pt.x] == SeaCellState.FOG) {
                    fireCannonShared(botPlayer, pt.x, pt.y)
                    return
                }
            }
        }

        val remainingFog = mutableListOf<Point>()
        for (y in 0 until sharedBoard.gridRows) {
            for (x in 0 until sharedBoard.gridCols) {
                if (sharedBoard.grid[y][x] == SeaCellState.FOG) {
                    remainingFog.add(Point(x, y))
                }
            }
        }

        if (remainingFog.isNotEmpty()) {
            val pick = remainingFog.random(random)
            fireCannonShared(botPlayer, pick.x, pick.y)
        }
    }

    private fun fireCannon(shooterId: PlayerId, targetOwner: PlayerId, targetX: Int, targetY: Int) {
        val targetBoard = boards[targetOwner] ?: return
        if (targetBoard.grid[targetY][targetX] != SeaCellState.FOG) return

        val fromPt = getCannonMuzzlePoint()
        val toPt = getBattleCellCenter(targetX, targetY)
        val isHit = targetBoard.ships.any { it.occupies(targetX, targetY) }

        val dx = toPt.x - fromPt.x
        val dy = toPt.y - fromPt.y
        cannonAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        cannonRecoil = 1f

        soundManager.play(SoundManager.SoundType.SHOOT, 0.95f)
        triggerVibration(35L)
        particles.spawnExplosion(fromPt.x, fromPt.y, Color.parseColor("#FFD54F"), 14)

        activeCannonball = CannonballFlight(
            fromPt = fromPt,
            toPt = toPt,
            targetOwner = targetOwner,
            targetX = targetX,
            targetY = targetY,
            isHit = isHit
        )
    }

    private fun fireCannonShared(shooter: Player, targetX: Int, targetY: Int) {
        if (sharedBoard.grid[targetY][targetX] != SeaCellState.FOG) return

        val fromPt = getCannonMuzzlePoint()
        val toPt = getSharedCellCenter(targetX, targetY)
        val isHit = sharedBoard.ships.any { it.occupies(targetX, targetY) }

        val dx = toPt.x - fromPt.x
        val dy = toPt.y - fromPt.y
        cannonAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        cannonRecoil = 1f

        soundManager.play(SoundManager.SoundType.SHOOT, 0.95f)
        triggerVibration(35L)
        particles.spawnExplosion(fromPt.x, fromPt.y, shooter.id.color, 14)

        activeCannonball = CannonballFlight(
            fromPt = fromPt,
            toPt = toPt,
            targetOwner = shooter.id,
            targetX = targetX,
            targetY = targetY,
            isHit = isHit
        )
    }

    private fun getCannonMuzzlePoint(): PointF {
        return PointF(cannonTurretRect.centerX(), cannonTurretRect.centerY())
    }

    private fun getBattleCellCenter(x: Int, y: Int): PointF {
        val gridR = enemyGridRect
        val cellSize = gridR.width() / 7f
        val cx = gridR.left + x * cellSize + cellSize / 2f
        val cy = gridR.top + y * cellSize + cellSize / 2f
        return PointF(cx, cy)
    }

    private fun getSharedCellCenter(x: Int, y: Int): PointF {
        val cellSize = sharedGridRect.width() / sharedBoard.gridCols.toFloat()
        val cx = sharedGridRect.left + x * cellSize + cellSize / 2f
        val cy = sharedGridRect.top + y * cellSize + cellSize / 2f
        return PointF(cx, cy)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isGameOver || activeCannonball != null) return true

        val tx = event.x
        val ty = event.y

        // ==========================================
        // 3-4 PLAYERS: SHARED FLEET HUNT
        // ==========================================
        if (totalPlayers >= 3) {
            val curP = getCurrentPlayer() ?: return true
            if (curP.isBot) return true

            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                if (sharedGridRect.contains(tx, ty)) {
                    val cellSize = sharedGridRect.width() / sharedBoard.gridCols.toFloat()
                    val cellX = ((tx - sharedGridRect.left) / cellSize).toInt().coerceIn(0, sharedBoard.gridCols - 1)
                    val cellY = ((ty - sharedGridRect.top) / cellSize).toInt().coerceIn(0, sharedBoard.gridRows - 1)

                    if (sharedBoard.grid[cellY][cellX] == SeaCellState.FOG) {
                        fireCannonShared(curP, cellX, cellY)
                        return true
                    } else {
                        soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                        feedbackMessage = "SECTOR ALREADY TARGETED! ⚓"
                        feedbackTimer = 0.8f
                        return true
                    }
                }
            }
            return true
        }

        // ==========================================
        // 1-2 PLAYERS: PLACEMENT & DUAL BATTLE
        // ==========================================
        if (phase == SeaPhase.PLACEMENT) {
            val curP = players.getOrNull(placementPlayerIndex) ?: return true

            if (showPassDeviceScreen) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    if (passReadyBtnRect.contains(tx, ty)) {
                        soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                        triggerVibration(30L)
                        showPassDeviceScreen = false
                    }
                }
                return true
            }

            if (curP.isBot || botDeployTimer > 0f) {
                return true
            }

            val b = boards[curP.id] ?: return true

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (deployRandomBtnRect.contains(tx, ty)) {
                        soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                        triggerVibration(25L)
                        b.randomizeShips(random)
                        return true
                    }

                    if (deploySaveBtnRect.contains(tx, ty)) {
                        soundManager.play(SoundManager.SoundType.TAP, 0.95f)
                        triggerVibration(45L)
                        b.isReady = true
                        advancePlacement()
                        return true
                    }

                    if (deployGridRect.contains(tx, ty)) {
                        val cellSize = deployGridRect.width() / b.gridCols.toFloat()
                        val cellX = ((tx - deployGridRect.left) / cellSize).toInt().coerceIn(0, b.gridCols - 1)
                        val cellY = ((ty - deployGridRect.top) / cellSize).toInt().coerceIn(0, b.gridRows - 1)

                        val touchedShip = b.ships.firstOrNull { it.occupies(cellX, cellY) }
                        if (touchedShip != null) {
                            draggingShip = touchedShip
                            dragStartX = tx
                            dragStartY = ty
                            dragCurrentX = tx
                            dragCurrentY = ty
                            isDragging = false
                            return true
                        }
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (draggingShip != null) {
                        dragCurrentX = tx
                        dragCurrentY = ty
                        val dist = hypot((tx - dragStartX).toDouble(), (ty - dragStartY).toDouble()).toFloat()
                        if (dist > 12f * density) {
                            isDragging = true
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val ship = draggingShip
                    if (ship != null) {
                        if (isDragging && deployGridRect.contains(tx, ty)) {
                            val cellSize = deployGridRect.width() / b.gridCols.toFloat()
                            val newX = ((tx - deployGridRect.left) / cellSize).toInt().coerceIn(0, b.gridCols - 1)
                            val newY = ((ty - deployGridRect.top) / cellSize).toInt().coerceIn(0, b.gridRows - 1)

                            if (b.canPlaceShip(ship.size, newX, newY, ship.isVertical, ignoreShip = ship)) {
                                ship.startX = newX
                                ship.startY = newY
                                soundManager.play(SoundManager.SoundType.TAP, 0.8f)
                                triggerVibration(25L)
                            } else {
                                soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                            }
                        } else {
                            val rotated = b.rotateShip(ship)
                            if (rotated) {
                                soundManager.play(SoundManager.SoundType.TAP, 0.85f)
                                triggerVibration(30L)
                            } else {
                                soundManager.play(SoundManager.SoundType.BOUNCE, 0.6f)
                            }
                        }
                    }
                    draggingShip = null
                    isDragging = false
                }
            }
            return true
        }

        if (phase == SeaPhase.BATTLE) {
            val currentP = getCurrentPlayer() ?: return true
            if (currentP.isBot) return true

            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val oppP = players.firstOrNull { it.id != currentP.id && boards[it.id]?.isEliminated == false } ?: return true
                val oppBoard = boards[oppP.id] ?: return true

                if (enemyGridRect.contains(tx, ty)) {
                    val cellSize = enemyGridRect.width() / oppBoard.gridCols.toFloat()
                    val cellX = ((tx - enemyGridRect.left) / cellSize).toInt().coerceIn(0, oppBoard.gridCols - 1)
                    val cellY = ((ty - enemyGridRect.top) / cellSize).toInt().coerceIn(0, oppBoard.gridRows - 1)

                    if (oppBoard.grid[cellY][cellX] == SeaCellState.FOG) {
                        fireCannon(currentP.id, oppP.id, cellX, cellY)
                        return true
                    } else {
                        soundManager.play(SoundManager.SoundType.BOUNCE, 0.5f)
                        feedbackMessage = "SECTOR ALREADY TARGETED! ⚓"
                        feedbackTimer = 0.8f
                        return true
                    }
                }
            }
        }
        return true
    }

    override fun render(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#080D1A"))

        computeLayout()

        if (totalPlayers >= 3) {
            renderSharedFleetHuntArena(canvas)
        } else {
            when (phase) {
                SeaPhase.PLACEMENT -> renderPlacementPhase(canvas)
                SeaPhase.BATTLE, SeaPhase.ROUND_OVER -> renderDualBattleView(canvas)
            }
        }

        drawActiveCannonball(canvas)
        drawImpactEffects(canvas)
        particles.draw(canvas)
    }

    private fun computeLayout() {
        if (width <= 0 || height <= 0) return

        val topSafe = 68f * density
        val bottomSafe = 20f * density
        val usableH = height - topSafe - bottomSafe

        if (totalPlayers >= 3) {
            // 3-4 Player Shared Fleet Hunt Layout (Screenshot 2)
            val scoreH = 34f * density
            val bottomControlH = 68f * density

            sharedScoreStripRect.set(12f * density, topSafe + 2f * density, width - 12f * density, topSafe + 2f * density + scoreH)

            val arenaTop = sharedScoreStripRect.bottom + 8f * density
            val arenaBottom = height - bottomSafe - bottomControlH
            val arenaH = arenaBottom - arenaTop

            val inventoryW = 46f * density
            val availableGridW = width - 24f * density - inventoryW - 8f * density

            val cellFromW = availableGridW / 7f
            val cellFromH = arenaH / 11f
            val cellSize = min(cellFromW, cellFromH)

            val gridW = cellSize * 7f
            val gridH = cellSize * 11f

            val totalContentW = inventoryW + 8f * density + gridW
            val contentLeft = (width - totalContentW) / 2f
            val gridTop = arenaTop + (arenaH - gridH) / 2f

            sharedInventoryRect.set(contentLeft, gridTop, contentLeft + inventoryW, gridTop + gridH)
            sharedGridRect.set(sharedInventoryRect.right + 8f * density, gridTop, sharedInventoryRect.right + 8f * density + gridW, gridTop + gridH)

            val cannonY = arenaBottom + 14f * density
            cannonTurretRect.set(width / 2f - 24f * density, cannonY, width / 2f + 24f * density, cannonY + 42f * density)
            return
        }

        // 1-2 Player Layouts
        if (phase == SeaPhase.PLACEMENT) {
            val btnW = min(width * 0.42f, 140f * density)
            val btnH = 38f * density
            val btnY = topSafe + 66f * density

            deployRandomBtnRect.set(width / 2f - btnW - 8f * density, btnY, width / 2f - 8f * density, btnY + btnH)
            deploySaveBtnRect.set(width / 2f + 8f * density, btnY, width / 2f + btnW + 8f * density, btnY + btnH)

            val gridTop = btnY + btnH + 16f * density
            val maxGridH = height - bottomSafe - gridTop - 12f * density
            val gridDim = min(width - 32f * density, maxGridH).coerceAtLeast(180f * density)
            val gridLeft = (width - gridDim) / 2f

            deployGridRect.set(gridLeft, gridTop, gridLeft + gridDim, gridTop + gridDim)

            val passW = min(width - 48f * density, 260f * density)
            val passH = 46f * density
            passReadyBtnRect.set(width / 2f - passW / 2f, height / 2f + 50f * density, width / 2f + passW / 2f, height / 2f + 50f * density + passH)
        } else {
            val statusH = 58f * density
            val cannonH = 50f * density
            val gap = 10f * density

            val remainingH = usableH - statusH - cannonH - gap * 3
            val boardDim = min(width - 28f * density, (remainingH / 2f)).coerceAtLeast(140f * density)
            val boardLeft = (width - boardDim) / 2f

            val enemyTop = topSafe + 4f * density
            enemyGridRect.set(boardLeft, enemyTop, boardLeft + boardDim, enemyTop + boardDim)

            val statusTop = enemyGridRect.bottom + gap
            fleetStatusBarRect.set(boardLeft, statusTop, boardLeft + boardDim, statusTop + statusH)

            val friendlyTop = fleetStatusBarRect.bottom + gap
            friendlyGridRect.set(boardLeft, friendlyTop, boardLeft + boardDim, friendlyTop + boardDim)

            val cannonY = friendlyGridRect.bottom + 6f * density
            cannonTurretRect.set(width / 2f - 30f * density, cannonY, width / 2f + 30f * density, cannonY + 44f * density)
        }
    }

    // =========================================================================
    // 3-4 PLAYERS: SHARED FLEET HUNT ARENA (Screenshot 2)
    // =========================================================================
    private fun renderSharedFleetHuntArena(canvas: Canvas) {
        val activeP = getCurrentPlayer() ?: return

        // 1. Top Player Scores Bar
        renderSharedScoreStrip(canvas, activeP)

        // 2. Left Side Ship Inventory Panel (Screenshot 2)
        renderSharedInventoryPanel(canvas)

        // 3. Shared Ocean Grid
        renderSharedOceanGrid(canvas)

        // 4. Bottom Active Player Turn Banner & Cannon Turret
        renderSharedTurnAndCannon(canvas, activeP)
    }

    private fun renderSharedScoreStrip(canvas: Canvas, activeP: Player) {
        val rect = sharedScoreStripRect
        val count = players.size
        val gap = 6f * density
        val itemW = (rect.width() - gap * (count - 1)) / count.toFloat()

        players.forEachIndexed { i, p ->
            val left = rect.left + i * (itemW + gap)
            val cardR = RectF(left, rect.top, left + itemW, rect.bottom)
            val isTurn = (p.id == activeP.id)

            paint.style = Paint.Style.FILL
            paint.color = if (isTurn) Color.argb(60, Color.red(p.id.color), Color.green(p.id.color), Color.blue(p.id.color)) else Color.parseColor("#0C152B")
            canvas.drawRoundRect(cardR, 8f * density, 8f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (isTurn) 2.2f * density else 1f * density
            val pulse = if (isTurn) 0.5f + 0.5f * sin(radarWaveTimer * 6f) else 0f
            paint.color = if (isTurn) p.id.color else Color.parseColor("#1C2C54")
            canvas.drawRoundRect(cardR, 8f * density, 8f * density, paint)

            // Dot
            val dotR = 4f * density
            val dotX = cardR.left + 8f * density
            val dotY = cardR.centerY()
            paint.style = Paint.Style.FILL
            paint.color = p.id.color
            canvas.drawCircle(dotX, dotY, dotR, paint)

            // Name + Score
            paint.color = Color.WHITE
            paint.textSize = 10f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.LEFT
            val fm = paint.fontMetrics
            val base = cardR.centerY() - (fm.ascent + fm.descent) / 2f
            val name = if (p.isBot) "ROBI" else getPlayerDisplayName(p).take(4).uppercase()
            val count = shipsClaimedCount[p.id] ?: 0
            canvas.drawText("$name: $count", dotX + dotR + 4f * density, base, paint)
            paint.isFakeBoldText = false
        }
    }

    private fun renderSharedInventoryPanel(canvas: Canvas) {
        val rect = sharedInventoryRect
        val types = listOf(
            Pair("Patrol Boat", 1),
            Pair("Destroyer", 2),
            Pair("Submarine", 2),
            Pair("Battleship", 3),
            Pair("Carrier", 4)
        )

        val itemH = rect.height() / types.size.toFloat()

        types.forEachIndexed { i, (name, size) ->
            val cy = rect.top + i * itemH + itemH / 2f
            val remaining = sharedBoard.ships.count { it.name == name && !it.isSunk }
            val isActive = remaining > 0

            // Draw mini ship silhouette
            val iconW = (size * 6.5f) * density
            val iconH = 6f * density
            val shipR = RectF(rect.centerX() - iconW / 2f, cy - iconH - 2f * density, rect.centerX() + iconW / 2f, cy - 2f * density)

            paint.style = Paint.Style.FILL
            paint.color = if (isActive) Color.parseColor("#ECEFF1") else Color.parseColor("#37474F")
            canvas.drawRoundRect(shipR, 2.5f * density, 2.5f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * density
            paint.color = if (isActive) Color.BLACK else Color.parseColor("#263238")
            canvas.drawRoundRect(shipR, 2.5f * density, 2.5f * density, paint)

            // Text: xN
            paint.style = Paint.Style.FILL
            paint.color = if (isActive) Color.WHITE else Color.parseColor("#546E7A")
            paint.textSize = 9.5f * density
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("x$remaining", rect.centerX(), cy + 10f * density, paint)
            paint.isFakeBoldText = false
        }
    }

    private fun renderSharedOceanGrid(canvas: Canvas) {
        val rect = sharedGridRect
        val cols = sharedBoard.gridCols
        val rows = sharedBoard.gridRows
        val cellSize = rect.width() / cols.toFloat()

        // Background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#091021")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = Color.parseColor("#1B2A50")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        // Draw Cells
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val cx = rect.left + x * cellSize
                val cy = rect.top + y * cellSize
                val cellR = RectF(cx + 1f * density, cy + 1f * density, cx + cellSize - 1f * density, cy + cellSize - 1f * density)

                val state = sharedBoard.grid[y][x]

                paint.style = Paint.Style.FILL
                paint.color = if ((x + y) % 2 == 0) Color.parseColor("#0E1933") else Color.parseColor("#0A1224")
                canvas.drawRoundRect(cellR, 3f * density, 3f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.6f * density
                paint.color = Color.parseColor("#1B2B4E")
                canvas.drawRoundRect(cellR, 3f * density, 3f * density, paint)

                // Render Miss (Matched to 1 and 2 player mode!)
                if (state == SeaCellState.WATER_MISS) {
                    val ccx = cellR.centerX()
                    val ccy = cellR.centerY()
                    val r = cellSize * 0.36f

                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#3300E5FF")
                    canvas.drawCircle(ccx, ccy, r * 0.75f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1.4f * density
                    paint.color = Color.parseColor("#00E5FF")
                    canvas.drawCircle(ccx, ccy, r * 0.75f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = Color.WHITE
                    canvas.drawCircle(ccx, ccy, 2f * density, paint)
                }

                // Render Hit Flame '🔥' (Matching Screenshot 2!)
                if (state == SeaCellState.SHIP_HIT) {
                    val ccx = cellR.centerX()
                    val ccy = cellR.centerY()
                    val cr = cellSize * 0.32f

                    // Burnt tile
                    val burntPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                    burntPaint.color = Color.parseColor("#1A0F0D")
                    canvas.drawRoundRect(cellR, 3f * density, 3f * density, burntPaint)

                    // Diamond Flame
                    paint.style = Paint.Style.FILL
                    paint.color = Color.parseColor("#FF5722")
                    val path = Path()
                    path.moveTo(ccx, ccy - cr)
                    path.lineTo(ccx + cr, ccy)
                    path.lineTo(ccx, ccy + cr)
                    path.lineTo(ccx - cr, ccy)
                    path.close()
                    canvas.drawPath(path, paint)

                    paint.color = Color.parseColor("#FFD54F")
                    val innerCr = cr * 0.55f
                    val innerPath = Path()
                    innerPath.moveTo(ccx, ccy - innerCr)
                    innerPath.lineTo(ccx + innerCr, ccy)
                    innerPath.lineTo(ccx, ccy + innerCr)
                    innerPath.lineTo(ccx - innerCr, ccy)
                    innerPath.close()
                    canvas.drawPath(innerPath, paint)
                }
            }
        }

        // Draw Sunk Ships in claiming player's color! (Matching Screenshot 2!)
        sharedBoard.ships.filter { it.isSunk }.forEach { ship ->
            val claimColor = if (ship.claimedBy != null) ship.claimedBy!!.color else Color.parseColor("#78909C")
            drawTacticalWarship(canvas, rect, cellSize, ship, claimColor, showRotateIcon = false)
        }
    }

    private fun renderSharedTurnAndCannon(canvas: Canvas, activeP: Player) {
        // Banner Pill
        val bannerW = min(width - 32f * density, 300f * density)
        val bannerH = 26f * density
        val bannerY = sharedGridRect.bottom + 16f * density
        val bannerR = RectF(width / 2f - bannerW / 2f, bannerY - bannerH / 2f, width / 2f + bannerW / 2f, bannerY + bannerH / 2f)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0C152B")
        canvas.drawRoundRect(bannerR, 13f * density, 13f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        paint.color = activeP.id.color
        canvas.drawRoundRect(bannerR, 13f * density, 13f * density, paint)

        val msg = feedbackMessage ?: if (activeP.isBot) "🤖 ROBI'S TURN..." else "${getPlayerDisplayName(activeP).uppercase()}'S TURN — TAP OCEAN TO FIRE!"
        paint.style = Paint.Style.FILL
        paint.textSize = 10f * density
        paint.color = if (feedbackMessage != null) Color.parseColor("#FFD54F") else Color.WHITE
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val base = bannerR.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(msg, bannerR.centerX(), base, paint)
        paint.isFakeBoldText = false

        // Cannon Turret in active player's color
        renderArtilleryTurret(canvas, cannonTurretRect, activeP.id.color)
    }

    // =========================================================================
    // 1-2 PLAYERS: PLACEMENT & DUAL BATTLE VIEW
    // =========================================================================
    private fun renderPlacementPhase(canvas: Canvas) {
        val curP = players.getOrNull(placementPlayerIndex) ?: return

        if (showPassDeviceScreen) {
            renderPassDeviceScreen(canvas, curP)
            return
        }

        if (curP.isBot || botDeployTimer > 0f) {
            renderBotDeployingScreen(canvas, curP)
            return
        }

        renderHumanDeploymentScreen(canvas, curP)
    }

    private fun renderPassDeviceScreen(canvas: Canvas, nextPlayer: Player) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#E6080D1A")
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val cardW = min(width - 40f * density, 330f * density)
        val cardH = 260f * density
        val cardR = RectF(width / 2f - cardW / 2f, height / 2f - cardH / 2f, width / 2f + cardW / 2f, height / 2f + cardH / 2f)

        paint.color = Color.parseColor("#0F1A33")
        canvas.drawRoundRect(cardR, 18f * density, 18f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * density
        paint.color = nextPlayer.id.color
        canvas.drawRoundRect(cardR, 18f * density, 18f * density, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 34f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("📱", cardR.centerX(), cardR.top + 48f * density, paint)

        paint.color = Color.WHITE
        paint.textSize = 14f * density
        paint.isFakeBoldText = true
        canvas.drawText("PASS DEVICE TO", cardR.centerX(), cardR.top + 84f * density, paint)

        paint.color = nextPlayer.id.color
        paint.textSize = 19f * density
        canvas.drawText(getPlayerDisplayName(nextPlayer).uppercase(), cardR.centerX(), cardR.top + 116f * density, paint)

        paint.color = Color.parseColor("#8E9EB8")
        paint.textSize = 11.5f * density
        paint.isFakeBoldText = false
        canvas.drawText("Secret Fleet Deployment - Do not peek!", cardR.centerX(), cardR.top + 144f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#00E676")
        canvas.drawRoundRect(passReadyBtnRect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = Color.WHITE
        canvas.drawRoundRect(passReadyBtnRect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#082312")
        paint.textSize = 13f * density
        paint.isFakeBoldText = true
        val fm = paint.fontMetrics
        val base = passReadyBtnRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("🚀 I'M READY TO DEPLOY", passReadyBtnRect.centerX(), base, paint)
        paint.isFakeBoldText = false
    }

    private fun renderBotDeployingScreen(canvas: Canvas, botPlayer: Player) {
        val centerY = height / 2f

        paint.style = Paint.Style.STROKE
        val wavePulse = (radarWaveTimer % 2f) / 2f
        paint.strokeWidth = 2.5f * density
        paint.color = Color.argb(((1f - wavePulse) * 180).toInt(), 0, 229, 255)
        canvas.drawCircle(width / 2f, centerY - 20f * density, (30f + wavePulse * 70f) * density, paint)

        paint.style = Paint.Style.FILL
        paint.textSize = 42f * density
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🤖", width / 2f, centerY - 10f * density, paint)

        paint.color = Color.WHITE
        paint.textSize = 18f * density
        paint.isFakeBoldText = true
        canvas.drawText("ROBI IS DEPLOYING SHIPS...", width / 2f, centerY + 54f * density, paint)

        paint.color = Color.parseColor("#00E5FF")
        paint.textSize = 12f * density
        paint.isFakeBoldText = false
        canvas.drawText("Tactical fleet positioning in progress", width / 2f, centerY + 80f * density, paint)
    }

    private fun renderHumanDeploymentScreen(canvas: Canvas, player: Player) {
        val b = boards[player.id] ?: return
        val topSafe = 68f * density

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 21f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Deploy your ships", width / 2f, topSafe + 24f * density, paint)

        paint.color = Color.parseColor("#9EAEC7")
        paint.textSize = 11f * density
        paint.isFakeBoldText = false
        canvas.drawText("${getPlayerDisplayName(player).uppercase()}: Tap to rotate, drag or tap Random", width / 2f, topSafe + 44f * density, paint)

        // RANDOM BUTTON
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0288D1")
        canvas.drawRoundRect(deployRandomBtnRect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = Color.parseColor("#29B6F6")
        canvas.drawRoundRect(deployRandomBtnRect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 12f * density
        paint.isFakeBoldText = true
        var fm = paint.fontMetrics
        var base = deployRandomBtnRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("🎲 RANDOM", deployRandomBtnRect.centerX(), base, paint)

        // SAVE BUTTON
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#2E7D32")
        canvas.drawRoundRect(deploySaveBtnRect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        paint.color = Color.parseColor("#00E676")
        canvas.drawRoundRect(deploySaveBtnRect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 12f * density
        paint.isFakeBoldText = true
        fm = paint.fontMetrics
        base = deploySaveBtnRect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("⚡ SAVE FLEET", deploySaveBtnRect.centerX(), base, paint)
        paint.isFakeBoldText = false

        val gSize = b.gridCols
        val cellSize = deployGridRect.width() / gSize.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0C1529")
        canvas.drawRoundRect(deployGridRect, 14f * density, 14f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = player.id.color
        canvas.drawRoundRect(deployGridRect, 14f * density, 14f * density, paint)

        for (y in 0 until gSize) {
            for (x in 0 until gSize) {
                val cx = deployGridRect.left + x * cellSize
                val cy = deployGridRect.top + y * cellSize
                val cellR = RectF(cx + 1.2f * density, cy + 1.2f * density, cx + cellSize - 1.2f * density, cy + cellSize - 1.2f * density)

                paint.style = Paint.Style.FILL
                paint.color = if ((x + y) % 2 == 0) Color.parseColor("#101C36") else Color.parseColor("#0B1428")
                canvas.drawRoundRect(cellR, 4f * density, 4f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f * density
                paint.color = Color.parseColor("#1B2B4E")
                canvas.drawRoundRect(cellR, 4f * density, 4f * density, paint)
            }
        }

        b.ships.forEach { ship ->
            if (ship !== draggingShip) {
                drawTacticalWarship(canvas, deployGridRect, cellSize, ship, player.id.color, showRotateIcon = true)
            }
        }

        if (draggingShip != null && isDragging) {
            val ship = draggingShip!!
            val shipW = if (ship.isVertical) cellSize else ship.size * cellSize
            val shipH = if (ship.isVertical) ship.size * cellSize else cellSize
            val dragRect = RectF(dragCurrentX - shipW / 2f, dragCurrentY - shipH / 2f, dragCurrentX + shipW / 2f, dragCurrentY + shipH / 2f)

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(160, 0, 229, 255)
            canvas.drawRoundRect(dragRect, 8f * density, 8f * density, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.color = Color.WHITE
            canvas.drawRoundRect(dragRect, 8f * density, 8f * density, paint)
        }
    }

    private fun renderDualBattleView(canvas: Canvas) {
        val activeP = getCurrentPlayer() ?: return
        val oppP = players.firstOrNull { it.id != activeP.id } ?: return
        val activeBoard = boards[activeP.id] ?: return
        val oppBoard = boards[oppP.id] ?: return

        renderTargetOceanGrid(canvas, oppP, oppBoard, enemyGridRect)
        renderFleetStatusBar(canvas, oppP, oppBoard, activeP, activeBoard, fleetStatusBarRect)
        renderDefensiveOceanGrid(canvas, activeP, activeBoard, friendlyGridRect)
        renderArtilleryTurret(canvas, cannonTurretRect, activeP.id.color)
    }

    private fun renderTargetOceanGrid(canvas: Canvas, targetPlayer: Player, targetBoard: SeaBoard, rect: RectF) {
        val gSize = targetBoard.gridCols
        val cellSize = rect.width() / gSize.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#091021")
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.color = targetPlayer.id.color
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        for (y in 0 until gSize) {
            for (x in 0 until gSize) {
                val cx = rect.left + x * cellSize
                val cy = rect.top + y * cellSize
                val cellR = RectF(cx + 1f * density, cy + 1f * density, cx + cellSize - 1f * density, cy + cellSize - 1f * density)

                paint.style = Paint.Style.FILL
                paint.color = if ((x + y) % 2 == 0) Color.parseColor("#0D172E") else Color.parseColor("#091022")
                canvas.drawRoundRect(cellR, 3f * density, 3f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f * density
                paint.color = Color.parseColor("#1B2A4E")
                canvas.drawRoundRect(cellR, 3f * density, 3f * density, paint)
            }
        }

        if (isGameOver) {
            targetBoard.ships.forEach { ship ->
                drawTacticalWarship(canvas, rect, cellSize, ship, targetPlayer.id.color, showRotateIcon = false)
            }
        } else {
            targetBoard.ships.filter { it.isSunk }.forEach { ship ->
                drawTacticalWarship(canvas, rect, cellSize, ship, Color.parseColor("#78909C"), showRotateIcon = false)
            }
        }

        drawImpactMarkersOnGrid(canvas, targetBoard, rect, cellSize)

        val sunkCount = targetBoard.ships.count { it.isSunk }
        val pillW = 40f * density
        val pillH = 22f * density
        val pillR = RectF(rect.right - pillW - 6f * density, rect.top + 6f * density, rect.right - 6f * density, rect.top + 6f * density + pillH)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CC14203D")
        canvas.drawRoundRect(pillR, 11f * density, 11f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = targetPlayer.id.color
        canvas.drawRoundRect(pillR, 11f * density, 11f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 10.5f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val base = pillR.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("$sunkCount", pillR.centerX(), base, paint)
        paint.isFakeBoldText = false
    }

    private fun renderDefensiveOceanGrid(canvas: Canvas, friendlyPlayer: Player, friendlyBoard: SeaBoard, rect: RectF) {
        val gSize = friendlyBoard.gridCols
        val cellSize = rect.width() / gSize.toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#091021")
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        paint.color = friendlyPlayer.id.color
        canvas.drawRoundRect(rect, 12f * density, 12f * density, paint)

        for (y in 0 until gSize) {
            for (x in 0 until gSize) {
                val cx = rect.left + x * cellSize
                val cy = rect.top + y * cellSize
                val cellR = RectF(cx + 1f * density, cy + 1f * density, cx + cellSize - 1f * density, cy + cellSize - 1f * density)

                paint.style = Paint.Style.FILL
                paint.color = if ((x + y) % 2 == 0) Color.parseColor("#121422") else Color.parseColor("#0C0E1A")
                canvas.drawRoundRect(cellR, 3f * density, 3f * density, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.8f * density
                paint.color = Color.parseColor("#261D33")
                canvas.drawRoundRect(cellR, 3f * density, 3f * density, paint)
            }
        }

        if (isGameOver) {
            friendlyBoard.ships.forEach { ship ->
                drawTacticalWarship(canvas, rect, cellSize, ship, friendlyPlayer.id.color, showRotateIcon = false)
            }
        } else {
            friendlyBoard.ships.filter { it.isSunk }.forEach { ship ->
                drawTacticalWarship(canvas, rect, cellSize, ship, Color.parseColor("#78909C"), showRotateIcon = false)
            }
        }

        drawImpactMarkersOnGrid(canvas, friendlyBoard, rect, cellSize)

        val sunkCount = friendlyBoard.ships.count { it.isSunk }
        val pillW = 40f * density
        val pillH = 22f * density
        val pillR = RectF(rect.right - pillW - 6f * density, rect.bottom - pillH - 6f * density, rect.right - 6f * density, rect.bottom - 6f * density)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#CC14203D")
        canvas.drawRoundRect(pillR, 11f * density, 11f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = friendlyPlayer.id.color
        canvas.drawRoundRect(pillR, 11f * density, 11f * density, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 10.5f * density
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val base = pillR.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText("$sunkCount", pillR.centerX(), base, paint)
        paint.isFakeBoldText = false
    }

    private fun renderFleetStatusBar(canvas: Canvas, oppP: Player, oppBoard: SeaBoard, activeP: Player, activeBoard: SeaBoard, rect: RectF) {
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#0C1529")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f * density
        paint.color = Color.parseColor("#1B2A50")
        canvas.drawRoundRect(rect, 10f * density, 10f * density, paint)

        val rowH = rect.height() / 2f
        drawFleetMiniIcons(canvas, oppBoard.ships, rect.left + 10f * density, rect.top + 4f * density, rect.width() - 20f * density, rowH - 6f * density, oppP.id.color)
        drawFleetMiniIcons(canvas, activeBoard.ships, rect.left + 10f * density, rect.top + rowH + 2f * density, rect.width() - 20f * density, rowH - 6f * density, activeP.id.color)

        val msg = feedbackMessage ?: if (getCurrentPlayer()?.isBot == true) "🤖 ROBI IS TARGETING..." else "🎯 TAP ENEMY OCEAN TO FIRE!"
        paint.style = Paint.Style.FILL
        paint.textSize = 10.5f * density
        paint.color = if (feedbackMessage != null) Color.parseColor("#FFD54F") else Color.WHITE
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.CENTER
        val fm = paint.fontMetrics
        val base = rect.centerY() - (fm.ascent + fm.descent) / 2f

        val textW = paint.measureText(msg) + 16f * density
        val pillR = RectF(rect.centerX() - textW / 2f, rect.centerY() - 10f * density, rect.centerX() + textW / 2f, rect.centerY() + 10f * density)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        pillPaint.color = Color.parseColor("#E60A1326")
        canvas.drawRoundRect(pillR, 8f * density, 8f * density, pillPaint)

        canvas.drawText(msg, rect.centerX(), base, paint)
        paint.isFakeBoldText = false
    }

    private fun drawFleetMiniIcons(canvas: Canvas, ships: List<SeaShip>, x: Float, y: Float, totalW: Float, h: Float, color: Int) {
        if (ships.isEmpty()) return
        val count = ships.size
        val itemW = totalW / count.toFloat()

        ships.forEachIndexed { i, ship ->
            val cx = x + i * itemW + itemW / 2f
            val cy = y + h / 2f
            val shipLen = min(itemW * 0.78f, (ship.size * 9f) * density)
            val shipH = min(h * 0.70f, 7f * density)
            val shipR = RectF(cx - shipLen / 2f, cy - shipH / 2f, cx + shipLen / 2f, cy + shipH / 2f)

            paint.style = Paint.Style.FILL
            paint.color = if (ship.isSunk) Color.parseColor("#37474F") else color
            canvas.drawRoundRect(shipR, 3.5f * density, 3.5f * density, paint)

            if (ship.isSunk) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.4f * density
                paint.color = Color.parseColor("#FF1744")
                canvas.drawLine(shipR.left, shipR.top, shipR.right, shipR.bottom, paint)
                canvas.drawLine(shipR.left, shipR.bottom, shipR.right, shipR.top, paint)
            }
        }
    }

    private fun renderArtilleryTurret(canvas: Canvas, rect: RectF, playerColor: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val recoilOffset = cannonRecoil * 5f * density

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#091021")
        canvas.drawCircle(cx, cy, 22f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.4f * density
        paint.color = playerColor
        canvas.drawCircle(cx, cy, 22f * density, paint)

        canvas.save()
        canvas.rotate(cannonAngle + 90f, cx, cy)

        val barrelW = 14f * density
        val barrelH = (26f - recoilOffset) * density
        val barrelR = RectF(cx - barrelW / 2f, cy - barrelH, cx + barrelW / 2f, cy)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#212121")
        canvas.drawRoundRect(barrelR, 4f * density, 4f * density, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f * density
        paint.color = Color.parseColor("#757575")
        canvas.drawRoundRect(barrelR, 4f * density, 4f * density, paint)

        val muzzleR = RectF(cx - barrelW * 0.6f, cy - barrelH - 2f * density, cx + barrelW * 0.6f, cy - barrelH + 2f * density)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#424242")
        canvas.drawRoundRect(muzzleR, 2f * density, 2f * density, paint)

        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#263238")
        canvas.drawCircle(cx, cy, 9f * density, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 3f * density, paint)
    }

    private fun drawImpactMarkersOnGrid(canvas: Canvas, board: SeaBoard, gridRect: RectF, cellSize: Float) {
        val gCols = board.gridCols
        val gRows = board.gridRows
        for (y in 0 until gRows) {
            for (x in 0 until gCols) {
                val state = board.grid[y][x]
                val cx = gridRect.left + x * cellSize + cellSize / 2f
                val cy = gridRect.top + y * cellSize + cellSize / 2f
                val r = cellSize * 0.36f

                when (state) {
                    SeaCellState.WATER_MISS -> {
                        paint.style = Paint.Style.FILL
                        paint.color = Color.parseColor("#3300E5FF")
                        canvas.drawCircle(cx, cy, r * 0.75f, paint)

                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.4f * density
                        paint.color = Color.parseColor("#00E5FF")
                        canvas.drawCircle(cx, cy, r * 0.75f, paint)

                        paint.style = Paint.Style.FILL
                        paint.color = Color.WHITE
                        canvas.drawCircle(cx, cy, 2f * density, paint)
                    }

                    SeaCellState.SHIP_HIT -> {
                        paint.style = Paint.Style.FILL
                        paint.color = Color.parseColor("#44FF3D00")
                        canvas.drawCircle(cx, cy, r, paint)

                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 1.8f * density
                        paint.color = Color.parseColor("#FF5722")
                        canvas.drawCircle(cx, cy, r * 0.85f, paint)

                        paint.style = Paint.Style.FILL
                        paint.textSize = cellSize * 0.55f
                        paint.textAlign = Paint.Align.CENTER
                        val ifm = paint.fontMetrics
                        val ibase = cy - (ifm.ascent + ifm.descent) / 2f
                        canvas.drawText("🔥", cx, ibase, paint)
                    }

                    SeaCellState.SHIP_SUNK -> {
                        paint.style = Paint.Style.FILL
                        paint.color = Color.parseColor("#66D50000")
                        canvas.drawCircle(cx, cy, r, paint)

                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f * density
                        paint.color = Color.parseColor("#FF1744")
                        canvas.drawCircle(cx, cy, r * 0.9f, paint)

                        paint.style = Paint.Style.FILL
                        paint.textSize = cellSize * 0.55f
                        paint.textAlign = Paint.Align.CENTER
                        val ifm = paint.fontMetrics
                        val ibase = cy - (ifm.ascent + ifm.descent) / 2f
                        canvas.drawText("💀", cx, ibase, paint)
                    }

                    SeaCellState.FOG -> {}
                }
            }
        }
    }

    private fun drawTacticalWarship(canvas: Canvas, gridRect: RectF, cellSize: Float, ship: SeaShip, color: Int, showRotateIcon: Boolean) {
        val cells = ship.getCells()
        if (cells.isEmpty()) return

        val minX = cells.minOf { it.x }
        val maxX = cells.maxOf { it.x }
        val minY = cells.minOf { it.y }
        val maxY = cells.maxOf { it.y }

        val pad = cellSize * 0.12f
        val shipR = RectF(
            gridRect.left + minX * cellSize + pad,
            gridRect.top + minY * cellSize + pad,
            gridRect.left + (maxX + 1) * cellSize - pad,
            gridRect.top + (maxY + 1) * cellSize - pad
        )

        // 1. Armored Metal Hull in Claimed / Team Color
        paint.style = Paint.Style.FILL
        paint.color = color
        val radius = min(shipR.width(), shipR.height()) / 2.2f
        canvas.drawRoundRect(shipR, radius, radius, paint)

        // 2. Beveled Dark Border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.8f * density
        paint.color = Color.BLACK
        canvas.drawRoundRect(shipR, radius, radius, paint)

        // 3. Deck details based on ship type (Carrier planes, Sub tower, Turrets)
        if (ship.name.contains("Carrier", ignoreCase = true)) {
            // Draw deck jet arrows ✈️
            cells.forEachIndexed { i, pt ->
                val cx = gridRect.left + pt.x * cellSize + cellSize / 2f
                val cy = gridRect.top + pt.y * cellSize + cellSize / 2f
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(190, 0, 0, 0)
                canvas.drawCircle(cx, cy, 2.5f * density, paint)
                paint.textSize = cellSize * 0.32f
                paint.textAlign = Paint.Align.CENTER
                val ifm = paint.fontMetrics
                val ibase = cy - (ifm.ascent + ifm.descent) / 2f
                canvas.drawText("✈", cx, ibase, paint)
            }
        } else if (ship.name.contains("Sub", ignoreCase = true)) {
            // Draw Submarine conning tower & tail
            cells.forEachIndexed { i, pt ->
                val cx = gridRect.left + pt.x * cellSize + cellSize / 2f
                val cy = gridRect.top + pt.y * cellSize + cellSize / 2f
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(160, 0, 0, 0)
                canvas.drawCircle(cx, cy, cellSize * 0.18f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, 2.2f * density, paint)
            }
        } else {
            // Gun Turrets & Bridge
            cells.forEachIndexed { _, pt ->
                val cx = gridRect.left + pt.x * cellSize + cellSize / 2f
                val cy = gridRect.top + pt.y * cellSize + cellSize / 2f

                paint.style = Paint.Style.FILL
                paint.color = Color.argb(140, 0, 0, 0)
                canvas.drawCircle(cx, cy, cellSize * 0.19f, paint)

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f * density
                paint.color = Color.BLACK
                canvas.drawCircle(cx, cy, cellSize * 0.19f, paint)

                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, 2f * density, paint)
            }
        }

        // 4. Rotate Icon indicator (during placement in 1-2P)
        if (showRotateIcon && !ship.isSunk) {
            paint.style = Paint.Style.FILL
            paint.textSize = cellSize * 0.36f
            paint.textAlign = Paint.Align.CENTER
            val ifm = paint.fontMetrics
            val ibase = shipR.centerY() - (ifm.ascent + ifm.descent) / 2f
            canvas.drawText("🔄", shipR.centerX(), ibase, paint)
        }
    }

    private fun drawActiveCannonball(canvas: Canvas) {
        val cb = activeCannonball ?: return
        val t = cb.progress.coerceIn(0f, 1f)

        val curX = cb.fromPt.x + (cb.toPt.x - cb.fromPt.x) * t
        val curY = cb.fromPt.y + (cb.toPt.y - cb.fromPt.y) * t
        val arcH = sin(t * Math.PI).toFloat() * 65f * density

        val ballY = curY - arcH
        val ballR = (6f + sin(t * Math.PI).toFloat() * 3f) * density

        paint.style = Paint.Style.FILL
        paint.color = Color.argb((80 * (1f - (arcH / (70f * density)))).toInt().coerceIn(0, 100), 0, 0, 0)
        canvas.drawOval(curX - ballR, curY - ballR * 0.5f, curX + ballR, curY + ballR * 0.5f, paint)

        paint.color = Color.parseColor("#212121")
        canvas.drawCircle(curX, ballY, ballR, paint)

        paint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(curX, ballY, ballR * 0.45f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * density
        paint.color = Color.parseColor("#FF9100")
        canvas.drawCircle(curX, ballY, ballR, paint)
    }

    private fun drawImpactEffects(canvas: Canvas) {
        splashes.forEach { s ->
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f * density
            paint.color = Color.argb((s.alpha * 255).toInt().coerceIn(0, 255), 0, 229, 255)
            canvas.drawCircle(s.cx, s.cy, s.radius, paint)

            paint.strokeWidth = 1f * density
            paint.color = Color.argb((s.alpha * 180).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawCircle(s.cx, s.cy, s.radius * 0.65f, paint)
        }
    }

    override fun getPlayerScoreString(player: Player): String {
        return if (totalPlayers >= 3) {
            val count = shipsClaimedCount[player.id] ?: 0
            "$count ${if (count == 1) "Ship" else "Ships"}"
        } else {
            val b = boards[player.id]
            val sunken = b?.ships?.count { it.isSunk } ?: 0
            val total = b?.ships?.size ?: 5
            "Fleet $sunken/$total Sunk"
        }
    }

    override fun getPlayerScoreValue(player: Player): Int {
        return if (totalPlayers >= 3) {
            shipsClaimedCount[player.id] ?: 0
        } else {
            val b = boards[player.id]
            b?.ships?.count { it.isSunk } ?: 0
        }
    }

    override fun getTeamScoreString(team: Int): String {
        val count = if (team == 1) {
            (shipsClaimedCount[PlayerId.PLAYER_1] ?: 0) + (shipsClaimedCount[PlayerId.PLAYER_2] ?: 0)
        } else {
            (shipsClaimedCount[PlayerId.PLAYER_3] ?: 0) + (shipsClaimedCount[PlayerId.PLAYER_4] ?: 0)
        }
        return "$count ${if (count == 1) "Ship" else "Ships"}"
    }
}
