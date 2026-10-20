package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.math.abs

/**
 * Water Tower: crank the bezel to pump water into the tank. Cranking faster
 * than the sweet spot makes the pipe spray, wasting most of each stroke.
 */
class PumpGame : Microgame(MicrogameId.PUMP, durationSeconds = 8f) {
    /** Units of water in the tank, 0..[CAPACITY]. */
    var fill = 0f
        private set

    /** Signed total detents, for drawing the crank angle. */
    var crankDetents = 0
        private set

    /** Detents per second over the last [SPEED_WINDOW]. */
    var speed = 0f
        private set

    /**
     * Cranking speed relative to the limit, eased like a real gauge needle:
     * 1.0 is exactly [SWEET_MAX]; above 1 the pipe sprays.
     */
    var pressure = 0f
        private set

    /** True for a moment after a too-fast stroke. */
    val spraying: Boolean get() = sprayTimer > 0f

    private var sprayTimer = 0f
    private val recent = ArrayDeque<Float>()

    override val progress: Float get() = fill / CAPACITY

    override fun onDetents(detents: Int) {
        if (finished || detents == 0) return
        crankDetents += detents
        repeat(abs(detents)) {
            recent.addLast(elapsed)
            prune()
            speed = recent.size / SPEED_WINDOW
            val tooFast = speed > SWEET_MAX
            if (tooFast) sprayTimer = SPRAY_HOLD
            fill = (fill + if (tooFast) SPRAY_EFFICIENCY else 1f).coerceAtMost(CAPACITY)
        }
        if (fill >= CAPACITY) finish(success = true)
    }

    override fun step(dt: Float) {
        prune()
        speed = recent.size / SPEED_WINDOW
        sprayTimer = (sprayTimer - dt).coerceAtLeast(0f)
        val target = speed / SWEET_MAX
        pressure += (target - pressure) * (dt * GAUGE_RESPONSE).coerceAtMost(1f)
    }

    private fun prune() {
        while (recent.isNotEmpty() && elapsed - recent.first() > SPEED_WINDOW) recent.removeFirst()
    }

    companion object {
        const val CAPACITY = 40f
        /** Detents per second above which the pump sprays. The bezel has 24 detents per turn. */
        const val SWEET_MAX = 9f
        const val SPEED_WINDOW = 0.5f
        const val SPRAY_EFFICIENCY = 0.3f
        const val SPRAY_HOLD = 0.35f
        /** How quickly the gauge needle follows the actual speed, per second. */
        const val GAUGE_RESPONSE = 8f
    }
}
