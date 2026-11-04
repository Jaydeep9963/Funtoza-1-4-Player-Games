package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import com.aaritlabs.funtoza.view.ParticleSystem

abstract class BaseGameEngine(
    val context: Context,
    val totalPlayers: Int,
    val soundManager: SoundManager
) {
    val players = mutableListOf<Player>()
    val particles = ParticleSystem()
    var width: Float = 0f
    var height: Float = 0f
    var isInitialized = false
    var isGameOver = false
    var winner: Player? = null

    // Touch boundaries for each player button/zone
    val controlZones = mutableMapOf<PlayerId, RectF>()
    var playerNames: List<String> = emptyList()

    fun getPlayerDisplayName(player: Player): String {
        val index = player.id.id - 1
        return if (index in playerNames.indices && playerNames[index].isNotBlank()) {
            playerNames[index]
        } else {
            player.id.displayName
        }
    }

    open fun getPlayerScoreString(player: Player): String {
        val wins = player.score
        return "$wins ${if (wins == 1) "Win" else "Wins"}"
    }

    open fun getPlayerScoreValue(player: Player): Int {
        return player.score
    }

    open fun shouldShowPlayerScoresOnWinnerDialog(): Boolean = true

    var onRoundOverListener: ((winner: Player?) -> Unit)? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    init {
        for (i in 1..totalPlayers) {
            val pId = PlayerId.fromId(i)
            val isBot = (totalPlayers == 1 && i > 1)
            players.add(Player(id = pId, isBot = isBot))
        }
        // If single player, we spawn 1 bot opponent if totalPlayers was set to 1
        if (totalPlayers == 1 && players.size == 1) {
            players.add(Player(id = PlayerId.PLAYER_2, isBot = true))
        }
    }

    open fun onSurfaceChanged(w: Float, h: Float) {
        width = w
        height = h
        calculateControlZones()
        if (!isInitialized) {
            initGame()
            isInitialized = true
        }
    }

    abstract fun initGame()
    abstract fun update(deltaTime: Float)
    abstract fun render(canvas: Canvas)

    open fun calculateControlZones() {
        controlZones.clear()
        val isPortrait = height > width
        val zoneSize = if (isPortrait) width * 0.38f else Math.min(width, height) * 0.28f
        val edgeH = if (isPortrait) height * 0.16f else height * 0.24f

        when (players.size) {
            2 -> {
                if (isPortrait) {
                    // P1 bottom edge, P2 top edge (players facing each other across the phone!)
                    controlZones[PlayerId.PLAYER_1] = RectF(0f, height - edgeH, width, height)
                    controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width, edgeH)
                } else {
                    controlZones[PlayerId.PLAYER_1] = RectF(0f, height - edgeH * 1.5f, width, height)
                    controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width, edgeH * 1.5f)
                }
            }
            3 -> {
                if (isPortrait) {
                    controlZones[PlayerId.PLAYER_1] = RectF(0f, height - edgeH, width, height)
                    controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width / 2f, edgeH)
                    controlZones[PlayerId.PLAYER_3] = RectF(width / 2f, 0f, width, edgeH)
                } else {
                    controlZones[PlayerId.PLAYER_1] = RectF(0f, height - edgeH * 1.5f, width, height)
                    controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, width / 2f, edgeH * 1.5f)
                    controlZones[PlayerId.PLAYER_3] = RectF(width / 2f, 0f, width, edgeH * 1.5f)
                }
            }
            4 -> {
                // 4 corners around the phone edges
                val cornerW = width * 0.40f
                val cornerH = if (isPortrait) height * 0.14f else zoneSize
                controlZones[PlayerId.PLAYER_1] = RectF(0f, height - cornerH, cornerW, height)
                controlZones[PlayerId.PLAYER_2] = RectF(0f, 0f, cornerW, cornerH)
                controlZones[PlayerId.PLAYER_3] = RectF(width - cornerW, 0f, width, cornerH)
                controlZones[PlayerId.PLAYER_4] = RectF(width - cornerW, height - cornerH, width, height)
            }
        }
    }

    open fun onTouchEvent(event: MotionEvent): Boolean {
        val actionMasked = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)

        when (actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                handleTouchPress(x, y, true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                handleTouchPress(x, y, false)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val px = event.getX(i)
                    val py = event.getY(i)
                    updatePlayerTouchPos(px, py)
                }
            }
        }
        return true
    }

    private fun handleTouchPress(x: Float, y: Float, isPressed: Boolean) {
        for ((pId, rect) in controlZones) {
            if (rect.contains(x, y)) {
                val player = players.firstOrNull { it.id == pId && !it.isBot } ?: continue
                player.isTouching = isPressed
                player.touchX = x
                player.touchY = y
                if (isPressed) {
                    onPlayerButtonPressed(player)
                }
            }
        }
    }

    private fun updatePlayerTouchPos(x: Float, y: Float) {
        for ((pId, rect) in controlZones) {
            if (rect.contains(x, y)) {
                val player = players.firstOrNull { it.id == pId && !it.isBot } ?: continue
                player.touchX = x
                player.touchY = y
            }
        }
    }

    open fun onPlayerButtonPressed(player: Player) {
        // Default trigger
    }

    fun triggerVibration(durationMs: Long = 40) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }

    fun endRound(roundWinner: Player?) {
        if (isGameOver) return
        isGameOver = true
        winner = roundWinner
        roundWinner?.let {
            it.score++
            particles.spawnExplosion(width / 2f, height / 2f, it.id.color, 60)
            soundManager.play(SoundManager.SoundType.VICTORY)
        }
        triggerVibration(100)
        onRoundOverListener?.invoke(roundWinner)
    }

    open fun drawPlayerControlButtons(canvas: Canvas, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        for ((pId, rect) in controlZones) {
            val player = players.firstOrNull { it.id == pId } ?: continue
            paint.color = player.id.color
            paint.alpha = if (player.isTouching) 220 else 90
            paint.style = if (player.isTouching) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            canvas.drawRoundRect(rect, 24f, 24f, paint)

            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            val label = if (player.isBot) "BOT" else "HOLD"
            canvas.drawText(label, rect.centerX(), rect.centerY() + 10f, paint)
        }
    }
}

