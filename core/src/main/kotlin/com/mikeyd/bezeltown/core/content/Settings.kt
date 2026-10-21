package com.mikeyd.bezeltown.core.content

import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.BuildingState
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.MicrogameId
import com.mikeyd.bezeltown.core.model.SettingState
import com.mikeyd.bezeltown.core.model.UpgradeTrack
import kotlin.math.ceil
import kotlin.math.pow

/** Cost of an upgrade track: base × growth^level, in that building's credits. */
data class TrackCost(val base: Double, val growth: Double) {
    fun at(level: Int): Long = ceil(base * growth.pow(level)).toLong()
}

/**
 * Static tuning for one building. Numbers are per microgame, not global: harder
 * games pay more credits per play.
 */
data class BuildingDef(
    val id: BuildingId,
    val name: String,
    /** Money to unlock. 0 means unlocked from the start. */
    val unlockCost: Double,
    /** Null while the building's microgame isn't built yet. */
    val microgame: MicrogameId?,
    /** Credits for a perfect (100) score at skill level 0. */
    val creditsPerPlay: Double = 0.0,
    /** Money per second at automation level 1. */
    val incomePerLevel: Double = 0.0,
    val costs: Map<UpgradeTrack, TrackCost> = emptyMap(),
    /** Hours of output banked at storage level 0. */
    val storageBaseHours: Double = 1.0,
)

data class SettingDef(
    val name: String,
    val portalThreshold: Double,
    val buildings: List<BuildingDef>,
) {
    fun building(id: BuildingId): BuildingDef = buildings.first { it.id == id }
}

object Settings {
    val SMALL_TOWN = SettingDef(
        name = "Small Town",
        portalThreshold = 500_000.0,
        buildings = listOf(
            BuildingDef(
                id = BuildingId.BAKERY,
                name = "Bakery",
                unlockCost = 0.0,
                microgame = MicrogameId.KNEAD,
                creditsPerPlay = 8.0,
                incomePerLevel = 0.5,
                costs = mapOf(
                    UpgradeTrack.SKILL to TrackCost(10.0, 1.6),
                    UpgradeTrack.AUTOMATION to TrackCost(8.0, 1.55),
                    UpgradeTrack.STORAGE to TrackCost(12.0, 1.5),
                ),
            ),
            BuildingDef(
                id = BuildingId.WATER_TOWER,
                name = "Water Tower",
                unlockCost = 150.0,
                microgame = MicrogameId.PUMP,
                creditsPerPlay = 12.0,
                incomePerLevel = 2.0,
                costs = mapOf(
                    UpgradeTrack.SKILL to TrackCost(12.0, 1.6),
                    UpgradeTrack.AUTOMATION to TrackCost(10.0, 1.55),
                    UpgradeTrack.STORAGE to TrackCost(15.0, 1.5),
                ),
            ),
            BuildingDef(
                id = BuildingId.BARBER_SHOP,
                name = "Barber Shop",
                unlockCost = 2_000.0,
                microgame = MicrogameId.BARBER,
                creditsPerPlay = 14.0,
                incomePerLevel = 8.0,
                costs = mapOf(
                    UpgradeTrack.SKILL to TrackCost(25.0, 1.6),
                    UpgradeTrack.AUTOMATION to TrackCost(20.0, 1.55),
                    UpgradeTrack.STORAGE to TrackCost(30.0, 1.5),
                ),
            ),
            BuildingDef(
                id = BuildingId.RADIO_STATION,
                name = "Radio Station",
                unlockCost = 20_000.0,
                microgame = MicrogameId.RADIO,
                creditsPerPlay = 20.0,
                incomePerLevel = 24.0,
                costs = mapOf(
                    UpgradeTrack.SKILL to TrackCost(40.0, 1.6),
                    UpgradeTrack.AUTOMATION to TrackCost(35.0, 1.55),
                    UpgradeTrack.STORAGE to TrackCost(50.0, 1.5),
                ),
            ),
            BuildingDef(
                id = BuildingId.CLOCK_TOWER,
                name = "Clock Tower",
                unlockCost = 200_000.0,
                microgame = MicrogameId.CLOCK,
                creditsPerPlay = 28.0,
                incomePerLevel = 72.0,
                costs = mapOf(
                    UpgradeTrack.SKILL to TrackCost(60.0, 1.6),
                    UpgradeTrack.AUTOMATION to TrackCost(55.0, 1.55),
                    UpgradeTrack.STORAGE to TrackCost(80.0, 1.5),
                ),
            ),
        ),
    )

    val all: List<SettingDef> = listOf(SMALL_TOWN)

    fun current(state: GameState): SettingDef = all[state.currentSettingIndex.coerceIn(all.indices)]

    fun freshSetting(def: SettingDef): SettingState = SettingState(
        money = 0.0,
        buildings = def.buildings.map { BuildingState(id = it.id, unlocked = it.unlockCost <= 0.0) },
    )

    fun newGame(): GameState = GameState(setting = freshSetting(SMALL_TOWN))

    /**
     * Repairs a loaded save against the current content: adds buildings that
     * didn't exist when it was written and drops ones that no longer do.
     */
    fun normalize(state: GameState): GameState {
        val def = current(state)
        val existing = state.setting.buildings.associateBy { it.id }
        val buildings = def.buildings.map { b ->
            existing[b.id] ?: BuildingState(id = b.id, unlocked = b.unlockCost <= 0.0)
        }
        return state.copy(setting = state.setting.copy(buildings = buildings))
    }
}
