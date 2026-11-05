package com.aaritlabs.funtoza.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.aaritlabs.funtoza.R
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Audio Manager for Funtoza.
 * Loads crisp .wav audio effects directly from res/raw (sfx_click.wav, sfx_start.wav, sfx_pop.wav, sfx_victory.wav)
 * for instant, zero-latency playback via Android SoundPool.
 */
class SoundManager private constructor(private val context: Context) {

    private var soundPool: SoundPool
    private val soundIds = mutableMapOf<SoundType, Int>()
    var isMuted: Boolean = false
    var isSoundEnabled: Boolean
        get() = !isMuted
        set(value) {
            isMuted = !value
        }

    enum class SoundType {
        TAP,        // Crisp tactile UI click (sfx_click.wav)
        CLICK,      // Same as TAP
        START,      // Uplifting modern game launch chime (sfx_start.wav, NO whistle)
        BOUNCE,     // Bubble pop (sfx_pop.wav)
        SHOOT,      // Laser / cannon shoot
        EXPLOSION,  // Deep bass explosion
        GOAL,       // Goal arpeggio
        WHISTLE,    // Game launch chime (remapped to pleasant sfx_start.wav)
        COUNTDOWN,  // Countdown tick (sfx_click.wav)
        VICTORY     // Victory fanfare (sfx_victory.wav)
    }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        loadAllSounds()
    }

    private fun loadAllSounds() {
        try {
            // Load direct .wav files from res/raw
            val clickId = soundPool.load(context, R.raw.sfx_click, 1)
            val startId = soundPool.load(context, R.raw.sfx_start, 1)
            val popId = soundPool.load(context, R.raw.sfx_pop, 1)
            val victoryId = soundPool.load(context, R.raw.sfx_victory, 1)

            soundIds[SoundType.TAP] = clickId
            soundIds[SoundType.CLICK] = clickId
            soundIds[SoundType.COUNTDOWN] = clickId

            // Pleasant game launch sound (replaces annoying referee whistle)
            soundIds[SoundType.START] = startId
            soundIds[SoundType.WHISTLE] = startId

            soundIds[SoundType.BOUNCE] = popId
            soundIds[SoundType.VICTORY] = victoryId
            soundIds[SoundType.GOAL] = startId
        } catch (_: Exception) {}

        // Fallback / in-memory procedural generation for shoot & explosion
        loadProceduralFallbacks()
    }

    private fun loadProceduralFallbacks() {
        val cacheDir = File(context.cacheDir, "procedural_sfx")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        listOf(SoundType.SHOOT, SoundType.EXPLOSION).forEach { type ->
            if (!soundIds.containsKey(type)) {
                try {
                    val wavFile = File(cacheDir, "${type.name.lowercase()}.wav")
                    if (!wavFile.exists() || wavFile.length() < 100) {
                        val samples = generateWaveform(type)
                        writeWavFile(wavFile, samples, 44100)
                    }
                    if (wavFile.exists()) {
                        val id = soundPool.load(wavFile.absolutePath, 1)
                        soundIds[type] = id
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun generateWaveform(type: SoundType): ShortArray {
        val sampleRate = 44100
        return when (type) {
            SoundType.SHOOT -> {
                val duration = 0.10
                val totalSamples = (sampleRate * duration).toInt()
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val freq = 900.0 - 650.0 * (i.toDouble() / totalSamples)
                    val env = exp(-t * 15.0)
                    val wave = sin(2.0 * PI * freq * t)
                    (wave * env * Short.MAX_VALUE * 0.85).toInt().toShort()
                }
            }
            SoundType.EXPLOSION -> {
                val duration = 0.25
                val totalSamples = (sampleRate * duration).toInt()
                val random = Random(42)
                var lastVal = 0.0
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 8.0)
                    val noise = random.nextDouble(-1.0, 1.0)
                    lastVal = 0.8 * lastVal + 0.2 * noise
                    (lastVal * env * Short.MAX_VALUE * 0.9).toInt().toShort()
                }
            }
            else -> ShortArray(100)
        }
    }

    private fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int) {
        val totalAudioLen = samples.size * 2
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(totalDataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort((channels * 2).toShort())
        bb.putShort(16)
        bb.put("data".toByteArray())
        bb.putInt(totalAudioLen)

        FileOutputStream(file).use { fos ->
            fos.write(header)
            val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                byteBuffer.putShort(sample)
            }
            fos.write(byteBuffer.array())
        }
    }

    /**
     * Plays a crisp tactile click on button taps.
     */
    fun playClick() {
        play(SoundType.CLICK, 1.0f)
    }

    /**
     * Plays the pleasant, modern rising game launch chime.
     */
    fun playStart() {
        play(SoundType.START, 1.0f)
    }

    /**
     * Plays a gentle bubble pop on switches/toggles.
     */
    fun playPop() {
        play(SoundType.BOUNCE, 0.9f)
    }

    fun playVictory() {
        play(SoundType.VICTORY, 1.0f)
    }

    fun play(type: SoundType, volume: Float = 1.0f) {
        if (isMuted) return
        val id = soundIds[type] ?: return
        soundPool.play(id, volume, volume, 1, 0, 1.0f)
    }

    fun release() {
        soundPool.release()
    }

    companion object {
        @Volatile
        private var instance: SoundManager? = null

        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
