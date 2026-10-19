package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

/** Bakery: swipe back and forth across the dough until it's springy. */
class KneadGame : Microgame(MicrogameId.KNEAD, durationSeconds = 7f) {
    var kneads = 0
        private set

    /** -1..1, how far the dough is currently pulled sideways. Relaxes back to 0. */
    var stretch = 0f
        private set

    private var travel = 0f

    override val progress: Float get() = kneads.toFloat() / TARGET_KNEADS

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (finished) return
        if (hypot(x, y) > DOUGH_RADIUS * GRAB_SLACK) return
        travel += abs(dx)
        stretch = (stretch + dx * STRETCH_GAIN).coerceIn(-1f, 1f)
        while (travel >= TRAVEL_PER_KNEAD) {
            travel -= TRAVEL_PER_KNEAD
            kneads++
        }
        if (kneads >= TARGET_KNEADS) finish(success = true)
    }

    override fun step(dt: Float) {
        stretch *= exp(-dt * RELAX_RATE)
    }

    companion object {
        const val DOUGH_RADIUS = 0.6f
        const val GRAB_SLACK = 1.2f
        const val TRAVEL_PER_KNEAD = 0.5f
        const val TARGET_KNEADS = 15
        const val STRETCH_GAIN = 1.2f
        const val RELAX_RATE = 6f
    }
}
