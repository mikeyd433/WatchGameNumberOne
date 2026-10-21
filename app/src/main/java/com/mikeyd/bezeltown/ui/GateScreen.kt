package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import com.mikeyd.bezeltown.GameViewModel
import com.mikeyd.bezeltown.core.economy.Portal
import com.mikeyd.bezeltown.core.format.BigNumber
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.input.BezelDetents
import com.mikeyd.bezeltown.render.FilamentSurface
import com.mikeyd.bezeltown.render.scenes.PortalScene
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The gatekeeper, an overly enthusiastic salesman pitching his portal. Once all
 * five buildings are open and you can afford the price, crank the bezel to feed
 * him every coin you have, then tap to step through.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun GateScreen(state: GameState, viewModel: GameViewModel, onLeft: () -> Unit, onShop: () -> Unit) {
    val context = LocalContext.current
    val setting = viewModel.setting(state)
    val haptics = viewModel.haptics
    val detents = remember { BezelDetents(context) }
    val focusRequester = rememberActiveFocusRequester()

    val price = Portal.threshold(state, setting)
    val money = state.setting.money
    val unlocked = state.setting.buildings.count { it.unlocked }
    val total = state.setting.buildings.size
    val ready = Portal.canLeave(state, setting)
    val bonus = Portal.bonusIfLeaving(state, setting)
    val tokens = Portal.tokensIfLeaving(state, setting)

    var scene by remember { mutableStateOf<PortalScene?>(null) }
    var paid by remember { mutableDoubleStateOf(0.0) }
    // Latches once everything's in: income keeps ticking while you crank, and a few
    // new dollars shouldn't send you back to cranking. Leaving takes it all anyway.
    var paidUp by remember { mutableStateOf(false) }
    var stepping by remember { mutableStateOf(false) }
    var fade by remember { mutableFloatStateOf(0f) }
    val paidAll = ready && paidUp

    SideEffect {
        scene?.bonus = bonus.toFloat()
        scene?.open = ready
    }

    // The tap detector is set up once; read through these so it sees the latest.
    val currentPaidAll by rememberUpdatedState(paidAll)
    val currentStepping by rememberUpdatedState(stepping)

    LaunchedEffect(stepping) {
        if (!stepping) return@LaunchedEffect
        haptics.success()
        val start = withFrameNanos { it }
        var t = 0f
        while (t < 1f) {
            t = withFrameNanos { (it - start) / 1e9f / STEP_IN_SECONDS }.coerceAtMost(1f)
            scene?.stepIn = t
            fade = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
        }
        viewModel.leaveThroughPortal()
        onLeft()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                val n = detents.fromPixels(event.verticalScrollPixels)
                // Clockwise feeds coins, one per detent, faster the more you've paid.
                if (ready && !stepping && !paidAll && n > 0) {
                    repeat(n) {
                        paid = min(money, paid + Portal.paymentPerDetent(paid, price))
                        scene?.addCoin()
                    }
                    if (paid >= money) {
                        paidUp = true
                        haptics.heavy()
                    } else {
                        haptics.tick()
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (currentPaidAll && !currentStepping) stepping = true
                }
            },
    ) {
        FilamentSurface(
            modifier = Modifier.fillMaxSize(),
            createScene = { engine ->
                PortalScene(engine, paidBefore = state.settingLoop > 0).also {
                    it.bonus = bonus.toFloat()
                    it.open = ready
                    scene = it
                }
            },
        )

        TopCurvedText("\$${BigNumber.format(money)}", Gold)

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 30.dp, bottom = 30.dp, start = 22.dp, end = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = pitch(unlocked, total, money, price, paid, paidAll, state.settingLoop, bonus),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(Ink.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                when {
                    !ready -> {
                        Text("Buildings $unlocked/$total", fontSize = 12.sp, color = if (unlocked == total) Good else Color.White)
                        Text(
                            "\$${BigNumber.format(money)} / \$${BigNumber.format(price)}",
                            fontSize = 12.sp,
                            color = if (money >= price) Good else Color.White,
                        )
                    }
                    paidAll -> Text("Tap to step in", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Good)
                    else -> {
                        Text(
                            if (paid == 0.0) "Crank to pay" else "Paid \$${BigNumber.format(paid)}",
                            fontSize = 12.sp,
                            color = Color.White,
                        )
                        PaidBar(fraction = (paid / max(money, 1.0)).toFloat())
                    }
                }
            }
            if (!stepping && !paidUp) {
                CompactChip(
                    onClick = onShop,
                    label = { Text("Shop · ${state.tokens} tk", fontSize = 11.sp) },
                    colors = ChipDefaults.secondaryChipColors(),
                )
            }
        }

        if (ready) {
            val percent = (bonus * 1000).roundToInt() / 10.0
            BottomCurvedText("Leave now: +$percent% · +$tokens tokens", Color(0xFFD9CCFF))
        }

        if (fade > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = fade)))
    }
}

@Composable
private fun PaidBar(fraction: Float) {
    Box(
        Modifier
            .padding(top = 3.dp)
            .fillMaxWidth(0.6f)
            .height(6.dp)
            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(Gold, RoundedCornerShape(3.dp)),
        )
    }
}

/** What he says: a pitch, a nudge, and plenty of feelings about how much you pay. */
private fun pitch(
    unlocked: Int,
    total: Int,
    money: Double,
    price: Double,
    paid: Double,
    paidAll: Boolean,
    loop: Int,
    bonus: Double,
): String = when {
    unlocked < total -> if (loop == 0) {
        "Folks! Open all $total buildings and this beauty is ALL yours!"
    } else {
        "Back again, friend? Open all $total and let's do business!"
    }
    money < price -> "Just \$${BigNumber.format(price - money)} more! You can't put a price on this view. Well, I can."
    paidAll -> if (bonus < 0.01) "…Barely worth the paperwork. Step in, I guess." else "Pleasure doing business! In you go!"
    paid == 0.0 -> "Step right up! Crank those coins into my hand!"
    paid < price -> "Keep 'em coming!"
    else -> {
        // How generous it's looking, from what's been paid past the price so far.
        val soFar = sqrt((paid - price) / price) * Portal.BASE_BONUS_COEFFICIENT / 100.0
        when {
            soFar < 0.01 -> "…that's it? Ouch."
            soFar < 0.05 -> "Ooh, NOW we're talking!"
            else -> "WHAT A CUSTOMER!!"
        }
    }
}

private const val STEP_IN_SECONDS = 1.4f
