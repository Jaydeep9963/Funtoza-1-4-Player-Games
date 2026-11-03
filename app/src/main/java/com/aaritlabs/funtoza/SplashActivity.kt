package com.aaritlabs.funtoza

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

        // Initialize sound engine asynchronously
        Thread {
            SoundManager.getInstance(applicationContext)
        }.start()

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
                // Hold animation screen for a moment, then transition to Phase 2
                logoContainer.postDelayed({
                    startPhase2ProgressBar()
                }, 900)
            }
            .start()
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

    private fun hideSystemUI() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {}
    }
}
