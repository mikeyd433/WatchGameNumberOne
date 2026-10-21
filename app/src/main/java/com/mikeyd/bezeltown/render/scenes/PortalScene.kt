package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.mikeyd.bezeltown.render.FilamentScene
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor.Companion.srgb
import com.mikeyd.bezeltown.render.MeshBuilder
import com.mikeyd.bezeltown.render.ProjectingCamera
import com.mikeyd.bezeltown.render.V3
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The gatekeeper's corner: him, his portal, and the coins you crank into his
 * hand. The portal grows and swirls faster as the bonus for leaving grows, and
 * swallows the view when you step in.
 */
class PortalScene(engine: Engine, paidBefore: Boolean) : FilamentScene(engine, srgb(0x6A5A9E), Fog(near = 7f, far = 16f)) {
    /** The bonus for leaving now, as a fraction (0.05 = +5%). Drives the portal's size and swirl. */
    var bonus = 0f

    /** 0..1 while stepping through the portal. */
    var stepIn = 0f

    /** True once the portal can be paid for: it lights up. */
    var open = false

    private val camera = ProjectingCamera()
    private val ring: Int
    private val swirl: Int
    private val swirlMaterial = tintedMaterial(SWIRL_SHUT)
    private val gatekeeper: Int
    private val sparks: List<Int>
    private val coins: List<Coin>

    private var time = 0f
    private var spin = 0f
    private var orbit = 0f
    private var shownSize = 1f
    private var wave = 0f

    private class Coin(val entity: Int) {
        var age = 1f
    }

    init {
        addMesh(ground())
        gatekeeper = addGroup()
        addMesh(gatekeeperModel(), parent = gatekeeper)
        if (paidBefore) addMesh(gatekeeperChainModel(), parent = gatekeeper)
        ring = addMesh(portalRingModel())
        swirl = addMesh(MeshBuilder().disc(0f, 0f, 0f, 0.82f, 0.04f, 7, srgb(0xFFFFFF)), swirlMaterial)
        sparks = List(8) { addMesh(MeshBuilder().box(0f, 0f, 0f, 0.08f, 0.08f, 0.08f, srgb(0xFFE9A8))) }
        coins = List(14) {
            Coin(addMesh(MeshBuilder().disc(0f, 0f, 0f, 0.08f, 0.02f, 8, srgb(0xFFD45C)))).also { place(it.entity, sx = 0f, sy = 0f, sz = 0f) }
        }
    }

    /** One coin flies from the bottom of the screen into the gatekeeper's hand. */
    fun addCoin() {
        val coin = coins.firstOrNull { it.age >= 1f } ?: coins.minByOrNull { -it.age } ?: return
        coin.age = 0f
    }

    override fun onFrame(dt: Float) {
        time += dt

        // Bigger, brighter and faster with a bigger bonus; huge while stepping in.
        val target = (1f + min(bonus * 6f, 0.6f)) * (1f + stepIn * 5f)
        shownSize += (target - shownSize) * (dt * 4f).coerceAtMost(1f)
        val speed = if (open) 1f + bonus * 25f else 0.3f
        spin += dt * 40f * speed * (1f + stepIn * 6f)
        orbit += dt * 1.5f * speed
        setTint(swirlMaterial, if (open) SWIRL_SHUT.lerp(SWIRL_OPEN, min(1f, 0.4f + bonus * 8f)) else SWIRL_SHUT)
        place(ring, x = PORTAL_X, y = PORTAL_Y, rotZDeg = spin * 0.3f, sx = shownSize, sy = shownSize, sz = shownSize)
        place(swirl, x = PORTAL_X, y = PORTAL_Y, z = -0.02f, rotZDeg = -spin, sx = shownSize, sy = shownSize, sz = 1f)

        sparks.forEachIndexed { i, e ->
            if (!open) {
                place(e, sx = 0f, sy = 0f, sz = 0f)
                return@forEachIndexed
            }
            val a = orbit + i * (Math.PI.toFloat() * 2f / sparks.size)
            val r = 1.1f * shownSize
            place(e, x = PORTAL_X + cos(a) * r, y = PORTAL_Y + sin(a) * r, z = 0.1f, rotZDeg = a * 90f)
        }

        // He bobs on his heels, and waves you off as you step in.
        wave += ((if (stepIn > 0f) 1f else 0f) - wave) * (dt * 6f).coerceAtMost(1f)
        place(gatekeeper, x = KEEPER_X, y = sin(time * 3f) * 0.02f, rotZDeg = sin(time * 14f) * 12f * wave, sx = KEEPER_SCALE, sy = KEEPER_SCALE, sz = KEEPER_SCALE)

        for (c in coins) {
            if (c.age >= 1f) continue
            c.age = (c.age + dt / COIN_FLIGHT_SECONDS).coerceAtMost(1f)
            val t = c.age
            // From below the screen up and over into his hand.
            val x = HAND_X + (1.0f - HAND_X) * (1f - t)
            val y = -1.2f + (HAND_Y + 1.2f) * t + sin(t * Math.PI.toFloat()) * 0.9f
            if (t >= 1f) place(c.entity, sx = 0f, sy = 0f, sz = 0f)
            else place(c.entity, x = x, y = y, z = 0.4f, rotYDeg = t * 720f)
        }
    }

    override fun configureCamera(camera: Camera) {
        val aspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1)
        // Pulled back and aimed above the action, so he and the portal sit in the middle
        // band between the speech bubble at the top and the status at the bottom.
        this.camera.apply(camera, V3(0f, 2.0f, 6.6f), V3(0f, 1.55f, 0f), fovYDeg = 44f, aspect = aspect)
    }

    private fun ground(): MeshBuilder {
        val b = MeshBuilder()
        b.block(0f, -0.3f, -2f, 14f, 0.3f, 10f, srgb(0x7CC36B))
        b.block(0f, 0f, 0.6f, 14f, 0.02f, 1.2f, srgb(0x8A8F98))
        for (k in 0 until 6) b.cone(-6f + k * 2.4f, -0.1f, -6f, 1.8f, 1.4f + (k % 2) * 0.6f, 6, srgb(0x5FA457))
        return b
    }

    companion object {
        private const val PORTAL_X = 0.7f
        private const val PORTAL_Y = 1.15f
        private const val KEEPER_X = -1.15f
        private const val KEEPER_SCALE = 1.4f
        // His flag hand, where the coins land.
        private const val HAND_X = KEEPER_X + 0.22f * KEEPER_SCALE
        private const val HAND_Y = 0.45f * KEEPER_SCALE
        private const val COIN_FLIGHT_SECONDS = 0.45f
        private val SWIRL_SHUT = srgb(0x3A2E5C)
        private val SWIRL_OPEN = srgb(0xC77DFF)
    }
}
