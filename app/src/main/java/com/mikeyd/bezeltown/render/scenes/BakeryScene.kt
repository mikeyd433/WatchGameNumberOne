package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.KneadGame
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Bakery counter with a lump of dough in the middle. */
class BakeryScene(engine: Engine) : MicrogameScene(engine, srgb(0xE9C79A)) {
    private val camera = ProjectingCamera()
    private val doughMaterial = tintedMaterial(PALE)
    private val dough: Int
    private val puffs: List<Puff>

    private var time = 0f
    private var lastKneads = 0
    private var squash = 0f

    private class Puff(val entity: Int) {
        var age = 1f
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f
    }

    init {
        addMesh(kitchen())
        dough = addMesh(MeshBuilder().sphere(0f, 0f, 0f, 0.55f, 6, 10, srgb(0xFFFFFF)), doughMaterial)
        puffs = List(10) {
            Puff(addMesh(MeshBuilder().box(0f, 0f, 0f, 0.12f, 0.12f, 0.12f, srgb(0xFFFFFF))))
        }
    }

    override fun onFrame(dt: Float) {
        time += dt
        val knead = game as? KneadGame
        val stretch = knead?.stretch ?: 0f
        val progress = knead?.progress ?: 0f

        if (knead != null && knead.kneads > lastKneads) {
            squash = 1f
            spawnPuffs(if (stretch >= 0f) 1f else -1f)
        }
        lastKneads = knead?.kneads ?: 0
        squash *= exp(-dt * 8f)

        val breathe = if (knead == null) sin(time * 2f) * 0.03f else 0f
        val s = abs(stretch)
        place(
            dough,
            x = stretch * 0.25f, y = 0.33f,
            sx = 1f + 0.45f * s + breathe, sy = 0.6f * (1f - 0.3f * s - 0.25f * squash) - breathe, sz = 1f - 0.15f * s + breathe,
        )
        setTint(doughMaterial, PALE.lerp(GOLDEN, progress.coerceIn(0f, 1f)))

        for (p in puffs) {
            if (p.age >= 1f) {
                place(p.entity, sx = 0f, sy = 0f, sz = 0f)
                continue
            }
            p.age += dt * 1.4f
            p.x += p.vx * dt; p.y += p.vy * dt
            p.vy *= exp(-dt * 2f)
            val size = (1f - p.age) * 1.4f
            place(p.entity, x = p.x, y = p.y, z = p.z, rotYDeg = p.age * 90f, sx = size, sy = size, sz = size)
        }
    }

    private fun spawnPuffs(direction: Float) {
        var spawned = 0
        for (p in puffs) {
            if (p.age < 1f) continue
            p.age = 0f
            p.x = direction * (0.5f + Random.nextFloat() * 0.3f)
            p.y = 0.3f
            p.z = Random.nextFloat() * 0.6f - 0.3f
            p.vx = direction * (0.2f + Random.nextFloat() * 0.5f)
            p.vy = 0.6f + Random.nextFloat() * 0.6f
            if (++spawned == 3) break
        }
    }

    override fun configureCamera(camera: Camera) {
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        this.camera.apply(camera, V3(0f, 2.3f, 2.7f), V3(0f, 0.15f, 0f), fovYDeg = 42f, aspect = aspect)
    }

    private fun kitchen(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, -3f, 0f, 3.6f, 3f, 1.8f, srgb(0x9C6B3E)) // counter body
        b.block(0f, 0f, 0f, 3.8f, 0.02f, 1.9f, srgb(0xD9B98C)) // floured top
        b.block(0f, -3f, -2.2f, 8f, 8f, 0.2f, srgb(0xE9C79A)) // back wall
        b.block(-0.2f, 0.4f, -2.05f, 1.6f, 1.3f, 0.2f, srgb(0x3B3B3B)) // oven
        b.box(-0.2f, 0.95f, -1.93f, 1.0f, 0.55f, 0.04f, srgb(0xF39C12)) // oven glow
        b.block(-1.35f, 0f, -0.35f, 0.5f, 0.75f, 0.35f, srgb(0xF3EFE6)) // flour sack
        b.box(-1.35f, 0.35f, -0.17f, 0.3f, 0.15f, 0.02f, srgb(0x3A86FF))
        // Rolling pin lying along X.
        b.box(1.25f, 0.08f, -0.4f, 0.8f, 0.12f, 0.12f, srgb(0xC8955C))
        b.box(0.75f, 0.08f, -0.4f, 0.2f, 0.06f, 0.06f, srgb(0x8B5A2B))
        b.box(1.75f, 0.08f, -0.4f, 0.2f, 0.06f, 0.06f, srgb(0x8B5A2B))
        return b
    }

    companion object {
        private val PALE = srgb(0xF6EBD3)
        private val GOLDEN = srgb(0xE8B864)
    }
}
