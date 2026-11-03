package com.aaritlabs.funtoza

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.engine.*
import com.aaritlabs.funtoza.model.GameType
import com.aaritlabs.funtoza.model.Player
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
    private lateinit var tvWinnerName: TextView
    private lateinit var btnNextRound: Button
    private lateinit var btnWinnerExit: Button

    private lateinit var soundManager: SoundManager
    private var gameType: GameType = GameType.TANK_BATTLE
    private var playerCount: Int = 2
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
        tvWinnerName = findViewById(R.id.tvWinnerName)
        btnNextRound = findViewById(R.id.btnNextRound)
        btnWinnerExit = findViewById(R.id.btnWinnerExit)

        tvCurrentGameTitle.text = gameType.title.uppercase()

        btnPause.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            pauseOverlay.visibility = View.VISIBLE
            gameSurfaceView.pauseGame()
        }

        btnResume.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            pauseOverlay.visibility = View.GONE
            gameSurfaceView.resumeGame()
        }

        btnRestart.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            pauseOverlay.visibility = View.GONE
            winnerOverlay.visibility = View.GONE
            startGame()
            gameSurfaceView.resumeGame()
        }

        btnExitMenu.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            finish()
        }

        btnWinnerExit.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            finish()
        }

        btnNextRound.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
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

    private fun startGame() {
        val engine: BaseGameEngine = when (gameType) {
            GameType.TANK_BATTLE -> TankBattleEngine(this, playerCount, soundManager)
            GameType.FINGER_SOCCER -> SoccerEngine(this, playerCount, soundManager)
            GameType.NEON_SNAKE -> SnakeEngine(this, playerCount, soundManager)
            GameType.SUMO_BUMPER -> SumoEngine(this, playerCount, soundManager)
            GameType.TARGET_BLITZ -> KnifeHitEngine(this, playerCount, soundManager)
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
        winner?.let {
            tvWinnerName.setTextColor(it.id.color)
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
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (pauseOverlay.visibility != View.VISIBLE && winnerOverlay.visibility != View.VISIBLE) {
            gameSurfaceView.resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        gameSurfaceView.pauseGame()
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

