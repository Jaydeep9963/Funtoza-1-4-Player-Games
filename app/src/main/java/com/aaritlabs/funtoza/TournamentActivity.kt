package com.aaritlabs.funtoza

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.PlayerId
import com.aaritlabs.funtoza.model.TournamentState

class TournamentActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager
    private lateinit var tourneyState: TournamentState
    private var playerNames: ArrayList<String> = arrayListOf()

    private lateinit var tvTourneyTitle: TextView
    private lateinit var tvRoundIndicator: TextView
    private lateinit var podiumContainer: LinearLayout
    private lateinit var btnNextMatch: Button
    private lateinit var btnTourneyExit: Button

    private val gameLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val winnerIdInt = result.data?.getIntExtra("WINNER_PLAYER_ID", -1) ?: -1
            if (winnerIdInt != -1) {
                val winnerId = PlayerId.fromId(winnerIdInt)
                tourneyState.addPoints(winnerId, 1)
            }
            tourneyState.currentRound++
            updatePodiumUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tournament)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        @Suppress("DEPRECATION")
        val state = intent.getSerializableExtra("TOURNAMENT_STATE") as? TournamentState
        tourneyState = state ?: TournamentState(totalPlayers = 2)
        playerNames = intent.getStringArrayListExtra("PLAYER_NAMES") ?: arrayListOf()

        initViews()
        updatePodiumUI()
    }

    private fun initViews() {
        tvTourneyTitle = findViewById(R.id.tvTourneyTitle)
        tvRoundIndicator = findViewById(R.id.tvRoundIndicator)
        podiumContainer = findViewById(R.id.podiumContainer)
        btnNextMatch = findViewById(R.id.btnNextMatch)
        btnTourneyExit = findViewById(R.id.btnTourneyExit)

        btnTourneyExit.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            finish()
        }

        btnNextMatch.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            if (tourneyState.isGameOver()) {
                // Exit or Restart
                finish()
            } else {
                launchNextTournamentGame()
            }
        }
    }

    private fun launchNextTournamentGame() {
        val currentGame = tourneyState.getCurrentGame()
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("GAME_TYPE", currentGame.name)
            putExtra("PLAYER_COUNT", tourneyState.totalPlayers)
            putExtra("IS_TOURNAMENT_MODE", true)
            putStringArrayListExtra("PLAYER_NAMES", playerNames)
        }
        gameLauncher.launch(intent)
    }

    private fun updatePodiumUI() {
        if (tourneyState.isGameOver()) {
            val champion = tourneyState.getLeaderboard().firstOrNull()?.first
            val name = (if (champion != null) playerNames.getOrNull(champion.id - 1) ?: champion.displayName else "Nobody")
            tvTourneyTitle.text = "👑 $name IS THE CHAMPION!"
            champion?.let { tvTourneyTitle.setTextColor(it.color) }
            tvRoundIndicator.text = "CUP TOURNAMENT COMPLETED"
            btnNextMatch.text = "FINISH CUP"
            soundManager.play(SoundManager.SoundType.VICTORY)
        } else {
            val nextGame = tourneyState.getCurrentGame()
            tvTourneyTitle.text = "🏆 CUP TOURNAMENT"
            tvRoundIndicator.text = "Round ${tourneyState.currentRound} of ${tourneyState.totalRounds} • Next: ${nextGame.title}"
            btnNextMatch.text = "PLAY ${nextGame.title.uppercase()} ➔"
        }

        // Render podium pillars for each player
        podiumContainer.removeAllViews()
        val leaderboard = tourneyState.getLeaderboard()
        val maxScore = leaderboard.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

        leaderboard.forEachIndexed { rank, (playerId, score) ->
            val pillarLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins(16, 0, 16, 0)
                }
            }

            // Medal / Crown / Emoji
            val medalText = when (rank) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "⭐"
            }

            val tvMedal = TextView(this).apply {
                text = "$medalText\n${score} pts"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }
            pillarLayout.addView(tvMedal)

            // Dynamic height podium bar
            val baseBarHeight = 70
            val dynamicHeight = (baseBarHeight + (score.toFloat() / maxScore.toFloat()) * 180).toInt()

            val barView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dynamicHeight)
                setBackgroundColor(playerId.color)
            }
            pillarLayout.addView(barView)

            // Player Label
            val tvPlayerLabel = TextView(this).apply {
                text = playerNames.getOrNull(playerId.id - 1) ?: playerId.displayName
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(playerId.color)
                setPadding(0, 8, 0, 0)
            }
            pillarLayout.addView(tvPlayerLabel)

            podiumContainer.addView(pillarLayout)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
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
