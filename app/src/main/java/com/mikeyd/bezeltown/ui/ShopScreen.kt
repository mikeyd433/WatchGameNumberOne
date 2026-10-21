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
import com.mikeyd.bezeltown.core.economy.Portal
import com.mikeyd.bezeltown.core.format.BigNumber
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.GlobalUpgrade

/**
 * The token stall at the start of the street, run by the gatekeeper's "cousin":
 * permanent upgrades, paid in tokens. The bezel scrolls the list.
 */
@Composable
fun ShopScreen(state: GameState, viewModel: GameViewModel) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
        item {
            Text("The Stall", fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        item {
            Text(
                "Run by the gatekeeper's cousin. Totally different guy.",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        item {
            Text("${state.tokens} tokens", color = Color(0xFFD9CCFF), fontSize = 13.sp)
        }
        for (upgrade in GlobalUpgrade.entries) {
            item {
                val tier = Portal.tier(state, upgrade)
                val maxed = tier >= Portal.maxTier(upgrade)
                val cost = Portal.upgradeCost(upgrade, tier)
                val affordable = !maxed && state.tokens >= cost
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (viewModel.buyGlobalUpgrade(upgrade)) viewModel.haptics.heavy() else viewModel.haptics.failure()
                    },
                    label = { Text("${upgradeName(upgrade)} · Lv $tier") },
                    secondaryLabel = {
                        Text(
                            if (maxed) "${effect(upgrade, tier)} · MAX" else "${effect(upgrade, tier)}→${effect(upgrade, tier + 1)} · $cost tk",
                            color = if (affordable) Color.White else Color.White.copy(alpha = 0.5f),
                        )
                    },
                    colors = if (affordable) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                )
            }
        }
        item {
            Text(
                "Tokens come from leaving with money to spare.",
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

private fun upgradeName(upgrade: GlobalUpgrade) = when (upgrade) {
    GlobalUpgrade.BONUS_RATE -> "Bonus Rate"
    GlobalUpgrade.HEAD_START -> "Head Start"
    GlobalUpgrade.CHEAPER_GATE -> "Cheaper Gate"
    GlobalUpgrade.OFFLINE_STORAGE -> "Offline Storage"
}

/** What the upgrade does at [tier], short enough for a chip. */
private fun effect(upgrade: GlobalUpgrade, tier: Int): String = when (upgrade) {
    GlobalUpgrade.BONUS_RATE -> "×${Portal.BASE_BONUS_COEFFICIENT + Portal.BONUS_RATE_STEP * tier}"
    GlobalUpgrade.HEAD_START -> "\$" + BigNumber.format(if (tier == 0) 0.0 else Portal.HEAD_START_BASE * Math.pow(Portal.HEAD_START_GROWTH, (tier - 1).toDouble()))
    GlobalUpgrade.CHEAPER_GATE -> "-${(Portal.CHEAPER_GATE_STEP * 100 * tier).toInt()}%"
    GlobalUpgrade.OFFLINE_STORAGE -> "+${50 * tier}%"
}
