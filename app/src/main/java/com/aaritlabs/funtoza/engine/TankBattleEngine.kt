package com.aaritlabs.funtoza.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
import kotlin.math.*
import kotlin.random.Random

data class Bullet(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val owner: PlayerId,
    var bouncesLeft: Int = 3,
    var life: Float = 6.0f
)

data class Tank(
    val player: Player,
    var x: Float,
    var y: Float,
    var angleDeg: Float,
    var isDestroyed: Boolean = false,
    var shootCooldown: Float = 0f
)

class TankBattleEngine(
    context: Context,
    totalPlayers: Int,
    soundManager: SoundManager
) : BaseGameEngine(context, totalPlayers, soundManager) {

    private val tanks = mutableListOf<Tank>()
    private val bullets = mutableListOf<Bullet>()
    private val obstacles = mutableListOf<RectF>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(42)

    private val tankSize = 34f
    private val bulletRadius = 7f
    private val moveSpeed = 160f
    private val rotSpeed = 150f // deg per sec

    override fun initGame() {
        tanks.clear()
        bullets.clear()
        obstacles.clear()
        isGameOver = false

        // Generate arena boundaries & obstacle blocks
        val arenaMargin = 40f
        val playRect = RectF(arenaMargin, arenaMargin, width - arenaMargin, height - arenaMargin)

        // Center obstacles
        val cx = width / 2f
        val cy = height / 2f
        val blockSize = min(width, height) * 0.12f
        obstacles.add(RectF(cx - blockSize, cy - blockSize, cx + blockSize, cy + blockSize))
        obstacles.add(RectF(cx - blockSize * 2.5f, cy - blockSize * 0.4f, cx - blockSize * 1.5f, cy + blockSize * 0.4f))
        obstacles.add(RectF(cx + blockSize * 1.5f, cy - blockSize * 0.4f, cx + blockSize * 2.5f, cy + blockSize * 0.4f))

        // Spawn positions for players
        val spawnPoints = listOf(
            Pair(playRect.left + 100f, playRect.bottom - 100f) to 315f,
            Pair(playRect.right - 100f, playRect.top + 100f) to 135f,
            Pair(playRect.left + 100f, playRect.top + 100f) to 45f,
            Pair(playRect.right - 100f, playRect.bottom - 100f) to 225f
        )

        players.forEachIndexed { index, player ->
            player.isAlive = true
            val (pos, angle) = spawnPoints[index % spawnPoints.size]
            tanks.add(
                Tank(
                    player = player,
                    x = pos.first,
                    y = pos.second,
                    angleDeg = angle
                )
            )
        }
    }

    override fun update(deltaTime: Float) {
        if (isGameOver) {
            particles.update()
            return
        }

        // Bot AI
        tanks.forEach { tank ->
            if (tank.player.isBot && !tank.isDestroyed) {
                updateBotAI(tank, deltaTime)
            }
        }

        // Update Tanks
        tanks.forEach { tank ->
            if (tank.isDestroyed) return@forEach
            tank.shootCooldown = max(0f, tank.shootCooldown - deltaTime)

            if (tank.player.isTouching) {
                // Moving forward
                val rad = Math.toRadians(tank.angleDeg.toDouble())
                val nx = (tank.x + cos(rad) * moveSpeed * deltaTime).toFloat()
                val ny = (tank.y + sin(rad) * moveSpeed * deltaTime).toFloat()

                // Collision with boundary
                val margin = 50f
                if (nx in margin..(width - margin) && ny in margin..(height - margin)) {
                    // Check obstacle collision
                    val tankRect = RectF(nx - tankSize, ny - tankSize, nx + tankSize, ny + tankSize)
                    val hitsObstacle = obstacles.any { RectF.intersects(it, tankRect) }
                    if (!hitsObstacle) {
                        tank.x = nx
                        tank.y = ny
                    }
                }

                // Fire bullet periodically while holding
                if (tank.shootCooldown <= 0f) {
                    fireBullet(tank)
                    tank.shootCooldown = 0.6f
                }
            } else {
                // Rotate in place
                tank.angleDeg = (tank.angleDeg + rotSpeed * deltaTime) % 360f
            }
        }

        // Update Bullets
        val bIter = bullets.iterator()
        while (bIter.hasNext()) {
            val b = bIter.next()
            b.life -= deltaTime
            b.x += b.vx * deltaTime
            b.y += b.vy * deltaTime

            if (b.life <= 0f) {
                bIter.remove()
                continue
            }

            // Bounce on boundaries
            val margin = 40f
            var bounced = false
            if (b.x <= margin || b.x >= width - margin) {
                b.vx = -b.vx
                bounced = true
            }
            if (b.y <= margin || b.y >= height - margin) {
                b.vy = -b.vy
                bounced = true
            }

            // Bounce on obstacles
            obstacles.forEach { obs ->
                if (obs.contains(b.x, b.y)) {
                    b.vx = -b.vx
                    b.vy = -b.vy
                    bounced = true
                }
            }

            if (bounced) {
                b.bouncesLeft--
                soundManager.play(SoundManager.SoundType.BOUNCE, 0.4f)
                if (b.bouncesLeft < 0) {
                    particles.spawnExplosion(b.x, b.y, Color.YELLOW, 10)
                    bIter.remove()
                    continue
                }
            }

            // Check hit tank
            tanks.forEach { tank ->
                if (!tank.isDestroyed) {
                    val dist = hypot(b.x - tank.x, b.y - tank.y)
                    if (dist < tankSize) {
                        tank.isDestroyed = true
                        tank.player.isAlive = false
                        particles.spawnExplosion(tank.x, tank.y, tank.player.id.color, 40)
                        soundManager.play(SoundManager.SoundType.EXPLOSION)
                        triggerVibration(80)
                        bIter.remove()
                        checkWinCondition()
                        return@forEach
                    }
                }
            }
        }

        particles.update()
    }

    private fun fireBullet(tank: Tank) {
        val rad = Math.toRadians(tank.angleDeg.toDouble())
        val bulletSpeed = 480f
        val spawnDist = tankSize + 12f
        val bx = tank.x + (cos(rad) * spawnDist).toFloat()
        val by = tank.y + (sin(rad) * spawnDist).toFloat()
        val bvx = (cos(rad) * bulletSpeed).toFloat()
        val bvy = (sin(rad) * bulletSpeed).toFloat()

        bullets.add(Bullet(bx, by, bvx, bvy, tank.player.id))
        soundManager.play(SoundManager.SoundType.SHOOT, 0.6f)
    }

    private fun updateBotAI(tank: Tank, deltaTime: Float) {
        val target = tanks.firstOrNull { it != tank && !it.isDestroyed } ?: return
        val angleToTarget = Math.toDegrees(atan2((target.y - tank.y).toDouble(), (target.x - tank.x).toDouble())).toFloat()
        val normTargetAngle = (angleToTarget + 360f) % 360f
        val diff = abs(normTargetAngle - tank.angleDeg)

        // If aimed approximately towards opponent, hold to shoot and move
        tank.player.isTouching = diff < 28f || diff > 332f
    }

    private fun checkWinCondition() {
        val aliveTanks = tanks.filter { !it.isDestroyed }
        if (aliveTanks.size <= 1) {
            val winner = aliveTanks.firstOrNull()?.player
            endRound(winner)
        }
    }

    override fun render(canvas: Canvas) {
        // Arena floor
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#151829")
        canvas.drawRect(0f, 0f, width, height, paint)

        // Arena border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.color = Color.parseColor("#2E365C")
        canvas.drawRect(40f, 40f, width - 40f, height - 40f, paint)

        // Obstacles
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#242B45")
        obstacles.forEach {
            canvas.drawRoundRect(it, 16f, 16f, paint)
        }

        // Draw Tanks
        tanks.forEach { tank ->
            if (tank.isDestroyed) return@forEach
            canvas.save()
            canvas.translate(tank.x, tank.y)
            canvas.rotate(tank.angleDeg)

            // Tank Body
            paint.style = Paint.Style.FILL
            paint.color = tank.player.id.color
            canvas.drawRoundRect(-tankSize, -tankSize * 0.75f, tankSize, tankSize * 0.75f, 8f, 8f, paint)

            // Tracks
            paint.color = Color.DKGRAY
            canvas.drawRect(-tankSize * 1.1f, -tankSize * 0.85f, tankSize * 1.1f, -tankSize * 0.65f, paint)
            canvas.drawRect(-tankSize * 1.1f, tankSize * 0.65f, tankSize * 1.1f, tankSize * 0.85f, paint)

            // Turret & Cannon
            paint.color = tank.player.id.darkColor
            canvas.drawCircle(0f, 0f, tankSize * 0.45f, paint)
            paint.color = Color.WHITE
            canvas.drawRect(0f, -4f, tankSize * 1.3f, 4f, paint)

            canvas.restore()
        }

        // Draw Bullets
        bullets.forEach { b ->
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#FFD23F")
            canvas.drawCircle(b.x, b.y, bulletRadius, paint)
        }

        // Particles
        particles.draw(canvas)

        // Control buttons
        drawPlayerControlButtons(canvas, paint)
    }
}

