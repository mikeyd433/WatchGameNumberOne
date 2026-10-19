package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikeyd.bezeltown.GameViewModel
import com.mikeyd.bezeltown.core.content.BuildingDef
import com.mikeyd.bezeltown.core.economy.Economy
import com.mikeyd.bezeltown.core.format.BigNumber
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.UpgradeTrack
import java.util.Locale

/** The building's three upgrade tracks, paid for with its own credits. The bezel scrolls the list. */
@Composable
fun UpgradeScreen(id: BuildingId, state: GameState, viewModel: GameViewModel) {
    val def = viewModel.setting(state).building(id)
    val building = state.building(id)
    val listState = rememberScalingLazyListState()

    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        item {
            Text(def.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
        item {
            Text("${BigNumber.format(building.credits.toDouble())} credits", color = Gold, fontSize = 13.sp)
        }
        for (track in UpgradeTrack.entries) {
            item {
                val level = building.level(track)
                val cost = Economy.upgradeCost(def, track, level)
                val affordable = building.credits >= cost
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (viewModel.upgrade(id, track)) viewModel.haptics.click() else viewModel.haptics.failure()
                    },
                    label = { Text("${trackName(track)} · Lv $level") },
                    secondaryLabel = {
                        Text(
                            "${effectText(track, level, def, state.totalBonus)} · ${BigNumber.format(cost.toDouble())} cr",
                            color = if (affordable) Color.White else Color.White.copy(alpha = 0.5f),
                        )
                    },
                    colors = if (affordable) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                )
            }
        }
    }
}

private fun trackName(track: UpgradeTrack) = when (track) {
    UpgradeTrack.SKILL -> "Skill"
    UpgradeTrack.AUTOMATION -> "Automation"
    UpgradeTrack.STORAGE -> "Storage"
}

/** "current → next" for the track's effect. */
private fun effectText(track: UpgradeTrack, level: Int, def: BuildingDef, totalBonus: Double): String = when (track) {
    UpgradeTrack.SKILL -> "×${fmt(Economy.skillMultiplier(level))}→×${fmt(Economy.skillMultiplier(level + 1))}"
    UpgradeTrack.AUTOMATION -> {
        val now = Economy.incomePerSecond(def, level, totalBonus)
        val next = Economy.incomePerSecond(def, level + 1, totalBonus)
        "\$${fmt(now)}→\$${fmt(next)}/s"
    }
    UpgradeTrack.STORAGE -> "${fmt(Economy.storageHours(def, level))}h→${fmt(Economy.storageHours(def, level + 1))}h"
}

private fun fmt(x: Double): String = when {
    x >= 1000 -> BigNumber.format(x)
    x == Math.floor(x) -> x.toLong().toString()
    else -> "%.1f".format(Locale.US, x)
}
