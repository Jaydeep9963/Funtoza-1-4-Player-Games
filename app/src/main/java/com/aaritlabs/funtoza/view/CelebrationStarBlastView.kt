package com.aaritlabs.funtoza.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*
import kotlin.random.Random

class CelebrationStarBlastView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density
    private val random = Random(System.currentTimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var animator: ValueAnimator? = null
    private var isPlaying = false
    private var blastColor: Int = Color.parseColor("#FFD54F")

    // Aura pulse
    private var animTime = 0f

    // Star particle classes
    private data class BlastStar(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var rotation: Float,
        var rotSpeed: Float,
        var color: Int,
        var alpha: Float = 1.0f,
        var decay: Float = 0.012f
    )

    private data class FloatingShowerStar(
        val seedX: Float,
        val seedY: Float,
        val speed: Float,
        val swayFreq: Float,
        val swayAmp: Float,
        val baseRotation: Float,
        val spinSpeed: Float,
        val size: Float,
        val color: Int
    )

    // Dedicated ambient sparkle stars positioned around the perimeter of the dialog card
    // (exact style, colors & twinkle as in SplashActivity and MainActivity)
    private data class AmbientCardSparkle(
        val offsetXdp: Float,
        val offsetYdp: Float,
        val sizeDp: Float,
        val color: Int,
        val phaseOffset: Float,
        val rotSpeed: Float
    )

    private val blastStars = mutableListOf<BlastStar>()
    private val showerStars = mutableListOf<FloatingShowerStar>()
    private val ambientCardSparkles = mutableListOf<AmbientCardSparkle>()

    // Color palette matching Splash and MainActivity sparkle stars
    private val starPalette = listOf(
        Color.parseColor("#E8A278"), // Coral Gold (Splash Top-Left)
        Color.parseColor("#5CE1E6"), // Bright Electric Cyan (Splash/Main Top-Right & Mid-Right)
        Color.parseColor("#D86290"), // Soft Cyber Pink (Splash/Main Bottom-Left)
        Color.parseColor("#FFB800"), // Warm Sunny Gold (Splash Bottom-Right)
        Color.parseColor("#FFD54F"), // Amber Gold
        Color.parseColor("#FFEE58"), // Lemon Sparkle
        Color.parseColor("#FFFFFF"), // Pure White Diamond
        Color.parseColor("#FF2A6D")  // Neon Red/Pink
    )

    init {
        // 1. Ambient Sparkle Stars explicitly ringing around the winner modal
        // Top-Left corner
        ambientCardSparkles.add(AmbientCardSparkle(-180f, -165f, 22f, Color.parseColor("#E8A278"), 0f, 25f))
        ambientCardSparkles.add(AmbientCardSparkle(-205f, -110f, 16f, Color.parseColor("#5CE1E6"), 1.2f, -30f))
        // Top-Right corner
        ambientCardSparkles.add(AmbientCardSparkle(185f, -160f, 20f, Color.parseColor("#5CE1E6"), 2.1f, 35f))
        ambientCardSparkles.add(AmbientCardSparkle(210f, -95f, 15f, Color.parseColor("#FFB800"), 0.8f, -20f))
        // Mid-Left & Mid-Right flanks
        ambientCardSparkles.add(AmbientCardSparkle(-195f, 15f, 24f, Color.parseColor("#D86290"), 3.4f, 40f))
        ambientCardSparkles.add(AmbientCardSparkle(195f, 10f, 22f, Color.parseColor("#FFD54F"), 1.7f, -35f))
        // Bottom-Left & Bottom-Right corners
        ambientCardSparkles.add(AmbientCardSparkle(-185f, 165f, 20f, Color.parseColor("#FFEE58"), 2.7f, 30f))
        ambientCardSparkles.add(AmbientCardSparkle(-205f, 110f, 15f, Color.parseColor("#E8A278"), 0.5f, -25f))
        ambientCardSparkles.add(AmbientCardSparkle(185f, 165f, 22f, Color.parseColor("#5CE1E6"), 4.0f, -40f))
        ambientCardSparkles.add(AmbientCardSparkle(210f, 115f, 16f, Color.parseColor("#D86290"), 1.9f, 25f))
        // Crown above and below
        ambientCardSparkles.add(AmbientCardSparkle(-15f, -205f, 26f, Color.parseColor("#FFFFFF"), 0.3f, 45f))
        ambientCardSparkles.add(AmbientCardSparkle(45f, -195f, 18f, Color.parseColor("#FFD54F"), 2.4f, -30f))
        ambientCardSparkles.add(AmbientCardSparkle(20f, 205f, 24f, Color.parseColor("#FF2A6D"), 3.1f, 35f))
        ambientCardSparkles.add(AmbientCardSparkle(-40f, 195f, 17f, Color.parseColor("#5CE1E6"), 1.5f, -20f))

        // 2. Pre-generate 55 floating background sparkle stars
        for (i in 0 until 55) {
            val seedX = ((i * 37 + 13) % 100) / 100f
            val seedY = ((i * 61 + 29) % 100) / 100f
            val speed = 0.18f + ((i * 7) % 10) * 0.035f
            val swayFreq = 1.2f + (i % 4) * 0.4f
            val swayAmp = (8f + (i % 5) * 4f) * density
            val baseRotation = (i * 53) % 360f
            val spinSpeed = ((i % 5) - 2) * 90f
            val size = (13f + (i % 5) * 3.5f) * density
            val c = starPalette[i % starPalette.size]

            showerStars.add(
                FloatingShowerStar(
                    seedX = seedX,
                    seedY = seedY,
                    speed = speed,
                    swayFreq = swayFreq,
                    swayAmp = swayAmp,
                    baseRotation = baseRotation,
                    spinSpeed = spinSpeed,
                    size = size,
                    color = c
                )
            )
        }
    }

    fun startBlast(winnerColor: Int? = null) {
        winnerColor?.let { blastColor = it }
        blastStars.clear()

        val cx = if (width > 0) width / 2f else context.resources.displayMetrics.widthPixels / 2f
        val cy = if (height > 0) height / 2f else context.resources.displayMetrics.heightPixels / 2f

        // Initial High-Energy Sparkle Star Blast:
        // Stars erupt violently outward past the dialog card margins
        val blastCount = 70
        for (i in 0 until blastCount) {
            val angle = random.nextDouble(0.0, Math.PI * 2)
            val speed = random.nextDouble(10.0, 32.0).toFloat() * density
            val size = random.nextDouble(14.0, 32.0).toFloat() * density
            val color = if (i % 3 == 0 && winnerColor != null) {
                winnerColor
            } else {
                starPalette[random.nextInt(starPalette.size)]
            }

            blastStars.add(
                BlastStar(
                    x = cx,
                    y = cy,
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed).toFloat(),
                    size = size,
                    rotation = random.nextFloat() * 360f,
                    rotSpeed = (random.nextFloat() * 300f - 150f),
                    color = color,
                    alpha = 1.0f,
                    decay = random.nextDouble(0.007, 0.018).toFloat()
                )
            )
        }

        isPlaying = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animTime += 0.022f
                updateBlast()
                invalidate()
            }
            start()
        }
    }

    fun stopBlast() {
        isPlaying = false
        animator?.cancel()
        animator = null
        blastStars.clear()
        invalidate()
    }

    private fun updateBlast() {
        val iterator = blastStars.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.x += s.vx
            s.y += s.vy
            s.vx *= 0.95f // Air resistance
            s.vy *= 0.95f
            s.rotation += s.rotSpeed * 0.02f
            s.alpha -= s.decay
            if (s.alpha <= 0.01f) {
                iterator.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPlaying && blastStars.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // 1. Radiant Glowing Aura behind the dialog card
        val auraPulse = 0.95f + 0.15f * sin(animTime * 3.5f)
        val auraRadius = (180f * density) * auraPulse
        val auraColors = intArrayOf(
            (blastColor and 0x00FFFFFF) or 0x66000000,
            0x44FF2A6D.toInt(),
            0x22FFD54F.toInt(),
            Color.TRANSPARENT
        )
        val auraStops = floatArrayOf(0f, 0.40f, 0.70f, 1f)
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, auraRadius, auraColors, auraStops, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, auraRadius, paint)
        paint.shader = null

        // 2. Floating & Drifting Sparkle Star Shower
        showerStars.forEach { p ->
            val curY = ((p.seedY + animTime * p.speed) % 1f) * h
            val sway = sin(animTime * p.swayFreq * 2.0 * PI + p.seedX * 10.0).toFloat() * p.swayAmp
            val curX = (p.seedX * w) + sway
            val rot = p.baseRotation + animTime * p.spinSpeed

            // Twinkle alpha & scale pulse
            val twinkle = 0.65f + 0.35f * sin(animTime * 4.5f + p.seedX * 20f)
            val alphaInt = (twinkle.coerceIn(0f, 1f) * 255).toInt()
            val scale = 0.85f + 0.25f * twinkle

            drawSparkleStar(canvas, curX, curY, p.size * scale, rot, p.color, alphaInt)
        }

        // 3. Ambient Sparkle Stars explicitly positioned around the popup card
        // Twinkle and scale in harmony with Splash and Main screens
        ambientCardSparkles.forEach { s ->
            val px = cx + s.offsetXdp * density
            val py = cy + s.offsetYdp * density

            val twinkle = 0.5f + 0.5f * sin(animTime * 3.8f + s.phaseOffset)
            val scale = 0.75f + 0.45f * twinkle
            val alphaInt = (0.35f + 0.65f * twinkle).coerceIn(0f, 1f)
            val rot = animTime * s.rotSpeed + s.phaseOffset * 30f

            drawSparkleStar(canvas, px, py, s.sizeDp * density * scale, rot, s.color, (alphaInt * 255).toInt())
        }

        // 4. Initial Star Blast Outward Explosion Particles
        blastStars.forEach { s ->
            val alphaInt = (s.alpha.coerceIn(0f, 1f) * 255).toInt()
            drawSparkleStar(canvas, s.x, s.y, s.size, s.rotation, s.color, alphaInt)
        }
    }

    /**
     * Draws the exact 4-pointed diamond sparkle star matching ic_sparkle_star.xml
     * (used in SplashActivity and MainActivity).
     */
    private fun drawSparkleStar(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        rotationDeg: Float,
        color: Int,
        alpha: Int
    ) {
        if (alpha <= 0) return

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationDeg)

        val r = radius / 2f
        val c = r * 0.5523f

        // Outer concave 4-point diamond star
        val path = Path().apply {
            moveTo(0f, -r)
            cubicTo(0f, -r + c, r - c, 0f, r, 0f)
            cubicTo(r - c, 0f, 0f, r - c, 0f, r)
            cubicTo(0f, r - c, -r + c, 0f, -r, 0f)
            cubicTo(-r + c, 0f, 0f, -r + c, 0f, -r)
            close()
        }

        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = alpha
        canvas.drawPath(path, paint)

        // Inner glowing white core diamond
        paint.color = Color.WHITE
        paint.alpha = (alpha * 0.90f).toInt()
        val coreR = r * 0.45f
        val coreC = coreR * 0.5523f
        val corePath = Path().apply {
            moveTo(0f, -coreR)
            cubicTo(0f, -coreR + coreC, coreR - coreC, 0f, coreR, 0f)
            cubicTo(coreR - coreC, 0f, 0f, coreR - coreC, 0f, coreR)
            cubicTo(0f, coreR - coreC, -coreR + coreC, 0f, -coreR, 0f)
            cubicTo(-coreR + coreC, 0f, 0f, -coreR + coreC, 0f, -coreR)
            close()
        }
        canvas.drawPath(corePath, paint)

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopBlast()
    }
}
