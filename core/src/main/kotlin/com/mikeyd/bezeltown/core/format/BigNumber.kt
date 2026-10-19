package com.mikeyd.bezeltown.core.format

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Short idle-game number formatting: 999, 1.2K, 34.5M, 678B, 9.9T, 1.2e15.
 * Always rounds down so a displayed amount is never more than you have.
 */
object BigNumber {
    private val suffixes = listOf("", "K", "M", "B", "T")

    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "∞"
        val sign = if (value < 0) "-" else ""
        val v = abs(value)
        if (v < 1000) return sign + floor(v).toLong().toString()

        var exponent = floor(log10(v)).toInt()
        // log10 can land just under an integer for exact powers of ten.
        if (10.0.pow(exponent + 1) <= v) exponent++

        if (exponent >= 15) {
            val mantissa = v / 10.0.pow(exponent)
            return sign + oneDecimal(mantissa) + "e" + exponent
        }
        val group = exponent / 3
        val scaled = v / 1000.0.pow(group)
        val body = if (scaled < 100) oneDecimal(scaled) else floor(scaled).toLong().toString()
        return sign + body + suffixes[group]
    }

    /** Floors to one decimal place and drops a trailing ".0". */
    private fun oneDecimal(x: Double): String {
        val tenths = floor(x * 10 + 1e-9).toLong()
        val whole = tenths / 10
        val frac = tenths % 10
        return if (frac == 0L) whole.toString() else "$whole.$frac"
    }
}
