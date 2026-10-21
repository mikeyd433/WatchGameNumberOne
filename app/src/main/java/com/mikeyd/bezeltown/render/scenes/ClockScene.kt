package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.ClockGame
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tan

/**
 * Clock Tower: the clock face seen straight on, centered on screen so touches
 * map onto it, with a winding key on its side, a bell above that swings when it
 * rings, and a cuckoo that pops out on success.
 */
class ClockScene(engine: Engine) : MicrogameScene(engine, srgb(0x2A2633), Fog(near = 6f, far = 12f)) {
    private val camera = ProjectingCamera()
    private val clock: Int
    private val hourHand: Int
    private val minuteHand: Int
    private val key: Int
    private val bell: Int
    private val cuckoo: Int

    private var time = 0f
    private var keyDegrees = 0f
    private var shownHour = 0f
    private var shownMinute = 0f
    private var ring = 0f
    private var cuckooOut = 0f
    private var shake = 0f
    private var lastSproings = 0
    private var rang = false

    init {
        addMesh(wall())
        clock = addGroup()
        addMesh(face(), parent = clock)
        hourHand = addMesh(hand(length = FACE_RADIUS * 0.45f, width = 0.11f, color = srgb(0x2B2B2B)), parent = clock)
        minuteHand = addMesh(hand(length = FACE_RADIUS * 0.8f, width = 0.07f, color = srgb(0x1C1C1C)), parent = clock)
        addMesh(MeshBuilder().disc(0f, 0f, 0.14f, 0.07f, 0.04f, 8, srgb(0xC9A227)), parent = clock) // center cap
        key = addMesh(windingKey())
        bell = addMesh(bellMesh())
        cuckoo = addMesh(cuckooMesh())
    }

    override fun onFrame(dt: Float) {
        time += dt
        val g = game as? ClockGame

        // Winding key: one notch per detent; spins back when the spring lets go.
        val targetKey = (g?.wind ?: 0) * 15f + if (g == null) time * 30f * activity else 0f
        keyDegrees += (targetKey - keyDegrees) * (dt * 14f).coerceAtMost(1f)
        place(key, x = FACE_RADIUS + 0.22f, rotXDeg = keyDegrees)

        // A sproing rattles the whole clock.
        val sproings = g?.sproings ?: 0
        if (sproings > lastSproings) shake = 1f
        lastSproings = sproings
        shake *= exp(-dt * 6f)
        place(clock, x = sin(time * 60f) * 0.05f * shake, rotZDeg = sin(time * 45f) * 4f * shake)

        // Hands ease toward their snapped positions.
        val hourTarget = (g?.hourHand ?: 0).toFloat()
        val minuteTarget = (g?.minuteHand ?: 0).toFloat()
        shownHour = ease(shownHour, hourTarget, dt)
        shownMinute = ease(shownMinute, minuteTarget, dt)
        place(hourHand, z = 0.06f, rotZDeg = -shownHour * 30f)
        place(minuteHand, z = 0.1f, rotZDeg = -shownMinute * 30f)

        // The bell swings and the cuckoo pops out when the time is set.
        val success = g?.finished == true && g.succeeded
        if (success && !rang) ring = 1f
        rang = success
        ring *= exp(-dt * 1.2f)
        place(bell, y = BELL_Y, rotZDeg = sin(time * 9f) * 28f * ring)
        cuckooOut += ((if (success) 1f else 0f) - cuckooOut) * (dt * 10f).coerceAtMost(1f)
        place(cuckoo, y = FACE_RADIUS + 0.18f, z = 0.05f + cuckooOut * 0.45f, sx = cuckooOut, sy = cuckooOut, sz = cuckooOut)
    }

    /** Moves around the dial the short way, so 11 to 0 goes forward, not all the way back. */
    private fun ease(from: Float, to: Float, dt: Float): Float {
        var d = to - from
        while (d > 6f) d -= 12f
        while (d < -6f) d += 12f
        return from + d * (dt * 18f).coerceAtMost(1f)
    }

