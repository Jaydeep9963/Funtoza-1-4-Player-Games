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
    private var isTeam: Boolean = false
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
        isTeam = intent.getBooleanExtra("IS_TEAM", false)
        playerNames = intent.getStringArrayListExtra("PLAYER_NAMES") ?: arrayListOf()

        val btnBack = findViewById<ImageButton>(R.id.btnSelectGameBack)
        val tvMatchup = findViewById<TextView>(R.id.tvSelectGameMatchup)

        val cardLudo = findViewById<LinearLayout>(R.id.cardSelectLudo)
        val cardSoccer = findViewById<LinearLayout>(R.id.cardSelectSoccer)
        val cardSnake = findViewById<LinearLayout>(R.id.cardSelectSnake)
        val tvSelectSnakeTitle = findViewById<TextView>(R.id.tvSelectSnakeTitle)
        val tvSelectSnakeDesc = findViewById<TextView>(R.id.tvSelectSnakeDesc)
        val cardSnakesAndLadders = findViewById<LinearLayout>(R.id.cardSelectSnakesAndLadders)
        val cardKnife = findViewById<LinearLayout>(R.id.cardSelectKnife)
        val cardTicTacToe = findViewById<LinearLayout>(R.id.cardSelectTicTacToe)
        val cardOops = findViewById<LinearLayout>(R.id.cardSelectOops)
        val cardSeaBattle = findViewById<LinearLayout>(R.id.cardSelectSeaBattle)
        val cardDotsAndBoxes = findViewById<LinearLayout>(R.id.cardSelectDotsAndBoxes)
        val cardMemory = findViewById<LinearLayout>(R.id.cardSelectMemory)

        if (playerCount == 1) {
            tvSelectSnakeTitle?.text = "SLITHER SNAKE"
            tvSelectSnakeDesc?.text = "Slither & grow! Eat glowing pellets, boost & defeat rival worms!"
        } else {
            tvSelectSnakeTitle?.text = "NEON SNAKE"
            tvSelectSnakeDesc?.text = "Tron-style light trails! Cut off opponents and survive!"
        }

        findViewById<View>(R.id.selectStarTopLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarTopRight)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarMidLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarBottomLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.selectStarBottomRight)?.let { starViews.add(it) }

        // Filter game cards based on maxPlayers configured in GameType
        cardSnake.visibility = if (playerCount <= GameType.NEON_SNAKE.maxPlayers) View.VISIBLE else View.GONE
        cardTicTacToe.visibility = if (playerCount <= GameType.TIC_TAC_TOE.maxPlayers) View.VISIBLE else View.GONE
        cardLudo.visibility = if (playerCount <= GameType.LUDO.maxPlayers) View.VISIBLE else View.GONE
        cardSoccer.visibility = if (playerCount <= GameType.FINGER_SOCCER.maxPlayers) View.VISIBLE else View.GONE
        cardSnakesAndLadders.visibility = if (playerCount <= GameType.SNAKES_AND_LADDERS.maxPlayers) View.VISIBLE else View.GONE
        cardKnife.visibility = if (playerCount <= GameType.TARGET_BLITZ.maxPlayers) View.VISIBLE else View.GONE
        cardOops.visibility = if (playerCount <= GameType.OOPS.maxPlayers) View.VISIBLE else View.GONE
        cardSeaBattle.visibility = if (playerCount <= GameType.SEA_BATTLE.maxPlayers) View.VISIBLE else View.GONE
        cardDotsAndBoxes.visibility = if (playerCount <= GameType.DOTS_AND_BOXES.maxPlayers) View.VISIBLE else View.GONE
        cardMemory.visibility = if (playerCount <= GameType.MEMORY.maxPlayers) View.VISIBLE else View.GONE

        // Set matchup subtitle
        tvMatchup.text = when {
            isTeam -> {
                val p1 = playerNames.getOrElse(0) { "P1" }
                val p2 = playerNames.getOrElse(1) { "P2" }
                val p3 = playerNames.getOrElse(2) { "P3" }
                val p4 = playerNames.getOrElse(3) { "P4" }
                "TEAM 1 ($p1+$p2) ⚔️ TEAM 2 ($p3+$p4)"
            }
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

        cardLudo.setOnClickListener { launchGame(GameType.LUDO) }
        cardSoccer.setOnClickListener { launchGame(GameType.FINGER_SOCCER) }
        cardSnake.setOnClickListener { launchGame(GameType.NEON_SNAKE) }
        cardSnakesAndLadders.setOnClickListener { launchGame(GameType.SNAKES_AND_LADDERS) }
        cardKnife.setOnClickListener { launchGame(GameType.TARGET_BLITZ) }
        cardTicTacToe.setOnClickListener { launchGame(GameType.TIC_TAC_TOE) }
        cardOops.setOnClickListener { launchGame(GameType.OOPS) }
        cardSeaBattle.setOnClickListener { launchGame(GameType.SEA_BATTLE) }
        cardDotsAndBoxes.setOnClickListener { launchGame(GameType.DOTS_AND_BOXES) }
        cardMemory.setOnClickListener { launchGame(GameType.MEMORY) }

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
            putExtra("IS_TEAM", isTeam)
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
