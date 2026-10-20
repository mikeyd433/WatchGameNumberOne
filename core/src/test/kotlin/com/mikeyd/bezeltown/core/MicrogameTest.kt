package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.microgame.KneadGame
import com.mikeyd.bezeltown.core.microgame.PumpGame
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
        // 1s of 8s used: 60 + 40 * 7/8
        assertEquals(95, g.score)
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
        // 0.5s used: 60 + round(40 * 7.5/8) = 98, minus 4 for the mistake.
        assertEquals(94, g.score)
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
}
