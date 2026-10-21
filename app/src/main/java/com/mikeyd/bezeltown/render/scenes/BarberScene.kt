package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.BarberGame
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Barber Shop: a customer with wild hair in a swivel chair, a ring of marks on
 * the checkered floor, and a gold mark to turn the chair toward. Each snip
 * drops a clump of hair on the floor.
 */
class BarberScene(engine: Engine) : MicrogameScene(engine, srgb(0x2F4A48), Fog(near = 4.5f, far = 10f)) {
    private val camera = ProjectingCamera()
    private val chair: Int
    private val clumps: List<Int>
    private val markMaterial = tintedMaterial(MARK_GOLD)
    private val mark: Int

    private var time = 0f
    private var shownDegrees = 0f
    private var lastSnips = 0
    private var markPop = 1f

    /** Cut hair: one per clump, made up front and reused every haircut. */
    private class Fallen(val entity: Int) {
        var active = false
        var x = 0f; var y = 0f; var z = 0f; var vy = 0f
    }
    private val fallen: List<Fallen>

    init {
        addMesh(room())
        addMesh(markRing())

        chair = addGroup()
        addMesh(chairAndCustomer(), parent = chair)
        clumps = CLUMP_SPOTS.map { (x, y, z) ->
            addMesh(MeshBuilder(seed = 5).sphere(0f, 0f, 0f, 0.17f, 4, 6, HAIR), parent = chair).also {
                place(it, x = x, y = y, z = z)
            }
        }

        fallen = CLUMP_SPOTS.map {
            Fallen(addMesh(MeshBuilder(seed = 5).sphere(0f, 0f, 0f, 0.17f, 4, 6, HAIR))).also { f ->
                place(f.entity, sx = 0f, sy = 0f, sz = 0f)
            }
        }

        // A tile on the floor plus a pole with a diamond above head height, so the
        // mark stays visible even when it's straight behind the customer.
        mark = addMesh(
            MeshBuilder()
                .box(0f, 0.04f, 0f, 0.26f, 0.08f, 0.26f, srgb(0xFFFFFF))
                .cone(0f, 0.08f, 0f, 0.16f, 0.22f, 4, srgb(0xFFFFFF), 45f)
                .box(0f, BEACON_HEIGHT / 2f, 0f, 0.035f, BEACON_HEIGHT, 0.035f, srgb(0xFFFFFF))
                .cone(0f, BEACON_HEIGHT, 0f, 0.17f, 0.22f, 4, srgb(0xFFFFFF), 45f)
                .cone(0f, BEACON_HEIGHT, 0f, 0.17f, -0.22f, 4, srgb(0xFFFFFF), 45f),
            markMaterial,
        )
    }

    override fun onFrame(dt: Float) {
        time += dt
        val g = game as? BarberGame

        // Ease the chair toward where the bezel has dialed it (clockwise turns it clockwise).
        val targetDegrees = if (g != null) -g.chairStep * BarberGame.DEGREES_PER_STEP else -time * 25f * activity
        var delta = targetDegrees - shownDegrees
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        shownDegrees += delta * (dt * 18f).coerceAtMost(1f)
        place(chair, rotYDeg = shownDegrees)

        // Each snip: a clump of hair falls off and the mark pops in at its new spot.
        val snips = g?.snips ?: 0
        if (snips > lastSnips) {
            for (i in lastSnips until snips.coerceAtMost(clumps.size)) dropClump(i)
            markPop = 0f
        }
        if (snips < lastSnips) resetHair()
        lastSnips = snips
        animateFallen(dt)

        if (g == null || g.finished) {
            place(mark, sx = 0f, sy = 0f, sz = 0f)
        } else {
            markPop = (markPop + dt / 0.18f).coerceAtMost(1f)
            val angle = Math.toRadians((-g.targetStep * BarberGame.DEGREES_PER_STEP).toDouble())
            val bob = if (g.aligned) 0f else sin(time * 6f) * 0.04f
            val s = markPop * (if (g.aligned) 1.25f else 1f)
            setTint(markMaterial, if (g.aligned) MARK_GREEN else MARK_GOLD)
            place(
                mark,
                x = (RING_RADIUS * sin(angle)).toFloat(), y = bob, z = (RING_RADIUS * cos(angle)).toFloat(),
                rotYDeg = time * 90f, sx = s, sy = s, sz = s,
            )
        }
    }

    private fun dropClump(index: Int) {
        val entity = clumps[index]
        val (x, y, z) = CLUMP_SPOTS[index]
        // Where the clump is right now, given the chair's turn.
        val a = Math.toRadians(shownDegrees.toDouble())
        val wx = (x * cos(a) + z * sin(a)).toFloat()
        val wz = (-x * sin(a) + z * cos(a)).toFloat()
        // Hide it on the head and drop its loose twin from the same spot.
        place(entity, sx = 0f, sy = 0f, sz = 0f)
        val f = fallen[index]
        f.active = true
        f.x = wx
        f.y = y
        f.z = wz
        f.vy = 0.8f
    }

    private fun animateFallen(dt: Float) {
        for (f in fallen) {
            if (!f.active) continue
            if (f.y > 0.06f) {
                f.vy -= 9f * dt
                f.y = (f.y + f.vy * dt).coerceAtLeast(0.06f)
            }
            place(f.entity, x = f.x, y = f.y, z = f.z, sy = if (f.y <= 0.06f) 0.45f else 1f)
        }
    }

    /** A new haircut: put the hair back on and sweep the floor. */
    private fun resetHair() {
        clumps.forEachIndexed { i, entity ->
            val (x, y, z) = CLUMP_SPOTS[i]
            place(entity, x = x, y = y, z = z)
        }
        fallen.forEach {
            it.active = false
            place(it.entity, sx = 0f, sy = 0f, sz = 0f)
        }
    }

