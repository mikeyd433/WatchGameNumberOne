package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.content.Settings
import com.mikeyd.bezeltown.core.economy.Economy
import com.mikeyd.bezeltown.core.economy.Portal
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.GlobalUpgrade
import com.mikeyd.bezeltown.core.model.MicrogameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalTest {
    private val town = Settings.SMALL_TOWN

    private fun allUnlocked(money: Double): GameState {
        var s = Settings.newGame().withMoney(money)
        for (id in BuildingId.entries) s = s.withBuilding(id) { it.copy(unlocked = true, automationLevel = 3, credits = 50) }
        return s
    }

    @Test fun priceStartsAt500KAndGrowsTenfoldPerLoop() {
        val s = Settings.newGame()
        assertEquals(500_000.0, Portal.threshold(s, town), 0.0)
        assertEquals(5_000_000.0, Portal.threshold(s.copy(settingLoop = 1), town), 0.0)
    }

    @Test fun cheaperGateTakes5PercentPerTier() {
        val s = Settings.newGame().copy(globalUpgrades = mapOf(GlobalUpgrade.CHEAPER_GATE to 2))
        assertEquals(450_000.0, Portal.threshold(s, town), 1e-6)
    }

    @Test fun leavingNeedsEveryBuildingAndThePrice() {
        assertFalse(Portal.canLeave(Settings.newGame().withMoney(1e9), town))
        assertFalse(Portal.canLeave(allUnlocked(499_999.0), town))
        assertTrue(Portal.canLeave(allUnlocked(500_000.0), town))
        assertNull(Portal.leave(allUnlocked(10.0), town))
    }

    @Test fun bonusAndTokensFollowTheOverflow() {
        // The spec's examples, scaled to a 500K price.
        assertEquals(0.0, Portal.bonusIfLeaving(allUnlocked(500_000.0), town), 1e-12)
        assertEquals(0.05, Portal.bonusIfLeaving(allUnlocked(1_000_000.0), town), 1e-12)
        assertEquals(0.10, Portal.bonusIfLeaving(allUnlocked(2_500_000.0), town), 1e-12)
        assertEquals(0L, Portal.tokensIfLeaving(allUnlocked(500_000.0), town))
        assertEquals(3L, Portal.tokensIfLeaving(allUnlocked(1_000_000.0), town))
        assertEquals(6L, Portal.tokensIfLeaving(allUnlocked(2_500_000.0), town))
    }

    @Test fun bonusRateRaisesTheCoefficient() {
        val s = allUnlocked(1_000_000.0).copy(globalUpgrades = mapOf(GlobalUpgrade.BONUS_RATE to 2))
        assertEquals(0.06, Portal.bonusIfLeaving(s, town), 1e-12)
    }

    @Test fun leavingResetsTheTownButKeepsProgress() {
        val before = allUnlocked(1_000_000.0).copy(
            totalBonus = 0.02,
            tokens = 4,
            personalBests = mapOf(MicrogameId.KNEAD to 90L),
        )
        val (after, report) = Portal.leave(before, town)!!
        assertEquals(0.05, report.bonus, 1e-12)
        assertEquals(3L, report.tokens)
        assertEquals(1, report.arrivingLoop)
        assertEquals(0.07, after.totalBonus, 1e-12)
        assertEquals(7L, after.tokens)
        assertEquals(1, after.settingLoop)
        assertEquals(0.0, after.setting.money, 0.0)
        assertTrue(after.building(BuildingId.BAKERY).unlocked)
        assertFalse(after.building(BuildingId.WATER_TOWER).unlocked)
        assertEquals(0, after.building(BuildingId.BAKERY).automationLevel)
        assertEquals(0L, after.building(BuildingId.BAKERY).credits)
        assertEquals(90L, after.personalBests[MicrogameId.KNEAD])
    }

    @Test fun headStartGivesStartingMoney() {
        val before = allUnlocked(1_000_000.0).copy(globalUpgrades = mapOf(GlobalUpgrade.HEAD_START to 2))
        val (after, _) = Portal.leave(before, town)!!
        assertEquals(2_000.0, after.setting.money, 0.0)
    }

    @Test fun bonusBoostsIncomeAfterLeaving() {
        val (after, _) = Portal.leave(allUnlocked(1_000_000.0), town)!!
        val busy = after.withBuilding(BuildingId.BAKERY) { it.copy(automationLevel = 1) }
        assertEquals(0.5 * 1.05, Economy.totalIncome(busy, town), 1e-12)
    }

    @Test fun buyingGlobalUpgrades() {
        val rich = Settings.newGame().copy(tokens = 10)
        val once = Portal.buy(rich, GlobalUpgrade.BONUS_RATE)!!
        assertEquals(5L, once.tokens)
        assertEquals(1, Portal.tier(once, GlobalUpgrade.BONUS_RATE))
        assertNull(Portal.buy(once, GlobalUpgrade.BONUS_RATE)) // next tier costs 8
        val gate = Portal.buy(once, GlobalUpgrade.CHEAPER_GATE)!!
        assertEquals(2L, gate.tokens)
        val maxed = rich.copy(tokens = 100, globalUpgrades = mapOf(GlobalUpgrade.CHEAPER_GATE to Portal.CHEAPER_GATE_MAX_TIER))
        assertNull(Portal.buy(maxed, GlobalUpgrade.CHEAPER_GATE))
    }

    @Test fun offlineStorageStretchesEveryBuildingsStorage() {
        val s = Settings.newGame()
            .withBuilding(BuildingId.BAKERY) { it.copy(automationLevel = 1) }
            .copy(globalUpgrades = mapOf(GlobalUpgrade.OFFLINE_STORAGE to 2))
        // Storage level 0 is 1 hour; two tiers make it 2 hours.
        val (_, report) = Economy.offlinePayout(s, town, 3 * 3600.0)
        assertEquals(0.5 * 2 * 3600, report.earned, 1e-9)
    }

    @Test fun paymentSpeedsUp() {
        val price = 500_000.0
        assertEquals(15_000.0, Portal.paymentPerDetent(0.0, price), 0.0)
        assertEquals(100_000.0, Portal.paymentPerDetent(1_000_000.0, price), 0.0)
        // Paying 10x the price takes about three turns of a 24-detent bezel.
        var paid = 0.0
        var detents = 0
        while (paid < 10 * price) {
            paid += Portal.paymentPerDetent(paid, price)
            detents++
        }
        assertTrue("took $detents detents", detents in 40..80)
    }

    @Test fun oldSavesLoadWithoutLoopField() {
        val full = SaveCodec.encode(Settings.newGame())
        val json = full.replace("\"settingLoop\":0,", "")
        assertTrue("field should have been removed", json.length < full.length)
        assertEquals(0, SaveCodec.decode(json).settingLoop)
    }
}