    override fun configureCamera(camera: Camera) {
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        this.camera.apply(camera, V3(0f, 0f, CAMERA_DISTANCE), V3(0f, 0f, 0f), fovYDeg = FOV, aspect = aspect)
    }

    private fun wall(): MeshBuilder {
        val b = MeshBuilder(seed = 21)
        // Stone blocks, offset every other row.
        for (row in -5..5) for (col in -5..5) {
            val x = col * 0.7f + if (row % 2 == 0) 0f else 0.35f
            val shade = if ((row * 7 + col * 3) % 3 == 0) 0x5E5868 else 0x6A6475
            b.box(x, row * 0.38f, -0.6f, 0.66f, 0.34f, 0.2f, srgb(shade))
        }
        return b
    }

    private fun face(): MeshBuilder {
        val b = MeshBuilder()
        b.disc(0f, 0f, -0.06f, FACE_RADIUS + 0.09f, 0.1f, 24, srgb(0xC9A227)) // brass rim
        b.disc(0f, 0f, 0f, FACE_RADIUS, 0.04f, 24, srgb(0xF4ECD6)) // face
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val quarter = i % 3 == 0
            val r = FACE_RADIUS * 0.84f
            b.box(
                (r * sin(a)).toFloat(), (r * Math.cos(a)).toFloat(), 0.04f,
                if (quarter) 0.07f else 0.04f, if (quarter) 0.18f else 0.11f, 0.03f,
                srgb(0x2B2B2B),
            )
        }
        return b
    }

    /** A hand pointing up (+Y) from the pivot at the origin. */
    private fun hand(length: Float, width: Float, color: LinearColor): MeshBuilder =
        MeshBuilder()
            .box(0f, length / 2f - 0.05f, 0f, width, length, 0.03f, color)
            .cone(0f, length - 0.05f, 0f, width * 1.1f, 0.12f, 4, color, 45f)

    /** A brass key sticking out of the clock's right side, its shaft along +X. */
    private fun windingKey(): MeshBuilder =
        MeshBuilder()
            .box(0.15f, 0f, 0f, 0.3f, 0.06f, 0.06f, srgb(0xC9A227))
            .box(0.34f, 0f, 0f, 0.06f, 0.36f, 0.12f, srgb(0xD8B23A))

    private fun bellMesh(): MeshBuilder {
        val b = MeshBuilder()
        val bronze = srgb(0xB8862B)
        b.box(0f, 0.32f, 0f, 0.05f, 0.14f, 0.05f, srgb(0x3B2A1E)) // hanger
        b.cone(0f, 0.04f, 0f, 0.3f, 0.3f, 8, bronze)
        b.prism(0f, -0.04f, 0f, 0.33f, 0.08f, 8, bronze)
        b.sphere(0f, -0.1f, 0f, 0.06f, 3, 6, srgb(0x6B4426)) // clapper
        return b
    }

    private fun cuckooMesh(): MeshBuilder =
        MeshBuilder()
            .box(0f, 0f, 0f, 0.2f, 0.18f, 0.2f, srgb(0xF2C14E))
            .box(0f, 0.15f, 0.02f, 0.14f, 0.14f, 0.14f, srgb(0xF2C14E))
            .cone(0f, 0.15f, 0.1f, 0.05f, 0.1f, 4, srgb(0xE86A2A), 45f)
            .box(0.04f, 0.19f, 0.09f, 0.03f, 0.03f, 0.01f, srgb(0x222222))

    companion object {
        private const val FACE_RADIUS = 0.85f
        private const val BELL_Y = 1.32f
        private const val CAMERA_DISTANCE = 4.2f
        private const val FOV = 42f

        /**
         * The face's radius as a fraction of half the screen height. Touches are
         * divided by this so the game sees them relative to the face.
         */
        val FACE_SCREEN_RADIUS: Float =
            (FACE_RADIUS / (CAMERA_DISTANCE * tan(Math.toRadians(FOV / 2.0)))).toFloat()
    }
}
