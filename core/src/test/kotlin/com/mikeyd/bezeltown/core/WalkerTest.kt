package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.overworld.Walker
import com.mikeyd.bezeltown.core.overworld.Walker.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkerTest {
    private fun settle(w: Walker, seconds: Float = 3f): List<Event> {
        val events = mutableListOf<Event>()
        var t = 0f
        while (t < seconds) {
            w.update(1f / 60f)?.let(events::add)
            t += 1f / 60f
        }
        return events
    }

    @Test fun sixDetentsReachNextDoor() {
        val w = Walker(slotCount = 7, startSlot = 1)
        val events = w.onDetents(6)
        assertEquals(List(5) { Event.Step } + Event.Doorway(2), events)
        settle(w)
        assertEquals(2, w.doorSlot)
        assertEquals(2f, w.position, 0f)
    }

    @Test fun walkingBackwards() {
        val w = Walker(slotCount = 7, startSlot = 3)
        assertEquals(Event.Doorway(2), w.onDetents(-6).last())
    }

    @Test fun clampsAtEnds() {
        val w = Walker(slotCount = 7, startSlot = 0)
        assertEquals(emptyList<Event>(), w.onDetents(-3))
        w.onDetents(100)
        assertEquals(36, w.targetStep)
    }

    @Test fun oneDetentShortOfADoorFinishesTheWalk() {
        val w = Walker(slotCount = 7, startSlot = 1)
        w.onDetents(5)
        assertNull(w.doorSlot)
        assertEquals(listOf<Event>(Event.Doorway(2)), settle(w))
        assertEquals(2, w.doorSlot)
    }

    @Test fun oneDetentPastADoorStepsBack() {
        val w = Walker(slotCount = 7, startSlot = 1)
        w.onDetents(1)
        settle(w)
        assertEquals(1, w.doorSlot)
    }

    @Test fun stoppingBetweenDoorsStaysPut() {
        for (detents in 2..4) {
            val w = Walker(slotCount = 7, startSlot = 1)
            w.onDetents(detents)
            assertEquals(emptyList<Event>(), settle(w))
            assertNull(w.doorSlot)
            assertEquals(1f + detents / 6f, w.position, 1e-4f)
        }
    }

    @Test fun noDoorWhileStillWalking() {
        val w = Walker(slotCount = 7, startSlot = 1)
        w.onDetents(6)
        w.update(1f / 60f)
        assertNull(w.doorSlot)
    }
}
