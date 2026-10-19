package com.mikeyd.bezeltown.render

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = V3(x * k, y * k, z * k)
    fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun normalized(): V3 {
        val len = sqrt(dot(this))
        return if (len == 0f) this else this * (1f / len)
    }
}

/** GPU buffers for one mesh, plus its bounds. */
class Mesh(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val indexCount: Int,
    val bounds: Box,
) {
    fun destroy(engine: Engine) {
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
    }
}

/**
 * Builds flat-shaded low-poly meshes out of simple shapes. Every triangle gets
 * its own vertices so faces stay hard-edged, and a fixed sun direction is baked
 * into the vertex colors, so the material can stay unlit. Each face also gets a
 * small random brightness change ([RetroLook.FACE_GRIME]), seeded so a mesh
 * looks the same every time it's built.
 *
 * Shapes take an "outward" reference point; faces are oriented away from it
 * for lighting, so winding order never matters.
 */
class MeshBuilder(seed: Int = 1) {
    private val random = java.util.Random(seed.toLong())
    private val data = ArrayList<Float>()
    private var vertexCount = 0
    private var minX = Float.MAX_VALUE; private var minY = Float.MAX_VALUE; private var minZ = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE; private var maxY = -Float.MAX_VALUE; private var maxZ = -Float.MAX_VALUE

    fun triangle(a: V3, b: V3, c: V3, color: LinearColor, outwardFrom: V3) {
        var n = (b - a).cross(c - a).normalized()
        val centroid = (a + b + c) * (1f / 3f)
        if (n.dot(centroid - outwardFrom) < 0f) n = n * -1f
        val grime = 1f + (random.nextFloat() * 2f - 1f) * RetroLook.FACE_GRIME
        val shade = (AMBIENT + DIFFUSE * max(0f, n.dot(SUN))) * grime
        val lit = color.scaled(shade)
        for (p in arrayOf(a, b, c)) {
            data.add(p.x); data.add(p.y); data.add(p.z)
            data.add(lit.r); data.add(lit.g); data.add(lit.b); data.add(1f)
            minX = min(minX, p.x); minY = min(minY, p.y); minZ = min(minZ, p.z)
            maxX = max(maxX, p.x); maxY = max(maxY, p.y); maxZ = max(maxZ, p.z)
            vertexCount++
        }
    }

    fun quad(a: V3, b: V3, c: V3, d: V3, color: LinearColor, outwardFrom: V3) {
        triangle(a, b, c, color, outwardFrom)
        triangle(a, c, d, color, outwardFrom)
    }

    /** Axis-aligned box centered at (cx, cy, cz), optionally turned about Y. */
    fun box(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        color: LinearColor,
        rotYDeg: Float = 0f,
    ): MeshBuilder {
        val hx = sx / 2; val hy = sy / 2; val hz = sz / 2
        val r = rotYDeg * PI.toFloat() / 180f
        val c = cos(r); val s = sin(r)
        fun p(x: Float, y: Float, z: Float) = V3(cx + x * c + z * s, cy + y, cz - x * s + z * c)
        val center = V3(cx, cy, cz)
        val v = arrayOf(
            p(-hx, -hy, -hz), p(hx, -hy, -hz), p(hx, hy, -hz), p(-hx, hy, -hz),
            p(-hx, -hy, hz), p(hx, -hy, hz), p(hx, hy, hz), p(-hx, hy, hz),
        )
        quad(v[4], v[5], v[6], v[7], color, center) // front
        quad(v[1], v[0], v[3], v[2], color, center) // back
        quad(v[0], v[4], v[7], v[3], color, center) // left
        quad(v[5], v[1], v[2], v[6], color, center) // right
        quad(v[3], v[7], v[6], v[2], color, center) // top
        quad(v[0], v[1], v[5], v[4], color, center) // bottom
        return this
    }

    /** Box given by its bottom-center point instead of its center. */
    fun block(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: LinearColor, rotYDeg: Float = 0f) =
        box(x, y + sy / 2, z, sx, sy, sz, color, rotYDeg)

    /** Upright n-sided prism (a low-poly cylinder) with its base at y. */
    fun prism(cx: Float, y: Float, cz: Float, radius: Float, height: Float, sides: Int, color: LinearColor, phaseDeg: Float = 0f): MeshBuilder {
        val center = V3(cx, y + height / 2, cz)
        val ring = ring(sides, radius, phaseDeg)
        val bottomC = V3(cx, y, cz); val topC = V3(cx, y + height, cz)
        for (i in 0 until sides) {
            val (ax, az) = ring[i]; val (bx, bz) = ring[(i + 1) % sides]
            val a0 = V3(cx + ax, y, cz + az); val b0 = V3(cx + bx, y, cz + bz)
            val a1 = V3(cx + ax, y + height, cz + az); val b1 = V3(cx + bx, y + height, cz + bz)
            quad(a0, b0, b1, a1, color, center)
            triangle(topC, a1, b1, color, center)
            triangle(bottomC, b0, a0, color, center)
        }
        return this
    }

