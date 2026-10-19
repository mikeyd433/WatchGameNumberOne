package com.mikeyd.bezeltown.input

import android.content.Context
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Turns rotary scroll pixels into whole bezel detents. The Galaxy Watch bezel
 * sends one event per click; anything smaller than a click still counts as one
 * so no turn of the bezel is ever lost.
 */
class BezelDetents(context: Context) {
    private val pixelsPerDetent = ViewConfiguration.get(context).scaledVerticalScrollFactor.coerceAtLeast(1f)

    /** Positive = clockwise. */
    fun fromPixels(pixels: Float): Int {
        if (pixels == 0f) return 0
        val detents = (abs(pixels) / pixelsPerDetent).roundToInt().coerceAtLeast(1)
        return detents * pixels.sign.toInt() * DIRECTION
    }

    companion object {
        /** Flip to -1 if clockwise turns walk left on a real watch. */
        const val DIRECTION = 1
    }
}
