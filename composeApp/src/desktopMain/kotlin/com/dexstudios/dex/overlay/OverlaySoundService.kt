package com.dexstudios.dex.overlay

import co.touchlab.kermit.Logger
import com.dexstudios.dex.core.network.DeviceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import kotlin.math.exp
import kotlin.math.sin

/**
 * Desktop audio feedback engine for DeX notifications.
 *
 * Provides:
 * - High-fidelity in-memory synthesized harmonic chime (zero disk I/O latency)
 * - Custom audio file playback when specified
 * - Preference gating via [DeviceConfig.notificationSoundEnabled]
 * - Non-blocking asynchronous audio dispatch
 */
class OverlaySoundService(private val deviceConfig: DeviceConfig, private val scope: CoroutineScope) {
    private var customAudioFile: File? = null
    private val synthesizedChimePcm: ByteArray by lazy { generateHarmonicChimePcm() }

    /**
     * Set a custom audio file (.wav, .aiff) for notification sounds.
     */
    fun setCustomAudioFile(file: File) {
        if (file.exists() && file.isFile) {
            customAudioFile = file
            Logger.i("OverlaySoundService: Custom audio file registered: ${file.absolutePath}")
        }
    }

    private var lastPlayEpochMs: Long = 0L

    companion object {
        const val AUDIO_COOLDOWN_MS = 1_500L
    }

    /**
     * Plays the notification chime if sounds are enabled in [DeviceConfig].
     */
    fun playNotificationSound() {
        if (!deviceConfig.notificationSoundEnabled) return

        val now = System.currentTimeMillis()
        if (now - lastPlayEpochMs < AUDIO_COOLDOWN_MS) {
            // Suppress audio spam within 1.5s cooldown window
            return
        }
        lastPlayEpochMs = now

        scope.launch(Dispatchers.IO) {
            try {
                val custom = customAudioFile
                if (custom != null && custom.exists()) {
                    playAudioFile(custom)
                } else {
                    playSynthesizedChime()
                }
            } catch (e: Throwable) {
                Logger.w("OverlaySoundService: Audio playback failed: ${e.message}")
            }
        }
    }

    private fun playAudioFile(file: File) {
        AudioSystem.getAudioInputStream(file).use { audioStream ->
            val clip: Clip = AudioSystem.getClip()
            clip.open(audioStream)
            clip.start()
        }
    }

    private fun playSynthesizedChime() {
        val format = AudioFormat(44100f, 16, 1, true, false)
        val stream = AudioInputStream(
            ByteArrayInputStream(synthesizedChimePcm),
            format,
            (synthesizedChimePcm.size / 2).toLong(),
        )
        val clip: Clip = AudioSystem.getClip()
        clip.open(stream)
        clip.start()
    }

    /**
     * Generates a 44.1kHz 16-bit mono dual-tone harmonic chime (880Hz A5 + 1320Hz E6)
     * with exponential smooth decay for an elegant Apple-style notification tone.
     */
    private fun generateHarmonicChimePcm(): ByteArray {
        val sampleRate = 44100
        val durationSeconds = 0.45
        val numSamples = (sampleRate * durationSeconds).toInt()
        val pcmData = ByteArray(numSamples * 2)

        val freq1 = 880.0 // A5
        val freq2 = 1320.0 // E6 (5th harmonic)

        for (i in 0 until numSamples) {
            val time = i.toDouble() / sampleRate
            val envelope = exp(-time * 8.0) // Soft exponential fade

            // First tone strikes immediately, second harmonic peaks at 0.06s
            val tone1 = sin(2.0 * Math.PI * freq1 * time) * envelope
            val tone2 = if (time >= 0.05) {
                sin(2.0 * Math.PI * freq2 * (time - 0.05)) * exp(-(time - 0.05) * 10.0) * 0.7
            } else {
                0.0
            }

            val mixed = ((tone1 + tone2) * 0.40).coerceIn(-1.0, 1.0)
            val sampleVal = (mixed * 32767.0).toInt().toShort()

            pcmData[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        return pcmData
    }
}
