package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.mikeyd.bezeltown.GameViewModel
import com.mikeyd.bezeltown.core.economy.PlayOutcome
import com.mikeyd.bezeltown.core.format.BigNumber
import com.mikeyd.bezeltown.core.microgame.KneadGame
import com.mikeyd.bezeltown.core.microgame.Microgame
import com.mikeyd.bezeltown.core.microgame.PumpGame
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.MicrogameId
import com.mikeyd.bezeltown.input.BezelDetents
import com.mikeyd.bezeltown.render.FilamentSurface
import com.mikeyd.bezeltown.render.scenes.BakeryScene
import com.mikeyd.bezeltown.render.scenes.MicrogameScene
import com.mikeyd.bezeltown.render.scenes.WaterTowerScene
import kotlin.math.roundToInt

private enum class Phase { LOBBY, COUNTDOWN, PLAYING, RESULT }

/** Per-microgame text. */
private data class GameCopy(val verb: String, val hint: String, val win: String, val lose: String)

private fun copyFor(game: MicrogameId) = when (game) {
    MicrogameId.KNEAD -> GameCopy("KNEAD!", "Swipe across the dough", "SPRINGY!", "Still lumpy…")
    MicrogameId.PUMP -> GameCopy("PUMP!", "Crank the bezel. Not too fast!", "FULL!", "Bone dry…")
}

private fun newGame(game: MicrogameId): Microgame = when (game) {
    MicrogameId.KNEAD -> KneadGame()
    MicrogameId.PUMP -> PumpGame()
}

