package com.mikeyd.bezeltown.render.scenes

import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder

// Models shared between scenes: the gatekeeper appears on the street and on his own screen.

/** The gatekeeper, this time as a cheesy town tour guide with a flag. */
fun gatekeeperModel(): MeshBuilder {
    val b = MeshBuilder()
    b.block(-0.07f, 0f, 0f, 0.09f, 0.32f, 0.1f, srgb(0xC8B48A)) // legs
    b.block(0.07f, 0f, 0f, 0.09f, 0.32f, 0.1f, srgb(0xC8B48A))
    b.block(0f, 0.32f, 0f, 0.3f, 0.34f, 0.18f, srgb(0xF4D03F)) // shirt
    b.block(0f, 0.66f, 0f, 0.2f, 0.2f, 0.2f, srgb(0xF1C27D)) // head
    b.block(0f, 0.86f, 0f, 0.22f, 0.07f, 0.22f, srgb(0xD7263D)) // cap
    b.box(0f, 0.875f, 0.15f, 0.2f, 0.03f, 0.12f, srgb(0xD7263D)) // brim
    b.box(-0.05f, 0.76f, 0.105f, 0.03f, 0.03f, 0.01f, srgb(0x222222)) // eyes
    b.box(0.05f, 0.76f, 0.105f, 0.03f, 0.03f, 0.01f, srgb(0x222222))
    b.block(0.22f, 0.3f, 0.05f, 0.03f, 0.95f, 0.03f, srgb(0x6B4B2E)) // flag pole
    b.box(0.37f, 1.12f, 0.05f, 0.28f, 0.18f, 0.02f, srgb(0x3A86FF)) // pennant
    return b
}

/** The gold chain he wears once he's been paid at least once. Fits over [gatekeeperModel]. */
fun gatekeeperChainModel(): MeshBuilder {
    val b = MeshBuilder()
    for (k in 0 until 7) {
        val a = Math.toRadians(200.0 + k * 20.0)
        b.box((0.11 * Math.cos(a)).toFloat(), (0.62 + 0.09 * Math.sin(a)).toFloat(), 0.1f, 0.04f, 0.04f, 0.02f, srgb(0xFFD45C))
    }
    b.disc(0f, 0.5f, 0.1f, 0.04f, 0.02f, 6, srgb(0xFFD45C))
    return b
}

fun portalRingModel(): MeshBuilder {
    val b = MeshBuilder()
    val segments = 14
    for (k in 0 until segments) {
        val a = (k.toFloat() / segments) * 360f
        val rad = Math.toRadians(a.toDouble())
        val cx = (0.95 * Math.cos(rad)).toFloat()
        val cy = (0.95 * Math.sin(rad)).toFloat()
        val color = if (k % 2 == 0) srgb(0x8E7CC3) else srgb(0xB4A7D6)
        // Each segment is a short box tangent to the ring.
        b.disc(cx, cy, 0f, 0.2f, 0.18f, 5, color, a)
    }
    return b
}
