package com.aaritlabs.funtoza

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.engine.*
import com.aaritlabs.funtoza.model.GameType
import com.aaritlabs.funtoza.model.Player
import com.aaritlabs.funtoza.model.PlayerId
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
    private lateinit var layoutWinnerScores: LinearLayout
    private lateinit var btnNextRound: Button
    private lateinit var btnWinnerExit: Button

    private val sessionWins = mutableMapOf<PlayerId, Int>()

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
    private var gameType: GameType = GameType.LUDO
    private var playerCount: Int = 2
    private var botDifficulty: Int = 1
    private var isTournamentMode = false
    private var isTeamMode = false
    private var playerNames: ArrayList<String> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        val gameTypeName = intent.getStringExtra("GAME_TYPE") ?: GameType.LUDO.name
        gameType = try {
            GameType.valueOf(gameTypeName)
        } catch (_: Exception) {
            GameType.LUDO
        }
        playerCount = intent.getIntExtra("PLAYER_COUNT", 2)
        botDifficulty = intent.getIntExtra("BOT_DIFFICULTY", 1)
        isTournamentMode = intent.getBooleanExtra("IS_TOURNAMENT_MODE", false)
        isTeamMode = intent.getBooleanExtra("IS_TEAM", false)
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
        layoutWinnerScores = findViewById(R.id.layoutWinnerScores)
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
            GameType.LUDO -> LudoEngine(this, playerCount, soundManager, botDifficulty)
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
            GameType.SNAKES_AND_LADDERS -> SnakesAndLaddersEngine(this, playerCount, soundManager, botDifficulty)
            GameType.TARGET_BLITZ -> KnifeHitEngine(this, playerCount, soundManager, botDifficulty)
            GameType.TIC_TAC_TOE -> TicTacToeEngine(this, playerCount, soundManager, botDifficulty)
            GameType.OOPS -> OopsEngine(this, playerCount, soundManager, botDifficulty)
            GameType.SEA_BATTLE -> SeaBattleEngine(this, playerCount, soundManager, botDifficulty)
            GameType.DOTS_AND_BOXES -> DotsAndBoxesEngine(this, playerCount, soundManager, botDifficulty)
            GameType.MEMORY -> MemoryEngine(this, playerCount, soundManager, botDifficulty)
        }

        engine.isTeamMode = isTeamMode
        engine.playerNames = playerNames
        engine.players.forEach { p ->
            p.score = sessionWins[p.id] ?: 0
        }
        engine.onRoundOverListener = { winner ->
            if (winner != null) {
                sessionWins[winner.id] = (sessionWins[winner.id] ?: 0) + 1
                winner.score = sessionWins[winner.id] ?: 1
            }
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
        val winnerText = if (engine.isTeamMode) {
            val team = engine.winningTeam ?: if (winner?.id == PlayerId.PLAYER_1 || winner?.id == PlayerId.PLAYER_2) 1 else 2
            val p1 = engine.getPlayerDisplayName(engine.players.getOrElse(0) { Player(PlayerId.PLAYER_1) })
            val p2 = engine.getPlayerDisplayName(engine.players.getOrElse(1) { Player(PlayerId.PLAYER_2) })
            val p3 = engine.getPlayerDisplayName(engine.players.getOrElse(2) { Player(PlayerId.PLAYER_3) })
            val p4 = engine.getPlayerDisplayName(engine.players.getOrElse(3) { Player(PlayerId.PLAYER_4) })
            if (team == 1) {
                "👑 TEAM 1 WINS!\n($p1 & $p2)"
            } else {
                "👑 TEAM 2 WINS!\n($p3 & $p4)"
            }
        } else if (winner != null) {
            "👑 ${engine.getPlayerDisplayName(winner).uppercase()} WINS!"
        } else {
            "DRAW MATCH!"
        }

        tvWinnerName.text = winnerText
        val winnerColor = if (engine.isTeamMode) {
            val team = engine.winningTeam ?: if (winner?.id == PlayerId.PLAYER_1 || winner?.id == PlayerId.PLAYER_2) 1 else 2
            if (team == 1) Color.parseColor("#FF3B30") else Color.parseColor("#007AFF")
        } else {
            winner?.id?.color ?: Color.parseColor("#FFD54F")
        }
        tvWinnerName.setTextColor(winnerColor)

        // For Tic Tac Toe, only show who wins without the score count list
        if (gameType == GameType.TIC_TAC_TOE || !engine.shouldShowPlayerScoresOnWinnerDialog()) {
            layoutWinnerScores.visibility = View.GONE
        } else {
            layoutWinnerScores.visibility = View.VISIBLE
            populateWinnerScores(engine, winner)
        }

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

    private fun populateWinnerScores(engine: BaseGameEngine, winner: Player?) {
        layoutWinnerScores.removeAllViews()
        val density = resources.displayMetrics.density

        if (engine.isTeamMode) {
            val winTeam = engine.winningTeam ?: if (winner?.id == PlayerId.PLAYER_1 || winner?.id == PlayerId.PLAYER_2) 1 else 2
            val p1 = engine.getPlayerDisplayName(engine.players.getOrElse(0) { Player(PlayerId.PLAYER_1) })
            val p2 = engine.getPlayerDisplayName(engine.players.getOrElse(1) { Player(PlayerId.PLAYER_2) })
            val p3 = engine.getPlayerDisplayName(engine.players.getOrElse(2) { Player(PlayerId.PLAYER_3) })
            val p4 = engine.getPlayerDisplayName(engine.players.getOrElse(3) { Player(PlayerId.PLAYER_4) })

            val teams = listOf(
                Triple(1, "TEAM 1: $p1 & $p2", Color.parseColor("#FF3B30")),
                Triple(2, "TEAM 2: $p3 & $p4", Color.parseColor("#007AFF"))
            ).sortedByDescending { it.first == winTeam }

            for (t in teams) {
                val isWinningTeam = (t.first == winTeam)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * density).toInt()
                    }
                    setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt())

                    val bgDrawable = GradientDrawable().apply {
                        cornerRadius = 14 * density
                        if (isWinningTeam) {
                            setColor(Color.parseColor("#2A1948"))
                            setStroke((1.5f * density).toInt(), Color.parseColor("#FFD54F"))
                        } else {
                            setColor(Color.parseColor("#1A1430"))
                            setStroke((1.0f * density).toInt(), Color.parseColor("#322558"))
                        }
                    }
                    background = bgDrawable
                }

                // Avatar
                val avatarView = TextView(this).apply {
                    val size = (28 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    gravity = Gravity.CENTER
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    text = if (t.first == 1) "🔴" else "🔵"
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(t.third)
                    }
                }
                row.addView(avatarView)

                // Team Name
                val nameView = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = (10 * density).toInt()
                        marginEnd = (8 * density).toInt()
                    }
                    textSize = 13.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isWinningTeam) Color.WHITE else Color.parseColor("#CDC4EB"))
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    text = t.second
                }
                row.addView(nameView)

                // Score / Status Pill
                val scorePill = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding((8 * density).toInt(), (3 * density).toInt(), (8 * density).toInt(), (3 * density).toInt())
                    val pillBg = GradientDrawable().apply {
                        cornerRadius = 8 * density
                        if (isWinningTeam) {
                            setColor(Color.parseColor("#38235C"))
                            setStroke((1.0f * density).toInt(), Color.parseColor("#FFD54F"))
                        } else {
                            setColor(Color.parseColor("#1C1534"))
                            setStroke((0.8f * density).toInt(), Color.parseColor("#3A2A60"))
                        }
                    }
                    background = pillBg
                    setTextColor(if (isWinningTeam) Color.parseColor("#FFD54F") else Color.parseColor("#9C8EB9"))
                    text = engine.getTeamScoreString(t.first)
                }
                row.addView(scorePill)

                layoutWinnerScores.addView(row)
            }
            return
        }

        val sortedPlayers = engine.players.sortedWith(
            compareByDescending<Player> { it == winner }
                .thenByDescending { engine.getPlayerScoreValue(it) }
        )

        for (player in sortedPlayers) {
            val isPlayerWinner = (player == winner)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (6 * density).toInt()
                }
                setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt())

                val bgDrawable = GradientDrawable().apply {
                    cornerRadius = 14 * density
                    if (isPlayerWinner) {
                        setColor(Color.parseColor("#2A1948"))
                        setStroke((1.5f * density).toInt(), Color.parseColor("#FFD54F"))
                    } else {
                        setColor(Color.parseColor("#1A1430"))
                        setStroke((1.0f * density).toInt(), Color.parseColor("#322558"))
                    }
                }
                background = bgDrawable
            }

            // 1. Avatar squircle
            val avatarView = TextView(this).apply {
                val size = (28 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                gravity = Gravity.CENTER
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                text = if (player.isBot) "🤖" else "P${player.id.id}"

                val avatarBg = when (player.id) {
                    PlayerId.PLAYER_1 -> R.drawable.bg_avatar_squircle_p1
                    PlayerId.PLAYER_2 -> R.drawable.bg_avatar_squircle_p2
                    PlayerId.PLAYER_3 -> R.drawable.bg_avatar_squircle_p3
                    PlayerId.PLAYER_4 -> R.drawable.bg_avatar_squircle_p4
                }
                setBackgroundResource(avatarBg)
            }
            row.addView(avatarView)

            // 2. Player Name
            val nameView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (10 * density).toInt()
                    marginEnd = (8 * density).toInt()
                }
                textSize = 13.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isPlayerWinner) Color.WHITE else Color.parseColor("#CDC4EB"))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                text = if (player.isBot) "Robi the Bot" else engine.getPlayerDisplayName(player)
            }
            row.addView(nameView)

            // 3. Score Pill
            val scorePill = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isPlayerWinner) Color.parseColor("#FFD54F") else Color.parseColor("#5CE1E6"))

                val pillBg = GradientDrawable().apply {
                    cornerRadius = 12 * density
                    setColor(if (isPlayerWinner) Color.parseColor("#3B2660") else Color.parseColor("#22183D"))
                    if (isPlayerWinner) {
                        setStroke((1f * density).toInt(), Color.parseColor("#FFD54F"))
                    }
                }
                background = pillBg

                val scoreStr = engine.getPlayerScoreString(player)
                text = if (isPlayerWinner) "$scoreStr 👑" else scoreStr
            }
            row.addView(scorePill)

            layoutWinnerScores.addView(row)
        }
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
