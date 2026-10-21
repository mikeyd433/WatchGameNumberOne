package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.microgame.BarberGame
import com.mikeyd.bezeltown.core.microgame.ClockGame
import com.mikeyd.bezeltown.core.microgame.KneadGame
import com.mikeyd.bezeltown.core.microgame.PumpGame
import com.mikeyd.bezeltown.core.microgame.RadioGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MicrogameTest {
    /** Swipes in [d] far enough to count, in a few drag steps like a real finger. */
    private fun swipe(g: KneadGame, d: KneadGame.Direction) {
        repeat(4) { g.onDrag(0f, 0f, d.x * 0.1f, d.y * 0.1f) }
        g.onDragEnd()
    }

    @Test fun clearingArrowsFinishesWithTimeBonus() {
        val g = KneadGame(Random(1))
        g.update(1f)
        while (!g.finished) swipe(g, g.arrow)
        assertTrue(g.succeeded)
        assertEquals(KneadGame.TARGET_ARROWS, g.cleared)
        assertEquals(0, g.mistakes)
        // Under par: full marks.
        assertEquals(100, g.score)
    }

    @Test fun kneadScoreSlidesFromParToTheBuzzer() {
        val g = KneadGame(Random(2))
        g.update(6.25f)
        while (!g.finished) swipe(g, g.arrow)
        // Halfway between par (4.5s) and the 8s limit: 60 + 40 * 0.5
        assertEquals(80, g.score)
    }

    @Test fun nextArrowAlwaysPointsSomewhereNew() {
        val g = KneadGame(Random(42))
        repeat(KneadGame.TARGET_ARROWS - 1) {
            val before = g.arrow
            swipe(g, before)
            assertNotEquals(before, g.arrow)
        }
    }

    @Test fun wrongSwipeCostsTimeAndScore() {
        val g = KneadGame(Random(3))
        val wrong = KneadGame.Direction.entries.first { it != g.arrow }
        swipe(g, wrong)
        assertEquals(1, g.mistakes)
        assertEquals(0, g.cleared)
        assertEquals(KneadGame.MISTAKE_PENALTY_SECONDS, g.elapsed, 1e-6f)
        while (!g.finished) swipe(g, g.arrow)
        // 0.5s used is under par, so 100, minus 4 for the mistake.
        assertEquals(96, g.score)
    }

    @Test fun shortWigglesDontCount() {
        val g = KneadGame(Random(5))
        repeat(20) {
            g.onDrag(0f, 0f, 0.1f, 0f)
            g.onDrag(0f, 0f, -0.1f, 0f)
        }
        assertEquals(0, g.cleared)
        assertEquals(0, g.mistakes)
    }

    @Test fun zigZagWithoutLiftingCountsEachSwipe() {
        val g = KneadGame(Random(7))
        repeat(3) {
            val d = g.arrow
            repeat(4) { g.onDrag(0f, 0f, d.x * 0.1f, d.y * 0.1f) }
        }
        assertEquals(3, g.cleared)
    }

    @Test fun kneadTimeoutGivesPartialScore() {
        val g = KneadGame(Random(9))
        repeat(6) { swipe(g, g.arrow) }
        g.update(10f)
        assertTrue(g.finished)
        assertFalse(g.succeeded)
        assertEquals(36, g.score) // 60 * 6/10
    }

    @Test fun steadyCrankingFillsTheTank() {
        val g = PumpGame()
        // 6 detents per second is inside the sweet spot.
        var t = 0f
        while (!g.finished) {
            g.update(1f / 6f)
            g.onDetents(1)
            t += 1f / 6f
        }
        assertTrue(g.succeeded)
        assertFalse(g.spraying)
        assertTrue(g.score > 60)
    }

    @Test fun crankingTooFastSprays() {
        val g = PumpGame()
        repeat(20) {
            g.update(0.02f)
            g.onDetents(1)
        }
        assertTrue(g.spraying)
        assertTrue(g.fill < 20f)
    }

    @Test fun gaugeStaysGreenWhenSteadyAndGoesRedWhenFast() {
        val steady = PumpGame()
        repeat(12) {
            steady.update(1f / 6f)
            steady.onDetents(1)
        }
        assertTrue(steady.pressure in 0.4f..1f)

        val fast = PumpGame()
        repeat(30) {
            fast.update(1f / 15f)
            fast.onDetents(1)
        }
        assertTrue(fast.pressure > 1f)
    }

    @Test fun pumpTimeoutScoresByFill() {
        val g = PumpGame()
        repeat(20) {
            g.update(0.2f)
            g.onDetents(1)
        }
        g.update(100f)
        assertFalse(g.succeeded)
        assertEquals(30, g.score) // half full
    }

    /** Turns the chair the short way to the mark, one detent at a time. */
    private fun dialToMark(g: BarberGame) {
        while (!g.aligned) {
            val diff = Math.floorMod(g.targetStep - g.chairStep, BarberGame.STEPS)
            g.onDetents(if (diff <= BarberGame.STEPS / 2) 1 else -1)
        }
    }

    @Test fun barberSnipsAfterHoldingOnTheMark() {
        val g = BarberGame(Random(4))
        assertFalse(g.aligned)
        dialToMark(g)
        g.update(0.2f)
        assertEquals(0, g.snips)
        g.update(0.2f)
        assertEquals(1, g.snips)
        assertFalse(g.aligned) // the mark moved on
    }

    @Test fun movingRestartsTheHold() {
        val g = BarberGame(Random(4))
        dialToMark(g)
        g.update(0.3f)
        g.onDetents(1)
        g.onDetents(-1)
        g.update(0.3f)
        assertEquals(0, g.snips)
        g.update(0.1f)
        assertEquals(1, g.snips)
    }

    @Test fun marksAreAlwaysAProperTurnAway() {
        val g = BarberGame(Random(11))
        repeat(BarberGame.SNIPS - 1) {
            val diff = Math.floorMod(g.targetStep - g.chairStep, BarberGame.STEPS)
            val turn = minOf(diff, BarberGame.STEPS - diff)
            assertTrue(turn in BarberGame.MIN_TURN..BarberGame.MAX_TURN)
            dialToMark(g)
            g.update(0.4f)
        }
    }

    @Test fun fullHaircutUnderParScores100() {
        val g = BarberGame(Random(8))
        while (!g.finished) {
            dialToMark(g)
            g.update(0.4f)
        }
        assertTrue(g.succeeded)
        assertEquals(BarberGame.SNIPS, g.snips)
        assertEquals(100, g.score) // 4 x 0.4s = 1.6s, well under par
    }

    @Test fun chairWrapsAroundFullCircle() {
        val g = BarberGame(Random(1))
        g.onDetents(-1)
        assertEquals(BarberGame.STEPS - 1, g.chairStep)
        g.onDetents(BarberGame.STEPS + 2)
        assertEquals(1, g.chairStep)
    }

    private fun tune(g: RadioGame) {
        while (!g.tuned) g.onDetents(if (g.targetStep > g.dialStep) 1 else -1)
    }

    @Test fun radioLocksAStationAfterHolding() {
        val g = RadioGame(Random(2))
        assertFalse(g.tuned)
        tune(g)
        assertEquals(1f, g.signal, 0f)
        g.update(0.3f)
        assertEquals(0, g.stations)
        g.update(0.3f)
        assertEquals(1, g.stations)
        assertFalse(g.tuned) // next station called out
    }

    @Test fun signalFadesWithDistance() {
        val g = RadioGame(Random(2))
        tune(g)
        g.onDetents(3)
        assertEquals(0.5f, g.signal, 1e-6f)
        g.onDetents(10)
        assertEquals(0f, g.signal, 0f)
    }

    @Test fun dialStopsAtTheEndsOfTheBand() {
        val g = RadioGame(Random(3))
        g.onDetents(-100)
        assertEquals(0, g.dialStep)
        g.onDetents(100)
        assertEquals(RadioGame.STEPS - 1, g.dialStep)
    }

    @Test fun stationsAreAlwaysAProperTurnAwayAndInBand() {
        for (seed in 0 until 50) {
            val g = RadioGame(Random(seed))
            repeat(RadioGame.STATIONS) {
                if (g.finished) return@repeat
                assertTrue(g.targetStep in 0 until RadioGame.STEPS)
                assertTrue(g.distance in RadioGame.MIN_TURN..RadioGame.MAX_TURN)
                tune(g)
                g.update(0.6f)
            }
            assertTrue(g.succeeded)
        }
    }

    @Test fun radioLabels() {
        assertEquals("88.0", RadioGame.label(0))
        assertEquals("94.5", RadioGame.label(13))
        assertEquals("108.0", RadioGame.label(RadioGame.STEPS - 1))
    }

    /** A touch at clock position [pos] (0 = twelve), [radius] out from the center. */
    private fun touchAt(pos: Int, radius: Float): Pair<Float, Float> {
        val a = pos * Math.PI / 6
        return (radius * Math.sin(a)).toFloat() to (radius * Math.cos(a)).toFloat()
    }

    private fun drag(g: ClockGame, from: Pair<Float, Float>, to: Pair<Float, Float>) {
        g.onDrag(from.first, from.second, 0f, 0f)
        g.onDrag(to.first, to.second, 0f, 0f)
        g.onDragEnd()
    }

    private fun windUp(g: ClockGame) {
        g.onDetents(ClockGame.WIND_FULL)
        g.update(0.4f)
    }

    @Test fun clockWindsThenLocksWhenYouStop() {
        val g = ClockGame(Random(1))
        g.onDetents(-10) // ratchet: no effect
        assertEquals(0, g.wind)
        g.onDetents(20)
        g.update(1f)
        assertEquals(ClockGame.Phase.WIND, g.phase) // not full yet
        g.onDetents(16)
        g.update(0.1f)
        assertEquals(ClockGame.Phase.WIND, g.phase) // full but still settling
        g.update(0.3f)
        assertEquals(ClockGame.Phase.SET, g.phase)
    }

    @Test fun overwindingSproings() {
        val g = ClockGame(Random(1))
        g.onDetents(ClockGame.WIND_FULL + ClockGame.OVERWIND_LIMIT)
        assertEquals(0, g.sproings) // right at the limit is fine
        g.onDetents(1)
        assertEquals(1, g.sproings)
        assertEquals(ClockGame.WIND_FULL - ClockGame.SPROING_SETBACK, g.wind)
        assertEquals(ClockGame.SPROING_PENALTY_SECONDS, g.elapsed, 1e-6f)
        g.update(0.5f)
        assertEquals(ClockGame.Phase.WIND, g.phase)
    }

    @Test fun handsCannotMoveWhileWinding() {
        val g = ClockGame(Random(1))
        drag(g, touchAt(0, 0.6f), touchAt(5, 0.6f))
        assertEquals(0, g.minuteHand)
    }

    @Test fun settingBothHandsRingsTheBell() {
        val g = ClockGame(Random(6))
        windUp(g)
        // Hands start together at twelve: a far-out touch grabs the minute hand first.
        drag(g, touchAt(0, 0.6f), touchAt(g.targetMinute, 0.6f))
        assertEquals(g.targetMinute, g.minuteHand)
        if (!g.finished) {
            val hourFrom = touchAt(g.hourHand, 0.3f)
            drag(g, hourFrom, touchAt(g.targetHour, 0.3f))
        }
        assertTrue(g.finished)
        assertTrue(g.succeeded)
        assertEquals(100, g.score) // well under par
    }

    @Test fun nearestHandIsTheOneGrabbed() {
        val g = ClockGame(Random(6))
        windUp(g)
        drag(g, touchAt(0, 0.6f), touchAt(6, 0.6f)) // minute hand to :30
        drag(g, touchAt(1, 0.3f), touchAt(3, 0.3f)) // nearer the hour hand at 12
        assertEquals(3, g.hourHand)
        assertEquals(6, g.minuteHand)
    }

    @Test fun clockTimeoutGivesPartialCredit() {
        val g = ClockGame(Random(2))
        g.onDetents(ClockGame.WIND_FULL / 2)
        g.update(20f)
        assertTrue(g.finished)
        assertFalse(g.succeeded)
        assertEquals(15, g.score) // 60 * (0.5 * 0.5)
    }

    @Test fun clockLabels() {
        assertEquals("12:00", ClockGame.label(0, 0))
        assertEquals("4:15", ClockGame.label(4, 3))
        assertEquals("11:45", ClockGame.label(11, 9))
        assertEquals(3, ClockGame.positionAt(1f, 0f))
        assertEquals(6, ClockGame.positionAt(0f, -1f))
        assertEquals(9, ClockGame.positionAt(-1f, 0f))
    }
}
