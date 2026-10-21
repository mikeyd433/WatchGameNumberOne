package com.mikeyd.bezeltown.core.economy

import com.mikeyd.bezeltown.core.content.BuildingDef
import com.mikeyd.bezeltown.core.content.SettingDef
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.GlobalUpgrade
import com.mikeyd.bezeltown.core.model.MicrogameId
import com.mikeyd.bezeltown.core.model.UpgradeTrack
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

/** Story plays pay out; arcade plays only count toward personal bests. */
enum class PlayMode { STORY, ARCADE }

/** What the buildings earned while the game was closed. */
data class AwayReport(
    val seconds: Double,
    val earned: Double,
    /** True if at least one building filled its storage and stopped earning. */
    val storageFilled: Boolean,
)

data class PlayOutcome(
    val state: GameState,
    val creditsEarned: Long,
    val newBest: Boolean,
)

/**
 * The economy layer. Microgames only report a 0–100 score; everything a score
 * is worth is decided here.
 */
object Economy {
    fun skillMultiplier(skillLevel: Int): Double = 1.0 + 0.5 * skillLevel

    fun creditsForScore(def: BuildingDef, score: Int, skillLevel: Int): Long {
        if (score <= 0) return 0
        val raw = def.creditsPerPlay * (score.coerceAtMost(100) / 100.0) * skillMultiplier(skillLevel)
        return max(1L, raw.roundToLong())
    }

    fun incomePerSecond(def: BuildingDef, automationLevel: Int, totalBonus: Double): Double {
        if (automationLevel <= 0) return 0.0
        return def.incomePerLevel * automationLevel * 1.1.pow(automationLevel - 1) * (1.0 + totalBonus)
    }

    /**
     * Hours of output a building can bank while the player is away. The Offline
     * Storage global upgrade adds 50% per tier on top.
     */
    fun storageHours(def: BuildingDef, storageLevel: Int, offlineStorageTier: Int = 0): Double =
        def.storageBaseHours * (1 + storageLevel) * (1 + 0.5 * offlineStorageTier)

    fun upgradeCost(def: BuildingDef, track: UpgradeTrack, level: Int): Long =
        def.costs.getValue(track).at(level)

    fun totalIncome(state: GameState, setting: SettingDef): Double =
        state.setting.buildings.filter { it.unlocked }.sumOf {
            incomePerSecond(setting.building(it.id), it.automationLevel, state.totalBonus)
        }

    fun tick(state: GameState, setting: SettingDef, seconds: Double): GameState {
        if (seconds <= 0.0) return state
        val income = totalIncome(state, setting)
        if (income == 0.0) return state
        return state.withMoney(state.setting.money + income * seconds)
    }

    /**
     * Pays out what each building earned over [seconds] away, each capped at
     * its Storage (hours of its own output).
     */
    fun offlinePayout(state: GameState, setting: SettingDef, seconds: Double): Pair<GameState, AwayReport> {
        if (seconds <= 0.0) return state to AwayReport(0.0, 0.0, storageFilled = false)
        var earned = 0.0
        var filled = false
        val offlineTier = state.globalUpgrades[GlobalUpgrade.OFFLINE_STORAGE] ?: 0
        for (b in state.setting.buildings) {
            if (!b.unlocked) continue
            val def = setting.building(b.id)
            val rate = incomePerSecond(def, b.automationLevel, state.totalBonus)
            if (rate <= 0.0) continue
            val capSeconds = storageHours(def, b.storageLevel, offlineTier) * 3600.0
            if (seconds > capSeconds) filled = true
            earned += rate * minOf(seconds, capSeconds)
        }
        val next = if (earned > 0.0) state.withMoney(state.setting.money + earned) else state
        return next to AwayReport(seconds, earned, filled)
    }

    fun portalProgress(state: GameState, setting: SettingDef): Double =
        (state.setting.money / Portal.threshold(state, setting)).coerceIn(0.0, 1.0)

    fun canUnlock(state: GameState, setting: SettingDef, id: BuildingId): Boolean {
        val def = setting.building(id)
        return def.microgame != null && !state.building(id).unlocked && state.setting.money >= def.unlockCost
    }

    /** Returns the new state, or null if the building can't be unlocked yet. */
    fun unlock(state: GameState, setting: SettingDef, id: BuildingId): GameState? {
        if (!canUnlock(state, setting, id)) return null
        val def = setting.building(id)
        return state.withMoney(state.setting.money - def.unlockCost).withBuilding(id) { it.copy(unlocked = true) }
    }

    /** Returns the new state, or null if the building can't afford the upgrade. */
    fun upgrade(state: GameState, setting: SettingDef, id: BuildingId, track: UpgradeTrack): GameState? {
        val building = state.building(id)
        if (!building.unlocked) return null
        val cost = upgradeCost(setting.building(id), track, building.level(track))
        if (building.credits < cost) return null
        return state.withBuilding(id) {
            it.withLevel(track, it.level(track) + 1).copy(credits = it.credits - cost)
        }
    }

    fun recordPlay(
        state: GameState,
        setting: SettingDef,
        id: BuildingId,
        game: MicrogameId,
        score: Int,
        mode: PlayMode,
    ): PlayOutcome {
        val best = state.personalBests[game] ?: 0L
        val newBest = score > best
        var next = if (newBest) state.copy(personalBests = state.personalBests + (game to score.toLong())) else state
        var earned = 0L
        if (mode == PlayMode.STORY) {
            val building = state.building(id)
            earned = creditsForScore(setting.building(id), score, building.skillLevel)
            next = next.withBuilding(id) { it.copy(credits = it.credits + earned) }
        }
        return PlayOutcome(next, earned, newBest)
    }
}
