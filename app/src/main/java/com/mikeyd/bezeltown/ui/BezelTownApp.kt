package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.mikeyd.bezeltown.core.economy.AwayReport
import com.mikeyd.bezeltown.core.economy.LeaveReport
import com.mikeyd.bezeltown.core.format.BigNumber
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.mikeyd.bezeltown.GameViewModel
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.render.FilamentRuntime

private const val OVERWORLD = "overworld"
private const val BUILDING = "building/{id}"
private const val UPGRADES = "upgrades/{id}"
private const val GATE = "gate"
private const val TITLE_MILLIS = 1500L
private const val SHOP = "shop"
private const val ARCADE = "arcade"
private const val ARCADE_PLAY = "arcade/{id}"

@Composable
fun BezelTownApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The title screen stays up at least this long, so the version can be read.
    var titleShown by remember { mutableStateOf(false) }
    // Engine startup and material loading take a moment; do it behind the title screen.
    LaunchedEffect(Unit) {
        FilamentRuntime.engine(context)
        delay(TITLE_MILLIS)
        titleShown = true
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    MaterialTheme {
        val loaded = state
        if (loaded == null || !titleShown) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Bezel Town", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Gold)
                    if (version != null) Text("v$version", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
            return@MaterialTheme
        }

        val navController = rememberSwipeDismissableNavController()
        // Shared so touch-driven microgames can limit swipe-to-dismiss to the screen edge.
        val swipeToDismissState = rememberSwipeToDismissBoxState()
        val away by viewModel.away.collectAsStateWithLifecycle()
        val arrival by viewModel.arrival.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize()) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = OVERWORLD,
                state = rememberSwipeDismissableNavHostState(swipeToDismissState),
            ) {
                // Each screen reads the live state itself: the nav graph is built once, so
                // a value captured out here would freeze at whatever it was back then.
                composable(OVERWORLD) {
                    val live = viewModel.liveState() ?: return@composable
                    OverworldScreen(
                        state = live,
                        viewModel = viewModel,
                        onEnter = { id -> navController.navigate("building/${id.name}") },
                        onGate = { navController.navigate(GATE) },
                        onStall = { navController.navigate(SHOP) },
                        onArcade = { navController.navigate(ARCADE) },
                    )
                }
                composable(BUILDING) { entry ->
                    val id = BuildingId.valueOf(entry.arguments?.getString("id") ?: return@composable)
                    val live = viewModel.liveState() ?: return@composable
                    BuildingScreen(
                        id = id,
                        state = live,
                        viewModel = viewModel,
                        swipeToDismissState = swipeToDismissState,
                        onUpgrades = { navController.navigate("upgrades/${id.name}") },
                    )
                }
                composable(GATE) {
                    val live = viewModel.liveState() ?: return@composable
                    GateScreen(
                        state = live,
                        viewModel = viewModel,
                        onLeft = { navController.popBackStack() },
                    )
                }
                composable(ARCADE) {
                    val live = viewModel.liveState() ?: return@composable
                    ArcadeScreen(state = live, viewModel = viewModel, onPlay = { id -> navController.navigate("arcade/${id.name}") })
                }
                composable(ARCADE_PLAY) { entry ->
                    val id = BuildingId.valueOf(entry.arguments?.getString("id") ?: return@composable)
                    val live = viewModel.liveState() ?: return@composable
                    BuildingScreen(
                        id = id,
                        state = live,
                        viewModel = viewModel,
                        swipeToDismissState = swipeToDismissState,
                        onUpgrades = {},
                        arcade = true,
                    )
                }
                composable(SHOP) {
                    val live = viewModel.liveState() ?: return@composable
                    ShopScreen(state = live, viewModel = viewModel)
                }
                composable(UPGRADES) { entry ->
                    val id = BuildingId.valueOf(entry.arguments?.getString("id") ?: return@composable)
                    val live = viewModel.liveState() ?: return@composable
                    UpgradeScreen(id = id, state = live, viewModel = viewModel)
                }
            }
            away?.let { AwayCard(it, onDismiss = viewModel::dismissAway) }
            arrival?.let { report ->
                ArrivalCard(report, totalBonus = loaded.totalBonus, tokens = loaded.tokens, onDismiss = viewModel::dismissArrival)
            }
        }
    }
}

/** After the portal: what the trip earned, and the running totals. */
@Composable
private fun ArrivalCard(report: LeaveReport, totalBonus: Double, tokens: Long, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF241B3D).copy(alpha = 0.9f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Text("Welcome back to", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text("Small Town ${report.arrivingLoop + 1}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFFD9CCFF))
            Text("+${percent(report.bonus)} bonus  ·  +${report.tokens} tokens", fontSize = 13.sp, color = Gold)
            Text(
                "Earning +${percent(totalBonus)} on everything. ${tokens} tokens to spend.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text("Tap to continue", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

/** 0.054 -> "5.4%". */
private fun percent(fraction: Double): String = "${(fraction * 1000).roundToInt() / 10.0}%"

/** "While you were away": what the buildings earned while the game was closed. */
@Composable
private fun AwayCard(report: AwayReport, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Text("While you were away", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            Text(formatAway(report.seconds), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Text(
                "+\$${BigNumber.format(report.earned)}",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Gold,
            )
            if (report.storageFilled) {
                Text(
                    "Some storage filled up. Upgrade Storage to bank more.",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = Warn,
                )
            }
            Text("Tap to continue", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

/** "3d 4h", "2h 13m", "45m", "50s". */
private fun formatAway(seconds: Double): String {
    val total = seconds.toLong()
    val d = total / 86_400
    val h = total % 86_400 / 3_600
    val m = total % 3_600 / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "${total}s"
    }
}

/** The current game state, recomposing the caller whenever it changes. */
@Composable
private fun GameViewModel.liveState(): GameState? = state.collectAsStateWithLifecycle().value
