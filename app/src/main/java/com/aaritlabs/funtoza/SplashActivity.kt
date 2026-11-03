package com.aaritlabs.funtoza

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager

class SplashActivity : AppCompatActivity() {

    private lateinit var logoContainer: LinearLayout
    private lateinit var iconBadge: FrameLayout
    private lateinit var phase2ProgressLayout: LinearLayout
    private lateinit var progressContainer: FrameLayout
    private lateinit var viewProgressFill: View
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvLoadingStatus: TextView

    private val splashPills = mutableListOf<View>()
    private val splashStars = mutableListOf<View>()
    private var logoBobAnimator: ObjectAnimator? = null
    private val runningAnimators = mutableListOf<ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        hideSystemUI()

        logoContainer = findViewById(R.id.logoContainer)
        iconBadge = findViewById(R.id.iconBadge)
        phase2ProgressLayout = findViewById(R.id.phase2ProgressLayout)
        progressContainer = findViewById(R.id.progressContainer)
        viewProgressFill = findViewById(R.id.viewProgressFill)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus)

        findViewById<View>(R.id.splashPill1)?.let { splashPills.add(it) }
        findViewById<View>(R.id.splashPill2)?.let { splashPills.add(it) }
        findViewById<View>(R.id.splashPill3)?.let { splashPills.add(it) }
        findViewById<View>(R.id.splashPill4)?.let { splashPills.add(it) }

        findViewById<View>(R.id.splashStarTopLeft)?.let { splashStars.add(it) }
        findViewById<View>(R.id.splashStarTopRight)?.let { splashStars.add(it) }
        findViewById<View>(R.id.splashStarBottomLeft)?.let { splashStars.add(it) }
        findViewById<View>(R.id.splashStarBottomRight)?.let { splashStars.add(it) }

        // Initialize sound engine asynchronously
        Thread {
            SoundManager.getInstance(applicationContext)
        }.start()

        startSparkleStarsAnimation()
        runTwoPhaseSplash()
    }

    private fun runTwoPhaseSplash() {
        // === PHASE 1: ANIMATION SCREEN ===
        logoContainer.scaleX = 0.4f
        logoContainer.scaleY = 0.4f
        logoContainer.alpha = 0f
        iconBadge.rotation = -25f

        iconBadge.animate()
            .rotation(0f)
            .setDuration(900)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()

        logoContainer.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(850)
            .setInterpolator(OvershootInterpolator(1.5f))
            .withEndAction {
                startLogoBobbingAnimation()
                startPillsGlowWaveAnimation()

                // Hold animation screen for a moment, then transition to Phase 2
                logoContainer.postDelayed({
                    startPhase2ProgressBar()
                }, 900)
            }
            .start()
    }

    private fun startLogoBobbingAnimation() {
        if (isFinishing || isDestroyed) return
        logoBobAnimator = ObjectAnimator.ofFloat(iconBadge, "translationY", -10f, 10f).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startPillsGlowWaveAnimation() {
        if (isFinishing || isDestroyed || splashPills.isEmpty()) return

        splashPills.forEachIndexed { index, pill ->
            val animator = ValueAnimator.ofFloat(0.4f, 1.0f).apply {
                duration = 500
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                startDelay = (index * 130).toLong()
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    pill.alpha = fraction
                    val scale = 0.95f + (0.25f * fraction)
                    pill.scaleX = scale
                    pill.scaleY = scale
                }
                start()
            }
            runningAnimators.add(animator)
        }
    }

    private fun startSparkleStarsAnimation() {
        val starConfigs = listOf(
            Triple(1600L, 0L, 25f),
            Triple(2000L, 500L, -35f),
            Triple(1800L, 1000L, 40f),
            Triple(1700L, 300L, -20f)
        )

        splashStars.forEachIndexed { index, star ->
            val (duration, startDelay, rotationAngle) = starConfigs.getOrElse(index) {
                Triple(1800L, 0L, 30f)
            }
            star.alpha = 0.25f
            star.scaleX = 0.75f
            star.scaleY = 0.75f

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                this.startDelay = startDelay
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    val frac = va.animatedValue as Float
                    star.alpha = 0.25f + (0.75f * frac)
                    val scale = 0.75f + (0.45f * frac)
                    star.scaleX = scale
                    star.scaleY = scale
                    star.rotation = rotationAngle * frac
                }
                start()
            }
            runningAnimators.add(animator)
        }
    }

    private fun startPhase2ProgressBar() {
        // === PHASE 2: PROGRESS BAR SCREEN ===
        // Float logo slightly upward to reveal progress screen
        logoContainer.animate()
            .translationY(-60f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        phase2ProgressLayout.visibility = View.VISIBLE
        phase2ProgressLayout.alpha = 0f
        phase2ProgressLayout.translationY = 40f
        phase2ProgressLayout.animate()
            .alpha(1.0f)
            .translationY(0f)
            .setDuration(450)
            .start()

        progressContainer.post {
            val totalWidth = progressContainer.width
            val progressAnim = ValueAnimator.ofFloat(0f, 1f)
            progressAnim.duration = 1600
            progressAnim.interpolator = AccelerateDecelerateInterpolator()

            progressAnim.addUpdateListener { anim ->
                val fraction = anim.animatedValue as Float
                val currentWidth = (totalWidth * fraction).toInt()
                val params = viewProgressFill.layoutParams
                params.width = currentWidth
                viewProgressFill.layoutParams = params

                val percent = (fraction * 100).toInt()
                tvProgressPercent.text = "$percent%"

                // Dynamic loading steps
                when {
                    percent < 30 -> tvLoadingStatus.text = "LOADING PARTY ARENA..."
                    percent < 60 -> tvLoadingStatus.text = "TUNING GAME ENGINES..."
                    percent < 90 -> tvLoadingStatus.text = "READYING BOTS & MODES..."
                    else -> tvLoadingStatus.text = "READY TO PLAY!"
                }
            }

            progressAnim.start()

            // Navigate to MainActivity upon completion
            progressContainer.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    val intent = Intent(this@SplashActivity, MainActivity::class.java)
                    startActivity(intent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }, 1950)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logoBobAnimator?.cancel()
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
    }

    private fun hideSystemUI() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {}
    }
}
