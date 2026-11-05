package com.aaritlabs.funtoza

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.PlayerId
import com.aaritlabs.funtoza.model.TournamentHistoryManager
import com.aaritlabs.funtoza.model.TournamentState

class TournamentActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager
    private lateinit var tourneyState: TournamentState
    private var playerNames: ArrayList<String> = arrayListOf()
    private var hasSavedCompletion = false

    private lateinit var tvTourneyTitle: TextView
    private lateinit var tvRoundIndicator: TextView

    private lateinit var cardNextGamePreview: View
    private lateinit var tvNextGameBadge: TextView
    private lateinit var tvNextRoundNumber: TextView
    private lateinit var tvNextGameEmoji: TextView
    private lateinit var tvNextGameTitle: TextView
    private lateinit var tvNextGameDesc: TextView
    private lateinit var layoutRoundTimeline: LinearLayout

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

        cardNextGamePreview = findViewById(R.id.cardNextGamePreview)
        tvNextGameBadge = findViewById(R.id.tvNextGameBadge)
        tvNextRoundNumber = findViewById(R.id.tvNextRoundNumber)
        tvNextGameEmoji = findViewById(R.id.tvNextGameEmoji)
        tvNextGameTitle = findViewById(R.id.tvNextGameTitle)
        tvNextGameDesc = findViewById(R.id.tvNextGameDesc)
        layoutRoundTimeline = findViewById(R.id.layoutRoundTimeline)

        podiumContainer = findViewById(R.id.podiumContainer)
        btnNextMatch = findViewById(R.id.btnNextMatch)
        btnTourneyExit = findViewById(R.id.btnTourneyExit)

        btnTourneyExit.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showLeaveConfirmationDialog()
        }

        btnNextMatch.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            if (tourneyState.isGameOver()) {
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
        val density = resources.displayMetrics.density

        if (tourneyState.isGameOver()) {
            val champion = tourneyState.getLeaderboard().firstOrNull()?.first
            val champName = if (champion != null) playerNames.getOrNull(champion.id - 1) ?: champion.displayName else "Nobody"
            val champScore = tourneyState.scores[champion] ?: 0

            tvTourneyTitle.text = "👑 $champName WINS THE CUP!"
            champion?.let { tvTourneyTitle.setTextColor(it.color) }
            tvRoundIndicator.text = "TOURNAMENT COMPLETED"

            tvNextGameBadge.text = "🏆 CHAMPION"
            tvNextRoundNumber.text = "FINAL"
            tvNextGameEmoji.text = "👑"
            tvNextGameTitle.text = "$champName IS THE CHAMPION!"
            tvNextGameDesc.text = "Cup Tournament Winner with $champScore victories across 5 rounds!"

            btnNextMatch.text = "FINISH CUP 🏆"
            soundManager.play(SoundManager.SoundType.VICTORY)

            if (!hasSavedCompletion) {
                hasSavedCompletion = true
                TournamentHistoryManager.saveTournament(
                    this,
                    tourneyState,
                    playerNames,
                    isCompleted = true,
                    winnerName = champName
                )
            }
        } else {
            val nextGame = tourneyState.getCurrentGame()
            tvTourneyTitle.text = "🏆 CUP TOURNAMENT"
            tvRoundIndicator.text = "Round ${tourneyState.currentRound} of ${tourneyState.totalRounds}"

            tvNextGameBadge.text = "NEXT MATCH"
            tvNextRoundNumber.text = "ROUND ${tourneyState.currentRound}/${tourneyState.totalRounds}"
            tvNextGameEmoji.text = nextGame.iconEmoji
            tvNextGameTitle.text = nextGame.title.uppercase()
            tvNextGameDesc.text = nextGame.description

            btnNextMatch.text = "PLAY ROUND ${tourneyState.currentRound} ➔"
        }

        // 1. Populate Round Timeline Indicators
        layoutRoundTimeline.removeAllViews()
        for (r in 1..tourneyState.totalRounds) {
            val gameIndex = (r - 1) % tourneyState.gamePlaylist.size
            val game = tourneyState.gamePlaylist[gameIndex]

            val pipView = TextView(this).apply {
                val isCompleted = r < tourneyState.currentRound
                val isCurrent = r == tourneyState.currentRound

                text = if (isCompleted) "✓" else game.iconEmoji
                textSize = if (isCompleted) 13f else 16f
                gravity = Gravity.CENTER
                paint.isFakeBoldText = true

                val pipSize = (32 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(pipSize, pipSize).apply {
                    setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }

                val pipDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    when {
                        isCompleted -> {
                            setColor(Color.parseColor("#382D16"))
                            setStroke((1.5f * density).toInt(), Color.parseColor("#FFD700"))
                            setTextColor(Color.parseColor("#FFD700"))
                        }
                        isCurrent -> {
                            setColor(Color.parseColor("#3A1634"))
                            setStroke((2f * density).toInt(), Color.parseColor("#FF2A85"))
                            setTextColor(Color.WHITE)
                        }
                        else -> {
                            setColor(Color.parseColor("#151228"))
                            setStroke((1f * density).toInt(), Color.parseColor("#28224E"))
                            setTextColor(Color.parseColor("#80FFFFFF"))
                        }
                    }
                }
                background = pipDrawable
            }
            layoutRoundTimeline.addView(pipView)
        }

        // 2. Render 3D-Styled Glass Podium Pillars for Each Player
        podiumContainer.removeAllViews()
        val leaderboard = tourneyState.getLeaderboard()
        val maxScore = leaderboard.maxOfOrNull { it.second } ?: 0
        val isFirstRound = (tourneyState.currentRound == 1 && maxScore == 0)

        leaderboard.forEachIndexed { rank, (playerId, score) ->
            // Base podium height in DP
            val baseHeightDp = if (isFirstRound) 110f else when (rank) {
                0 -> 165f
                1 -> 125f
                2 -> 95f
                else -> 75f
            }
            val dynamicHeightPx = (baseHeightDp * density).toInt()

            val pillarLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    setMargins((8 * density).toInt(), 0, (8 * density).toInt(), 0)
                }
            }

            // Top Medal / Emoji & Score
            val medalIcon = if (isFirstRound) "⭐" else when (rank) {
                0 -> "🥇"
                1 -> "🥈"
                2 -> "🥉"
                else -> "🎖️"
            }

            val tvMedal = TextView(this).apply {
                text = "$medalIcon\n${score} pts"
                textSize = 13.5f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
                setPadding(0, 0, 0, (6 * density).toInt())
            }
            pillarLayout.addView(tvMedal)

            // 3D Glass Pillar Bar
            val barView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dynamicHeightPx)

                val topCorner = 14f * density
                val bgDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(topCorner, topCorner, topCorner, topCorner, 0f, 0f, 0f, 0f)
                    val c = playerId.color
                    setColor(Color.argb(150, Color.red(c), Color.green(c), Color.blue(c)))
                    setStroke((2f * density).toInt(), c)
                }
                background = bgDrawable
            }

            // Rank Number Inside Pillar
            val tvRank = TextView(this).apply {
                text = "#${rank + 1}"
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                paint.isFakeBoldText = true
            }
            barView.addView(tvRank)
            pillarLayout.addView(barView)

            // Player Label Below Pillar
            val tvPlayerLabel = TextView(this).apply {
                text = playerNames.getOrNull(playerId.id - 1) ?: playerId.displayName
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(playerId.color)
                paint.isFakeBoldText = true
                setPadding(0, (6 * density).toInt(), 0, 0)
            }
            pillarLayout.addView(tvPlayerLabel)

            podiumContainer.addView(pillarLayout)
        }
    }

    private fun showLeaveConfirmationDialog() {
        if (tourneyState.isGameOver()) {
            finish()
            return
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_save_tournament_confirmation)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvSaveMessage = dialog.findViewById<TextView>(R.id.tvSaveMessage)
        val btnSaveAndExit = dialog.findViewById<Button>(R.id.btnSaveAndExit)
        val btnDiscardAndExit = dialog.findViewById<Button>(R.id.btnDiscardAndExit)
        val btnCancelSave = dialog.findViewById<Button>(R.id.btnCancelSave)

        tvSaveMessage?.text = "Round ${tourneyState.currentRound} of ${tourneyState.totalRounds} in progress.\nSave now to resume or replay this cup anytime!"

        btnSaveAndExit?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            TournamentHistoryManager.saveTournament(
                this,
                tourneyState,
                playerNames,
                isCompleted = false
            )
            Toast.makeText(this, "Tournament progress saved! You can resume it anytime.", Toast.LENGTH_SHORT).show()
            try { dialog.dismiss() } catch (_: Exception) {}
            finish()
        }

        btnDiscardAndExit?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            try { dialog.dismiss() } catch (_: Exception) {}
            finish()
        }

        btnCancelSave?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            try { dialog.dismiss() } catch (_: Exception) {}
        }

        if (!isFinishing && !isDestroyed) {
            try { dialog.show() } catch (_: Exception) {}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showLeaveConfirmationDialog()
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
