package com.aaritlabs.funtoza

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.GameType
import com.aaritlabs.funtoza.monetization.AdMobManager
import com.aaritlabs.funtoza.monetization.BillingManager

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

        cardLudo.setOnClickListener { checkAndLaunchGame(GameType.LUDO) }
        cardSoccer.setOnClickListener { checkAndLaunchGame(GameType.FINGER_SOCCER) }
        cardSnake.setOnClickListener { checkAndLaunchGame(GameType.NEON_SNAKE) }
        cardSnakesAndLadders.setOnClickListener { checkAndLaunchGame(GameType.SNAKES_AND_LADDERS) }
        cardKnife.setOnClickListener { checkAndLaunchGame(GameType.TARGET_BLITZ) }
        cardTicTacToe.setOnClickListener { checkAndLaunchGame(GameType.TIC_TAC_TOE) }
        cardOops.setOnClickListener { checkAndLaunchGame(GameType.OOPS) }
        cardSeaBattle.setOnClickListener { checkAndLaunchGame(GameType.SEA_BATTLE) }
        cardDotsAndBoxes.setOnClickListener { checkAndLaunchGame(GameType.DOTS_AND_BOXES) }
        cardMemory.setOnClickListener { checkAndLaunchGame(GameType.MEMORY) }

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

    private fun checkAndLaunchGame(gameType: GameType) {
        soundManager.playClick()
        if (BillingManager.isGameUnlocked(this, gameType)) {
            launchGame(gameType)
        } else {
            showUnlockPremiumGameDialog(gameType)
        }
    }

    private fun showUnlockPremiumGameDialog(gameType: GameType) {
        val dialog = Dialog(this)
        val density = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * density).toInt(), (22 * density).toInt(), (22 * density).toInt(), (22 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.parseColor("#1C1536"))
                setStroke((1.5f * density).toInt(), Color.parseColor("#FFD700"))
            }
        }

        val tvIcon = TextView(this).apply {
            text = gameType.iconEmoji
            textSize = 36f
            gravity = Gravity.CENTER
        }
        container.addView(tvIcon)

        val tvTitle = TextView(this).apply {
            text = "UNLOCK ${gameType.title.uppercase()} 🔓"
            textSize = 16f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        container.addView(tvTitle)

        val tvDesc = TextView(this).apply {
            text = "Watch 1 short video to unlock ${gameType.title} FREE for 24 hours!\nOr get the VIP Pass to unlock all games permanently with NO ads."
            textSize = 12.5f
            setTextColor(Color.parseColor("#9EADC8"))
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (20 * density).toInt())
            setLineSpacing(2f * density, 1f)
        }
        container.addView(tvDesc)

        // 1. Watch Video to Unlock (Free for 24H)
        val btnWatchAd = Button(this).apply {
            text = "🎬 WATCH VIDEO TO UNLOCK (FREE)"
            textSize = 13.5f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            minHeight = 0
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(Color.parseColor("#FF2A85"))
            }
            setOnClickListener {
                soundManager.playClick()
                dialog.dismiss()
                AdMobManager.getInstance(this@SelectGameActivity).showRewarded(
                    this@SelectGameActivity,
                    onRewardEarned = {
                        BillingManager.unlockGameFor24Hours(this@SelectGameActivity, gameType)
                        Toast.makeText(this@SelectGameActivity, "🎉 ${gameType.title} unlocked for 24 hours!", Toast.LENGTH_LONG).show()
                        launchGame(gameType)
                    },
                    onDismiss = {
                        // Dismissed
                    }
                )
            }
        }
        container.addView(btnWatchAd)

        // 2. Get VIP Pass
        val btnGetVip = Button(this).apply {
            text = "👑 GET VIP PASS (ALL GAMES FOREVER)"
            textSize = 12f
            setTextColor(Color.parseColor("#FFD700"))
            paint.isFakeBoldText = true
            minHeight = 0
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                topMargin = (10 * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(Color.parseColor("#261D14"))
                setStroke((1f * density).toInt(), Color.parseColor("#FFD700"))
            }
            setOnClickListener {
                soundManager.playClick()
                dialog.dismiss()
                startActivity(Intent(this@SelectGameActivity, SettingsActivity::class.java))
            }
        }
        container.addView(btnGetVip)

        // 3. Cancel button
        val btnCancel = Button(this).apply {
            text = "MAYBE LATER"
            textSize = 12f
            setTextColor(Color.parseColor("#7A86A8"))
            minHeight = 0
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (38 * density).toInt()).apply {
                topMargin = (8 * density).toInt()
            }
            background = ColorDrawable(Color.TRANSPARENT)
            setOnClickListener {
                soundManager.playClick()
                dialog.dismiss()
            }
        }
        container.addView(btnCancel)

        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        if (!isFinishing && !isDestroyed) {
            try { dialog.show() } catch (_: Exception) {}
        }
    }

    private fun launchGame(gameType: GameType) {
        soundManager.playClick()
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
        val bannerContainer = findViewById<FrameLayout>(R.id.bannerContainerSelectGame)
        if (bannerContainer != null) {
            AdMobManager.getInstance(this).loadBanner(this, bannerContainer)
        }
        AdMobManager.getInstance(this).loadInterstitial()
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
