package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class EmbeddedKnife(
    val owner: PlayerId,
    val angleOnTargetDeg: Float
)

data class FlyingKnife(
    val player: Player,
    var x: Float,
    var y: Float,
    val targetX: Float,
    val targetY: Float,
    val angleDeg: Float,
    var speed: Float = 1200f
)

class KnifeHitEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val targetRadius = 90f
    private var targetAngleDeg = 0f
    private var targetRotSpeed = 90f // deg/s
    private var rotTimer = 0f

    private val embeddedKnives = mutableListOf<EmbeddedKnife>()
    private val flyingKnives = mutableListOf<FlyingKnife>()
    private val playerKnifeCounts = mutableMapOf<PlayerId, Int>()
    private val maxKnivesToWin = 4

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(System.currentTimeMillis())

    override fun initGame() {
        embeddedKnives.clear()
        flyingKnives.clear()
        playerKnifeCounts.clear()
        isGameOver = false

        players.forEach {
            playerKnifeCounts[it.id] = 0
            it.isAlive = true
        }

        // Add 2 initial hazard pins on target
        embeddedKnives.add(EmbeddedKnife(PlayerId.PLAYER_1, 0f))
        embeddedKnives.add(EmbeddedKnife(PlayerId.PLAYER_2, 180f))
    }

    override fun onPlayerButtonPressed(player: Player) {
        if (isGameOver) return
        launchKnife(player)
    }

    private fun launchKnife(player: Player) {
        // Find player spawn point around perimeter
        val cx = width / 2f
        val cy = height / 2f
        val spawnDist = min(width, height) * 0.44f

        val index = players.indexOf(player).coerceAtLeast(0)
        val angleDeg = when (index) {
            0 -> 90f // from bottom
            1 -> 270f // from top
            2 -> 180f // from left
            else -> 0f // from right
        }

        val rad = Math.toRadians(angleDeg.toDouble())
        val sx = (cx + cos(rad) * spawnDist).toFloat()
        val sy = (cy + sin(rad) * spawnDist).toFloat()

        flyingKnives.add(
            FlyingKnife(
                player = player,
                x = sx,
                y = sy,
                targetX = cx,
                targetY = cy,
                angleDeg = (angleDeg + 180f) % 360f
            )
        )
        soundManager.play(SoundManager.SoundType.SHOOT, 0.5f)
    }

    override fun update(deltaTime: Float) {
        if (isGameOver) {
            particles.update()
            return
        }

        // Target rotation & occasional direction flip
        targetAngleDeg = (targetAngleDeg + targetRotSpeed * deltaTime) % 360f
        rotTimer += deltaTime
        if (rotTimer > 2.5f) {
            rotTimer = 0f
            targetRotSpeed = if (random.nextBoolean()) 120f else -120f
        }

        // Bot AI
        players.filter { it.isBot }.forEach { bot ->
            if (random.nextInt(100) < 3) {
                // Check if gap is clear
                launchKnife(bot)
            }
        }

        val cx = width / 2f
        val cy = height / 2f

        // Move flying knives towards target center
        val kIter = flyingKnives.iterator()
        while (kIter.hasNext()) {
            val k = kIter.next()
            val rad = Math.toRadians(k.angleDeg.toDouble())
            k.x += (cos(rad) * k.speed * deltaTime).toFloat()
            k.y += (sin(rad) * k.speed * deltaTime).toFloat()

            // Check if reached target perimeter
            val distToCenter = hypot(k.x - cx, k.y - cy)
            if (distToCenter <= targetRadius) {
                // Calculate impact angle relative to rotating target
                val globalAngle = (Math.toDegrees(atan2((k.y - cy).toDouble(), (k.x - cx).toDouble())).toFloat() + 360f) % 360f
                val relativeAngle = (globalAngle - targetAngleDeg + 360f) % 360f

                // Check collision with embedded knives
                var collided = false
                for (emb in embeddedKnives) {
                    val angleDiff = abs(emb.angleOnTargetDeg - relativeAngle)
                    val normDiff = min(angleDiff, 360f - angleDiff)
                    if (normDiff < 14f) {
                        collided = true
                        break
                    }
                }

                if (collided) {
                    // Knife clatter
                    soundManager.play(SoundManager.SoundType.BOUNCE, 0.9f)
                    particles.spawnExplosion(k.x, k.y, Color.parseColor("#FFD23F"), 25)
                    triggerVibration(70)
                    kIter.remove()
                } else {
                    // Successfully embedded
                    embeddedKnives.add(EmbeddedKnife(k.player.id, relativeAngle))
                    val count = (playerKnifeCounts[k.player.id] ?: 0) + 1
                    playerKnifeCounts[k.player.id] = count
                    soundManager.play(SoundManager.SoundType.TAP, 0.9f)
                    triggerVibration(40)
                    particles.spawnExplosion(k.x, k.y, k.player.id.color, 12)
                    kIter.remove()

                    if (count >= maxKnivesToWin) {
                        endRound(k.player)
                        break
                    }
                }
            }
        }

        particles.update()
    }

    override fun render(canvas: Canvas) {
        // Dark background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#121422")
        canvas.drawRect(0f, 0f, width, height, paint)

        val cx = width / 2f
        val cy = height / 2f

        // Draw central rotating target
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(targetAngleDeg)

        // Wood/Neon Log
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#3D291D")
        canvas.drawCircle(0f, 0f, targetRadius, paint)

        paint.color = Color.parseColor("#5A3E2C")
        canvas.drawCircle(0f, 0f, targetRadius * 0.75f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.parseColor("#FF9500")
        canvas.drawCircle(0f, 0f, targetRadius, paint)

        // Draw embedded knives rotating with target
        embeddedKnives.forEach { emb ->
            canvas.save()
            canvas.rotate(emb.angleOnTargetDeg)
            paint.style = Paint.Style.FILL
            paint.color = emb.owner.color
            // Blade sticking out
            canvas.drawRect(targetRadius - 10f, -4f, targetRadius + 35f, 4f, paint)
            // Handle
            paint.color = Color.WHITE
            canvas.drawCircle(targetRadius + 38f, 0f, 6f, paint)
            canvas.restore()
        }

        canvas.restore()

        // Draw Flying Knives
        flyingKnives.forEach { k ->
            canvas.save()
            canvas.translate(k.x, k.y)
            canvas.rotate(k.angleDeg)
            paint.style = Paint.Style.FILL
            paint.color = k.player.id.color
            canvas.drawRect(-20f, -4f, 20f, 4f, paint)
            canvas.restore()
        }

        // Knives remaining HUD for each player
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER
        players.forEach { p ->
            val count = playerKnifeCounts[p.id] ?: 0
            paint.color = p.id.color
            val zone = controlZones[p.id]
            if (zone != null) {
                canvas.drawText("🎯 $count / $maxKnivesToWin", zone.centerX(), zone.centerY() - 35f, paint)
            }
        }

        // Particles
        particles.draw(canvas)

        // Player Buttons
        drawPlayerControlButtons(canvas, paint)
    }
}

