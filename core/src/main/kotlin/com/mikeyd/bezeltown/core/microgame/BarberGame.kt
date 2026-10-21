package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.random.Random

/**
 * Barber Shop: dial the customer's chair with the bezel until it faces the
 * gold mark on the floor, then hold still for a moment to snip. Each snip moves
 * the mark somewhere new. Precision play: one detent is one step of the chair.
 */
class BarberGame(private val random: Random = Random.Default) : Microgame(MicrogameId.BARBER, durationSeconds = 10f) {
    /** Which way the chair faces, in steps of [DEGREES_PER_STEP] (0 = facing you). */
    var chairStep = 0
        private set

    /** Which step the gold mark is at. */
    var targetStep = newTarget(from = 0)
        private set

    var snips = 0
        private set

    /** How long the chair has been held on the mark. */
    var holdSeconds = 0f
        private set

    val aligned: Boolean get() = chairStep == targetStep

    override val progress: Float get() = snips.toFloat() / SNIPS

    override val parSeconds: Float get() = PAR_SECONDS

    override fun onDetents(detents: Int) {
        if (finished || detents == 0) return
        chairStep = Math.floorMod(chairStep + detents, STEPS)
        // Any movement restarts the hold, so you have to actually stop on the mark.
        holdSeconds = 0f
    }

    override fun step(dt: Float) {
        if (!aligned) return
        holdSeconds += dt
        if (holdSeconds < HOLD_SECONDS) return
        snips++
        holdSeconds = 0f
        if (snips >= SNIPS) finish(success = true) else targetStep = newTarget(from = chairStep)
    }

    /** A mark a decent turn away (75°–165°), in either direction. */
    private fun newTarget(from: Int): Int {
        val distance = MIN_TURN + random.nextInt(MAX_TURN - MIN_TURN + 1)
        val direction = if (random.nextBoolean()) 1 else -1
        return Math.floorMod(from + direction * distance, STEPS)
    }

    companion object {
        /** Positions around the chair; the bezel has 24 detents per turn. */
        const val STEPS = 24
        const val DEGREES_PER_STEP = 360f / STEPS
        const val SNIPS = 4
        const val HOLD_SECONDS = 0.35f
        const val PAR_SECONDS = 5f
        const val MIN_TURN = 5
        const val MAX_TURN = 11
    }
}
