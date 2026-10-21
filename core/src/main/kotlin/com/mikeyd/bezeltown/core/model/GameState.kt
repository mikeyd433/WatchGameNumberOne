package com.mikeyd.bezeltown.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class BuildingId { BAKERY, WATER_TOWER, BARBER_SHOP, RADIO_STATION, CLOCK_TOWER }

@Serializable
enum class MicrogameId { KNEAD, PUMP, BARBER, RADIO }

@Serializable
enum class UpgradeTrack { SKILL, AUTOMATION, STORAGE }

@Serializable
enum class GlobalUpgrade { BONUS_RATE, HEAD_START, CHEAPER_GATE, OFFLINE_STORAGE }

@Serializable
data class GameState(
    val currentSettingIndex: Int = 0,
    /** Running additive overflow bonus as a fraction (0.05 = +5%). */
    val totalBonus: Double = 0.0,
    val tokens: Long = 0,
    val globalUpgrades: Map<GlobalUpgrade, Int> = emptyMap(),
    val personalBests: Map<MicrogameId, Long> = emptyMap(),
    /** Epoch millis of the last save. */
    val lastSeen: Long = 0,
    val setting: SettingState,
) {
    fun building(id: BuildingId): BuildingState = setting.buildings.first { it.id == id }

    fun withBuilding(id: BuildingId, change: (BuildingState) -> BuildingState): GameState =
        copy(setting = setting.copy(buildings = setting.buildings.map { if (it.id == id) change(it) else it }))

    fun withMoney(money: Double): GameState = copy(setting = setting.copy(money = money))
}

@Serializable
data class SettingState(
    val money: Double = 0.0,
    val buildings: List<BuildingState>,
)

@Serializable
data class BuildingState(
    val id: BuildingId,
    val unlocked: Boolean = false,
    val credits: Long = 0,
    val skillLevel: Int = 0,
    val automationLevel: Int = 0,
    val storageLevel: Int = 0,
    val banked: Double = 0.0,
) {
    fun level(track: UpgradeTrack): Int = when (track) {
        UpgradeTrack.SKILL -> skillLevel
        UpgradeTrack.AUTOMATION -> automationLevel
        UpgradeTrack.STORAGE -> storageLevel
    }

    fun withLevel(track: UpgradeTrack, level: Int): BuildingState = when (track) {
        UpgradeTrack.SKILL -> copy(skillLevel = level)
        UpgradeTrack.AUTOMATION -> copy(automationLevel = level)
        UpgradeTrack.STORAGE -> copy(storageLevel = level)
    }
}