    /** n-sided prism lying along Z (a disc facing the camera), centered at (cx, cy, cz). */
    fun disc(cx: Float, cy: Float, cz: Float, radius: Float, thickness: Float, sides: Int, color: LinearColor, phaseDeg: Float = 0f): MeshBuilder {
        val center = V3(cx, cy, cz)
        val ring = ring(sides, radius, phaseDeg)
        val zf = cz + thickness / 2; val zb = cz - thickness / 2
        val frontC = V3(cx, cy, zf); val backC = V3(cx, cy, zb)
        for (i in 0 until sides) {
            val (ax, ay) = ring[i]; val (bx, by) = ring[(i + 1) % sides]
            val af = V3(cx + ax, cy + ay, zf); val bf = V3(cx + bx, cy + by, zf)
            val ab = V3(cx + ax, cy + ay, zb); val bb = V3(cx + bx, cy + by, zb)
            quad(ab, bb, bf, af, color, center)
            triangle(frontC, af, bf, color, center)
            triangle(backC, bb, ab, color, center)
        }
        return this
    }

    /** Upright n-sided cone (sides = 4 gives a pyramid) with its base at y. */
    fun cone(cx: Float, y: Float, cz: Float, radius: Float, height: Float, sides: Int, color: LinearColor, phaseDeg: Float = 0f): MeshBuilder {
        val center = V3(cx, y + height / 3, cz)
        val apex = V3(cx, y + height, cz)
        val baseC = V3(cx, y, cz)
        val ring = ring(sides, radius, phaseDeg)
        for (i in 0 until sides) {
            val (ax, az) = ring[i]; val (bx, bz) = ring[(i + 1) % sides]
            val a = V3(cx + ax, y, cz + az); val b = V3(cx + bx, y, cz + bz)
            triangle(a, b, apex, color, center)
            triangle(baseC, b, a, color, center)
        }
        return this
    }

    /** Gable roof running along X: a triangular prism sitting on y. */
    fun gableRoof(cx: Float, y: Float, cz: Float, width: Float, depth: Float, height: Float, color: LinearColor): MeshBuilder {
        val hx = width / 2; val hz = depth / 2
        val center = V3(cx, y + height / 3, cz)
        val lf = V3(cx - hx, y, cz + hz); val rf = V3(cx + hx, y, cz + hz)
        val lb = V3(cx - hx, y, cz - hz); val rb = V3(cx + hx, y, cz - hz)
        val lt = V3(cx - hx, y + height, cz); val rt = V3(cx + hx, y + height, cz)
        quad(lf, rf, rt, lt, color, center)
        quad(rb, lb, lt, rt, color, center)
        triangle(lf, lt, lb, color, center)
        triangle(rf, rb, rt, color, center)
        quad(lb, rb, rf, lf, color, center)
        return this
    }

    /** Faceted lat-long sphere. */
    fun sphere(cx: Float, cy: Float, cz: Float, radius: Float, rings: Int, segments: Int, color: LinearColor): MeshBuilder {
        val center = V3(cx, cy, cz)
        fun point(ring: Int, seg: Int): V3 {
            val theta = PI.toFloat() * ring / rings
            val phi = 2f * PI.toFloat() * seg / segments
            return V3(
                cx + radius * sin(theta) * cos(phi),
                cy + radius * cos(theta),
                cz + radius * sin(theta) * sin(phi),
            )
        }
        for (r in 0 until rings) for (s in 0 until segments) {
            val a = point(r, s); val b = point(r, s + 1)
            val c = point(r + 1, s + 1); val d = point(r + 1, s)
            when (r) {
                0 -> triangle(a, c, d, color, center)
                rings - 1 -> triangle(a, b, d, color, center)
                else -> quad(a, b, c, d, color, center)
            }
        }
        return this
    }

    fun build(engine: Engine): Mesh {
        check(vertexCount in 1..65535) { "mesh has $vertexCount vertices" }
        val vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        for (f in data) vertices.putFloat(f)
        vertices.flip()

        val indices = ByteBuffer.allocateDirect(vertexCount * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until vertexCount) indices.putShort(i.toShort())
        indices.flip()

        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexAttribute.COLOR, 0, AttributeType.FLOAT4, 12, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vertices)

        val ib = IndexBuffer.Builder()
            .indexCount(vertexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, indices)

        val bounds = Box(
            (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
            max(0.01f, (maxX - minX) / 2), max(0.01f, (maxY - minY) / 2), max(0.01f, (maxZ - minZ) / 2),
        )
        return Mesh(vb, ib, vertexCount, bounds)
    }

    private fun ring(sides: Int, radius: Float, phaseDeg: Float): List<Pair<Float, Float>> =
        (0 until sides).map {
            val a = 2f * PI.toFloat() * it / sides + phaseDeg * PI.toFloat() / 180f
            radius * cos(a) to radius * sin(a)
        }

    companion object {
        private const val STRIDE = 28 // float3 position + float4 color
        private const val AMBIENT = 0.58f
        private const val DIFFUSE = 0.42f
        private val SUN = V3(-0.35f, 0.85f, 0.55f).normalized()
    }
}
