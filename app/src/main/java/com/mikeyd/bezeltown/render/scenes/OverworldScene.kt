package com.mikeyd.bezeltown.render.scenes

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.mikeyd.bezeltown.core.overworld.Walker
import com.mikeyd.bezeltown.render.FilamentScene
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.SILHOUETTE
import com.mikeyd.bezeltown.render.V3
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/** What the overworld needs to know about each of the five buildings. */
data class BuildingVisual(val unlocked: Boolean, val activity: Float)

/**
 * The Small Town street: arcade cabinet, five buildings, and the gatekeeper's
 * portal in a row, with the tiny walker in front.
 */
// Dusk sky; distant hills fade into it.
class OverworldScene(engine: Engine, private val walker: Walker) :
    FilamentScene(engine, srgb(0x6A5A9E), Fog(near = 11f, far = 22f)) {
    /** Screen positions of each slot's label, refreshed every frame. Null when off screen. */
    val labelPositions = mutableStateListOf<Offset?>().apply { repeat(SLOT_COUNT) { add(null) } }

    private val camera = ProjectingCamera()
    private val buildingMaterials: List<MaterialInstance>
    private var visuals = List(5) { BuildingVisual(unlocked = false, activity = 0f) }

    private val smoke: List<Int>
    private val pumpWheel: Int
    private val portalRing: Int
    private val portalSwirl: Int
    private val character: Int
    private val leftLeg: Int
    private val rightLeg: Int

    private var time = 0f
    private var walkPhase = 0f
    private var facing = 0f
    private var cameraX = walker.position * SPACING

    init {
        addMesh(scenery())
        addMesh(arcadeCabinet()).also { place(it, x = slotX(0), z = BUILDING_Z) }

        buildingMaterials = List(5) { tintedMaterial() }
        val models = listOf(bakery(), waterTower(), barberShop(), radioStation(), clockTower())
        models.forEachIndexed { i, model ->
            addMesh(model, buildingMaterials[i]).also { place(it, x = slotX(i + 1), z = BUILDING_Z) }
        }

        smoke = List(3) {
            addMesh(MeshBuilder().box(0f, 0f, 0f, 0.22f, 0.22f, 0.22f, srgb(0xEEEEEE)))
        }
        pumpWheel = addMesh(
            MeshBuilder()
                .disc(0f, 0f, 0f, 0.28f, 0.06f, 8, srgb(0x5A6470))
                .box(0.2f, 0f, 0.05f, 0.08f, 0.08f, 0.12f, srgb(0xF2C14E)),
            buildingMaterials[1],
        )

        addMesh(gatekeeper()).also { place(it, x = slotX(6) - 1.1f, z = -0.2f) }
        portalRing = addMesh(portalRingMesh())
        portalSwirl = addMesh(MeshBuilder().disc(0f, 0f, 0f, 0.82f, 0.04f, 7, srgb(0x6B3FA0)))

        character = addGroup()
        addMesh(characterBody(), parent = character)
        leftLeg = addMesh(MeshBuilder().box(0f, -0.08f, 0f, 0.06f, 0.16f, 0.07f, srgb(0x2E3A66)), parent = character)
        rightLeg = addMesh(MeshBuilder().box(0f, -0.08f, 0f, 0.06f, 0.16f, 0.07f, srgb(0x2E3A66)), parent = character)
        place(leftLeg, x = -0.045f, y = 0.16f)
        place(rightLeg, x = 0.045f, y = 0.16f)
    }

    fun setBuildings(newVisuals: List<BuildingVisual>) {
        newVisuals.forEachIndexed { i, v ->
            if (visuals.getOrNull(i)?.unlocked != v.unlocked) {
                setTint(buildingMaterials[i], if (v.unlocked) LinearColor(1f, 1f, 1f) else SILHOUETTE)
            }
        }
        visuals = newVisuals
    }

    override fun onFrame(dt: Float) {
        time += dt

        // Walker.
        val x = walker.position * SPACING
        val speed = abs(walker.velocity)
        walkPhase += speed * dt * 14f
        val targetFacing = when {
            walker.velocity > 0.05f -> 90f
            walker.velocity < -0.05f -> -90f
            else -> 0f
        }
        facing += (targetFacing - facing) * min(1f, dt * 10f)
        val stride = if (speed > 0.05f) sin(walkPhase) * 35f else 0f
        val bob = if (speed > 0.05f) abs(sin(walkPhase)) * 0.03f else 0f
        place(character, x = x, y = bob, z = WALK_Z, rotYDeg = facing)
        place(leftLeg, x = -0.045f, y = 0.16f, rotXDeg = stride)
        place(rightLeg, x = 0.045f, y = 0.16f, rotXDeg = -stride)

        // Bakery chimney smoke while it's producing.
        val bakery = visuals[0]
        smoke.forEachIndexed { i, puff ->
            if (bakery.unlocked && bakery.activity > 0f) {
                val t = ((time * (0.35f + 0.25f * bakery.activity) + i / 3f) % 1f)
                val s = 1f - t
                place(puff, x = slotX(1) + 0.6f + t * 0.35f, y = 2.35f + t * 1.1f, z = BUILDING_Z - 0.2f, sx = s, sy = s, sz = s)
            } else {
                place(puff, sx = 0f, sy = 0f, sz = 0f)
            }
        }

        // Water tower pump wheel.
        val tower = visuals[1]
        val wheelSpeed = if (tower.unlocked) 40f + 200f * tower.activity else 0f
        place(pumpWheel, x = slotX(2) - 0.75f, y = 0.45f, z = BUILDING_Z + 0.7f, rotZDeg = -time * wheelSpeed)

        // Portal: slow idle swirl until it opens.
        place(portalRing, x = slotX(6) + 0.4f, y = 1.25f, z = BUILDING_Z, rotZDeg = time * 12f)
        place(portalSwirl, x = slotX(6) + 0.4f, y = 1.25f, z = BUILDING_Z, rotZDeg = -time * 30f)
    }

    override fun configureCamera(camera: Camera) {
        val targetX = walker.position * SPACING
        cameraX = targetX
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        this.camera.apply(camera, V3(cameraX, 2.1f, 8.6f), V3(cameraX, 1.25f, 0f), fovYDeg = 30f, aspect = aspect)

        for (slot in 0 until SLOT_COUNT) {
            val p = this.camera.project(slotX(slot), LABEL_Y[slot], BUILDING_Z, viewportWidth, viewportHeight)
            val offset = p?.let { Offset(it.first, it.second) }
            if (labelPositions[slot] != offset) labelPositions[slot] = offset
        }
    }

    // ---- Models. Each is built around its own ground-center origin. ----

    private fun scenery(): MeshBuilder {
        val b = MeshBuilder()
        val left = slotX(0) - 8f
        val right = slotX(SLOT_COUNT - 1) + 8f
        val width = right - left
        val mid = (left + right) / 2
        b.block(mid, -0.3f, -3f, width, 0.3f, 12f, srgb(0x7CC36B)) // grass
        b.block(mid, 0f, 0.8f, width, 0.02f, 1.2f, srgb(0x8A8F98)) // road
        b.block(mid, 0f, -0.05f, width, 0.06f, 0.5f, srgb(0xD8D2C4)) // sidewalk
        // Distant hills and trees between the buildings give the strip some depth.
        var hx = left
        var i = 0
        while (hx < right) {
            val r = 2.2f + (i % 3) * 0.7f
            b.cone(hx, -0.1f, -7f - (i % 2) * 1.5f, r, 1.4f + (i % 3) * 0.5f, 6, srgb(if (i % 2 == 0) 0x5FA457 else 0x6DB362))
            hx += 3.1f
            i++
        }
        for (slot in 0 until SLOT_COUNT - 1) {
            val tx = slotX(slot) + SPACING / 2
            b.block(tx, 0f, -1.9f, 0.12f, 0.45f, 0.12f, srgb(0x7A5230))
            b.cone(tx, 0.4f, -1.9f, 0.38f, 0.9f, 6, srgb(0x3F8F4A))
        }
        return b
    }

    private fun arcadeCabinet() = MeshBuilder()
        .block(0f, 0f, 0f, 0.7f, 1.35f, 0.6f, srgb(0x5B3C9E))
        .box(0f, 0.95f, 0.31f, 0.52f, 0.4f, 0.04f, srgb(0x1C2541))
        .box(0f, 0.95f, 0.335f, 0.4f, 0.28f, 0.02f, srgb(0x3DDC97))
        .block(0f, 1.35f, 0f, 0.74f, 0.2f, 0.64f, srgb(0xF2C14E))
        .box(0f, 0.62f, 0.36f, 0.7f, 0.08f, 0.2f, srgb(0x2B2B2B))
        .box(-0.12f, 0.69f, 0.38f, 0.06f, 0.06f, 0.06f, srgb(0xE84855))
        .box(0.12f, 0.69f, 0.38f, 0.06f, 0.06f, 0.06f, srgb(0x3A86FF))

    private fun bakery(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, 0f, 0f, 2.2f, 1.5f, 1.6f, srgb(0xF2D7A6))
        b.gableRoof(0f, 1.5f, 0f, 2.5f, 1.9f, 0.8f, srgb(0xB5543C))
        b.block(0.6f, 1.6f, -0.2f, 0.3f, 0.8f, 0.3f, srgb(0x8C3B2E)) // chimney
        b.block(-0.45f, 0f, 0.8f, 0.5f, 0.95f, 0.05f, srgb(0x7A4A2A)) // door
        b.box(0.5f, 0.75f, 0.81f, 0.7f, 0.5f, 0.04f, srgb(0x9FD3E6)) // window
        for (k in 0 until 6) { // striped awning
            val color = if (k % 2 == 0) srgb(0xE86A92) else srgb(0xFFF4F0)
            b.box(-0.9f + k * 0.36f, 1.25f, 0.95f, 0.36f, 0.08f, 0.35f, color)
        }
        b.box(0.5f, 1.15f, 0.84f, 0.5f, 0.14f, 0.06f, srgb(0xE0A85A)) // loaf sign
        return b
    }

    private fun waterTower(): MeshBuilder {
        val b = MeshBuilder()
        val legColor = srgb(0x8C6E54)
        for (lx in floatArrayOf(-0.5f, 0.5f)) for (lz in floatArrayOf(-0.5f, 0.5f)) {
            b.block(lx, 0f, lz, 0.1f, 1.85f, 0.1f, legColor)
        }
        b.box(0f, 0.9f, 0.5f, 1.1f, 0.07f, 0.07f, legColor)
        b.box(0f, 0.9f, -0.5f, 1.1f, 0.07f, 0.07f, legColor)
        b.prism(0f, 1.85f, 0f, 0.85f, 1.0f, 10, srgb(0x8FB3C9), 18f)
        b.prism(0f, 2.15f, 0f, 0.87f, 0.08f, 10, srgb(0x5D7F96), 18f) // band
        b.cone(0f, 2.85f, 0f, 0.95f, 0.5f, 10, srgb(0x5D7F96), 18f)
        b.block(-0.75f, 0f, 0.55f, 0.45f, 0.6f, 0.4f, srgb(0xC0392B)) // pump housing
        b.block(-0.75f, 0.6f, 0.55f, 0.08f, 1.5f, 0.08f, srgb(0x6F7B87)) // pipe up
        b.box(-0.45f, 2.1f, 0.55f, 0.6f, 0.08f, 0.08f, srgb(0x6F7B87))
        return b
    }

    private fun barberShop(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, 0f, 0f, 2.0f, 1.4f, 1.5f, srgb(0xE7EEF5))
        b.block(0f, 1.4f, 0f, 2.15f, 0.15f, 1.65f, srgb(0x2F5D8A))
        b.block(-0.4f, 0f, 0.75f, 0.5f, 0.95f, 0.05f, srgb(0x2F5D8A))
        b.box(0.45f, 0.75f, 0.76f, 0.7f, 0.5f, 0.04f, srgb(0x9FD3E6))
        for (k in 0 until 6) {
            val color = if (k % 2 == 0) srgb(0xD7263D) else srgb(0xFFFFFF)
            b.prism(1.15f, 0.2f + k * 0.14f, 0.7f, 0.07f, 0.14f, 6, color, k * 20f)
        }
        return b
    }

    private fun radioStation(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, 0f, 0f, 1.8f, 1.2f, 1.5f, srgb(0xA8C3A0))
        b.block(0f, 1.2f, 0f, 1.9f, 0.1f, 1.6f, srgb(0x55705A))
        b.block(-0.3f, 0f, 0.75f, 0.45f, 0.85f, 0.05f, srgb(0x55705A))
        b.block(0.45f, 1.3f, 0f, 0.08f, 1.9f, 0.08f, srgb(0xB0B0B0)) // mast
        for (k in 0 until 4) b.box(0.45f, 1.55f + k * 0.42f, 0f, 0.36f - k * 0.06f, 0.04f, 0.04f, srgb(0xB0B0B0))
        b.sphere(0.45f, 3.25f, 0f, 0.09f, 4, 6, srgb(0xE84855))
        return b
    }

    private fun clockTower(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, 0f, 0f, 1.7f, 1.2f, 1.5f, srgb(0xC9B79C))
        b.block(0f, 1.2f, 0f, 1.0f, 1.5f, 1.0f, srgb(0xB8A486))
        b.disc(0f, 2.2f, 0.52f, 0.34f, 0.04f, 12, srgb(0xFFF8E7))
        b.box(0f, 2.3f, 0.55f, 0.04f, 0.22f, 0.02f, srgb(0x222222))
        b.box(0.07f, 2.2f, 0.55f, 0.16f, 0.04f, 0.02f, srgb(0x222222))
        b.cone(0f, 2.7f, 0f, 0.8f, 0.7f, 4, srgb(0x4E6E8E), 45f)
        b.block(0f, 0f, 0.75f, 0.5f, 0.9f, 0.05f, srgb(0x6B4B2E))
        return b
    }

    /** The gatekeeper, this time as a cheesy town tour guide with a flag. */
    private fun gatekeeper(): MeshBuilder {
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

    private fun portalRingMesh(): MeshBuilder {
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

    private fun characterBody() = MeshBuilder()
        .block(0f, 0.16f, 0f, 0.17f, 0.18f, 0.11f, srgb(0xE84855)) // shirt
        .block(0f, 0.34f, 0f, 0.13f, 0.13f, 0.13f, srgb(0xF1C27D)) // head
        .block(0f, 0.46f, 0f, 0.14f, 0.04f, 0.14f, srgb(0x3D2817)) // hair
        .box(0f, 0.4f, 0.07f, 0.08f, 0.02f, 0.01f, srgb(0x222222)) // eyes face +Z

    companion object {
        const val SLOT_COUNT = 7
        const val SPACING = 3.4f
        const val BUILDING_Z = -1.0f
        const val WALK_Z = 0.8f
        /** World height of each slot's floating label. */
        private val LABEL_Y = floatArrayOf(1.95f, 2.85f, 3.05f, 2.0f, 2.85f, 3.05f, 2.6f)

        fun slotX(slot: Int) = slot * SPACING
    }
}
