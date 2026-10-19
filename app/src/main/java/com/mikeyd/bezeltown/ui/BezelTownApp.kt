package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.mikeyd.bezeltown.render.FilamentRuntime

private const val OVERWORLD = "overworld"
private const val BUILDING = "building/{id}"
private const val UPGRADES = "upgrades/{id}"

@Composable
fun BezelTownApp(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Engine startup and material loading take a moment; do it behind the loading screen.
    LaunchedEffect(Unit) { FilamentRuntime.engine(context) }

    MaterialTheme {
        val loaded = state
        if (loaded == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text("Bezel Town")
            }
            return@MaterialTheme
        }

        val navController = rememberSwipeDismissableNavController()
        // Shared so touch-driven microgames can limit swipe-to-dismiss to the screen edge.
        val swipeToDismissState = rememberSwipeToDismissBoxState()
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = OVERWORLD,
            state = rememberSwipeDismissableNavHostState(swipeToDismissState),
        ) {
            composable(OVERWORLD) {
                OverworldScreen(
                    state = loaded,
                    viewModel = viewModel,
                    onEnter = { id -> navController.navigate("building/${id.name}") },
                )
            }
            composable(BUILDING) { entry ->
                val id = BuildingId.valueOf(entry.arguments?.getString("id") ?: return@composable)
                BuildingScreen(
                    id = id,
                    state = loaded,
                    viewModel = viewModel,
                    swipeToDismissState = swipeToDismissState,
                    onUpgrades = { navController.navigate("upgrades/${id.name}") },
                )
            }
            composable(UPGRADES) { entry ->
                val id = BuildingId.valueOf(entry.arguments?.getString("id") ?: return@composable)
                UpgradeScreen(id = id, state = loaded, viewModel = viewModel)
            }
        }
    }
}
