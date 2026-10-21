package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.content.Settings
import com.mikeyd.bezeltown.core.economy.Economy
import com.mikeyd.bezeltown.core.economy.PlayMode
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.MicrogameId
import com.mikeyd.bezeltown.core.model.UpgradeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyTest {
    private val town = Settings.SMALL_TOWN

    @Test fun newGameHasOnlyBakeryUnlocked() {
        val state = Settings.newGame()
        assertTrue(state.building(BuildingId.BAKERY).unlocked)
        assertFalse(state.building(BuildingId.WATER_TOWER).unlocked)
        assertEquals(5, state.setting.buildings.size)
        assertEquals(0.0, Economy.totalIncome(state, town), 0.0)
    }

    @Test fun storyPlayEarnsCreditsForThatBuildingOnly() {
        val state = Settings.newGame()
        val outcome = Economy.recordPlay(state, town, BuildingId.BAKERY, MicrogameId.KNEAD, 100, PlayMode.STORY)
        assertEquals(8L, outcome.creditsEarned)
        assertEquals(8L, outcome.state.building(BuildingId.BAKERY).credits)
        assertEquals(0L, outcome.state.building(BuildingId.WATER_TOWER).credits)
        assertEquals(0.0, outcome.state.setting.money, 0.0)
        assertTrue(outcome.newBest)
    }

    @Test fun arcadePlayOnlyTracksBest() {
        val state = Settings.newGame()
        val outcome = Economy.recordPlay(state, town, BuildingId.BAKERY, MicrogameId.KNEAD, 80, PlayMode.ARCADE)
        assertEquals(0L, outcome.creditsEarned)
        assertEquals(0L, outcome.state.building(BuildingId.BAKERY).credits)
        assertEquals(80L, outcome.state.personalBests[MicrogameId.KNEAD])
    }

    @Test fun skillRaisesCreditsPerPlay() {
        val def = town.building(BuildingId.BAKERY)
        assertEquals(8L, Economy.creditsForScore(def, 100, 0))
        assertEquals(16L, Economy.creditsForScore(def, 100, 2))
        assertEquals(1L, Economy.creditsForScore(def, 1, 0))
        assertEquals(0L, Economy.creditsForScore(def, 0, 5))
    }

    @Test fun upgradeSpendsCreditsAndRaisesLevel() {
        var state = Settings.newGame().withBuilding(BuildingId.BAKERY) { it.copy(credits = 20) }
        state = Economy.upgrade(state, town, BuildingId.BAKERY, UpgradeTrack.AUTOMATION)!!
        val bakery = state.building(BuildingId.BAKERY)
        assertEquals(1, bakery.automationLevel)
        assertEquals(12L, bakery.credits)
        assertEquals(0.5, Economy.totalIncome(state, town), 1e-9)
    }

    @Test fun cannotUpgradeWithoutCreditsOrWhileLocked() {
        val state = Settings.newGame()
        assertNull(Economy.upgrade(state, town, BuildingId.BAKERY, UpgradeTrack.SKILL))
        val rich = state.withBuilding(BuildingId.WATER_TOWER) { it.copy(credits = 1000) }
        assertNull(Economy.upgrade(rich, town, BuildingId.WATER_TOWER, UpgradeTrack.SKILL))
    }

    @Test fun tickAccruesMoneyFromAutomation() {
        val state = Settings.newGame().withBuilding(BuildingId.BAKERY) { it.copy(automationLevel = 2) }
        val after = Economy.tick(state, town, 10.0)
        assertEquals(0.5 * 2 * 1.1 * 10, after.setting.money, 1e-9)
    }

    @Test fun bonusMultipliesIncome() {
        val state = Settings.newGame().copy(totalBonus = 0.1).withBuilding(BuildingId.BAKERY) { it.copy(automationLevel = 1) }
        assertEquals(0.55, Economy.totalIncome(state, town), 1e-9)
    }

    @Test fun unlockCostsMoney() {
        val poor = Settings.newGame()
        assertNull(Economy.unlock(poor, town, BuildingId.WATER_TOWER))
        val rich = poor.withMoney(200.0)
        val unlocked = Economy.unlock(rich, town, BuildingId.WATER_TOWER)
        assertNotNull(unlocked)
        unlocked!!
        assertTrue(unlocked.building(BuildingId.WATER_TOWER).unlocked)
        assertEquals(50.0, unlocked.setting.money, 1e-9)
        assertNull(Economy.unlock(unlocked, town, BuildingId.WATER_TOWER))
    }

    @Test fun unbuiltBuildingsCannotBeUnlocked() {
        val rich = Settings.newGame().withMoney(1e9)
        assertNull(Economy.unlock(rich, town, BuildingId.CLOCK_TOWER))
    }

    @Test fun upgradeCostsGrow() {
        val def = town.building(BuildingId.BAKERY)
        val costs = (0..5).map { Economy.upgradeCost(def, UpgradeTrack.SKILL, it) }
        assertEquals(10L, costs[0])
        assertTrue(costs.zipWithNext().all { (a, b) -> b > a })
    }

    @Test fun offlinePayoutEarnsWhileAway() {
        val state = Settings.newGame().withBuilding(BuildingId.BAKERY) { it.copy(automationLevel = 1) }
        val (after, report) = Economy.offlinePayout(state, town, 600.0)
        assertEquals(0.5 * 600, report.earned, 1e-9)
        assertEquals(300.0, after.setting.money, 1e-9)
        assertFalse(report.storageFilled)
    }

    @Test fun offlinePayoutStopsAtStorage() {
        val state = Settings.newGame().withBuilding(BuildingId.BAKERY) { it.copy(automationLevel = 1) }
        // Storage level 0 holds 1 hour; away for 3 hours.
        val (_, report) = Economy.offlinePayout(state, town, 3 * 3600.0)
        assertEquals(0.5 * 3600, report.earned, 1e-9)
        assertTrue(report.storageFilled)

        val bigger = state.withBuilding(BuildingId.BAKERY) { it.copy(storageLevel = 2) }
        val (_, report2) = Economy.offlinePayout(bigger, town, 3 * 3600.0)
        assertEquals(0.5 * 3 * 3600, report2.earned, 1e-9)
        assertFalse(report2.storageFilled)
    }

    @Test fun offlinePayoutIgnoresLockedAndIdleBuildings() {
        val state = Settings.newGame().withBuilding(BuildingId.WATER_TOWER) { it.copy(automationLevel = 5) }
        val (after, report) = Economy.offlinePayout(state, town, 1000.0)
        assertEquals(0.0, report.earned, 0.0)
        assertEquals(state, after)
    }

    @Test fun saveRoundTripsAndNormalizes() {
        val state = Settings.newGame().withMoney(42.0).copy(personalBests = mapOf(MicrogameId.PUMP to 77L))
        assertEquals(state, SaveCodec.decode(SaveCodec.encode(state)))

        val trimmed = state.copy(setting = state.setting.copy(buildings = state.setting.buildings.take(2)))
        val repaired = SaveCodec.decode(SaveCodec.encode(trimmed))
        assertEquals(5, repaired.setting.buildings.size)
        assertEquals(42.0, repaired.setting.money, 0.0)
    }
}
