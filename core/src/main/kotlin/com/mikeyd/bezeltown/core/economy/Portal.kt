package com.mikeyd.bezeltown.core.economy

import com.mikeyd.bezeltown.core.content.SettingDef
import com.mikeyd.bezeltown.core.content.Settings
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.GlobalUpgrade
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** What leaving through the portal earned. */
data class LeaveReport(
    /** Added to the running bonus, as a fraction (0.05 = +5%). */
    val bonus: Double,
    val tokens: Long,
    /** The loop number of the setting you arrive in (1 = first time back). */
    val arrivingLoop: Int,
)

/**
 * The gatekeeper's portal and the permanent progression around it: the price to
 * leave, the overflow bonus and tokens it pays, and the global upgrades tokens buy.
 *
 * Only Small Town exists so far, so the portal loops back to a fresh Small Town,
 * ten times pricier each trip.
 */
object Portal {
    /** Base coefficient in `bonus % = coefficient × √(overflow ÷ price)`. */
    const val BASE_BONUS_COEFFICIENT = 5.0
    const val BONUS_RATE_STEP = 0.5
    /** Tokens = round(TOKEN_SCALE × √(overflow ÷ price)): doubling the price earns 3. */
    const val TOKEN_SCALE = 3.0
    const val LOOP_PRICE_MULTIPLIER = 10.0
    const val CHEAPER_GATE_STEP = 0.05
    const val CHEAPER_GATE_MAX_TIER = 8
    const val HEAD_START_BASE = 500.0
    const val HEAD_START_GROWTH = 4.0
    /** Each detent pays at least this fraction of the price... */
    const val PAYMENT_MIN_FRACTION = 0.03
    /** ...or this fraction of what's already paid, whichever is more, so it speeds up. */
    const val PAYMENT_GROWTH = 0.1

    fun tier(state: GameState, upgrade: GlobalUpgrade): Int = state.globalUpgrades[upgrade] ?: 0

    /** The price to leave the current setting. */
    fun threshold(state: GameState, setting: SettingDef): Double {
        val gate = 1.0 - CHEAPER_GATE_STEP * tier(state, GlobalUpgrade.CHEAPER_GATE)
        return setting.portalThreshold * LOOP_PRICE_MULTIPLIER.pow(state.settingLoop) * gate
    }

    fun allBuildingsUnlocked(state: GameState): Boolean = state.setting.buildings.all { it.unlocked }

    fun canLeave(state: GameState, setting: SettingDef): Boolean =
        allBuildingsUnlocked(state) && state.setting.money >= threshold(state, setting)

    private fun overflowRatio(state: GameState, setting: SettingDef): Double {
        val price = threshold(state, setting)
        return max(0.0, state.setting.money - price) / price
    }

    fun bonusCoefficient(state: GameState): Double =
        BASE_BONUS_COEFFICIENT + BONUS_RATE_STEP * tier(state, GlobalUpgrade.BONUS_RATE)

    /** The bonus leaving now would add, as a fraction (0.05 = +5%). */
    fun bonusIfLeaving(state: GameState, setting: SettingDef): Double =
        bonusCoefficient(state) * sqrt(overflowRatio(state, setting)) / 100.0

    fun tokensIfLeaving(state: GameState, setting: SettingDef): Long =
        (TOKEN_SCALE * sqrt(overflowRatio(state, setting))).roundToLong()

    /** Money one detent of cranking feeds the gatekeeper, given what's been paid so far. */
    fun paymentPerDetent(paid: Double, price: Double): Double = max(price * PAYMENT_MIN_FRACTION, paid * PAYMENT_GROWTH)

    /** Money each new visit to a setting starts with. */
    fun headStartMoney(state: GameState): Double {
        val t = tier(state, GlobalUpgrade.HEAD_START)
        return if (t == 0) 0.0 else HEAD_START_BASE * HEAD_START_GROWTH.pow(t - 1)
    }

    /**
     * Pays everything to the gatekeeper and steps through. Money, credits and
     * buildings reset; the bonus, tokens, global upgrades and bests carry over.
     * Returns null if the requirements aren't met.
     */
    fun leave(state: GameState, setting: SettingDef): Pair<GameState, LeaveReport>? {
        if (!canLeave(state, setting)) return null
        val bonus = bonusIfLeaving(state, setting)
        val tokens = tokensIfLeaving(state, setting)
        val arriving = state.copy(
            totalBonus = state.totalBonus + bonus,
            tokens = state.tokens + tokens,
            settingLoop = state.settingLoop + 1,
            setting = Settings.freshSetting(setting),
        )
        val next = arriving.withMoney(headStartMoney(arriving))
        return next to LeaveReport(bonus, tokens, next.settingLoop)
    }

    fun maxTier(upgrade: GlobalUpgrade): Int = when (upgrade) {
        GlobalUpgrade.CHEAPER_GATE -> CHEAPER_GATE_MAX_TIER
        else -> Int.MAX_VALUE
    }

    /** Token cost of the next tier. Bonus Rate is steep because it compounds with everything. */
    fun upgradeCost(upgrade: GlobalUpgrade, currentTier: Int): Long = when (upgrade) {
        GlobalUpgrade.BONUS_RATE -> 5L + 3L * currentTier
        else -> 3L + currentTier
    }

    /** Returns the new state, or null if it can't be afforded or is maxed out. */
    fun buy(state: GameState, upgrade: GlobalUpgrade): GameState? {
        val current = tier(state, upgrade)
        if (current >= maxTier(upgrade)) return null
        val cost = upgradeCost(upgrade, current)
        if (state.tokens < cost) return null
        return state.copy(tokens = state.tokens - cost, globalUpgrades = state.globalUpgrades + (upgrade to current + 1))
    }
}
