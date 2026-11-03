package com.aaritlabs.funtoza

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.TournamentState

class MainActivity : AppCompatActivity() {

    private lateinit var cardMode1P: FrameLayout
    private lateinit var cardMode2P: FrameLayout
    private lateinit var cardMode3P: FrameLayout
    private lateinit var cardMode4P: FrameLayout
    private lateinit var cardModeTeam: FrameLayout
    private lateinit var cardModeTournament: FrameLayout
    private lateinit var btnShareFriends: View
    private lateinit var btnSoundToggle: ImageButton
    private lateinit var btnSettings: ImageButton

    private lateinit var soundManager: SoundManager
    private var selectedBotDifficulty: Int = 1 // 0: Easy, 1: Medium, 2: Hard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        initViews()
        setupModeCards()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Update sound icon state based on SoundManager
        btnSoundToggle.alpha = if (soundManager.isSoundEnabled && !soundManager.isMuted) 1.0f else 0.4f
    }

    private fun hideSystemUI() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {}
    }

    private fun initViews() {
        cardMode1P = findViewById(R.id.cardMode1P)
        cardMode2P = findViewById(R.id.cardMode2P)
        cardMode3P = findViewById(R.id.cardMode3P)
        cardMode4P = findViewById(R.id.cardMode4P)
        cardModeTeam = findViewById(R.id.cardModeTeam)
        cardModeTournament = findViewById(R.id.cardModeTournament)
        btnShareFriends = findViewById(R.id.btnShareFriends)
        btnSoundToggle = findViewById(R.id.btnSoundToggle)
        btnSettings = findViewById(R.id.btnSettings)

        btnSoundToggle.setOnClickListener {
            soundManager.isMuted = !soundManager.isMuted
            btnSoundToggle.alpha = if (soundManager.isMuted) 0.4f else 1.0f
            if (!soundManager.isMuted) {
                soundManager.play(SoundManager.SoundType.TAP)
            }
        }

        btnSettings.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnShareFriends.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Funtoza: 1 2 3 4 Player Games")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Play Funtoza: 1, 2, 3, 4 Player Offline Party Games with me on the same screen! Download now!"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Funtoza with Friends"))
        }
    }

    private fun setupModeCards() {
        // 1 Player: Ask Name + Bot Difficulty -> Select Game
        cardMode1P.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showPlayerSetupDialog(playerCount = 1, isTournament = false, isTeam = false)
        }

        // 2 Players: Ask Names -> Select Game
        cardMode2P.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showPlayerSetupDialog(playerCount = 2, isTournament = false, isTeam = false)
        }

        // 3 Players: Ask Names -> Select Game
        cardMode3P.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showPlayerSetupDialog(playerCount = 3, isTournament = false, isTeam = false)
        }

        // 4 Players: Ask Names -> Select Game
        cardMode4P.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showPlayerSetupDialog(playerCount = 4, isTournament = false, isTeam = false)
        }

        // 2 vs 2 Team Play: Ask Team / Player Names -> Select Game
        cardModeTeam.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showPlayerSetupDialog(playerCount = 4, isTournament = false, isTeam = true)
        }

        // Cup Tournament: Ask Names -> Launch Cup
        cardModeTournament.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            showPlayerSetupDialog(playerCount = 2, isTournament = true, isTeam = false)
        }
    }

    /**
     * Prompts for player names, persists them across app sessions in SharedPreferences,
     * and seamlessly transitions to Game Selection (or Tournament).
     */
    private fun showPlayerSetupDialog(playerCount: Int, isTournament: Boolean, isTeam: Boolean) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_player_names)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val prefs = getSharedPreferences("funtoza_prefs", Context.MODE_PRIVATE)

        val btnBack = dialog.findViewById<ImageButton>(R.id.btnPlayerSetupBack)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvPlayerSetupTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvPlayerSetupSubtitle)

        val rowP1 = dialog.findViewById<LinearLayout>(R.id.rowP1)
        val rowP2 = dialog.findViewById<LinearLayout>(R.id.rowP2)
        val rowP3 = dialog.findViewById<LinearLayout>(R.id.rowP3)
        val rowP4 = dialog.findViewById<LinearLayout>(R.id.rowP4)

        val etP1 = dialog.findViewById<EditText>(R.id.etPlayer1Name)
        val etP2 = dialog.findViewById<EditText>(R.id.etPlayer2Name)
        val etP3 = dialog.findViewById<EditText>(R.id.etPlayer3Name)
        val etP4 = dialog.findViewById<EditText>(R.id.etPlayer4Name)

        val layoutBotSection = dialog.findViewById<LinearLayout>(R.id.layoutBotDifficultySection)
        val btnEasy = dialog.findViewById<Button>(R.id.btnSetupDiffEasy)
        val btnMedium = dialog.findViewById<Button>(R.id.btnSetupDiffMedium)
        val btnHard = dialog.findViewById<Button>(R.id.btnSetupDiffHard)
        val btnContinue = dialog.findViewById<Button>(R.id.btnPlayerSetupContinue)

        // Title styling
        tvTitle.text = when {
            isTournament -> "CUP TOURNAMENT SETUP"
            isTeam -> "2vs2 TEAM SETUP"
            playerCount == 1 -> "1 PLAYER VS BOT"
            else -> "$playerCount PLAYERS SETUP"
        }

        tvSubtitle.text = if (playerCount == 1) "Enter your name & choose bot level" else "Enter player names for this match"

        // Visibility & defaults
        rowP1.visibility = View.VISIBLE
        rowP2.visibility = if (playerCount >= 2) View.VISIBLE else View.GONE
        rowP3.visibility = if (playerCount >= 3) View.VISIBLE else View.GONE
        rowP4.visibility = if (playerCount >= 4) View.VISIBLE else View.GONE

        etP1.setText(prefs.getString("player_1_name", "Player 1"))
        etP2.setText(prefs.getString("player_2_name", "Player 2"))
        etP3.setText(prefs.getString("player_3_name", "Player 3"))
        etP4.setText(prefs.getString("player_4_name", "Player 4"))

        // Bot Difficulty UI for 1 Player
        if (playerCount == 1) {
            layoutBotSection.visibility = View.VISIBLE
            fun updateDiffButtons(diff: Int) {
                selectedBotDifficulty = diff
                btnEasy.setBackgroundResource(if (diff == 0) R.drawable.bg_diff_active_green else android.R.color.transparent)
                btnEasy.setTextColor(if (diff == 0) Color.WHITE else Color.parseColor("#7A8B9E"))

                btnMedium.setBackgroundResource(if (diff == 1) R.drawable.bg_diff_active_yellow else android.R.color.transparent)
                btnMedium.setTextColor(if (diff == 1) Color.WHITE else Color.parseColor("#7A8B9E"))

                btnHard.setBackgroundResource(if (diff == 2) R.drawable.bg_diff_active_red else android.R.color.transparent)
                btnHard.setTextColor(if (diff == 2) Color.WHITE else Color.parseColor("#7A8B9E"))
            }
            updateDiffButtons(selectedBotDifficulty)

            btnEasy.setOnClickListener { soundManager.play(SoundManager.SoundType.TAP); updateDiffButtons(0) }
            btnMedium.setOnClickListener { soundManager.play(SoundManager.SoundType.TAP); updateDiffButtons(1) }
            btnHard.setOnClickListener { soundManager.play(SoundManager.SoundType.TAP); updateDiffButtons(2) }
        } else {
            layoutBotSection.visibility = View.GONE
        }

        btnBack.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            dialog.dismiss()
        }

        btnContinue.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)

            val p1Name = etP1.text.toString().trim().ifEmpty { "Player 1" }
            val p2Name = etP2.text.toString().trim().ifEmpty { "Player 2" }
            val p3Name = etP3.text.toString().trim().ifEmpty { "Player 3" }
            val p4Name = etP4.text.toString().trim().ifEmpty { "Player 4" }

            // Save player names persistently for while the game and future rounds
            prefs.edit().apply {
                putString("player_1_name", p1Name)
                putString("player_2_name", p2Name)
                putString("player_3_name", p3Name)
                putString("player_4_name", p4Name)
                putInt("bot_difficulty", selectedBotDifficulty)
                apply()
            }

            val namesList = when (playerCount) {
                1 -> arrayListOf(p1Name, "Bot")
                2 -> arrayListOf(p1Name, p2Name)
                3 -> arrayListOf(p1Name, p2Name, p3Name)
                else -> arrayListOf(p1Name, p2Name, p3Name, p4Name)
            }

            try {
                dialog.dismiss()
            } catch (_: Exception) {}

            if (isTournament) {
                val tourney = TournamentState(totalPlayers = playerCount)
                val intent = Intent(this, TournamentActivity::class.java).apply {
                    putExtra("TOURNAMENT_STATE", tourney)
                    putStringArrayListExtra("PLAYER_NAMES", namesList)
                }
                startActivity(intent)
            } else {
                // Launch Game Selection Screen!
                val intent = Intent(this, SelectGameActivity::class.java).apply {
                    putExtra("PLAYER_COUNT", playerCount)
                    putExtra("BOT_DIFFICULTY", selectedBotDifficulty)
                    putStringArrayListExtra("PLAYER_NAMES", namesList)
                }
                startActivity(intent)
            }
        }

        if (!isFinishing && !isDestroyed) {
            try {
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
