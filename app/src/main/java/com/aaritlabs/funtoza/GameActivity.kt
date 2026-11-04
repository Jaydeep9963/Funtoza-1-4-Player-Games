package com.aaritlabs.funtoza

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.engine.*
import com.aaritlabs.funtoza.model.GameType
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.view.CelebrationStarBlastView
import com.aaritlabs.funtoza.view.GameSurfaceView

class GameActivity : AppCompatActivity() {

    private lateinit var gameSurfaceView: GameSurfaceView
    private lateinit var tvCurrentGameTitle: TextView
    private lateinit var btnPause: ImageButton
    private lateinit var pauseOverlay: View
    private lateinit var btnResume: Button
    private lateinit var btnRestart: Button
    private lateinit var btnExitMenu: Button

    private lateinit var winnerOverlay: View
    private lateinit var starBlastView: CelebrationStarBlastView
    private lateinit var tvWinnerName: TextView
    private lateinit var btnNextRound: Button
    private lateinit var btnWinnerExit: Button

    // Outside & inside sparkle stars for pause dialog
    private val pauseStarViews = mutableListOf<View>()
    private val pauseBoxStarViews = mutableListOf<View>()
    private val pauseAnimators = mutableListOf<ValueAnimator>()

    // Inside sparkle stars for winner dialog
    private val winnerBoxStarViews = mutableListOf<View>()
    private val winnerBoxAnimators = mutableListOf<ValueAnimator>()

    // Snake Game Over Modal Views & Animators
    private lateinit var snakeGameOverOverlay: View
    private lateinit var tvSnakeFinalScore: TextView
    private lateinit var tvSnakeFinalRank: TextView
    private lateinit var btnSnakeWatchAdContinue: Button
    private lateinit var btnSnakePlayAgain: Button
    private lateinit var btnSnakeExitBack: Button
    private lateinit var adSimulatedOverlay: View
    private lateinit var tvAdCountdown: TextView
    private lateinit var pbAdProgress: ProgressBar
    private val snakeGameOverStarViews = mutableListOf<View>()
    private val snakeGameOverAnimators = mutableListOf<ValueAnimator>()
    private var adAnimator: ValueAnimator? = null

    private lateinit var soundManager: SoundManager
    private var gameType: GameType = GameType.TANK_BATTLE
    private var playerCount: Int = 2
    private var botDifficulty: Int = 1
    private var isTournamentMode = false
    private var playerNames: ArrayList<String> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        val gameTypeName = intent.getStringExtra("GAME_TYPE") ?: GameType.TANK_BATTLE.name
        gameType = try {
            GameType.valueOf(gameTypeName)
        } catch (_: Exception) {
            GameType.TANK_BATTLE
        }
        playerCount = intent.getIntExtra("PLAYER_COUNT", 2)
        botDifficulty = intent.getIntExtra("BOT_DIFFICULTY", 1)
        isTournamentMode = intent.getBooleanExtra("IS_TOURNAMENT_MODE", false)
        playerNames = intent.getStringArrayListExtra("PLAYER_NAMES") ?: arrayListOf()

