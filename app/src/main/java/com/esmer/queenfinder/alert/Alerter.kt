package com.esmer.queenfinder.alert

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Buzzes and beeps when a stable queen track appears. Debounced so a queen that
 * walks in and out of view does not fire continuously.
 */
class Alerter(context: Context) {

    @Volatile var hapticsEnabled = true
    @Volatile var soundEnabled = true
    var debounceMs = 2500L

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val tone: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
    }.getOrNull()

    private var lastAlertAt = 0L

    /** Safe to call from any thread. */
    fun queenFound() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlertAt < debounceMs) return
        lastAlertAt = now
        if (hapticsEnabled) {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)
            )
        }
        if (soundEnabled) {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        }
    }

    fun release() {
        tone?.release()
    }
}
