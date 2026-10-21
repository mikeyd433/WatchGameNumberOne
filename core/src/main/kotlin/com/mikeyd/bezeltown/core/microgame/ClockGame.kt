package com.mikeyd.bezeltown.core.microgame

import com.mikeyd.bezeltown.core.model.MicrogameId
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Clock Tower, Small Town's finale: both inputs in sequence.
 *
 * 1. Wind: crank the bezel clockwise to fill the mainspring, then stop to lock
 *    it in. Crank too far past full and the spring goes sproing: time lost and
 *    the spring half unwinds.
 * 2. Set: drag the hands to the time shown. Both snap to the 12 positions on
 *    the face (minutes in 5-minute steps, hours on whole hours). The bell rings
 *    the moment both are right.
 *
 * Touch coordinates are relative to the clock face: its center is (0, 0) and its
 * rim is radius 1, y up.
 */
class ClockGame(private val random: Random = Random.Default) : Microgame(MicrogameId.CLOCK, durationSeconds = 12f) {
    enum class Phase { WIND, SET }

    var phase = Phase.WIND
        private set

    /** Detents wound, 0..[WIND_FULL] + [OVERWIND_LIMIT]. */
    var wind = 0
        private set

    /** Times the spring has gone sproing. */
    var sproings = 0
        private set

    /** Seconds since the last sproing (large when there hasn't been one). */
    var sproingAge = 10f
        private set

    /** Seconds since the bezel last moved, for locking in a full wind. */
    private var stillSeconds = 0f

    /** Hand positions, 0..11 around the face, 0 at twelve o'clock. */
    var hourHand = 0
        private set
    var minuteHand = 0
        private set

    var targetHour = 0
        private set
    var targetMinute = 0
        private set

    private var grabbed: Hand? = null
    private enum class Hand { HOUR, MINUTE }

    init {
        // A quarter-hour time that isn't where the hands start (12:00).
        do {
            targetHour = random.nextInt(12)
            targetMinute = random.nextInt(4) * 3
        } while (targetHour == 0 && targetMinute == 0)
    }

    val windFraction: Float get() = (wind.toFloat() / WIND_FULL).coerceIn(0f, 1f)

    /** 0 at full, rising to 1 at the sproing point. */
    val overwind: Float get() = ((wind - WIND_FULL).toFloat() / OVERWIND_LIMIT).coerceIn(0f, 1f)

    val hourRight: Boolean get() = hourHand == targetHour
    val minuteRight: Boolean get() = minuteHand == targetMinute

    /** The time to set, e.g. "4:15". */
    val targetLabel: String get() = label(targetHour, targetMinute)

    override val progress: Float
        get() = 0.5f * windFraction + if (phase == Phase.SET) (if (hourRight) 0.25f else 0f) + (if (minuteRight) 0.25f else 0f) else 0f

    override val parSeconds: Float get() = PAR_SECONDS

    override fun onDetents(detents: Int) {
        if (finished || phase != Phase.WIND || detents <= 0) return // a ratchet: backwards does nothing
        stillSeconds = 0f
        wind += detents
        if (wind > WIND_FULL + OVERWIND_LIMIT) {
            sproings++
            sproingAge = 0f
            wind = WIND_FULL - SPROING_SETBACK
            addTime(SPROING_PENALTY_SECONDS)
        }
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (finished || phase != Phase.SET) return
        val radius = hypot(x, y)
        if (radius < MIN_GRAB_RADIUS) return
        val position = positionAt(x, y)
        val hand = grabbed ?: pickHand(position, radius).also { grabbed = it }
        when (hand) {
            Hand.HOUR -> hourHand = position
            Hand.MINUTE -> minuteHand = position
        }
        if (hourRight && minuteRight) finish(success = true)
    }

    override fun onDragEnd() {
        grabbed = null
    }

    override fun step(dt: Float) {
        sproingAge += dt
        if (phase != Phase.WIND) return
        stillSeconds += dt
        if (wind >= WIND_FULL && stillSeconds >= SETTLE_SECONDS) phase = Phase.SET
    }

    /** Grab whichever hand is pointing nearer the touch; on a tie, the long hand if touching far out. */
    private fun pickHand(position: Int, radius: Float): Hand {
        val toHour = ringDistance(position, hourHand)
        val toMinute = ringDistance(position, minuteHand)
        return when {
            toHour < toMinute -> Hand.HOUR
            toMinute < toHour -> Hand.MINUTE
            radius >= LONG_HAND_RADIUS -> Hand.MINUTE
            else -> Hand.HOUR
        }
    }

    companion object {
        /** About 1.5 turns of a 24-detent bezel. */
        const val WIND_FULL = 36
        /** Detents past full that the spring tolerates before it goes sproing. */
        const val OVERWIND_LIMIT = 3
        const val SPROING_SETBACK = 18
        const val SPROING_PENALTY_SECONDS = 1f
        /** Stop cranking this long at full wind to lock it in. */
        const val SETTLE_SECONDS = 0.3f
        const val PAR_SECONDS = 7f
        /** Touches nearer the center than this don't move a hand. */
        const val MIN_GRAB_RADIUS = 0.15f
        /** Beyond this radius a tie goes to the minute hand (the hour hand is shorter). */
        const val LONG_HAND_RADIUS = 0.6f

        /** Which of the 12 positions a touch points at, clockwise from twelve. */
        fun positionAt(x: Float, y: Float): Int {
            val turns = atan2(x, y) / (2 * Math.PI.toFloat())
            return Math.floorMod((turns * 12).roundToInt(), 12)
        }

        fun label(hour: Int, minute: Int): String {
            val h = if (hour == 0) 12 else hour
            val m = minute * 5
            return "$h:${m.toString().padStart(2, '0')}"
        }

        private fun ringDistance(a: Int, b: Int): Int {
            val d = Math.floorMod(a - b, 12)
            return minOf(d, 12 - d)
        }
    }
}
