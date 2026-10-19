package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.PumpGame
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The water tower up close: an open-staved tank you can see filling, and the crank pump. */
class WaterTowerScene(engine: Engine) : MicrogameScene(engine, srgb(0x9ED8F0)) {
    private val camera = ProjectingCamera()
    private val water: Int
    private val wheel: Int
    private val drops: List<Drop>

    private var time = 0f
    private var shownFill = IDLE_FILL
    private var wasFinished = false

    private class Drop(val entity: Int) {
        var life = 0f
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
    }

    init {
        addMesh(yard())
        water = addMesh(MeshBuilder().prism(0f, 0f, 0f, 0.8f, 1f, 10, srgb(0x3C8DD6), 18f))
        wheel = addMesh(
            MeshBuilder()
                .disc(0f, 0f, 0f, 0.38f, 0.08f, 10, srgb(0x5A6470))
                .box(0f, 0f, 0.02f, 0.7f, 0.07f, 0.1f, srgb(0x3E4650))
                .box(0.3f, 0f, 0.14f, 0.1f, 0.1f, 0.22f, srgb(0xF2C14E)),
        )
        drops = List(16) {
            Drop(addMesh(MeshBuilder().box(0f, 0f, 0f, 0.09f, 0.09f, 0.09f, srgb(0x6FB7F0))))
        }
    }

    override fun onFrame(dt: Float) {
        time += dt
        val pump = game as? PumpGame

        val targetFill = pump?.progress ?: IDLE_FILL
        shownFill += (targetFill - shownFill) * (dt * 10f).coerceAtMost(1f)
        place(water, x = TANK_X, y = TANK_BOTTOM + 0.02f, sy = (shownFill * TANK_HEIGHT).coerceAtLeast(0.001f))

        val crankDeg = if (pump != null) -pump.crankDetents * 15f else -time * 60f * activity
        place(wheel, x = PUMP_X, y = 0.5f, z = 0.42f, rotZDeg = crankDeg)

        if (pump?.spraying == true) {
            emit(2, PUMP_X + 0.05f, 1.0f, 0.3f, spread = 1.4f, up = 2.2f)
        }
        if (pump != null && pump.finished && pump.succeeded && !wasFinished) {
            emit(16, TANK_X, TANK_BOTTOM + TANK_HEIGHT, 0.4f, spread = 2.0f, up = 2.6f)
        }
        wasFinished = pump?.finished ?: false

        for (d in drops) {
            if (d.life <= 0f) {
                place(d.entity, sx = 0f, sy = 0f, sz = 0f)
                continue
            }
            d.life -= dt
            d.vy -= 9f * dt
            d.x += d.vx * dt; d.y += d.vy * dt; d.z += d.vz * dt
            if (d.y < 0f) d.life = 0f
            place(d.entity, x = d.x, y = d.y, z = d.z)
        }
    }

    private fun emit(count: Int, x: Float, y: Float, z: Float, spread: Float, up: Float) {
        var n = 0
        for (d in drops) {
            if (d.life > 0f) continue
            d.life = 1.2f
            d.x = x; d.y = y; d.z = z
            d.vx = (Random.nextFloat() - 0.5f) * spread
            d.vz = (Random.nextFloat() - 0.2f) * spread * 0.5f
            d.vy = up * (0.6f + Random.nextFloat() * 0.4f)
            if (++n == count) break
        }
    }

    override fun configureCamera(camera: Camera) {
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        this.camera.apply(camera, V3(-0.1f, 1.9f, 6.4f), V3(-0.1f, 1.45f, 0f), fovYDeg = 42f, aspect = aspect)
    }

    private fun yard(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, -0.3f, -2f, 14f, 0.3f, 10f, srgb(0x7CC36B))
        for (k in 0 until 5) b.cone(-5f + k * 2.6f, -0.1f, -6f, 1.8f, 1.6f + (k % 2) * 0.6f, 6, srgb(0x5FA457))

        // Tower legs and braces.
        val legColor = srgb(0x8C6E54)
        for (lx in floatArrayOf(-0.6f, 0.6f)) for (lz in floatArrayOf(-0.6f, 0.6f)) {
            b.block(TANK_X + lx, 0f, lz, 0.12f, TANK_BOTTOM, 0.12f, legColor)
        }
        b.box(TANK_X, 0.8f, 0.6f, 1.3f, 0.08f, 0.08f, legColor)
        b.box(TANK_X, 0.8f, -0.6f, 1.3f, 0.08f, 0.08f, legColor)

        // Tank: floor, open staves so the water shows, top rim, roof.
        b.prism(TANK_X, TANK_BOTTOM - 0.06f, 0f, 0.92f, 0.08f, 10, srgb(0x5D7F96), 18f)
        val staves = 10
        for (k in 0 until staves) {
            val a = 2.0 * PI * k / staves
            val sx = TANK_X + (0.88 * cos(a)).toFloat()
            val sz = (0.88 * sin(a)).toFloat()
            // Skip the staves facing the camera so you can look into the tank.
            if (sz > 0.5f) continue
            b.block(sx, TANK_BOTTOM, sz, 0.2f, TANK_HEIGHT, 0.2f, srgb(0x8FB3C9), -Math.toDegrees(a).toFloat())
        }
        b.prism(TANK_X, TANK_BOTTOM + 0.35f, 0f, 0.95f, 0.06f, 10, srgb(0x5D7F96), 18f)
        b.prism(TANK_X, TANK_BOTTOM + 0.8f, 0f, 0.95f, 0.06f, 10, srgb(0x5D7F96), 18f)
        b.cone(TANK_X, TANK_BOTTOM + TANK_HEIGHT + 0.25f, 0f, 1.05f, 0.5f, 10, srgb(0x5D7F96), 18f)
        // Little posts holding the roof up over the open front.
        for (px in floatArrayOf(-0.55f, 0.55f)) {
            b.block(TANK_X + px, TANK_BOTTOM + TANK_HEIGHT, 0.7f, 0.06f, 0.25f, 0.06f, srgb(0x5D7F96))
        }

        // Pump and pipe.
        b.block(PUMP_X, 0f, 0f, 0.6f, 0.9f, 0.7f, srgb(0xC0392B))
        b.block(PUMP_X, 0.9f, 0f, 0.12f, 2.25f, 0.12f, srgb(0x6F7B87))
        b.box((PUMP_X + TANK_X) / 2 - 0.1f, 3.1f, 0f, TANK_X - PUMP_X - 0.2f, 0.12f, 0.12f, srgb(0x6F7B87))
        b.block(TANK_X - 0.5f, 2.85f, 0f, 0.12f, 0.25f, 0.12f, srgb(0x6F7B87)) // spout
        return b
    }

    companion object {
        private const val TANK_X = 0.55f
        private const val TANK_BOTTOM = 1.6f
        private const val TANK_HEIGHT = 1.15f
        private const val PUMP_X = -1.55f
        private const val IDLE_FILL = 0.45f
    }
}
