package com.mikeyd.bezeltown.core.overworld

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Bezel-driven walking along the overworld strip.
 *
 * Positions are measured in slots (0 = arcade cabinet, 1–5 = buildings,
 * 6 = gatekeeper). Each bezel detent moves the walk target a fraction of a
 * slot; landing on a slot is a doorway. When the bezel goes quiet between
 * doorways, the target snaps to the nearest one.
 */
class Walker(
    val slotCount: Int,
    startSlot: Int,
    val stepsPerSlot: Int = 4,
) {
    sealed interface Event {
        /** Moved one detent between doorways. */
        data object Step : Event

        /** Arrived at (or snapped to) a doorway. */
        data class Doorway(val slot: Int) : Event
    }

    var targetStep: Int = startSlot * stepsPerSlot
        private set

    /** Character position in slots, eased toward the target. */
    var position: Float = startSlot.toFloat()
        private set

    /** Slots per second, for walk animation. */
    var velocity: Float = 0f
        private set

    private var idleSeconds = 0f
    private var lastDirection = 1

    private val maxStep get() = (slotCount - 1) * stepsPerSlot
    val targetPosition: Float get() = targetStep.toFloat() / stepsPerSlot

    /** The slot the character is standing at, or null while between doorways. */
    val doorSlot: Int?
        get() = if (targetStep % stepsPerSlot == 0 && abs(position - targetPosition) < DOOR_TOLERANCE) {
            targetStep / stepsPerSlot
        } else {
            null
        }

    fun onDetents(detents: Int): List<Event> {
        if (detents == 0) return emptyList()
        idleSeconds = 0f
        val dir = detents.sign
        lastDirection = dir
        val events = mutableListOf<Event>()
        repeat(abs(detents)) {
            val next = (targetStep + dir).coerceIn(0, maxStep)
            if (next == targetStep) return events
            targetStep = next
            events += if (next % stepsPerSlot == 0) Event.Doorway(next / stepsPerSlot) else Event.Step
        }
        return events
    }

    /** Advances the walk; returns a Doorway event if the target snapped this frame. */
    fun update(dt: Float): Event? {
        if (dt <= 0f) return null
        idleSeconds += dt
        var event: Event? = null
        val remainder = targetStep % stepsPerSlot
        if (remainder != 0 && idleSeconds >= SNAP_DELAY) {
            val base = targetStep - remainder
            val goUp = remainder * 2 > stepsPerSlot || (remainder * 2 == stepsPerSlot && lastDirection > 0)
            targetStep = if (goUp) base + stepsPerSlot else base
            event = Event.Doorway(targetStep / stepsPerSlot)
        }

        val diff = targetPosition - position
        var move = diff * min(1f, dt * FOLLOW_RATE)
        val maxMove = MAX_SPEED * dt
        if (abs(move) > maxMove) move = maxMove * move.sign
        position += move
        if (abs(targetPosition - position) < 0.001f) position = targetPosition
        velocity = move / dt
        return event
    }

    companion object {
        const val SNAP_DELAY = 0.35f
        const val FOLLOW_RATE = 8f
        const val MAX_SPEED = 4f
        const val DOOR_TOLERANCE = 0.2f
    }
}
