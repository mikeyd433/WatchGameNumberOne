package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.background
import androidx.wear.compose.foundation.curvedText
import androidx.wear.compose.foundation.padding
import kotlin.math.roundToInt

val Gold = Color(0xFFFFD45C)
val Ink = Color(0xFF1B1F2A)
val Warn = Color(0xFFFF5C5C)
val Good = Color(0xFF7CFFB2)

/** Curved text hugging the top of the round screen. */
@Composable
fun TopCurvedText(text: String, color: Color = Color.White) {
    CurvedLayout(modifier = Modifier.fillMaxSize().padding(2.dp), anchor = 270f) {
        curvedText(
            text = text,
            modifier = CurvedModifier.background(Ink.copy(alpha = 0.55f), StrokeCap.Round).padding(angular = 6.dp, radial = 2.dp),
            style = CurvedTextStyle(fontSize = 15.sp, color = color, fontWeight = FontWeight.Bold),
        )
    }
}

/** Curved text along the bottom, reading left to right. */
@Composable
fun BottomCurvedText(text: String, color: Color = Color.White) {
    CurvedLayout(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        anchor = 90f,
        angularDirection = CurvedDirection.Angular.CounterClockwise,
    ) {
        curvedText(
            text = text,
            modifier = CurvedModifier.background(Ink.copy(alpha = 0.55f), StrokeCap.Round).padding(angular = 6.dp, radial = 2.dp),
            style = CurvedTextStyle(fontSize = 12.sp, color = color),
        )
    }
}

/**
 * A progress arc around the screen edge. [startAngle]/[sweep] are in degrees,
 * clockwise from 3 o'clock, like Canvas.drawArc.
 */
@Composable
fun EdgeArc(
    progress: Float,
    color: Color,
    startAngle: Float = 135f,
    sweep: Float = -90f,
    strokeWidth: Float = 5f,
    track: Color = Color.White.copy(alpha = 0.25f),
) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = strokeWidth.dp.toPx()
        val inset = stroke / 2 + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)
        drawArc(track, startAngle, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        if (progress > 0f) {
            drawArc(color, startAngle, sweep * progress.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * Places the content so its bottom-center sits on [anchor] (in pixels within the
 * parent). Reads the anchor during placement only, so moving it every frame
 * doesn't recompose. Hidden while the anchor is null.
 */
fun Modifier.anchoredAbove(anchor: () -> Offset?): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        val p = anchor() ?: return@layout
        placeable.place((p.x - placeable.width / 2f).roundToInt(), (p.y - placeable.height).roundToInt())
    }
}