        initViews()
        startGame()
    }

    private fun initViews() {
        gameSurfaceView = findViewById(R.id.gameSurfaceView)
        tvCurrentGameTitle = findViewById(R.id.tvCurrentGameTitle)
        btnPause = findViewById(R.id.btnPause)
        pauseOverlay = findViewById(R.id.pauseOverlay)
        btnResume = findViewById(R.id.btnResume)
        btnRestart = findViewById(R.id.btnRestart)
        btnExitMenu = findViewById(R.id.btnExitMenu)

        winnerOverlay = findViewById(R.id.winnerOverlay)
        starBlastView = findViewById(R.id.starBlastView)
        tvWinnerName = findViewById(R.id.tvWinnerName)
        btnNextRound = findViewById(R.id.btnNextRound)
        btnWinnerExit = findViewById(R.id.btnWinnerExit)

        // Outside Pause Stars
        findViewById<View>(R.id.pauseStarTopLeft)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarTopRight)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarTopCenter)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarMidLeft)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarMidRight)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarBottomLeft)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarBottomRight)?.let { pauseStarViews.add(it) }
        findViewById<View>(R.id.pauseStarBottomCenter)?.let { pauseStarViews.add(it) }

        // Inside Pause Box Stars
        findViewById<View>(R.id.pauseBoxStarTopLeft)?.let { pauseBoxStarViews.add(it) }
        findViewById<View>(R.id.pauseBoxStarTopRight)?.let { pauseBoxStarViews.add(it) }
        findViewById<View>(R.id.pauseBoxStarMidLeft)?.let { pauseBoxStarViews.add(it) }
        findViewById<View>(R.id.pauseBoxStarMidRight)?.let { pauseBoxStarViews.add(it) }
        findViewById<View>(R.id.pauseBoxStarBottomLeft)?.let { pauseBoxStarViews.add(it) }
        findViewById<View>(R.id.pauseBoxStarBottomRight)?.let { pauseBoxStarViews.add(it) }

        // Inside Winner Box Stars
        findViewById<View>(R.id.winnerBoxStarTopLeft)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarTopRight)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarTrophyLeft)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarTrophyRight)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarMidLeft)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarMidRight)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarBottomLeft)?.let { winnerBoxStarViews.add(it) }
        findViewById<View>(R.id.winnerBoxStarBottomRight)?.let { winnerBoxStarViews.add(it) }

        // Snake Game Over Modal
        snakeGameOverOverlay = findViewById(R.id.snakeGameOverOverlay)
        tvSnakeFinalScore = findViewById(R.id.tvSnakeFinalScore)
        tvSnakeFinalRank = findViewById(R.id.tvSnakeFinalRank)
        btnSnakeWatchAdContinue = findViewById(R.id.btnSnakeWatchAdContinue)
        btnSnakePlayAgain = findViewById(R.id.btnSnakePlayAgain)
        btnSnakeExitBack = findViewById(R.id.btnSnakeExitBack)

        adSimulatedOverlay = findViewById(R.id.adSimulatedOverlay)
        tvAdCountdown = findViewById(R.id.tvAdCountdown)
        pbAdProgress = findViewById(R.id.pbAdProgress)

        findViewById<View>(R.id.snakeGameOverStar1)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverStar2)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverStar3)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverStar4)?.let { snakeGameOverStarViews.add(it) }

        // Inside Snake Game Over Box Stars
        findViewById<View>(R.id.snakeGameOverBoxStarTopLeft)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarTopRight)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarSkullLeft)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarSkullRight)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarMidLeft)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarMidRight)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarBottomLeft)?.let { snakeGameOverStarViews.add(it) }
        findViewById<View>(R.id.snakeGameOverBoxStarBottomRight)?.let { snakeGameOverStarViews.add(it) }

        tvCurrentGameTitle.text = gameType.title.uppercase()

        btnPause.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            pauseOverlay.visibility = View.VISIBLE
            startPauseSparklesAnimation()
            gameSurfaceView.pauseGame()
        }

        btnResume.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopPauseSparklesAnimation()
            pauseOverlay.visibility = View.GONE
            gameSurfaceView.resumeGame()
        }

        btnRestart.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopPauseSparklesAnimation()
            stopWinnerBoxSparklesAnimation()
            stopSnakeGameOverSparklesAnimation()
            adAnimator?.cancel()
            starBlastView.stopBlast()
            pauseOverlay.visibility = View.GONE
            winnerOverlay.visibility = View.GONE
            snakeGameOverOverlay.visibility = View.GONE
            adSimulatedOverlay.visibility = View.GONE
            startGame()
            gameSurfaceView.resumeGame()
        }

        btnExitMenu.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopPauseSparklesAnimation()
            stopWinnerBoxSparklesAnimation()
            stopSnakeGameOverSparklesAnimation()
            adAnimator?.cancel()
            starBlastView.stopBlast()
            finish()
        }

        btnWinnerExit.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopWinnerBoxSparklesAnimation()
            starBlastView.stopBlast()
            finish()
        }

        btnNextRound.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopWinnerBoxSparklesAnimation()
            starBlastView.stopBlast()
            if (isTournamentMode) {
                // Return to tournament
                finish()
            } else {
                // Play another round
                winnerOverlay.visibility = View.GONE
                startGame()
                gameSurfaceView.resumeGame()
            }
        }
    }

    private fun startPauseSparklesAnimation() {
        stopPauseSparklesAnimation()
        val allPauseStars = pauseStarViews + pauseBoxStarViews

        val starConfigs = listOf(
            Triple(1600L, 0L, 25f),
            Triple(2000L, 500L, -35f),
            Triple(1700L, 250L, 30f),
            Triple(1900L, 800L, -30f),
            Triple(2100L, 400L, 35f),
            Triple(1800L, 1000L, 40f),
            Triple(2200L, 650L, -25f),
            Triple(1650L, 150L, 20f)
        )

        allPauseStars.forEachIndexed { index, star ->
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
            pauseAnimators.add(animator)
        }
    }

    private fun stopPauseSparklesAnimation() {
        pauseAnimators.forEach { it.cancel() }
        pauseAnimators.clear()
    }

    private fun startWinnerBoxSparklesAnimation() {
        stopWinnerBoxSparklesAnimation()

        val starConfigs = listOf(
            Triple(1500L, 0L, 30f),
            Triple(1800L, 300L, -35f),
            Triple(1600L, 600L, 40f),
            Triple(2000L, 150L, -25f),
            Triple(1700L, 750L, 35f),
            Triple(1900L, 450L, -40f),
            Triple(1650L, 200L, 25f),
            Triple(2100L, 850L, -30f)
        )

        winnerBoxStarViews.forEachIndexed { index, star ->
            val (duration, startDelay, rotationAngle) = starConfigs[index % starConfigs.size]
            star.alpha = 0.30f
            star.scaleX = 0.80f
            star.scaleY = 0.80f

            val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                this.duration = duration
                this.startDelay = startDelay
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    val frac = va.animatedValue as Float
                    star.alpha = 0.30f + (0.70f * frac)
                    val scale = 0.80f + (0.45f * frac)
                    star.scaleX = scale
                    star.scaleY = scale
                    star.rotation = rotationAngle * frac
                }
                start()
            }
            winnerBoxAnimators.add(animator)
        }
    }

    private fun stopWinnerBoxSparklesAnimation() {
        winnerBoxAnimators.forEach { it.cancel() }
        winnerBoxAnimators.clear()
    }

    private fun startGame() {
        val engine: BaseGameEngine = when (gameType) {
            GameType.TANK_BATTLE -> TankBattleEngine(this, playerCount, soundManager)
            GameType.FINGER_SOCCER -> SoccerEngine(this, playerCount, soundManager)
            GameType.NEON_SNAKE -> {
                if (playerCount == 1) {
                    val slither = SlitherSnakeEngine(this, playerCount, soundManager, botDifficulty)
                    slither.onPlayerDiedListener = { score, rank ->
                        runOnUiThread {
                            showSnakeGameOverDialog(score, rank, slither)
                        }
                    }
                    slither
                } else {
                    SnakeEngine(this, playerCount, soundManager)
                }
            }
            GameType.SUMO_BUMPER -> SumoEngine(this, playerCount, soundManager)
            GameType.TARGET_BLITZ -> KnifeHitEngine(this, playerCount, soundManager)
            GameType.TIC_TAC_TOE -> TicTacToeEngine(this, playerCount, soundManager, botDifficulty)
        }

        engine.playerNames = playerNames
        engine.onRoundOverListener = { winner ->
            runOnUiThread {
                showWinnerDialog(winner, engine)
            }
        }

        gameSurfaceView.engine = engine
        if (gameSurfaceView.width > 0 && gameSurfaceView.height > 0) {
            engine.onSurfaceChanged(gameSurfaceView.width.toFloat(), gameSurfaceView.height.toFloat())
        }
    }

    private fun showWinnerDialog(winner: Player?, engine: BaseGameEngine) {
        val winnerText = if (winner != null) {
            "👑 ${engine.getPlayerDisplayName(winner).uppercase()} WINS!"
        } else {
            "DRAW MATCH!"
        }

        tvWinnerName.text = winnerText
        val winnerColor = winner?.id?.color ?: Color.parseColor("#FFD54F")
        tvWinnerName.setTextColor(winnerColor)

        if (isTournamentMode) {
            btnNextRound.text = "RETURN TO PODIUM"
            val returnIntent = Intent().apply {
                winner?.let { putExtra("WINNER_PLAYER_ID", it.id.id) }
            }
            setResult(Activity.RESULT_OK, returnIntent)
        } else {
            btnNextRound.text = "PLAY AGAIN"
        }

        winnerOverlay.visibility = View.VISIBLE
        starBlastView.startBlast(winnerColor)
        startWinnerBoxSparklesAnimation()
    }

    private fun showSnakeGameOverDialog(score: Int, rank: Int, slitherEngine: SlitherSnakeEngine) {
        tvSnakeFinalScore.text = "$score"
        tvSnakeFinalRank.text = "#$rank"

        snakeGameOverOverlay.visibility = View.VISIBLE
        startSnakeGameOverSparklesAnimation()
        gameSurfaceView.pauseGame()

        btnSnakeWatchAdContinue.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            playRewardedAdSimulation(slitherEngine)
        }

        btnSnakePlayAgain.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopSnakeGameOverSparklesAnimation()
            adAnimator?.cancel()
            adSimulatedOverlay.visibility = View.GONE
            snakeGameOverOverlay.visibility = View.GONE
            startGame()
            gameSurfaceView.resumeGame()
        }

        btnSnakeExitBack.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            stopSnakeGameOverSparklesAnimation()
            adAnimator?.cancel()
            finish()
        }
    }

    private fun playRewardedAdSimulation(slitherEngine: SlitherSnakeEngine) {
        adSimulatedOverlay.visibility = View.VISIBLE
        pbAdProgress.progress = 0
        tvAdCountdown.text = "Reviving your snake in 3s..."

        adAnimator?.cancel()
        adAnimator = ValueAnimator.ofInt(0, 100).apply {
            duration = 2600L
            addUpdateListener { va ->
                val prog = va.animatedValue as Int
                pbAdProgress.progress = prog
                val remainingSec = ((100 - prog) * 2.6f / 100f).toInt() + 1
                tvAdCountdown.text = "Reviving your snake in ${remainingSec}s..."
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    soundManager.play(SoundManager.SoundType.GOAL)
                    adSimulatedOverlay.visibility = View.GONE
                    snakeGameOverOverlay.visibility = View.GONE
                    stopSnakeGameOverSparklesAnimation()
                    slitherEngine.revivePlayer()
                    gameSurfaceView.resumeGame()
                }
            })
            start()
        }
    }

    private fun startSnakeGameOverSparklesAnimation() {
        stopSnakeGameOverSparklesAnimation()
        val starConfigs = listOf(
            Triple(1500L, 0L, 30f),
            Triple(1800L, 300L, -35f),
            Triple(1600L, 650L, 25f),
            Triple(1900L, 150L, -30f),
            Triple(1700L, 450L, 35f),
            Triple(2000L, 800L, -25f),
            Triple(1650L, 250L, 20f),
            Triple(1850L, 550L, -40f)
        )

        snakeGameOverStarViews.forEachIndexed { index, star ->
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
            snakeGameOverAnimators.add(animator)
        }
    }

    private fun stopSnakeGameOverSparklesAnimation() {
        snakeGameOverAnimators.forEach { it.cancel() }
        snakeGameOverAnimators.clear()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (pauseOverlay.visibility != View.VISIBLE &&
            winnerOverlay.visibility != View.VISIBLE &&
            snakeGameOverOverlay.visibility != View.VISIBLE &&
            adSimulatedOverlay.visibility != View.VISIBLE
        ) {
            gameSurfaceView.resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPauseSparklesAnimation()
        stopWinnerBoxSparklesAnimation()
        stopSnakeGameOverSparklesAnimation()
        adAnimator?.cancel()
        starBlastView.stopBlast()
        gameSurfaceView.pauseGame()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPauseSparklesAnimation()
        stopWinnerBoxSparklesAnimation()
        stopSnakeGameOverSparklesAnimation()
        adAnimator?.cancel()
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
