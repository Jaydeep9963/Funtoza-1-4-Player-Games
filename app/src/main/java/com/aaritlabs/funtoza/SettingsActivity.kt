package com.aaritlabs.funtoza

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.aaritlabs.funtoza.audio.SoundManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        hideSystemUI()

        soundManager = SoundManager.getInstance(this)
        val prefs = getSharedPreferences("funtoza_prefs", Context.MODE_PRIVATE)

        val btnBack = findViewById<ImageButton>(R.id.btnSettingsBack)
        val switchSound = findViewById<SwitchCompat>(R.id.switchSound)
        val switchVibration = findViewById<SwitchCompat>(R.id.switchVibration)
        val btnShare = findViewById<Button>(R.id.btnSettingsShare)

        switchSound.isChecked = soundManager.isSoundEnabled
        switchVibration.isChecked = prefs.getBoolean("vibration_enabled", true)

        switchSound.setOnCheckedChangeListener { _, isChecked ->
            soundManager.isSoundEnabled = isChecked
            soundManager.play(SoundManager.SoundType.TAP)
            prefs.edit().putBoolean("sound_enabled", isChecked).apply()
        }

        switchVibration.setOnCheckedChangeListener { _, isChecked ->
            soundManager.play(SoundManager.SoundType.TAP)
            prefs.edit().putBoolean("vibration_enabled", isChecked).apply()
        }

        btnBack.setOnClickListener {
            soundManager.play(SoundManager.SoundType.TAP)
            finish()
        }

        btnShare.setOnClickListener {
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

    private fun hideSystemUI() {
        try {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {}
    }
}

