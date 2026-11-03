package com.aaritlabs.funtoza.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural Audio Synthesizer & Manager for Funtoza.
 * Generates custom 16-bit 44.1kHz retro-arcade sound effects in memory on first run,
 * caching them to app storage and playing through high-performance SoundPool.
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
        TAP,
        BOUNCE,
        SHOOT,
        EXPLOSION,
        GOAL,
        WHISTLE,
        COUNTDOWN,
        VICTORY
    }

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(audioAttributes)
            .build()

        generateAndLoadSounds()
    }

    private fun generateAndLoadSounds() {
        val cacheDir = File(context.cacheDir, "procedural_sfx")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        SoundType.values().forEach { type ->
            val wavFile = File(cacheDir, "${type.name.lowercase()}.wav")
            if (!wavFile.exists() || wavFile.length() < 100) {
                val samples = generateWaveform(type)
                writeWavFile(wavFile, samples, 44100)
            }
            if (wavFile.exists()) {
                val id = soundPool.load(wavFile.absolutePath, 1)
                soundIds[type] = id
            }
        }
    }

    private fun generateWaveform(type: SoundType): ShortArray {
        val sampleRate = 44100
        return when (type) {
            SoundType.TAP -> {
                // Short crisp click / pop (40ms)
                val duration = 0.04
                val totalSamples = (sampleRate * duration).toInt()
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val freq = 800.0 - 400.0 * (i.toDouble() / totalSamples)
                    val env = (1.0 - (i.toDouble() / totalSamples))
                    val wave = sin(2.0 * PI * freq * t)
                    (wave * env * Short.MAX_VALUE * 0.7).toInt().toShort()
                }
            }
            SoundType.BOUNCE -> {
                // Springy rubber / air hockey ping (90ms)
                val duration = 0.09
                val totalSamples = (sampleRate * duration).toInt()
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val freq = 220.0 + 350.0 * exp(-t * 30.0)
                    val env = exp(-t * 22.0)
                    val wave = sin(2.0 * PI * freq * t) + 0.3 * sin(4.0 * PI * freq * t)
                    (wave * env * Short.MAX_VALUE * 0.8).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
            }
            SoundType.SHOOT -> {
                // Tank cannon laser chirp (120ms frequency slide down)
                val duration = 0.12
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
                // Low rumble noise explosion (300ms)
                val duration = 0.30
                val totalSamples = (sampleRate * duration).toInt()
                val random = Random(42)
                var lastVal = 0.0
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 8.0)
                    val noise = random.nextDouble(-1.0, 1.0)
                    lastVal = 0.8 * lastVal + 0.2 * noise // Low-pass filter for bass thump
                    (lastVal * env * Short.MAX_VALUE * 0.9).toInt().toShort()
                }
            }
            SoundType.GOAL -> {
                // Rising major triad arpeggio (C5 - E5 - G5 - C6 fanfare)
                val duration = 0.45
                val totalSamples = (sampleRate * duration).toInt()
                val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val noteIndex = ((t / duration) * notes.size).toInt().coerceIn(0, notes.size - 1)
                    val freq = notes[noteIndex]
                    val noteTime = t - (noteIndex * duration / notes.size)
                    val env = exp(-noteTime * 12.0)
                    val wave = sin(2.0 * PI * freq * t) + 0.25 * sin(4.0 * PI * freq * t)
                    (wave * env * Short.MAX_VALUE * 0.75).toInt().toShort()
                }
            }
            SoundType.WHISTLE -> {
                // Dual tone sports referee whistle (250ms)
                val duration = 0.25
                val totalSamples = (sampleRate * duration).toInt()
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val tremolo = 1.0 + 0.3 * sin(2.0 * PI * 35.0 * t)
                    val wave = 0.5 * sin(2.0 * PI * 2400.0 * t) + 0.5 * sin(2.0 * PI * 2700.0 * t)
                    val env = if (t < 0.02) t / 0.02 else exp(-(t - 0.02) * 5.0)
                    (wave * tremolo * env * Short.MAX_VALUE * 0.65).toInt().toShort()
                }
            }
            SoundType.COUNTDOWN -> {
                // Wooden tick / beeper (60ms)
                val duration = 0.06
                val totalSamples = (sampleRate * duration).toInt()
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val env = exp(-t * 40.0)
                    val wave = sin(2.0 * PI * 1100.0 * t)
                    (wave * env * Short.MAX_VALUE * 0.75).toInt().toShort()
                }
            }
            SoundType.VICTORY -> {
                // Upbeat 8-bit retro fanfare (600ms)
                val duration = 0.60
                val totalSamples = (sampleRate * duration).toInt()
                val melody = doubleArrayOf(440.0, 554.37, 659.25, 880.0)
                ShortArray(totalSamples) { i ->
                    val t = i.toDouble() / sampleRate
                    val idx = ((t / duration) * melody.size).toInt().coerceIn(0, melody.size - 1)
                    val freq = melody[idx]
                    // 8-bit square wave with harmonic richness
                    val rawWave = if (sin(2.0 * PI * freq * t) > 0) 1.0 else -1.0
                    val env = exp(-(t % (duration / melody.size)) * 6.0)
                    (rawWave * env * Short.MAX_VALUE * 0.5).toInt().toShort()
                }
            }
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
        bb.putInt(16) // Subchunk1Size for PCM
        bb.putShort(1) // AudioFormat 1 = PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort((channels * 2).toShort()) // block align
        bb.putShort(16) // bits per sample
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

