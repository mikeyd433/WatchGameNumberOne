package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.math.roundToInt

/**
 * A short single-action game. It knows nothing about credits or money: when it
 * ends it reports a 0–100 [score] and the economy layer decides what that's worth.
 *
 * Coordinates for touch input are normalized to the round screen: x and y in
 * [-1, 1], origin at the center, y up.
 */
abstract class Microgame(val id: MicrogameId, val durationSeconds: Float) {
    var elapsed = 0f
        private set
    var finished = false
        private set
    var succeeded = false
        private set
    var score = 0
        private set

    /** 0–1 toward the goal. */
    abstract val progress: Float

    val timeLeftFraction: Float get() = (1f - elapsed / durationSeconds).coerceIn(0f, 1f)

    /**
     * Finishing within this many seconds earns the full time bonus. 0 means the
     * bonus only maxes out at an instant finish; games whose every action takes a
     * moment set a realistic par so a good run can still score 100.
     */
    protected open val parSeconds: Float = 0f

    fun update(dt: Float) {
        if (finished || dt <= 0f) return
        elapsed += dt
        step(dt)
        if (!finished && elapsed >= durationSeconds) finish(success = false)
    }

    open fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {}

    /** The finger lifted (or the gesture was cancelled). */
    open fun onDragEnd() {}
    open fun onDetents(detents: Int) {}

    protected open fun step(dt: Float) {}

    /** Uses up extra clock time, e.g. as a penalty. The timeout is checked on the next [update]. */
    protected fun addTime(seconds: Float) {
        elapsed += seconds
    }

    protected fun finish(success: Boolean) {
        if (finished) return
        finished = true
        succeeded = success
        score = computeScore(success)
    }

    /**
     * Finishing earns 60 plus up to 40 for speed (full marks at or under par);
     * failing earns partial credit for progress.
     */
    protected open fun computeScore(success: Boolean): Int {
        if (!success) return (60 * progress.coerceIn(0f, 1f)).roundToInt()
        val speed = ((durationSeconds - elapsed) / (durationSeconds - parSeconds)).coerceIn(0f, 1f)
        return 60 + (40 * speed).roundToInt()
    }
}
