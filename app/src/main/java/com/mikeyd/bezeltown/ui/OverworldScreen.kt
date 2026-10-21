package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.material.Text
import com.mikeyd.bezeltown.GameViewModel
import com.mikeyd.bezeltown.core.economy.Economy
import com.mikeyd.bezeltown.core.format.BigNumber
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.overworld.Walker
import com.mikeyd.bezeltown.input.BezelDetents
import com.mikeyd.bezeltown.render.FilamentSurface
import com.mikeyd.bezeltown.render.scenes.BuildingVisual
import com.mikeyd.bezeltown.render.scenes.OverworldScene
import com.mikeyd.bezeltown.render.PixelFont
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val ARCADE_SLOT = 0
private const val GATE_SLOT = 6

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun OverworldScreen(
    state: GameState,
    viewModel: GameViewModel,
    onEnter: (BuildingId) -> Unit,
) {
    val context = LocalContext.current
    val setting = viewModel.setting(state)
    val walker = viewModel.walker
    val haptics = viewModel.haptics
    val detents = remember { BezelDetents(context) }
    val focusRequester = rememberActiveFocusRequester()

    var scene by remember { mutableStateOf<OverworldScene?>(null) }
    // Bumped when the walker reaches or leaves a doorway, so the hint updates.
    var doorSlot by remember { mutableIntStateOf(walker.doorSlot ?: -1) }
    var message by remember { mutableStateOf<String?>(null) }

    fun onWalkEvent(event: Walker.Event) {
        when (event) {
            Walker.Event.Step -> haptics.tick()
            is Walker.Event.Doorway -> haptics.click()
        }
    }

    val income = Economy.totalIncome(state, setting)
    val visuals = setting.buildings.map { def ->
        val b = state.building(def.id)
        val rate = Economy.incomePerSecond(def, b.automationLevel, state.totalBonus)
        BuildingVisual(unlocked = b.unlocked, activity = (rate / (def.incomePerLevel * 10).coerceAtLeast(1e-9)).toFloat().coerceIn(0f, 1f))
    }
    val signs = (0 until OverworldScene.SLOT_COUNT).map { signFor(it, state, viewModel) }
    SideEffect { scene?.setBuildings(visuals) }

    // The tap detector is set up once and keeps whichever handler it first saw, so
    // the handler must read the game through these holders, never capture it.
    val currentState by rememberUpdatedState(state)
    val currentOnEnter by rememberUpdatedState(onEnter)

    fun onTap() {
        val slot = walker.doorSlot ?: return
        val now = currentState
        val here = viewModel.setting(now)
        when (slot) {
            ARCADE_SLOT -> message = "Arcade: coming soon"
            GATE_SLOT -> message = "\"Portal opens at \$${BigNumber.format(here.portalThreshold)}! What a deal!\""
            else -> {
                val def = here.buildings.getOrNull(slot - 1) ?: return
                when {
                    now.building(def.id).unlocked -> currentOnEnter(def.id)
                    def.microgame == null -> message = "${def.name}: under construction"
                    viewModel.unlock(def.id) -> {
                        haptics.heavy()
                        message = "${def.name} open!"
                    }
                    else -> {
                        haptics.failure()
                        message = "Need \$${BigNumber.format(def.unlockCost)}"
                    }
                }
            }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(1800)
            message = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                walker.onDetents(detents.fromPixels(event.verticalScrollPixels)).forEach(::onWalkEvent)
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        FilamentSurface(
            modifier = Modifier.fillMaxSize(),
            createScene = { engine ->
                OverworldScene(engine, walker).also { created ->
                    created.setBuildings(visuals)
                    created.frameListener = { dt ->
                        walker.update(dt)?.let(::onWalkEvent)
                        val slot = walker.doorSlot ?: -1
                        if (slot != doorSlot) doorSlot = slot
                    }
                    scene = created
                }
            },
        )

        // The name of the door you're standing at floats over it, clear of the money counter.
        FloatingName(
            lines = signs.getOrNull(doorSlot),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 38.dp)
                .fillMaxWidth(0.86f)
                .height(64.dp),
        )

        TopCurvedText("\$${BigNumber.format(state.setting.money)}  +${BigNumber.format(income)}/s", Gold)

        val progress = Economy.portalProgress(state, setting).toFloat()
        EdgeArc(progress = progress, color = Color(0xFFB4A7D6))
        BottomCurvedText("Portal ${(progress * 100).roundToInt()}%")

        val hint = message ?: hintFor(doorSlot, state, viewModel)
        if (hint != null) {
            Text(
                text = hint,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp, start = 36.dp, end = 36.dp)
                    .fillMaxWidth()
                    .background(Ink.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** One line of floating text, colored with an sRGB hex value. */
data class SignLine(val text: String, val color: Int)

/**
 * Chunky pixel-letter text with a 3D extruded look, drawn at full screen
 * resolution so it stays sharp over the low-resolution scene. Springs up when
 * [lines] appears and sinks away when it goes.
 */
@Composable
private fun FloatingName(lines: List<SignLine>?, modifier: Modifier = Modifier) {
    // Keep showing the last name while it animates out.
    var shown by remember { mutableStateOf(lines) }
    if (lines != null) shown = lines
    val rise by animateFloatAsState(
        targetValue = if (lines != null) 1f else 0f,
        animationSpec = if (lines != null) spring(dampingRatio = 0.45f, stiffness = 500f) else tween(140),
        label = "floating name",
    )
    val content = shown ?: return
    if (rise <= 0.001f) return

    Canvas(modifier) {
        val gap = 2
        val textRows = content.size * PixelFont.HEIGHT + (content.size - 1) * gap
        val widest = content.maxOf { PixelFont.width(it.text) }.coerceAtLeast(1)
        // Fit the widest line, but never bigger than ~4dp per font pixel.
        val px = minOf(size.width / widest, size.height / textRows, 4.dp.toPx())
        val depth = (px * 0.45f).coerceAtLeast(1f)
        val blockHeight = textRows * px

        // Rise up from below and grow into place.
        val lift = (1f - rise) * size.height * 0.5f
        withTransform({
            translate(top = lift)
            scale(scaleX = 1f, scaleY = rise.coerceAtLeast(0f), pivot = Offset(size.width / 2f, size.height))
        }) {
            content.forEachIndexed { row, line ->
                val left = (size.width - PixelFont.width(line.text) * px) / 2f
                val top = (size.height - blockHeight) / 2f + row * (PixelFont.HEIGHT + gap) * px
                val face = Color(0xFF000000.toInt() or line.color)
                val side = lerp(face, Color.Black, 0.55f)
                val cell = Size(px + 0.5f, px + 0.5f)
                // Shadow, then the extruded sides (down-right), then the front face.
                PixelFont.forEachPixel(line.text) { x, y ->
                    drawRect(Color.Black.copy(alpha = 0.35f), Offset(left + x * px + depth * 2.2f, top + y * px + depth * 2.6f), cell)
                }
                for (step in 3 downTo 1) {
                    val d = depth * step / 3f
                    PixelFont.forEachPixel(line.text) { x, y ->
                        drawRect(side, Offset(left + x * px + d, top + y * px + d), cell)
                    }
                }
                PixelFont.forEachPixel(line.text) { x, y ->
                    drawRect(face, Offset(left + x * px, top + y * px), cell)
                }
            }
        }
    }
}

/** The floating text for [slot]: the name, plus the price while it's locked. */
private fun signFor(slot: Int, state: GameState, viewModel: GameViewModel): List<SignLine> {
    val setting = viewModel.setting(state)
    return when (slot) {
        ARCADE_SLOT -> listOf(SignLine("ARCADE", SIGN_NAME))
        GATE_SLOT -> listOf(
            SignLine("PORTAL", SIGN_PORTAL),
            SignLine("\$${BigNumber.format(setting.portalThreshold)}", SIGN_PRICE),
        )
        else -> {
            val def = setting.buildings[slot - 1]
            val building = state.building(def.id)
            val name = SignLine(def.name.uppercase(), SIGN_NAME)
            when {
                building.unlocked -> listOf(name)
                def.microgame == null -> listOf(name, SignLine("SOON", SIGN_SOON))
                else -> {
                    val affordable = state.setting.money >= def.unlockCost
                    listOf(name, SignLine("\$${BigNumber.format(def.unlockCost)}", if (affordable) SIGN_AFFORD else SIGN_PRICE))
                }
            }
        }
    }
}

private const val SIGN_NAME = 0xFFF4D6
private const val SIGN_PORTAL = 0xD9CCFF
private const val SIGN_PRICE = 0xFFD45C
private const val SIGN_AFFORD = 0x7CFFB2
private const val SIGN_SOON = 0x9AA8B8

private fun hintFor(slot: Int, state: GameState, viewModel: GameViewModel): String? {
    if (slot !in 1..5) return null
    val def = viewModel.setting(state).buildings[slot - 1]
    val building = state.building(def.id)
    return when {
        building.unlocked -> "Tap to enter"
        def.microgame == null -> "Under construction"
        else -> "Tap to buy"
    }
}
