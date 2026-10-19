package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.microgame.KneadGame
import com.mikeyd.bezeltown.core.microgame.PumpGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrogameTest {
    @Test fun kneadingFinishesWithTimeBonus() {
        val g = KneadGame()
        g.update(1f)
        var dir = 1f
        while (!g.finished) {
            g.onDrag(0f, 0f, 0.5f * dir, 0f)
            dir = -dir
        }
        assertTrue(g.succeeded)
        assertEquals(KneadGame.TARGET_KNEADS, g.kneads)
        // 1s of 7s used: 60 + 40 * 6/7
        assertEquals(94, g.score)
    }

    @Test fun swipesOffTheDoughDontCount() {
        val g = KneadGame()
        repeat(50) { g.onDrag(0.95f, 0.95f, 0.5f, 0f) }
        assertEquals(0, g.kneads)
    }

    @Test fun kneadTimeoutGivesPartialScore() {
        val g = KneadGame()
        repeat(10) { g.onDrag(0f, 0f, 0.25f, 0f) } // 2.5 travel = 5 kneads
        g.update(10f)
        assertTrue(g.finished)
        assertFalse(g.succeeded)
        assertEquals(20, g.score) // 60 * 5/15
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
