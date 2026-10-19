package com.mikeyd.bezeltown.render

import kotlin.math.pow

/** Linear-space RGB. Filament works in linear light; we author colors in sRGB hex. */
data class LinearColor(val r: Float, val g: Float, val b: Float) {
    fun scaled(k: Float) = LinearColor(r * k, g * k, b * k)

    fun lerp(to: LinearColor, t: Float) = LinearColor(
        r + (to.r - r) * t,
        g + (to.g - g) * t,
        b + (to.b - b) * t,
    )

    companion object {
        fun srgb(hex: Int): LinearColor = LinearColor(
            toLinear((hex shr 16) and 0xFF),
            toLinear((hex shr 8) and 0xFF),
            toLinear(hex and 0xFF),
        )

        private fun toLinear(channel: Int): Float {
            val c = channel / 255f
            return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
        }
    }
}

/** Tint that turns a locked building into a dusky silhouette. */
val SILHOUETTE = LinearColor(0.10f, 0.12f, 0.20f)
