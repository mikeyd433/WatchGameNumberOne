package com.mikeyd.bezeltown.core.overworld

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * Bezel-driven walking along the overworld strip.
 *
 * Positions are measured in slots (0 = arcade cabinet, 1–5 = buildings,
 * 6 = gatekeeper). Each bezel detent moves the walk target a fraction of a
 * slot; landing on a slot is a doorway. You can stop anywhere between
 * doorways; only when the bezel goes quiet within [SNAP_RANGE] detents of a
 * door does the walker step the rest of the way to it.
 */
class Walker(
    val slotCount: Int,
    startSlot: Int,
    val stepsPerSlot: Int = 6,
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

    /** Jumps straight to a slot, e.g. arriving through the portal. */
    fun teleport(slot: Int) {
        targetStep = slot.coerceIn(0, slotCount - 1) * stepsPerSlot
        position = targetPosition
        velocity = 0f
        idleSeconds = 0f
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
            val toNext = stepsPerSlot - remainder
            val snapped = when {
                remainder < toNext -> if (remainder <= SNAP_RANGE) base else null
                toNext < remainder -> if (toNext <= SNAP_RANGE) base + stepsPerSlot else null
                // Exactly between two doors only matters if the range reaches the middle.
                remainder <= SNAP_RANGE -> if (lastDirection > 0) base + stepsPerSlot else base
                else -> null
            }
            if (snapped != null) {
                targetStep = snapped
                event = Event.Doorway(targetStep / stepsPerSlot)
            }
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
        /** Stopping this many detents or fewer from a door finishes the walk to it. */
        const val SNAP_RANGE = 1
        const val FOLLOW_RATE = 8f
        const val MAX_SPEED = 4f
        const val DOOR_TOLERANCE = 0.2f
    }
}
