package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/**
 * Bakery: arrows pop up over the dough one at a time. Swipe the way each one
 * points to knead. A wrong swipe splats the dough and costs time.
 */
class KneadGame(private val random: Random = Random.Default) : Microgame(MicrogameId.KNEAD, durationSeconds = 8f) {
    enum class Direction(val x: Int, val y: Int) { UP(0, 1), DOWN(0, -1), LEFT(-1, 0), RIGHT(1, 0) }

    /** The arrow to swipe now. */
    var arrow: Direction = Direction.entries[random.nextInt(Direction.entries.size)]
        private set

    /** Seconds since [arrow] appeared, for its pop-in animation. */
    var arrowAge = 0f
        private set

    /** The arrow just cleared and how long ago, for its fly-off animation. */
    var lastCleared: Direction? = null
        private set
    var clearedAge = 0f
        private set

    var cleared = 0
        private set
    var mistakes = 0
        private set

    /** Seconds since the last wrong swipe (large when there hasn't been one). */
    var mistakeAge = 10f
        private set

    /** -1..1 per axis: how far the dough is pulled. Relaxes back to 0. */
    var stretchX = 0f
        private set
    var stretchY = 0f
        private set

    // Finger movement since the last recognized swipe.
    private var dragX = 0f
    private var dragY = 0f

    override val progress: Float get() = cleared.toFloat() / TARGET_ARROWS

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (finished) return
        dragX += dx
        dragY += dy
        if (abs(dragX) < SWIPE_DISTANCE && abs(dragY) < SWIPE_DISTANCE) return

        val swiped = when {
            abs(dragX) >= abs(dragY) -> if (dragX > 0) Direction.RIGHT else Direction.LEFT
            else -> if (dragY > 0) Direction.UP else Direction.DOWN
        }
        // Start measuring afresh, so a zig-zag without lifting the finger keeps working.
        dragX = 0f
        dragY = 0f
        if (swiped == arrow) clear(swiped) else miss()
    }

    override fun onDragEnd() {
        dragX = 0f
        dragY = 0f
    }

    private fun clear(direction: Direction) {
        cleared++
        stretchX = direction.x.toFloat()
        stretchY = direction.y.toFloat()
        lastCleared = direction
        clearedAge = 0f
        if (cleared >= TARGET_ARROWS) {
            finish(success = true)
            return
        }
        // Always a different direction from the one just cleared.
        val others = Direction.entries.filter { it != direction }
        arrow = others[random.nextInt(others.size)]
        arrowAge = 0f
    }

    private fun miss() {
        mistakes++
        mistakeAge = 0f
        addTime(MISTAKE_PENALTY_SECONDS)
    }

    override fun step(dt: Float) {
        arrowAge += dt
        clearedAge += dt
        mistakeAge += dt
        val relax = exp(-dt * RELAX_RATE)
        stretchX *= relax
        stretchY *= relax
    }

    override fun computeScore(success: Boolean): Int {
        val base = super.computeScore(success)
        return if (success) (base - mistakes * SCORE_PER_MISTAKE).coerceIn(1, 100) else base
    }

    companion object {
        const val TARGET_ARROWS = 10
        /** Finger travel, in half-screen units, that counts as a swipe. */
        const val SWIPE_DISTANCE = 0.3f
        const val MISTAKE_PENALTY_SECONDS = 0.5f
        const val SCORE_PER_MISTAKE = 4
        const val RELAX_RATE = 7f
    }
}
