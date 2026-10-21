package com.mikeyd.bezeltown.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short, distinct haptics for bezel detents, doorways, and results. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** One bezel detent. */
    fun tick() = predefined(VibrationEffect.EFFECT_TICK)

    /** Snapping to a doorway, unlocking, buying. */
    fun click() = predefined(VibrationEffect.EFFECT_CLICK)

    fun heavy() = predefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    /** A rough, buzzy detent: the feel of turning through static. */
    fun buzz() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(
            if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(28, 70)
            else VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    fun success() = waveform(longArrayOf(0, 40, 60, 40, 60, 90), intArrayOf(0, 180, 0, 180, 0, 255))

    fun failure() = waveform(longArrayOf(0, 160), intArrayOf(0, 120))

    private fun predefined(effect: Int) {
        val v = vibrator ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createPredefined(effect))
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        v.vibrate(effect)
    }
}
