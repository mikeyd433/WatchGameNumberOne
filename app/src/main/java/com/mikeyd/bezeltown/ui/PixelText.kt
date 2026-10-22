package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikeyd.bezeltown.render.PixelFont

/**
 * Draws [text] in the chunky 3x5 pixel font with a 3D extruded look: a soft
 * shadow, darker sides stepping down-right, then the front face. [left]/[top]
 * are the top-left of the front face; [px] is the size of one font pixel.
 */
fun DrawScope.drawPixelText(text: String, left: Float, top: Float, px: Float, face: Color) {
    val depth = (px * 0.45f).coerceAtLeast(1f)
    val side = lerp(face, Color.Black, 0.55f)
    val cell = Size(px + 0.5f, px + 0.5f)
    PixelFont.forEachPixel(text) { x, y ->
        drawRect(Color.Black.copy(alpha = 0.35f), Offset(left + x * px + depth * 2.2f, top + y * px + depth * 2.6f), cell)
    }
    for (step in 3 downTo 1) {
        val d = depth * step / 3f
        PixelFont.forEachPixel(text) { x, y -> drawRect(side, Offset(left + x * px + d, top + y * px + d), cell) }
    }
    PixelFont.forEachPixel(text) { x, y -> drawRect(face, Offset(left + x * px, top + y * px), cell) }
}

/** One line of extruded pixel text, sized to fit: [pixel] is the size of one font pixel. */
@Composable
fun PixelText(text: String, color: Color, pixel: Dp = 3.dp, modifier: Modifier = Modifier) {
    val width = pixel * (PixelFont.width(text) + 2)
    val height = pixel * (PixelFont.HEIGHT + 2)
    Canvas(modifier.width(width).height(height)) {
        drawPixelText(text, left = 0f, top = 0f, px = pixel.toPx(), face = color)
    }
}
