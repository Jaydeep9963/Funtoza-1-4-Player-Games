package com.aaritlabs.funtoza

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.GameType

class SelectGameActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager
    private var playerCount: Int = 2
    private var botDifficulty: Int = 1
    private var playerNames: ArrayList<String> = arrayListOf()

    private val starViews = mutableListOf<View>()
    private val activeAnimators = mutableListOf<ValueAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_game)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        playerCount = intent.getIntExtra("PLAYER_COUNT", 2)
        botDifficulty = intent.getIntExtra("BOT_DIFFICULTY", 1)
        playerNames = intent.getStringArrayListExtra("PLAYER_NAMES") ?: arrayListOf()

        val btnBack = findViewById<ImageButton>(R.id.btnSelectGameBack)
        val tvMatchup = findViewById<TextView>(R.id.tvSelectGameMatchup)

        val cardTank = findViewById<LinearLayout>(R.id.cardSelectTank)
        val cardSoccer = findViewById<LinearLayout>(R.id.cardSelectSoccer)
        val cardSnake = findViewById<LinearLayout>(R.id.cardSelectSnake)
        val cardSumo = findViewById<LinearLayout>(R.id.cardSelectSumo)
        val cardKnife = findViewById<LinearLayout>(R.id.cardSelectKnife)
        val cardTicTacToe = findViewById<LinearLayout>(R.id.cardSelectTicTacToe)

        findViewById<View>(R.id.selectStarTopLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarTopRight)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarMidLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarBottomLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarBottomRight)?.let { starViews.add(it) }

        // Tic Tac Toe is exclusively for 1 & 2 players
        cardTicTacToe.visibility = if (playerCount <= 2) View.VISIBLE else View.GONE

        // Set matchup subtitle
        tvMatchup.text = when {
            playerCount == 1 -> {
                val diffStr = when (botDifficulty) {
                    0 -> "Easy"
                    2 -> "Hard"
                    else -> "Medium"
                }
                "${playerNames.getOrElse(0) { "Player 1" }} ⚔️ Bot ($diffStr)"
            }
            playerCount == 2 -> {
                "${playerNames.getOrElse(0) { "Player 1" }} ⚔️ ${playerNames.getOrElse(1) { "Player 2" }}"
            }
            playerCount == 3 -> {
                "${playerNames.getOrElse(0) { "P1" }} • ${playerNames.getOrElse(1) { "P2" }} • ${playerNames.getOrElse(2) { "P3" }}"
            }
            playerCount == 4 -> {
                "4 PLAYERS BATTLE ARENA"
            }
            else -> "PARTY ARENA"
        }

        btnBack.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            finish()
        }

        cardTank.setOnClickListener { launchGame(GameType.TANK_BATTLE) }
        cardSoccer.setOnClickListener { launchGame(GameType.FINGER_SOCCER) }
        cardSnake.setOnClickListener { launchGame(GameType.NEON_SNAKE) }
        cardSumo.setOnClickListener { launchGame(GameType.SUMO_BUMPER) }
        cardKnife.setOnClickListener { launchGame(GameType.TARGET_BLITZ) }
        cardTicTacToe.setOnClickListener { launchGame(GameType.TIC_TAC_TOE) }

        startSparkleStarsAnimation()
    }

    private fun startSparkleStarsAnimation() {
        val starConfigs = listOf(
            Triple(1600L, 0L, 25f),
            Triple(2000L, 500L, -35f),
            Triple(1800L, 1000L, 40f),
            Triple(2200L, 750L, -25f),
            Triple(1700L, 250L, 30f)
        )

        starViews.forEachIndexed { index, star ->
            val (duration, startDelay, rotationAngle) = starConfigs[index % starConfigs.size]
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
            activeAnimators.add(animator)
        }
    }

    private fun launchGame(gameType: GameType) {
        soundManager.play(SoundManager.SoundType.TAP)
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("GAME_TYPE", gameType.name)
            putExtra("PLAYER_COUNT", playerCount)
            putExtra("BOT_DIFFICULTY", botDifficulty)
            putStringArrayListExtra("PLAYER_NAMES", playerNames)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
    }

    private fun hideSystemUI() {
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {}
    }
}
