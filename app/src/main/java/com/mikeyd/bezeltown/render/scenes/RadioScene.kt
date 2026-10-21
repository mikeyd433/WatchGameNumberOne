package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.RadioGame
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.abs
import kotlin.math.sin

/**
 * Radio Station: an old wooden tabletop radio in a dim studio. The needle
 * slides along the tuning window, the knob turns with the bezel, the speaker
 * thumps and the antenna glows as the signal comes in, and the ON AIR light
 * comes on when you're right on the station.
 */
class RadioScene(engine: Engine) : MicrogameScene(engine, srgb(0x1E1A2E), Fog(near = 4f, far = 9f)) {
    private val camera = ProjectingCamera()
    private val needle: Int
    private val knob: Int
    private val speaker: Int
    private val antennaTipMaterial = tintedMaterial(TIP_DIM)
    private val antennaTip: Int
    private val onAirMaterial = tintedMaterial(ON_AIR_OFF)

    private var time = 0f
    private var shownStep = 20f
    private var thump = 0f

    init {
        addMesh(studio())
        addMesh(radioBody())
        addMesh(MeshBuilder().box(0f, 0f, 0f, 1.2f, 0.32f, 0.06f, srgb(0xD7263D)), onAirMaterial).also {
            place(it, x = 1.05f, y = 2.05f, z = -1.88f)
        }
        needle = addMesh(MeshBuilder().box(0f, 0f, 0f, 0.035f, 0.22f, 0.03f, srgb(0xE84855)))
        knob = addMesh(
            MeshBuilder()
                .disc(0f, 0f, 0f, 0.2f, 0.1f, 10, srgb(0x3B2A1E))
                .box(0f, 0.13f, 0.06f, 0.05f, 0.1f, 0.03f, srgb(0xFFF4D6)),
        )
        speaker = addMesh(
            MeshBuilder()
                .disc(0f, 0f, 0f, 0.3f, 0.04f, 12, srgb(0x2A2420))
                .disc(0f, 0f, 0.03f, 0.2f, 0.04f, 10, srgb(0x3B332C))
                .disc(0f, 0f, 0.06f, 0.08f, 0.04f, 8, srgb(0x4A4038)),
        )
        antennaTip = addMesh(MeshBuilder().sphere(0f, 0f, 0f, 0.07f, 4, 6, srgb(0xFFFFFF)), antennaTipMaterial)
    }

    override fun onFrame(dt: Float) {
        time += dt
        val g = game as? RadioGame
        // In the lobby the needle drifts lazily, more so the busier the station is.
        val step = g?.dialStep?.toFloat() ?: (20f + sin(time * 0.3f) * 4f * activity)
        val signal = g?.signal ?: (0.4f + 0.6f * activity)

        shownStep += (step - shownStep) * (dt * 16f).coerceAtMost(1f)
        val t = shownStep / (RadioGame.STEPS - 1)
        place(needle, x = WINDOW_LEFT + (WINDOW_RIGHT - WINDOW_LEFT) * t, y = WINDOW_Y, z = FRONT_Z + 0.04f)
        // One detent turns the knob a notch, like the bezel itself.
        place(knob, x = 0.6f, y = 0.42f, z = FRONT_Z + 0.06f, rotZDeg = -shownStep * 15f)

        // The speaker thumps to the music once the signal comes through.
        thump = if (signal > 0.2f) abs(sin(time * 9f)) * signal else 0f
        val s = 1f + 0.12f * thump
        place(speaker, x = -0.5f, y = 0.42f, z = FRONT_Z + 0.02f + 0.03f * thump, sx = s, sy = s)

        setTint(antennaTipMaterial, TIP_DIM.lerp(TIP_LIT, signal))
        place(antennaTip, x = 0.62f, y = 1.95f, z = -0.15f)
        setTint(onAirMaterial, if (g?.tuned == true) ON_AIR_ON else ON_AIR_OFF)
    }

    override fun configureCamera(camera: Camera) {
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        this.camera.apply(camera, V3(0f, 1.35f, 3.3f), V3(0f, 0.75f, 0f), fovYDeg = 40f, aspect = aspect)
    }

    private fun studio(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, -0.3f, -0.2f, 4.5f, 0.3f, 2.6f, srgb(0x5A3B22)) // table
        b.block(0f, -0.3f, -2f, 8f, 5f, 0.2f, srgb(0x3A3550)) // back wall
        // Foam panels on the wall.
        for (i in -3..3) for (j in 0..2) {
            if ((i + j) % 2 == 0) b.box(i * 0.6f, 0.6f + j * 0.6f, -1.88f, 0.5f, 0.5f, 0.05f, srgb(0x2C2840))
        }
        b.box(1.05f, 2.05f, -1.9f, 1.32f, 0.44f, 0.04f, srgb(0x1A1A1A)) // ON AIR frame
        b.prism(-1.6f, 0f, 0.1f, 0.18f, 0.12f, 8, srgb(0xF2F2F2)) // coffee mug
        b.prism(-1.6f, 0.12f, 0.1f, 0.15f, 0.02f, 8, srgb(0x3B2A1E))
        return b
    }

    private fun radioBody(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, 0f, -0.15f, 2.1f, 1.25f, 0.8f, srgb(0x8A5A34)) // cabinet
        b.block(0f, 1.25f, -0.15f, 2.2f, 0.08f, 0.88f, srgb(0x6B4426)) // top trim
        b.box(0f, 0.6f, FRONT_Z - 0.01f, 1.9f, 1.05f, 0.02f, srgb(0xEFE2C2)) // front panel
        b.box(0f, WINDOW_Y, FRONT_Z, WINDOW_RIGHT - WINDOW_LEFT + 0.12f, 0.28f, 0.02f, srgb(0xFFE9A8)) // window
        // Tick marks along the window, a long one every 4 MHz.
        for (i in 0 until RadioGame.STEPS step 2) {
            val x = WINDOW_LEFT + (WINDOW_RIGHT - WINDOW_LEFT) * i / (RadioGame.STEPS - 1)
            val tall = i % 8 == 0
            b.box(x, WINDOW_Y + 0.08f, FRONT_Z + 0.015f, 0.015f, if (tall) 0.09f else 0.05f, 0.01f, srgb(0x3B2A1E))
        }
        b.block(-0.8f, -0.06f, 0.0f, 0.12f, 0.06f, 0.12f, srgb(0x3B2A1E)) // feet
        b.block(0.8f, -0.06f, 0.0f, 0.12f, 0.06f, 0.12f, srgb(0x3B2A1E))
        // Telescopic antenna.
        b.box(0.62f, 1.6f, -0.15f, 0.03f, 0.7f, 0.03f, srgb(0xB8BEC6))
        return b
    }

    companion object {
        private const val FRONT_Z = 0.26f
        private const val WINDOW_Y = 0.93f
        private const val WINDOW_LEFT = -0.8f
        private const val WINDOW_RIGHT = 0.8f
        private val TIP_DIM = srgb(0x40303A)
        private val TIP_LIT = srgb(0xFF5C5C)
        private val ON_AIR_OFF = srgb(0x3A1416)
        private val ON_AIR_ON = srgb(0xFF3B3B)
    }
}