    override fun configureCamera(camera: Camera) {
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        this.camera.apply(camera, V3(0f, 3.6f, 3.6f), V3(0f, 0.75f, 0f), fovYDeg = 44f, aspect = aspect)
    }

    private fun room(): MeshBuilder {
        val b = MeshBuilder()
        // Checkered floor.
        for (i in -5..5) for (k in -5..3) {
            val color = if ((i + k) % 2 == 0) srgb(0xEDEDED) else srgb(0x262626)
            b.block(i * 0.5f, -0.05f, k * 0.5f, 0.5f, 0.05f, 0.5f, color)
        }
        b.block(0f, -0.05f, -2.7f, 6f, 4f, 0.2f, srgb(0x9FD6C6)) // back wall
        b.box(0f, 1.6f, -2.58f, 2.2f, 1.1f, 0.04f, srgb(0xBFE4F2)) // mirror
        b.box(0f, 1.6f, -2.6f, 2.4f, 1.3f, 0.03f, srgb(0xC9A227)) // mirror frame
        b.block(0f, 0.75f, -2.4f, 2.6f, 0.08f, 0.35f, srgb(0x6B4B2E)) // shelf
        b.prism(-0.8f, 0.83f, -2.4f, 0.07f, 0.3f, 6, srgb(0x3A86FF)) // tonic bottles
        b.prism(-0.55f, 0.83f, -2.4f, 0.06f, 0.24f, 6, srgb(0xE84855))
        b.prism(0.7f, 0.83f, -2.4f, 0.08f, 0.2f, 6, srgb(0x7CFFB2))
        // A barber pole on the wall.
        for (k in 0 until 6) {
            val color = if (k % 2 == 0) srgb(0xD7263D) else srgb(0xFFFFFF)
            b.prism(2.2f, 0.9f + k * 0.16f, -2.45f, 0.09f, 0.16f, 6, color, k * 20f)
        }
        return b
    }

    /** Grey ticks on the floor at every position the chair can face. */
    private fun markRing(): MeshBuilder {
        val b = MeshBuilder()
        for (i in 0 until BarberGame.STEPS) {
            val a = Math.toRadians((i * BarberGame.DEGREES_PER_STEP).toDouble())
            b.box(
                (RING_RADIUS * sin(a)).toFloat(), 0.01f, (RING_RADIUS * cos(a)).toFloat(),
                0.07f, 0.03f, 0.2f, srgb(0x8A8F98), rotYDeg = Math.toDegrees(a).toFloat(),
            )
        }
        return b
    }

    /** Swivel chair with the customer facing +Z, plus a red pointer on the footrest. */
    private fun chairAndCustomer(): MeshBuilder {
        val b = MeshBuilder()
        val chrome = srgb(0xB8BEC6)
        val leather = srgb(0xB3262E)
        b.prism(0f, 0f, 0f, 0.38f, 0.06f, 10, chrome) // base
        b.prism(0f, 0.06f, 0f, 0.07f, 0.4f, 8, chrome) // column
        b.block(0f, 0.46f, 0f, 0.62f, 0.14f, 0.6f, leather) // seat
        b.block(0f, 0.6f, -0.27f, 0.62f, 0.7f, 0.1f, leather) // back
        b.block(-0.33f, 0.6f, 0.02f, 0.08f, 0.12f, 0.5f, chrome) // arms
        b.block(0.33f, 0.6f, 0.02f, 0.08f, 0.12f, 0.5f, chrome)
        b.block(0f, 0.2f, 0.42f, 0.4f, 0.05f, 0.18f, chrome) // footrest
        b.cone(0f, 0.24f, 0.55f, 0.1f, 0.14f, 4, srgb(0xFF5C5C), 45f) // pointer
        // Customer in a striped cape.
        b.block(0f, 0.6f, -0.05f, 0.5f, 0.55f, 0.36f, srgb(0x3A86FF))
        b.block(0f, 0.6f, 0.14f, 0.5f, 0.12f, 0.02f, srgb(0xFFFFFF))
        b.block(-0.12f, 0.46f, 0.25f, 0.13f, 0.13f, 0.3f, srgb(0x2E3A66)) // legs
        b.block(0.12f, 0.46f, 0.25f, 0.13f, 0.13f, 0.3f, srgb(0x2E3A66))
        b.block(0f, 1.15f, -0.03f, 0.3f, 0.32f, 0.3f, srgb(0xF1C27D)) // head
        b.box(0f, 1.32f, 0.13f, 0.06f, 0.07f, 0.06f, srgb(0xE0A06A)) // nose
        b.box(-0.07f, 1.37f, 0.125f, 0.04f, 0.04f, 0.02f, srgb(0x222222)) // eyes
        b.box(0.07f, 1.37f, 0.125f, 0.04f, 0.04f, 0.02f, srgb(0x222222))
        return b
    }

    companion object {
        private val HAIR = srgb(0x6B3E1F)
        private val MARK_GOLD = srgb(0xFFD45C)
        private val MARK_GREEN = srgb(0x7CFFB2)
        private const val RING_RADIUS = 1.3f
        /** Height of the floating diamond over the mark: above the customer's head. */
        private const val BEACON_HEIGHT = 1.85f

        /** Wild hair: one clump per snip, around the customer's head (chair space). */
        private val CLUMP_SPOTS = listOf(
            Triple(-0.2f, 1.42f, -0.02f),
            Triple(0.2f, 1.42f, -0.02f),
            Triple(0f, 1.55f, -0.08f),
            Triple(0f, 1.38f, -0.22f),
        )
    }
}
