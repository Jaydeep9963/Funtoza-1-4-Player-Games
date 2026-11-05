package com.aaritlabs.funtoza

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager
import com.aaritlabs.funtoza.model.TournamentHistoryManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)
        val prefs = getSharedPreferences("funtoza_prefs", Context.MODE_PRIVATE)

        // Views
        val btnBack = findViewById<ImageButton>(R.id.btnSettingsBack)
        val switchSound = findViewById<SwitchCompat>(R.id.switchSound)
        val switchVibration = findViewById<SwitchCompat>(R.id.switchVibration)

        val btnDiffEasy = findViewById<Button>(R.id.btnSettingsDiffEasy)
        val btnDiffMedium = findViewById<Button>(R.id.btnSettingsDiffMedium)
        val btnDiffHard = findViewById<Button>(R.id.btnSettingsDiffHard)
        val tvDiffDesc = findViewById<TextView>(R.id.tvSettingsDiffDesc)

        val btnRounds1 = findViewById<Button>(R.id.btnSettingsRounds1)
        val btnRounds3 = findViewById<Button>(R.id.btnSettingsRounds3)
        val btnRounds5 = findViewById<Button>(R.id.btnSettingsRounds5)

        val btnViewHistory = findViewById<View>(R.id.btnSettingsViewHistory)
        val btnClearHistory = findViewById<View>(R.id.btnSettingsClearHistory)

        val btnShare = findViewById<Button>(R.id.btnSettingsShare)
        val btnFeedback = findViewById<Button>(R.id.btnSettingsFeedback)
        val btnResetDefaults = findViewById<Button>(R.id.btnSettingsResetDefaults)

        // 0. VIP Store & In-App Purchases Setup
        val billingManager = com.aaritlabs.funtoza.monetization.BillingManager.getInstance(this)

        val cardRemoveAds = findViewById<View>(R.id.cardRemoveAds)
        val tvRemoveAdsTitle = findViewById<TextView>(R.id.tvRemoveAdsTitle)
        val tvRemoveAdsDesc = findViewById<TextView>(R.id.tvRemoveAdsDesc)
        val tvRemoveAdsPrice = findViewById<TextView>(R.id.tvRemoveAdsPrice)

        val cardVipBundle = findViewById<View>(R.id.cardVipBundle)
        val tvVipBundleTitle = findViewById<TextView>(R.id.tvVipBundleTitle)
        val tvVipBundlePrice = findViewById<TextView>(R.id.tvVipBundlePrice)

        val cardTipCoffee = findViewById<View>(R.id.cardTipCoffee)
        val tvTipPrice = findViewById<TextView>(R.id.tvTipPrice)
        val btnRestorePurchases = findViewById<Button>(R.id.btnRestorePurchases)

        val updateVipStoreUI = {
            val isNoAds = com.aaritlabs.funtoza.monetization.BillingManager.isNoAdsActive(this)
            val isVipParty = com.aaritlabs.funtoza.monetization.BillingManager.isVipPartyActive(this)

            if (isNoAds) {
                tvRemoveAdsTitle?.text = "✓ VIP ACTIVE"
                tvRemoveAdsDesc?.text = "All Ads Permanently Removed"
                tvRemoveAdsPrice?.text = "OWNED"
                tvRemoveAdsPrice?.setTextColor(Color.parseColor("#00E676"))
                cardRemoveAds?.isClickable = false
            } else {
                tvRemoveAdsPrice?.text = billingManager.getFormattedPrice(com.aaritlabs.funtoza.monetization.BillingManager.SKU_REMOVE_ADS, "$1.99")
            }

            if (isVipParty) {
                tvVipBundleTitle?.text = "✓ VIP PARTY PACK ACTIVE"
                tvVipBundlePrice?.text = "OWNED"
                tvVipBundlePrice?.setTextColor(Color.parseColor("#00E676"))
                cardVipBundle?.isClickable = false
            } else {
                tvVipBundlePrice?.text = billingManager.getFormattedPrice(com.aaritlabs.funtoza.monetization.BillingManager.SKU_VIP_PARTY_BUNDLE, "$3.99")
            }

            tvTipPrice?.text = billingManager.getFormattedPrice(com.aaritlabs.funtoza.monetization.BillingManager.SKU_TIP_COFFEE, "$0.99")
        }
        updateVipStoreUI()

        cardRemoveAds?.setOnClickListener {
            soundManager.playClick()
            if (!com.aaritlabs.funtoza.monetization.BillingManager.isNoAdsActive(this)) {
                billingManager.launchPurchaseFlow(this, com.aaritlabs.funtoza.monetization.BillingManager.SKU_REMOVE_ADS)
            }
        }

        // Long press for testers to toggle/reset VIP state and test ads freely
        cardRemoveAds?.setOnLongClickListener {
            val prefs = getSharedPreferences(com.aaritlabs.funtoza.monetization.BillingManager.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Toast.makeText(this, "🔧 Developer Test: VIP status reset! Ads enabled.", Toast.LENGTH_SHORT).show()
            updateVipStoreUI()
            true
        }

        cardVipBundle?.setOnClickListener {
            soundManager.playClick()
            if (!com.aaritlabs.funtoza.monetization.BillingManager.isVipPartyActive(this)) {
                billingManager.launchPurchaseFlow(this, com.aaritlabs.funtoza.monetization.BillingManager.SKU_VIP_PARTY_BUNDLE)
            }
        }

        cardTipCoffee?.setOnClickListener {
            soundManager.playClick()
            billingManager.launchPurchaseFlow(this, com.aaritlabs.funtoza.monetization.BillingManager.SKU_TIP_COFFEE)
        }

        btnRestorePurchases?.setOnClickListener {
            soundManager.playClick()
            Toast.makeText(this, "Restoring purchases from Google Play...", Toast.LENGTH_SHORT).show()
            billingManager.restorePurchases { count ->
                runOnUiThread {
                    updateVipStoreUI()
                    if (count > 0) {
                        Toast.makeText(this, "✓ Successfully restored $count purchases!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "No previous purchases found for this Google account.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        billingManager.onPurchaseCompletedListener = { sku ->
            runOnUiThread {
                updateVipStoreUI()
                if (sku == com.aaritlabs.funtoza.monetization.BillingManager.SKU_TIP_COFFEE) {
                    Toast.makeText(this, "☕ Thank you so much for your support!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "👑 Welcome to VIP! Ads permanently removed!", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 1. Audio & Haptics Setup
        switchSound.isChecked = soundManager.isSoundEnabled
        switchVibration.isChecked = prefs.getBoolean("vibration_enabled", true)

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            soundManager.isSoundEnabled = isChecked
            if (isChecked) soundManager.playClick()
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            soundManager.playClick()
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply()
            if (isChecked) {
                triggerHapticBuzz()
            }
        }

        // 2. Default Bot Difficulty (0: Easy, 1: Medium, 2: Hard)
        var selectedDifficulty = prefs.getInt("bot_difficulty", 1)

        val updateDifficultyUI = {
            btnDiffEasy.setBackgroundResource(if (selectedDifficulty == 0) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnDiffEasy.setTextColor(if (selectedDifficulty == 0) Color.WHITE else Color.parseColor("#7A8B9E"))

            btnDiffMedium.setBackgroundResource(if (selectedDifficulty == 1) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnDiffMedium.setTextColor(if (selectedDifficulty == 1) Color.WHITE else Color.parseColor("#7A8B9E"))

            btnDiffHard.setBackgroundResource(if (selectedDifficulty == 2) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnDiffHard.setTextColor(if (selectedDifficulty == 2) Color.WHITE else Color.parseColor("#7A8B9E"))

            tvDiffDesc.text = when (selectedDifficulty) {
                0 -> "Easy: Relaxed pace & friendly mistakes"
                1 -> "Medium: Balanced reaction & clever tactics"
                else -> "Hard: Fast reflex & ruthless precision"
            }
        }
        updateDifficultyUI()

        btnDiffEasy.setOnClickListener {
            selectedDifficulty = 0
            prefs.edit().putInt("bot_difficulty", selectedDifficulty).apply()
            soundManager.playClick()
            updateDifficultyUI()
        }

        btnDiffMedium.setOnClickListener {
            selectedDifficulty = 1
            prefs.edit().putInt("bot_difficulty", selectedDifficulty).apply()
            soundManager.playClick()
            updateDifficultyUI()
        }

        btnDiffHard.setOnClickListener {
            selectedDifficulty = 2
            prefs.edit().putInt("bot_difficulty", selectedDifficulty).apply()
            soundManager.playClick()
            updateDifficultyUI()
        }

        // 3. Default Match Format (1, 3, or 5 rounds)
        var selectedRounds = prefs.getInt("match_rounds", 3)

        val updateRoundsUI = {
            btnRounds1.setBackgroundResource(if (selectedRounds == 1) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnRounds1.setTextColor(if (selectedRounds == 1) Color.WHITE else Color.parseColor("#7A8B9E"))

            btnRounds3.setBackgroundResource(if (selectedRounds == 3) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnRounds3.setTextColor(if (selectedRounds == 3) Color.WHITE else Color.parseColor("#7A8B9E"))

            btnRounds5.setBackgroundResource(if (selectedRounds == 5) R.drawable.bg_diff_active_pink else android.R.color.transparent)
            btnRounds5.setTextColor(if (selectedRounds == 5) Color.WHITE else Color.parseColor("#7A8B9E"))
        }
        updateRoundsUI()

        btnRounds1.setOnClickListener {
            selectedRounds = 1
            prefs.edit().putInt("match_rounds", selectedRounds).apply()
            soundManager.playClick()
            updateRoundsUI()
        }

        btnRounds3.setOnClickListener {
            selectedRounds = 3
            prefs.edit().putInt("match_rounds", selectedRounds).apply()
            soundManager.playClick()
            updateRoundsUI()
        }

        btnRounds5.setOnClickListener {
            selectedRounds = 5
            prefs.edit().putInt("match_rounds", selectedRounds).apply()
            soundManager.playClick()
            updateRoundsUI()
        }

        // 4. Cup Tournaments History & Replay
        btnViewHistory.setOnClickListener {
            soundManager.playClick()
            showTournamentHistoryDialog()
        }

        btnClearHistory.setOnClickListener {
            soundManager.playClick()
            showClearHistoryConfirmationDialog()
        }

        // 5. App Actions
        btnShare.setOnClickListener {
            soundManager.playClick()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Funtoza: 1-4 Player Games")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Play Funtoza: 1, 2, 3, 4 Player Offline Party Games with me on the same screen! 🎮 Download now and let the fun begin!"
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share Funtoza with Friends"))
        }

        btnFeedback.setOnClickListener {
            soundManager.playClick()
            Toast.makeText(this, "⭐ Thank you for playing Funtoza! Enjoy party battles with friends & family!", Toast.LENGTH_LONG).show()
        }

        btnResetDefaults.setOnClickListener {
            soundManager.playClick()
            prefs.edit().apply {
                putBoolean("sound_enabled", true)
                putBoolean("vibration_enabled", true)
                putInt("bot_difficulty", 1)
                putInt("match_rounds", 3)
                apply()
            }
            soundManager.isSoundEnabled = true
            switchSound.isChecked = true
            switchVibration.isChecked = true
            selectedDifficulty = 1
            selectedRounds = 3
            updateDifficultyUI()
            updateRoundsUI()
            Toast.makeText(this, "🔄 Settings restored to defaults!", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener {
            soundManager.playClick()
            finish()
        }
    }

    private fun triggerHapticBuzz() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    private fun showTournamentHistoryDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tournament_history)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnBack = dialog.findViewById<ImageButton>(R.id.btnHistoryBack)
        val tvEmpty = dialog.findViewById<TextView>(R.id.tvHistoryEmpty)
        val layoutItems = dialog.findViewById<LinearLayout>(R.id.layoutHistoryItems)

        btnBack?.setOnClickListener {
            soundManager.playClick()
            dialog.dismiss()
        }

        fun populateList() {
            layoutItems?.removeAllViews()
            val list = TournamentHistoryManager.getSavedTournaments(this)
            if (list.isEmpty()) {
                tvEmpty?.visibility = View.VISIBLE
            } else {
                tvEmpty?.visibility = View.GONE
                val density = resources.displayMetrics.density

                list.forEach { item ->
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, (12 * density).toInt())
                        }
                        setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 14f * density
                            if (!item.isCompleted) {
                                setColor(Color.parseColor("#261738"))
                                setStroke((1.8f * density).toInt(), Color.parseColor("#FFD700"))
                            } else {
                                setColor(Color.parseColor("#15112B"))
                                setStroke((1f * density).toInt(), Color.parseColor("#29224A"))
                            }
                        }
                    }

                    // Header row: Title & Date
                    val headerRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val tvTitle = TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                        text = item.title
                        setTextColor(if (!item.isCompleted) Color.parseColor("#FFD700") else Color.WHITE)
                        textSize = 14f
                        paint.isFakeBoldText = true
                    }
                    headerRow.addView(tvTitle)

                    val tvDate = TextView(this).apply {
                        text = item.dateStr
                        setTextColor(Color.parseColor("#7A86A8"))
                        textSize = 11f
                    }
                    headerRow.addView(tvDate)
                    card.addView(headerRow)

                    val playersStr = item.playerNames.joinToString(", ")
                    val tvDetails = TextView(this).apply {
                        text = "👥 $playersStr (${item.totalPlayers}P)"
                        setTextColor(Color.parseColor("#9EADC8"))
                        textSize = 12f
                        setPadding(0, (4 * density).toInt(), 0, (8 * density).toInt())
                    }
                    card.addView(tvDetails)

                    // Buttons Row
                    val btnRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (38 * density).toInt()
                        )
                    }

                    if (!item.isCompleted) {
                        val btnResume = Button(this).apply {
                            text = "RESUME ➔"
                            textSize = 11.5f
                            setTextColor(Color.WHITE)
                            paint.isFakeBoldText = true
                            minHeight = 0
                            minWidth = 0
                            includeFontPadding = false
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = 8f * density
                                setColor(Color.parseColor("#FF2A85"))
                            }
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.2f).apply {
                                marginEnd = (6 * density).toInt()
                            }
                            setOnClickListener {
                                soundManager.playClick()
                                dialog.dismiss()
                                val restored = TournamentHistoryManager.toTournamentState(item)
                                val intent = Intent(this@SettingsActivity, TournamentActivity::class.java).apply {
                                    putExtra("TOURNAMENT_STATE", restored)
                                    putStringArrayListExtra("PLAYER_NAMES", ArrayList(item.playerNames))
                                }
                                startActivity(intent)
                            }
                        }
                        btnRow.addView(btnResume)
                    }

                    val btnReplay = Button(this).apply {
                        text = "REPLAY 🔄"
                        textSize = 11.5f
                        setTextColor(Color.WHITE)
                        paint.isFakeBoldText = true
                        minHeight = 0
                        minWidth = 0
                        includeFontPadding = false
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 8f * density
                            setColor(Color.parseColor("#0077B6"))
                        }
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f).apply {
                            marginEnd = (6 * density).toInt()
                        }
                        setOnClickListener {
                            soundManager.playClick()
                            dialog.dismiss()
                            val freshReplay = TournamentHistoryManager.toReplayTournamentState(item)
                            val intent = Intent(this@SettingsActivity, TournamentActivity::class.java).apply {
                                putExtra("TOURNAMENT_STATE", freshReplay)
                                putStringArrayListExtra("PLAYER_NAMES", ArrayList(item.playerNames))
                            }
                            startActivity(intent)
                        }
                    }
                    btnRow.addView(btnReplay)

                    val btnDelete = Button(this).apply {
                        text = "🗑️"
                        textSize = 13f
                        minHeight = 0
                        minWidth = 0
                        includeFontPadding = false
                        setTextColor(Color.parseColor("#FF4D6D"))
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 8f * density
                            setColor(Color.parseColor("#281424"))
                            setStroke((1f * density).toInt(), Color.parseColor("#5A1E32"))
                        }
                        val widthPx = (44 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.MATCH_PARENT)
                        setOnClickListener {
                            soundManager.playClick()
                            TournamentHistoryManager.deleteTournament(this@SettingsActivity, item.id)
                            populateList()
                        }
                    }
                    btnRow.addView(btnDelete)

                    card.addView(btnRow)
                    layoutItems.addView(card)
                }
            }
        }

        populateList()

        if (!isFinishing && !isDestroyed) {
            try { dialog.show() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showClearHistoryConfirmationDialog() {
        val dialog = Dialog(this)
        val density = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * density).toInt(), (22 * density).toInt(), (22 * density).toInt(), (22 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                setColor(Color.parseColor("#1C1635"))
                setStroke((1.5f * density).toInt(), Color.parseColor("#FF4D6D"))
            }
        }

        val tvTitle = TextView(this).apply {
            text = "CLEAR CUP HISTORY?"
            textSize = 16f
            setTextColor(Color.WHITE)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        container.addView(tvTitle)

        val tvDesc = TextView(this).apply {
            text = "Are you sure you want to delete all saved cups and tournament replays? This cannot be undone."
            textSize = 12.5f
            setTextColor(Color.parseColor("#9EADC8"))
            gravity = Gravity.CENTER
            setPadding(0, (8 * density).toInt(), 0, (18 * density).toInt())
        }
        container.addView(tvDesc)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * density).toInt())
        }

        val btnCancel = Button(this).apply {
            text = "CANCEL"
            textSize = 12.5f
            setTextColor(Color.WHITE)
            minHeight = 0
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(Color.parseColor("#15112B"))
                setStroke((1f * density).toInt(), Color.parseColor("#29224A"))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            }
            setOnClickListener {
                soundManager.playClick()
                dialog.dismiss()
            }
        }
        btnRow.addView(btnCancel)

        val btnConfirm = Button(this).apply {
            text = "DELETE ALL"
            textSize = 12.5f
            setTextColor(Color.WHITE)
            minHeight = 0
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(Color.parseColor("#C9184A"))
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = (6 * density).toInt()
            }
            setOnClickListener {
                soundManager.playClick()
                getSharedPreferences("funtoza_tournament_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                dialog.dismiss()
                Toast.makeText(this@SettingsActivity, "🗑️ All saved cup history cleared!", Toast.LENGTH_SHORT).show()
            }
        }
        btnRow.addView(btnConfirm)
        container.addView(btnRow)

        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.88).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        if (!isFinishing && !isDestroyed) {
            try { dialog.show() } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    private fun hideSystemUI() {
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {}
    }
}
