package com.aaritlabs.funtoza

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
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

    private val activeAnimators = mutableListOf<ValueAnimator>()
    private val starViews = mutableListOf<View>()
    private val headerPills = mutableListOf<View>()
    private var funtozaTitle: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        initViews()
        setupModeCards()
        setupAnimations()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        updateSoundButtonState()
    }

    private fun updateSoundButtonState() {
        if (soundManager.isMuted) {
            btnSoundToggle.setImageResource(R.drawable.ic_volume_off)
            btnSoundToggle.alpha = 0.55f
        } else {
            btnSoundToggle.setImageResource(R.drawable.ic_volume_up)
            btnSoundToggle.alpha = 1.0f
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

        findViewById<View>(R.id.starTopLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.starMidRight)?.let { starViews.add(it) }
        findViewById<View>(R.id.starBottomLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.starBottomRight)?.let { starViews.add(it) }
        findViewById<View>(R.id.starMidLeft)?.let { starViews.add(it) }
        findViewById<View>(R.id.starCenterRight)?.let { starViews.add(it) }
        findViewById<View>(R.id.starTopRight)?.let { starViews.add(it) }

        findViewById<View>(R.id.mainPill1)?.let { headerPills.add(it) }
        findViewById<View>(R.id.mainPill2)?.let { headerPills.add(it) }
        findViewById<View>(R.id.mainPill3)?.let { headerPills.add(it) }
        findViewById<View>(R.id.mainPill4)?.let { headerPills.add(it) }

        funtozaTitle = findViewById(R.id.tvFuntozaTitle)

        updateSoundButtonState()

        btnSoundToggle.setOnClickListener {
            soundManager.isMuted = !soundManager.isMuted
            updateSoundButtonState()
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
            btnShareFriends.animate()
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(90)
                .withEndAction {
                    btnShareFriends.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start()

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
                .start()
        }
    }

    private fun setupModeCards() {
        addCardSpringEffect(cardMode1P) {
            showPlayerSetupDialog(playerCount = 1, isTournament = false, isTeam = false)
        }

        addCardSpringEffect(cardMode2P) {
            showPlayerSetupDialog(playerCount = 2, isTournament = false, isTeam = false)
        }

        addCardSpringEffect(cardMode3P) {
            showPlayerSetupDialog(playerCount = 3, isTournament = false, isTeam = false)
        }

        addCardSpringEffect(cardMode4P) {
            showPlayerSetupDialog(playerCount = 4, isTournament = false, isTeam = false)
        }

        addCardSpringEffect(cardModeTeam) {
            showPlayerSetupDialog(playerCount = 4, isTournament = false, isTeam = true)
        }

        addCardSpringEffect(cardModeTournament) {
            showPartyGamesPlayerChoiceDialog()
        }
    }

    private fun addCardSpringEffect(card: View, action: () -> Unit) {
        card.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            card.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(80)
                .withEndAction {
                    card.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(130)
                        .setInterpolator(OvershootInterpolator(2.0f))
                        .withEndAction { action() }
                        .start()
                }
                .start()
        }
    }

    private fun setupAnimations() {
        startSparkleStarsAnimation()
        startHeaderPillsGlowAnimation()
        startFuntozaTitleAnimation()
        startShareButtonPulse()
        runEntranceAnimations()
    }

    /**
     * Sparkle stars twinkle: smooth fade in/out + subtle rotation and scale pulse.
     */
    private fun startSparkleStarsAnimation() {
        val starConfigs = listOf(
            Triple(1600L, 0L, 25f),
            Triple(2000L, 600L, -35f),
            Triple(1800L, 1100L, 40f),
            Triple(1700L, 400L, -25f)
        )

        starViews.forEachIndexed { index, star ->
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
            activeAnimators.add(animator)
        }
    }

    /**
     * 4-line color pills glow wave animation from left to right.
     */
    private fun startHeaderPillsGlowAnimation() {
        headerPills.forEachIndexed { index, pill ->
            val animator = ValueAnimator.ofFloat(0.45f, 1.0f).apply {
                duration = 550
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                startDelay = (index * 130).toLong()
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    val fraction = va.animatedValue as Float
                    pill.alpha = fraction
                    val scale = 0.95f + (0.20f * fraction)
                    pill.scaleX = scale
                    pill.scaleY = scale
                }
                start()
            }
            activeAnimators.add(animator)
        }
    }

    /**
     * Gentle cosmic breathing pulse on the FUNTOZA brand logo.
     */
    private fun startFuntozaTitleAnimation() {
        funtozaTitle?.let { title ->
            val animator = ValueAnimator.ofFloat(1.0f, 1.035f).apply {
                duration = 1900
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { va ->
                    val scale = va.animatedValue as Float
                    title.scaleX = scale
                    title.scaleY = scale
                }
                start()
            }
            activeAnimators.add(animator)
        }
    }

    /**
     * Subtle pulse on the Share With Friends button to invite player engagement.
     */
    private fun startShareButtonPulse() {
        val animator = ValueAnimator.ofFloat(1.0f, 1.025f).apply {
            duration = 1500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val scale = va.animatedValue as Float
                btnShareFriends.scaleX = scale
                btnShareFriends.scaleY = scale
            }
            start()
        }
        activeAnimators.add(animator)
    }

    /**
     * Smooth staggered slide-up and fade-in entrance for the mode cards.
     */
    private fun runEntranceAnimations() {
        val rows = listOf(
            listOf(cardMode1P, cardMode2P),
            listOf(cardMode3P, cardMode4P),
            listOf(cardModeTeam, cardModeTournament)
        )

        rows.forEachIndexed { rowIndex, rowCards ->
            rowCards.forEach { card ->
                card.alpha = 0f
                card.translationY = 35f
                card.animate()
                    .alpha(1.0f)
                    .translationY(0f)
                    .setDuration(500)
                    .setStartDelay((100 + rowIndex * 70).toLong())
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
    }

    /**
     * Shows a modal to select player count for Party Games (2, 3, or 4 players),
     * followed by player name setup.
     */
    private fun showPartyGamesPlayerChoiceDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_select_party_players)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnBack = dialog.findViewById<ImageButton>(R.id.btnPartySelectBack)
        val btn2P = dialog.findViewById<View>(R.id.btnParty2P)
        val btn3P = dialog.findViewById<View>(R.id.btnParty3P)
        val btn4P = dialog.findViewById<View>(R.id.btnParty4P)

        btnBack?.setOnClickListener {
            dialog.dismiss()
        }

        btn2P?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            dialog.dismiss()
            showPlayerSetupDialog(playerCount = 2, isTournament = true, isTeam = false)
        }

        btn3P?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            dialog.dismiss()
            showPlayerSetupDialog(playerCount = 3, isTournament = true, isTeam = false)
        }

        btn4P?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            dialog.dismiss()
            showPlayerSetupDialog(playerCount = 4, isTournament = true, isTeam = false)
        }

        if (!isFinishing && !isDestroyed) {
            try {
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

        val etP1 = dialog.findViewById<EditText>(R.id.etPlayer1Name)
        val etP2 = dialog.findViewById<EditText>(R.id.etPlayer2Name)
        val etP3 = dialog.findViewById<EditText>(R.id.etPlayer3Name)
        val etP4 = dialog.findViewById<EditText>(R.id.etPlayer4Name)

        val rowP1 = dialog.findViewById<View>(R.id.rowP1)
        val rowP2 = dialog.findViewById<View>(R.id.rowP2)
        val rowP3 = dialog.findViewById<View>(R.id.rowP3)
        val rowP4 = dialog.findViewById<View>(R.id.rowP4)

        val btnEditP1 = dialog.findViewById<View>(R.id.btnEditP1)
        val btnEditP2 = dialog.findViewById<View>(R.id.btnEditP2)
        val btnEditP3 = dialog.findViewById<View>(R.id.btnEditP3)
        val btnEditP4 = dialog.findViewById<View>(R.id.btnEditP4)

        // Helper to focus EditText, place cursor at end and show software keyboard
        val startEditing = { et: EditText? ->
            et?.let {
                it.isFocusableInTouchMode = true
                it.requestFocus()
                it.setSelection(it.text.length)
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        btnEditP1?.setOnClickListener { startEditing(etP1) }
        btnEditP2?.setOnClickListener { startEditing(etP2) }
        btnEditP3?.setOnClickListener { startEditing(etP3) }
        btnEditP4?.setOnClickListener { startEditing(etP4) }

        rowP1?.setOnClickListener { startEditing(etP1) }
        rowP2?.setOnClickListener { startEditing(etP2) }
        rowP3?.setOnClickListener { startEditing(etP3) }
        rowP4?.setOnClickListener { startEditing(etP4) }

        val layoutTeam1Header = dialog.findViewById<View>(R.id.layoutTeam1Header)
        val layoutTeam2Header = dialog.findViewById<View>(R.id.layoutTeam2Header)
        val tvLabelP1 = dialog.findViewById<TextView>(R.id.tvLabelP1)
        val tvLabelP2 = dialog.findViewById<TextView>(R.id.tvLabelP2)
        val tvLabelP3 = dialog.findViewById<TextView>(R.id.tvLabelP3)
        val tvLabelP4 = dialog.findViewById<TextView>(R.id.tvLabelP4)

        val layoutBotDifficulty = dialog.findViewById<View>(R.id.layoutBotDifficultySection)
        val btnDiffEasy = dialog.findViewById<Button>(R.id.btnSetupDiffEasy)
        val btnDiffMedium = dialog.findViewById<Button>(R.id.btnSetupDiffMedium)
        val btnDiffHard = dialog.findViewById<Button>(R.id.btnSetupDiffHard)
        val tvDifficultyDesc = dialog.findViewById<TextView>(R.id.tvDifficultyDesc)
        val btnMatchFormat = dialog.findViewById<View>(R.id.btnMatchFormat)
        val tvFormatText = dialog.findViewById<TextView>(R.id.tvFormatText)
        val tvFormatBadge = dialog.findViewById<TextView>(R.id.tvFormatBadge)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvPlayerSetupTitle)
        val tvSubtitle = dialog.findViewById<TextView>(R.id.tvPlayerSetupSubtitle)
        val btnStart = dialog.findViewById<Button>(R.id.btnPlayerSetupContinue)
        val btnBack = dialog.findViewById<ImageButton>(R.id.btnPlayerSetupBack)

        btnBack?.setOnClickListener {
            dialog.dismiss()
        }

        // Fresh input fields every time so user can enter new names without old names pre-filling
        etP1?.setText("")
        etP2?.setText("")
        etP3?.setText("")
        etP4?.setText("")

        selectedBotDifficulty = prefs.getInt("bot_difficulty", 1)

        val updateDiffButtons = {
            btnDiffEasy?.setBackgroundResource(if (selectedBotDifficulty == 0) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnDiffEasy?.setTextColor(if (selectedBotDifficulty == 0) Color.WHITE else Color.parseColor("#7A8B9E"))

            btnDiffMedium?.setBackgroundResource(if (selectedBotDifficulty == 1) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnDiffMedium?.setTextColor(if (selectedBotDifficulty == 1) Color.WHITE else Color.parseColor("#7A8B9E"))

            btnDiffHard?.setBackgroundResource(if (selectedBotDifficulty == 2) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnDiffHard?.setTextColor(if (selectedBotDifficulty == 2) Color.WHITE else Color.parseColor("#7A8B9E"))

            tvDifficultyDesc?.text = when (selectedBotDifficulty) {
                0 -> "Easy: Relaxed pace & friendly mistakes"
                1 -> "Medium: Balanced reaction & clever tactics"
                else -> "Hard: Fast reflex & ruthless precision"
            }
        }
        updateDiffButtons()

        btnDiffEasy?.setOnClickListener {
            selectedBotDifficulty = 0
            updateDiffButtons()
            soundManager.play(SoundManager.SoundType.TAP)
        }
        btnDiffMedium?.setOnClickListener {
            selectedBotDifficulty = 1
            updateDiffButtons()
            soundManager.play(SoundManager.SoundType.TAP)
        }
        btnDiffHard?.setOnClickListener {
            selectedBotDifficulty = 2
            updateDiffButtons()
            soundManager.play(SoundManager.SoundType.TAP)
        }

        var matchRounds = prefs.getInt("match_rounds", 3)
        val updateFormatView = {
            when (matchRounds) {
                1 -> {
                    tvFormatText?.text = "Single Match"
                    tvFormatBadge?.text = "⚡ 1R"
                }
                5 -> {
                    tvFormatText?.text = "Best of 5"
                    tvFormatBadge?.text = "⚡ 5R"
                }
                else -> {
                    tvFormatText?.text = "Best of 3"
                    tvFormatBadge?.text = "⚡ 3R"
                }
            }
        }
        updateFormatView()

        btnMatchFormat?.setOnClickListener {
            matchRounds = when (matchRounds) {
                1 -> 3
                3 -> 5
                else -> 1
            }
            prefs.edit().putInt("match_rounds", matchRounds).apply()
            updateFormatView()
            soundManager.play(SoundManager.SoundType.TAP)
        }

        if (isTeam) {
            tvTitle?.text = "2 VS 2 TEAM PLAY"
            tvSubtitle?.text = "Team Red vs Team Blue"
            layoutTeam1Header?.visibility = View.VISIBLE
            layoutTeam2Header?.visibility = View.VISIBLE
            rowP1?.visibility = View.VISIBLE
            rowP2?.visibility = View.VISIBLE
            rowP3?.visibility = View.VISIBLE
            rowP4?.visibility = View.VISIBLE
            tvLabelP1?.text = "TEAM 1 (RED) • PLAYER 1"
            tvLabelP2?.text = "TEAM 1 (RED) • PLAYER 2"
            tvLabelP3?.text = "TEAM 2 (BLUE) • PLAYER 3"
            tvLabelP4?.text = "TEAM 2 (BLUE) • PLAYER 4"
            layoutBotDifficulty?.visibility = View.GONE
            btnStart?.text = "START TEAM MATCH ➔"
        } else if (isTournament) {
            tvTitle?.text = "PARTY GAMES (${playerCount}P)"
            tvSubtitle?.text = "Enter party player names"
            layoutTeam1Header?.visibility = View.GONE
            layoutTeam2Header?.visibility = View.GONE
            layoutBotDifficulty?.visibility = View.GONE
            btnStart?.text = "START PARTY GAMES ➔"
            rowP1?.visibility = View.VISIBLE
            rowP2?.visibility = if (playerCount >= 2) View.VISIBLE else View.GONE
            rowP3?.visibility = if (playerCount >= 3) View.VISIBLE else View.GONE
            rowP4?.visibility = if (playerCount >= 4) View.VISIBLE else View.GONE
            tvLabelP1?.text = "PLAYER NAME"
            tvLabelP2?.text = "PLAYER NAME"
            tvLabelP3?.text = "PLAYER NAME"
            tvLabelP4?.text = "PLAYER NAME"
        } else {
            layoutTeam1Header?.visibility = View.GONE
            layoutTeam2Header?.visibility = View.GONE
            tvLabelP1?.text = "PLAYER NAME"
            tvLabelP2?.text = "PLAYER NAME"
            tvLabelP3?.text = "PLAYER NAME"
            tvLabelP4?.text = "PLAYER NAME"
            btnStart?.text = "SELECT MINIGAME ➔"

            when (playerCount) {
                1 -> {
                    tvTitle?.text = "1 PLAYER SETUP"
                    tvSubtitle?.text = "Enter names & configure match"
                    rowP1?.visibility = View.VISIBLE
                    rowP2?.visibility = View.GONE
                    rowP3?.visibility = View.GONE
                    rowP4?.visibility = View.GONE
                    layoutBotDifficulty?.visibility = View.VISIBLE
                }
                2 -> {
                    tvTitle?.text = "2 PLAYERS SETUP"
                    tvSubtitle?.text = "Enter names & configure match"
                    rowP1?.visibility = View.VISIBLE
                    rowP2?.visibility = View.VISIBLE
                    rowP3?.visibility = View.GONE
                    rowP4?.visibility = View.GONE
                    layoutBotDifficulty?.visibility = View.GONE
                }
                3 -> {
                    tvTitle?.text = "3 PLAYERS SETUP"
                    tvSubtitle?.text = "Enter names & configure match"
                    rowP1?.visibility = View.VISIBLE
                    rowP2?.visibility = View.VISIBLE
                    rowP3?.visibility = View.VISIBLE
                    rowP4?.visibility = View.GONE
                    layoutBotDifficulty?.visibility = View.GONE
                }
                4 -> {
                    tvTitle?.text = "4 PLAYERS SETUP"
                    tvSubtitle?.text = "Enter names & configure match"
                    rowP1?.visibility = View.VISIBLE
                    rowP2?.visibility = View.VISIBLE
                    rowP3?.visibility = View.VISIBLE
                    rowP4?.visibility = View.VISIBLE
                    layoutBotDifficulty?.visibility = View.GONE
                }
            }
        }

        btnStart?.setOnClickListener {
            soundManager.play(SoundManager.SoundType.WHISTLE)

            val p1Name = etP1?.text?.toString()?.trim()?.ifEmpty { "Player 1" } ?: "Player 1"
            val p2Name = etP2?.text?.toString()?.trim()?.ifEmpty { "Player 2" } ?: "Player 2"
            val p3Name = etP3?.text?.toString()?.trim()?.ifEmpty { "Player 3" } ?: "Player 3"
            val p4Name = etP4?.text?.toString()?.trim()?.ifEmpty { "Player 4" } ?: "Player 4"

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
