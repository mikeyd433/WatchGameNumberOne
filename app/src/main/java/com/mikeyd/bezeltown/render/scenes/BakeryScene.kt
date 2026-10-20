package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.KneadGame
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bakery counter with a lump of dough in the middle, and the 3D arrow to swipe
 * floating above it. Dim and warm: the back of the kitchen fades into the dark.
 */
class BakeryScene(engine: Engine) : MicrogameScene(engine, srgb(0x4A2E22), Fog(near = 3.6f, far = 8f)) {
    private val camera = ProjectingCamera()
    private val doughMaterial = tintedMaterial(PALE)
    private val dough: Int
    private val puffs: List<Puff>

    // The arrow to swipe, and a copy that flies off when one is cleared.
    private val arrowMaterial = tintedMaterial(ARROW_GOLD)
    private val ghostMaterial = tintedMaterial(ARROW_GREEN)
    private val arrow: Int
    private val ghost: Int

    private var time = 0f
    private var lastCleared = 0
    private var lastMistakes = 0
    private var squash = 0f
    private var splat = 0f

    private class Puff(val entity: Int) {
        var age = 1f
        var x = 0f; var y = 0f; var z = 0f
        var vx = 0f; var vy = 0f; var vz = 0f
    }

    init {
        addMesh(kitchen())
        dough = addMesh(MeshBuilder().sphere(0f, 0f, 0f, 0.55f, 6, 10, srgb(0xFFFFFF)), doughMaterial)
        puffs = List(10) {
            Puff(addMesh(MeshBuilder().box(0f, 0f, 0f, 0.12f, 0.12f, 0.12f, srgb(0xFFFFFF))))
        }
        arrow = addMesh(arrowMesh(), arrowMaterial)
        ghost = addMesh(arrowMesh(), ghostMaterial)
        hide(arrow)
        hide(ghost)
    }

    override fun onFrame(dt: Float) {
        time += dt
        val knead = game as? KneadGame
        // Screen right is world +x; screen up is away from the camera (world -z).
        val pullX = knead?.stretchX ?: 0f
        val pullZ = -(knead?.stretchY ?: 0f)
        val progress = knead?.progress ?: 0f

        if (knead != null && knead.cleared > lastCleared) {
            squash = 1f
            knead.lastCleared?.let { spawnPuffs(it.x.toFloat(), -it.y.toFloat()) }
        }
        if (knead != null && knead.mistakes > lastMistakes) splat = 1f
        lastCleared = knead?.cleared ?: 0
        lastMistakes = knead?.mistakes ?: 0
        squash *= exp(-dt * 8f)
        splat *= exp(-dt * 5f)

        val breathe = if (knead == null) sin(time * 2f) * 0.03f else 0f
        val sx = abs(pullX)
        val sz = abs(pullZ)
        val spread = 0.35f * splat // a wrong swipe flattens the dough into a pancake
        place(
            dough,
            x = pullX * 0.25f, y = 0.33f - 0.12f * splat, z = pullZ * 0.2f,
            sx = 1f + 0.45f * sx - 0.12f * sz + spread + breathe,
            sy = 0.6f * (1f - 0.3f * (sx + sz) - 0.25f * squash - 0.55f * splat) - breathe,
            sz = 1f + 0.45f * sz - 0.12f * sx + spread + breathe,
        )
        setTint(doughMaterial, PALE.lerp(GOLDEN, progress.coerceIn(0f, 1f)))
        updateArrows(knead)

        for (p in puffs) {
            if (p.age >= 1f) {
                place(p.entity, sx = 0f, sy = 0f, sz = 0f)
                continue
            }
            p.age += dt * 1.4f
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt
            p.vy *= exp(-dt * 2f)
            val size = (1f - p.age) * 1.4f
            place(p.entity, x = p.x, y = p.y, z = p.z, rotYDeg = p.age * 90f, sx = size, sy = size, sz = size)
        }
    }

    /** A few flour puffs flying off the dough in the swiped direction (world x/z). */
    private fun spawnPuffs(dirX: Float, dirZ: Float) {
        var spawned = 0
        for (p in puffs) {
            if (p.age < 1f) continue
            val side = Random.nextFloat() * 0.6f - 0.3f
            p.age = 0f
            p.x = dirX * (0.5f + Random.nextFloat() * 0.3f) + dirZ * side
            p.y = 0.3f
            p.z = dirZ * (0.45f + Random.nextFloat() * 0.25f) + dirX * side
            p.vx = dirX * (0.2f + Random.nextFloat() * 0.5f)
            p.vz = dirZ * (0.2f + Random.nextFloat() * 0.5f)
            p.vy = 0.6f + Random.nextFloat() * 0.6f
            if (++spawned == 3) break
        }
    }

