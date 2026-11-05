package com.aaritlabs.funtoza

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
import com.aaritlabs.funtoza.model.TournamentState
import com.aaritlabs.funtoza.monetization.AdMobManager
import com.aaritlabs.funtoza.monetization.BillingManager

class SelectPartyGamesActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager
    private var playerCount: Int = 2
    private var playerNames: ArrayList<String> = arrayListOf()

    private val selectedGames = mutableListOf<GameType>()
    private var availableGames = listOf<GameType>()

    private lateinit var tvCounterBadge: TextView
    private lateinit var btnQuickRandom: Button
    private lateinit var btnClearSelection: Button
    private lateinit var layoutRoundSlotsBar: LinearLayout
    private lateinit var layoutGameItemsContainer: LinearLayout
    private lateinit var btnStartCustomTournament: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_party_games)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)

        playerCount = intent.getIntExtra("PLAYER_COUNT", 2)
        playerNames = intent.getStringArrayListExtra("PLAYER_NAMES") ?: arrayListOf()

        availableGames = GameType.getGamesForPlayerCount(playerCount)

        initViews()
        renderSlotsBar()
        renderGameCards()
        updateUIState()
    }

    private fun initViews() {
        val btnBack = findViewById<ImageButton>(R.id.btnPartySelectGamesBack)
        tvCounterBadge = findViewById(R.id.tvCounterBadge)
        btnQuickRandom = findViewById(R.id.btnQuickRandom)
        btnClearSelection = findViewById(R.id.btnClearSelection)
        layoutRoundSlotsBar = findViewById(R.id.layoutRoundSlotsBar)
        layoutGameItemsContainer = findViewById(R.id.layoutGameItemsContainer)
        btnStartCustomTournament = findViewById(R.id.btnStartCustomTournament)

        btnBack.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            finish()
        }

        // Quick Random (5): guaranteed unique, NEVER picks the same game in random selection
        btnQuickRandom.setOnClickListener {
            soundManager.playStart()
            pickRandom5DistinctGames()
        }

        btnClearSelection.setOnClickListener {
            soundManager.playClick()
            selectedGames.clear()
            renderSlotsBar()
            renderGameCards()
            updateUIState()
        }

        btnStartCustomTournament.setOnClickListener {
            if (selectedGames.size == 5) {
                soundManager.playStart()
                val tourney = TournamentState(
                    totalPlayers = playerCount,
                    totalRounds = 5,
                    currentRound = 1,
                    gamePlaylist = ArrayList(selectedGames)
                )

                val intent = Intent(this, TournamentActivity::class.java).apply {
                    putExtra("TOURNAMENT_STATE", tourney)
                    putStringArrayListExtra("PLAYER_NAMES", playerNames)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    /**
     * Randomly selects 5 distinct games with 0 duplicate games.
     */
    private fun pickRandom5DistinctGames() {
        selectedGames.clear()
        val unlocked = availableGames.filter { BillingManager.isGameUnlocked(this, it) }
        val pool = if (unlocked.size >= 5) unlocked else availableGames
        val distinct5 = pool.shuffled().distinct().take(5)
        selectedGames.addAll(distinct5)
        renderSlotsBar()
        renderGameCards()
        updateUIState()
        Toast.makeText(this, "🎲 5 unique minigames selected!", Toast.LENGTH_SHORT).show()
    }

    private fun tryAddGame(game: GameType) {
        if (BillingManager.isGameUnlocked(this, game)) {
            if (selectedGames.size < 5) {
                soundManager.playClick()
                selectedGames.add(game)
                renderSlotsBar()
                renderGameCards()
                updateUIState()
            } else {
                soundManager.play(SoundManager.SoundType.BOUNCE)
                Toast.makeText(this, "All 5 rounds selected! Tap ✕ on a slot or − to remove.", Toast.LENGTH_SHORT).show()
            }
        } else {
            showUnlockDialog(game)
        }
    }

    private fun showUnlockDialog(gameType: GameType) {
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

        // 1. Watch Video to Unlock
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
                AdMobManager.getInstance(this@SelectPartyGamesActivity).showRewarded(
                    this@SelectPartyGamesActivity,
                    onRewardEarned = {
                        BillingManager.unlockGameFor24Hours(this@SelectPartyGamesActivity, gameType)
                        Toast.makeText(this@SelectPartyGamesActivity, "🎉 ${gameType.title} unlocked for 24 hours!", Toast.LENGTH_LONG).show()
                        tryAddGame(gameType)
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
                startActivity(Intent(this@SelectPartyGamesActivity, SettingsActivity::class.java))
            }
        }
        container.addView(btnGetVip)

        // 3. Cancel
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

    /**
     * Renders the 5 Round Slots (R1 to R5) across the top.
     * Displays emoji and a quick [✕] button to remove any specific round.
     */
    private fun renderSlotsBar() {
        val density = resources.displayMetrics.density
        layoutRoundSlotsBar.removeAllViews()

        for (i in 0 until 5) {
            val isFilled = i < selectedGames.size
            val game = if (isFilled) selectedGames[i] else null

            val slotLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                    val margin = (3 * density).toInt()
                    setMargins(margin, 0, margin, 0)
                }
                setPadding((2 * density).toInt(), (3 * density).toInt(), (2 * density).toInt(), (3 * density).toInt())

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f * density
                    if (isFilled) {
                        setColor(Color.parseColor("#2A1438"))
                        setStroke((1.6f * density).toInt(), Color.parseColor("#FF2A85"))
                    } else {
                        setColor(Color.parseColor("#120E22"))
                        setStroke((1f * density).toInt(), Color.parseColor("#251E44"))
                    }
                }
            }

            if (isFilled && game != null) {
                // Header row with Round label (e.g. R1) and [✕] remove button
                val headerRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                val tvRound = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                    text = "R${i + 1}"
                    textSize = 9.5f
                    setTextColor(Color.parseColor("#FF80BF"))
                    gravity = Gravity.START
                    paint.isFakeBoldText = true
                    setPadding((3 * density).toInt(), 0, 0, 0)
                }
                headerRow.addView(tvRound)

                val tvRemove = TextView(this).apply {
                    text = "✕"
                    textSize = 10.5f
                    setTextColor(Color.parseColor("#FF4D6D"))
                    gravity = Gravity.END
                    paint.isFakeBoldText = true
                    setPadding(0, 0, (3 * density).toInt(), 0)
                }
                headerRow.addView(tvRemove)
                slotLayout.addView(headerRow)

                val tvEmoji = TextView(this).apply {
                    text = game.iconEmoji
                    textSize = 15f
                    gravity = Gravity.CENTER
                }
                slotLayout.addView(tvEmoji)

                // Clicking this round slot removes it
                slotLayout.setOnClickListener {
                    soundManager.play(SoundManager.SoundType.TAP)
                    if (i < selectedGames.size) {
                        selectedGames.removeAt(i)
                        renderSlotsBar()
                        renderGameCards()
                        updateUIState()
                    }
                }
            } else {
                val tvRound = TextView(this).apply {
                    text = "R${i + 1}"
                    textSize = 9.5f
                    setTextColor(Color.parseColor("#5A4E7A"))
                    gravity = Gravity.CENTER
                    paint.isFakeBoldText = true
                }
                slotLayout.addView(tvRound)

                val tvPlus = TextView(this).apply {
                    text = "+"
                    textSize = 13f
                    setTextColor(Color.parseColor("#443A62"))
                    gravity = Gravity.CENTER
                    paint.isFakeBoldText = true
                }
                slotLayout.addView(tvPlus)
            }

            layoutRoundSlotsBar.addView(slotLayout)
        }
    }

    /**
     * Renders minigames and allows:
     * 1. Random 5 selection card (always 5 unique games, no duplicates).
     * 2. Choosing the SAME game multiple times in manual selection.
     */
    private fun renderGameCards() {
        val density = resources.displayMetrics.density
        layoutGameItemsContainer.removeAllViews()

        // 1. Featured Top Card: RANDOM 5 SELECTION
        val randomCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (12 * density).toInt())
            }
            setPadding((14 * density).toInt(), (13 * density).toInt(), (14 * density).toInt(), (13 * density).toInt())

            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(Color.parseColor("#251D14"))
                setStroke((1.8f * density).toInt(), Color.parseColor("#FFD700"))
            }
            isClickable = true
            isFocusable = true
        }

        val tvRandomEmoji = TextView(this).apply {
            val badgeSize = (44 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
            gravity = Gravity.CENTER
            text = "🎲"
            textSize = 23f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3B2E15"))
                setStroke((1f * density).toInt(), Color.parseColor("#FFD700"))
            }
        }
        randomCard.addView(tvRandomEmoji)

        val randomTextLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins((12 * density).toInt(), 0, (10 * density).toInt(), 0)
            }
        }

        val tvRandomTitle = TextView(this).apply {
            text = "RANDOM 5 SELECTION ⚡"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 15f
            paint.isFakeBoldText = true
        }
        randomTextLayout.addView(tvRandomTitle)

        val tvRandomDesc = TextView(this).apply {
            text = "Auto-pick 5 distinct games with 0 duplicate minigames"
            setTextColor(Color.parseColor("#E0C58A"))
            textSize = 11.5f
            setPadding(0, (2 * density).toInt(), 0, 0)
        }
        randomTextLayout.addView(tvRandomDesc)
        randomCard.addView(randomTextLayout)

        val tvRollBadge = TextView(this).apply {
            text = "ROLL 🎲"
            textSize = 11.5f
            setTextColor(Color.BLACK)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(Color.parseColor("#FFD700"))
            }
        }
        randomCard.addView(tvRollBadge)

        randomCard.setOnClickListener {
            soundManager.playStart()
            pickRandom5DistinctGames()
        }
        layoutGameItemsContainer.addView(randomCard)

        // 2. Render each Available Minigame (Allows selecting the same game multiple times!)
        availableGames.forEach { game ->
            val count = selectedGames.count { it == game }
            val isSelected = count > 0
            val roundLabels = selectedGames.mapIndexedNotNull { idx, g ->
                if (g == game) "R${idx + 1}" else null
            }

            val cardLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, (10 * density).toInt())
                }
                setPadding((14 * density).toInt(), (13 * density).toInt(), (14 * density).toInt(), (13 * density).toInt())

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f * density
                    if (isSelected) {
                        setColor(Color.parseColor("#261536"))
                        setStroke((2f * density).toInt(), Color.parseColor("#FF2A85"))
                    } else {
                        setColor(Color.parseColor("#15112B"))
                        setStroke((1f * density).toInt(), Color.parseColor("#29224A"))
                    }
                }
                isClickable = true
                isFocusable = true
            }

            // Game Emoji Icon Badge
            val tvEmoji = TextView(this).apply {
                val badgeSize = (46 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize)
                gravity = Gravity.CENTER
                text = game.iconEmoji
                textSize = 24f

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1E1A3C"))
                    setStroke((1f * density).toInt(), Color.parseColor("#342D62"))
                }
            }
            cardLayout.addView(tvEmoji)

            // Game Title & Description Column
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    setMargins((12 * density).toInt(), 0, (10 * density).toInt(), 0)
                }
            }

            val tvTitle = TextView(this).apply {
                text = game.title.uppercase()
                setTextColor(if (isSelected) Color.parseColor("#FF2A85") else Color.WHITE)
                textSize = 15f
                paint.isFakeBoldText = true
            }
            textLayout.addView(tvTitle)

            val descText = if (isSelected) {
                "${game.description}\n⭐ Picked in: ${roundLabels.joinToString(", ")}"
            } else {
                game.description
            }

            val tvDesc = TextView(this).apply {
                text = descText
                setTextColor(if (isSelected) Color.parseColor("#FFB3D9") else Color.parseColor("#8E99B4"))
                textSize = 11.5f
                maxLines = 3
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            textLayout.addView(tvDesc)
            cardLayout.addView(textLayout)

            // Controls on the right: [ - ] [ ×Count ] [ + ]
            val controlsLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            if (isSelected) {
                // Minus (-) button: removes the last occurrence of this game
                val btnMinus = TextView(this).apply {
                    val size = (32 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = (6 * density).toInt()
                    }
                    gravity = Gravity.CENTER
                    text = "−"
                    textSize = 17f
                    setTextColor(Color.WHITE)
                    paint.isFakeBoldText = true
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#38172D"))
                        setStroke((1.5f * density).toInt(), Color.parseColor("#FF4D6D"))
                    }
                    setOnClickListener {
                        soundManager.play(SoundManager.SoundType.TAP)
                        val lastIdx = selectedGames.lastIndexOf(game)
                        if (lastIdx != -1) {
                            selectedGames.removeAt(lastIdx)
                            renderSlotsBar()
                            renderGameCards()
                            updateUIState()
                        }
                    }
                }
                controlsLayout.addView(btnMinus)

                // Count Badge (e.g. ×1, ×2)
                val tvCountBadge = TextView(this).apply {
                    val width = (34 * density).toInt()
                    val height = (32 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(width, height).apply {
                        marginEnd = (6 * density).toInt()
                    }
                    gravity = Gravity.CENTER
                    text = "×$count"
                    textSize = 13f
                    setTextColor(Color.WHITE)
                    paint.isFakeBoldText = true
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 16f * density
                        setColor(Color.parseColor("#FF2A85"))
                    }
                }
                controlsLayout.addView(tvCountBadge)

                // Plus (+) button: adds this game AGAIN (as long as total < 5)
                val btnPlus = TextView(this).apply {
                    val size = (32 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    gravity = Gravity.CENTER
                    text = "+"
                    textSize = 17f
                    paint.isFakeBoldText = true
                    val canAddMore = selectedGames.size < 5
                    setTextColor(if (canAddMore) Color.WHITE else Color.parseColor("#5A4E88"))
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (canAddMore) Color.parseColor("#2D163D") else Color.parseColor("#1E1A3C"))
                        setStroke((1.5f * density).toInt(), if (canAddMore) Color.parseColor("#00E676") else Color.parseColor("#342D62"))
                    }
                    setOnClickListener {
                        tryAddGame(game)
                    }
                }
                controlsLayout.addView(btnPlus)

            } else {
                // Not selected yet: Single circular (+) button
                val btnAdd = TextView(this).apply {
                    val size = (34 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(size, size)
                    gravity = Gravity.CENTER
                    text = "+"
                    textSize = 18f
                    setTextColor(Color.parseColor("#7A6EA8"))
                    paint.isFakeBoldText = true
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor("#1E1A3C"))
                        setStroke((1.4f * density).toInt(), Color.parseColor("#383068"))
                    }
                    setOnClickListener {
                        tryAddGame(game)
                    }
                }
                controlsLayout.addView(btnAdd)
            }

            cardLayout.addView(controlsLayout)

            // Clicking the card body adds another instance of this game
            cardLayout.setOnClickListener {
                tryAddGame(game)
            }

            layoutGameItemsContainer.addView(cardLayout)
        }
    }

    private fun updateUIState() {
        val count = selectedGames.size
        tvCounterBadge.text = "SELECTED: $count/5"

        if (count == 5) {
            tvCounterBadge.setTextColor(Color.parseColor("#00E676"))
            btnStartCustomTournament.isEnabled = true
            btnStartCustomTournament.alpha = 1.0f
            btnStartCustomTournament.text = "START TOURNAMENT (5/5) ➔"
        } else {
            tvCounterBadge.setTextColor(Color.parseColor("#FF2A85"))
            btnStartCustomTournament.isEnabled = false
            btnStartCustomTournament.alpha = 0.45f
            val remaining = 5 - count
            btnStartCustomTournament.text = "SELECT $remaining MORE GAME${if (remaining == 1) "" else "S"}"
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