/**
 * A building's screen: lobby with its credits and a Play button, then the
 * microgame itself, then the result. The game runs on the 3D scene's frame loop.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun BuildingScreen(
    id: BuildingId,
    state: GameState,
    viewModel: GameViewModel,
    swipeToDismissState: SwipeToDismissBoxState,
    onUpgrades: () -> Unit,
) {
    val context = LocalContext.current
    val setting = viewModel.setting(state)
    val def = setting.building(id)
    val building = state.building(id)
    val microgame = def.microgame ?: return
    val copy = copyFor(microgame)
    val haptics = viewModel.haptics
    val detents = remember { BezelDetents(context) }
    val focusRequester = rememberActiveFocusRequester()

    var scene by remember { mutableStateOf<MicrogameScene?>(null) }
    var phase by remember { mutableStateOf(Phase.LOBBY) }
    var game by remember { mutableStateOf<Microgame?>(null) }
    var outcome by remember { mutableStateOf<PlayOutcome?>(null) }
    var countdown by remember { mutableFloatStateOf(0f) }
    var resultAge by remember { mutableFloatStateOf(0f) }
    // Bumped every frame while playing so the overlay redraws.
    var frame by remember { mutableIntStateOf(0) }
    var lastKneads by remember { mutableIntStateOf(0) }

    fun start() {
        val g = newGame(microgame)
        game = g
        scene?.game = g
        lastKneads = 0
        countdown = COUNTDOWN_SECONDS
        phase = Phase.COUNTDOWN
        haptics.click()
    }

    fun onFrame(dt: Float) {
        val g = game
        when (phase) {
            Phase.COUNTDOWN -> {
                countdown -= dt
                if (countdown <= 0f) phase = Phase.PLAYING
            }
            Phase.PLAYING -> if (g != null) {
                g.update(dt)
                if (g is KneadGame && g.kneads > lastKneads) {
                    lastKneads = g.kneads
                    haptics.tick()
                }
                if (g.finished) {
                    outcome = viewModel.recordPlay(id, g.score)
                    if (g.succeeded) haptics.success() else haptics.failure()
                    resultAge = 0f
                    phase = Phase.RESULT
                }
                frame++
            }
            Phase.RESULT -> resultAge += dt
            Phase.LOBBY -> Unit
        }
    }

    val latestOnFrame by rememberUpdatedState(::onFrame)
    SideEffect { scene?.activity = (building.automationLevel / 10f).coerceIn(0f, 1f) }

    val playing = phase == Phase.PLAYING
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swiping is the bakery's input, so mid-game only an edge swipe exits.
            .then(
                if (microgame == MicrogameId.KNEAD && phase != Phase.LOBBY) {
                    Modifier.edgeSwipeToDismiss(swipeToDismissState)
                } else {
                    Modifier
                },
            )
            .onRotaryScrollEvent { event ->
                val n = detents.fromPixels(event.verticalScrollPixels)
                val g = game
                if (playing && g != null && n != 0) {
                    g.onDetents(n)
                    haptics.tick()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    val g = game ?: return@detectDragGestures
                    if (phase != Phase.PLAYING) return@detectDragGestures
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val x = change.position.x / w * 2f - 1f
                    val y = 1f - change.position.y / h * 2f
                    g.onDrag(x, y, drag.x / w * 2f, -drag.y / h * 2f)
                    change.consume()
                }
            },
    ) {
        FilamentSurface(
            modifier = Modifier.fillMaxSize(),
            createScene = { engine ->
                val created: MicrogameScene = when (microgame) {
                    MicrogameId.KNEAD -> BakeryScene(engine)
                    MicrogameId.PUMP -> WaterTowerScene(engine)
                }
                created.frameListener = { dt -> latestOnFrame(dt) }
                scene = created
                created
            },
        )

        when (phase) {
            Phase.LOBBY -> Lobby(
                title = "${def.name} · ${BigNumber.format(building.credits.toDouble())} cr",
                hint = copy.hint,
                best = state.personalBests[microgame],
                onPlay = ::start,
                onUpgrades = onUpgrades,
            )
            Phase.COUNTDOWN -> Text(
                text = copy.verb,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Gold,
                modifier = Modifier.align(Alignment.Center),
            )
            // Only the overlay reads the per-frame counter, so only it recomposes each frame.
            Phase.PLAYING -> game?.let { PlayingOverlay(it, frame = { frame }) }
            Phase.RESULT -> {
                val g = game
                val result = outcome
                Result(
                    title = if (g?.succeeded == true) copy.win else copy.lose,
                    score = g?.score ?: 0,
                    credits = result?.creditsEarned ?: 0,
                    newBest = result?.newBest == true,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            // Ignore taps that land right as the game ends.
                            if (resultAge > 0.6f) {
                                phase = Phase.LOBBY
                                game = null
                                scene?.game = null
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Timer ring, progress and warnings while a microgame runs. Redrawn every frame. */
@Composable
private fun PlayingOverlay(g: Microgame, frame: () -> Int) {
    frame()
    EdgeArc(progress = g.timeLeftFraction, color = Gold, startAngle = 270f, sweep = 360f, strokeWidth = 6f)
    val status = when (g) {
        is KneadGame -> "${g.kneads}/${KneadGame.TARGET_KNEADS}"
        else -> "${(g.progress * 100).roundToInt()}%"
    }
    BottomCurvedText(status)
    if (g is PumpGame && g.spraying) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Text(
                text = "TOO FAST!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = Warn,
                modifier = Modifier.padding(top = 36.dp),
            )
        }
    }
}

@Composable
private fun Lobby(
    title: String,
    hint: String,
    best: Long?,
    onPlay: () -> Unit,
    onUpgrades: () -> Unit,
) {
    TopCurvedText(title, Gold)
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 34.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = hint,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .background(Ink.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onPlay,
            modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Gold, contentColor = Ink),
        ) {
            Text("PLAY", fontWeight = FontWeight.Black, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        CompactChip(
            onClick = onUpgrades,
            label = { Text("Upgrades") },
            colors = ChipDefaults.secondaryChipColors(),
        )
        if (best != null) {
            Text("Best $best", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun Result(
    title: String,
    score: Int,
    credits: Long,
    newBest: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Gold)
        Text("Score $score", fontSize = 16.sp)
        Text("+$credits credits", fontSize = 16.sp, color = Good)
        if (newBest) Text("New best!", fontSize = 12.sp, color = Gold)
        Spacer(Modifier.height(6.dp))
        Text("Tap to continue", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

private const val COUNTDOWN_SECONDS = 0.9f
