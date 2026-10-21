package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.math.abs
import kotlin.random.Random

/**
 * Radio Station: tune the dial through static to the station being called out.
 * One detent is [MHZ_PER_STEP]. There's no mark to aim at: you go by the
 * number, the static clearing, and the feel of the detents. Hold on the station
 * to lock it in.
 */
class RadioGame(private val random: Random = Random.Default) : Microgame(MicrogameId.RADIO, durationSeconds = 10f) {
    /** Where the dial is, in steps from the bottom of the band. */
    var dialStep = START_LOW + random.nextInt(START_HIGH - START_LOW + 1)
        private set

    var targetStep = newTarget(from = dialStep)
        private set

    var stations = 0
        private set

    var holdSeconds = 0f
        private set

    /** Steps between the dial and the station. */
    val distance: Int get() = abs(dialStep - targetStep)

    val tuned: Boolean get() = distance == 0

    /** 0 = pure static, 1 = clear signal. */
    val signal: Float get() = (1f - distance.toFloat() / STATIC_RANGE).coerceIn(0f, 1f)

    override val progress: Float get() = stations.toFloat() / STATIONS

    override val parSeconds: Float get() = PAR_SECONDS

    override fun onDetents(detents: Int) {
        if (finished || detents == 0) return
        dialStep = (dialStep + detents).coerceIn(0, STEPS - 1)
        holdSeconds = 0f
    }

    override fun step(dt: Float) {
        if (!tuned) return
        holdSeconds += dt
        if (holdSeconds < HOLD_SECONDS) return
        stations++
        holdSeconds = 0f
        if (stations >= STATIONS) finish(success = true) else targetStep = newTarget(from = dialStep)
    }

    /** A station a real turn away, inside the band. */
    private fun newTarget(from: Int): Int {
        val options = (0 until STEPS).filter { abs(it - from) in MIN_TURN..MAX_TURN }
        return options[random.nextInt(options.size)]
    }

    companion object {
        const val BAND_BOTTOM_MHZ = 88f
        const val MHZ_PER_STEP = 0.5f
        /** 88.0 to 108.0 MHz. */
        const val STEPS = 41
        const val STATIONS = 3
        const val HOLD_SECONDS = 0.5f
        const val PAR_SECONDS = 6f
        /** How many steps away the signal starts coming through the static. */
        const val STATIC_RANGE = 6f
        const val MIN_TURN = 6
        const val MAX_TURN = 16
        private const val START_LOW = 10
        private const val START_HIGH = 30

        /** "94.5" for a dial step. */
        fun label(step: Int): String {
            val tenths = (BAND_BOTTOM_MHZ * 10).toInt() + step * (MHZ_PER_STEP * 10).toInt()
            return "${tenths / 10}.${tenths % 10}"
        }
    }
}