    private fun updateArrows(knead: KneadGame?) {
        if (knead == null || knead.finished) {
            hide(arrow)
            hide(ghost)
            return
        }

        // The arrow being swiped: pops in with a little overshoot, shakes red on a miss.
        val pop = (knead.arrowAge / ARROW_POP_SECONDS).coerceIn(0f, 1f)
        val q = pop - 1f
        val scale = ARROW_SIZE * (1f + 2.70158f * q * q * q + 1.70158f * q * q).coerceAtLeast(0.05f)
        val missed = knead.mistakeAge < ARROW_SHAKE_SECONDS
        val shake = if (missed) sin(knead.mistakeAge * 70f) * 0.06f else 0f
        setTint(arrowMaterial, if (missed) ARROW_RED else ARROW_GOLD, ARROW_ALPHA)
        // A slow yaw so you can see the arrow's thickness.
        val yaw = sin(time * 2.2f) * 18f
        placeArrow(arrow, knead.arrow, offset = 0f, extraX = shake, yaw = yaw, scale = scale)

        // The cleared arrow flies off the way it was swiped and fades out.
        val gone = knead.lastCleared
        val t = knead.clearedAge / ARROW_FLY_SECONDS
        if (gone != null && t < 1f) {
            setTint(ghostMaterial, ARROW_GREEN, ARROW_ALPHA * (1f - t))
            placeArrow(ghost, gone, offset = t * 0.9f, extraX = 0f, yaw = yaw, scale = ARROW_SIZE)
        } else {
            hide(ghost)
        }
    }

    /**
     * Puts an arrow above the dough, facing the camera and pointing the way to
     * swipe. [offset] moves it that way along the screen, for the fly-off.
     */
    private fun placeArrow(entity: Int, d: KneadGame.Direction, offset: Float, extraX: Float, yaw: Float, scale: Float) {
        // Screen up is world up tilted away from the camera by ARROW_TILT.
        val tilt = Math.toRadians(ARROW_TILT.toDouble())
        val upY = Math.cos(tilt).toFloat()
        val upZ = -Math.sin(tilt).toFloat()
        val roll = when (d) {
            KneadGame.Direction.UP -> 0f
            KneadGame.Direction.LEFT -> 90f
            KneadGame.Direction.DOWN -> 180f
            KneadGame.Direction.RIGHT -> -90f
        }
        place(
            entity,
            x = ARROW_X + d.x * offset + extraX,
            y = ARROW_Y + d.y * offset * upY,
            z = ARROW_Z + d.y * offset * upZ,
            rotYDeg = yaw,
            rotXDeg = -ARROW_TILT,
            rotZDeg = roll,
            sx = scale, sy = scale, sz = scale,
        )
    }

    private fun hide(entity: Int) = place(entity, sx = 0f, sy = 0f, sz = 0f)

    /** A thick arrow pointing +Y in the XY plane, about 2 units long. White, so the tint sets its color. */
    private fun arrowMesh(): MeshBuilder {
        val b = MeshBuilder(seed = 3)
        val white = srgb(0xFFFFFF)
        val t = ARROW_THICKNESS / 2f
        val center = V3(0f, 0f, 0f)
        // Head: a triangular prism.
        val tip = 1f; val base = 0.05f; val half = 0.85f
        val f = arrayOf(V3(0f, tip, t), V3(-half, base, t), V3(half, base, t))
        val k = arrayOf(V3(0f, tip, -t), V3(-half, base, -t), V3(half, base, -t))
        b.triangle(f[0], f[1], f[2], white, center)
        b.triangle(k[0], k[2], k[1], white, center)
        for (i in 0 until 3) {
            val j = (i + 1) % 3
            b.quad(f[i], f[j], k[j], k[i], white, center)
        }
        // Shaft: a box under the head.
        b.box(0f, -0.42f, 0f, 0.62f, 0.95f, ARROW_THICKNESS, white)
        return b
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

        private val ARROW_GOLD = srgb(0xFFD45C)
        private val ARROW_RED = srgb(0xFF5C5C)
        private val ARROW_GREEN = srgb(0x7CFFB2)
        /** Below 1 the arrows are see-through; 0.75 skips a quarter of their pixels. */
        private const val ARROW_ALPHA = 0.75f
        private const val ARROW_SIZE = 0.36f
        private const val ARROW_THICKNESS = 0.35f
        private const val ARROW_X = 0f
        private const val ARROW_Y = 1.0f
        private const val ARROW_Z = 0.15f
        /** Degrees the arrow leans back so it faces the camera above the counter. */
        private const val ARROW_TILT = 27f
        private const val ARROW_POP_SECONDS = 0.15f
        private const val ARROW_FLY_SECONDS = 0.25f
        private const val ARROW_SHAKE_SECONDS = 0.3f
    }
}
