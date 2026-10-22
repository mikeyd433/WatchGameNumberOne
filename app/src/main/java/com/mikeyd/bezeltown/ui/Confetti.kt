package com.mikeyd.bezeltown.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A one-shot confetti burst for a new best: colored paper bits fire up from the
 * bottom of the screen, tumble, and fall with gravity. Plain Compose drawing,
 * so it costs nothing once it's done.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val pieces = remember { List(PIECES) { Piece(Random(it * 31 + 7)) } }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < DURATION) {
            t = withFrameNanos { (it - start) / 1e9f }
        }
    }
    if (t >= DURATION) return

    Canvas(modifier.fillMaxSize()) {
        val unit = size.minDimension
        for (p in pieces) {
            // Positions in screen-size units from the bottom center; y is up.
            val x = 0.5f + p.vx * t
            val y = p.vy * t - GRAVITY * t * t / 2f
            if (y < -0.05f) continue
            val fade = (1f - (t - DURATION + 0.4f) / 0.4f).coerceIn(0f, 1f)
            val center = Offset(x * size.width, size.height - y * unit)
            rotate(degrees = p.spin * t, pivot = center) {
                drawRect(
                    p.color.copy(alpha = fade),
                    topLeft = Offset(center.x - p.w * unit / 2f, center.y - p.h * unit / 2f),
                    size = Size(p.w * unit, p.h * unit),
                )
            }
        }
    }
}

private class Piece(r: Random) {
    private val angle = Math.toRadians(60.0 + r.nextDouble() * 60.0) // fan out upward
    private val speed = 0.9f + r.nextFloat() * 0.7f
    val vx = (cos(angle) * speed).toFloat() * 0.6f
    val vy = (sin(angle) * speed).toFloat()
    val spin = (r.nextFloat() - 0.5f) * 1400f
    val w = 0.018f + r.nextFloat() * 0.014f
    val h = 0.01f + r.nextFloat() * 0.01f
    val color = COLORS[r.nextInt(COLORS.size)]
}

private const val PIECES = 70
private const val DURATION = 2.2f
private const val GRAVITY = 1.1f
private val COLORS = listOf(
    Color(0xFFFFD45C), Color(0xFFFF5C8A), Color(0xFF7CFFB2), Color(0xFF6FB7F0), Color(0xFFC77DFF), Color(0xFFFFFFFF),
)
