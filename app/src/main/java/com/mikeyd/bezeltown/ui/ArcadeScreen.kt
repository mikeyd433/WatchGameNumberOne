package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import com.mikeyd.bezeltown.GameViewModel
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState

/**
 * The arcade cabinet: replay any game you've played, as free play that only
 * counts toward your best. Games you haven't played yet show as ???. The bezel
 * scrolls the list.
 */
@Composable
fun ArcadeScreen(state: GameState, viewModel: GameViewModel, onPlay: (BuildingId) -> Unit) {
    val setting = viewModel.setting(state)
    val listState = rememberScalingLazyListState()
    Box(Modifier.fillMaxSize().background(ARCADE_BG)) {
        ScalingLazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item { PixelText("ARCADE", NEON_PINK, pixel = 4.dp) }
            item {
                Text(
                    "HI-SCORE ${totalBest(state)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NEON_CYAN,
                )
            }
            for (def in setting.buildings) {
                val game = def.microgame ?: continue
                item {
                    val best = state.personalBests[game]
                    val unlocked = best != null
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, if (unlocked) NEON_CYAN else Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable(enabled = unlocked) { onPlay(def.id) }
                            .padding(vertical = 6.dp),
                    ) {
                        PixelText(if (unlocked) def.name else "???", if (unlocked) NEON_YELLOW else Color.White.copy(alpha = 0.35f), pixel = 2.5.dp)
                        Text(
                            if (unlocked) "BEST $best" else "Play it in town first",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = if (unlocked) 0.85f else 0.5f),
                        )
                    }
                }
            }
            item {
                Text("Free play: no credits, just glory.", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
            }
        }
        // CRT scanlines over everything.
        Canvas(Modifier.fillMaxSize()) {
            val step = 3.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
    }
}

/** All personal bests added up: the cabinet's running high score. */
fun totalBest(state: GameState): Long = state.personalBests.values.sum()

private val ARCADE_BG = Color(0xFF120A24)
private val NEON_PINK = Color(0xFFFF4FA3)
private val NEON_CYAN = Color(0xFF4FE3FF)
private val NEON_YELLOW = Color(0xFFFFE45C)
