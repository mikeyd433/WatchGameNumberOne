package com.mikeyd.bezeltown.render

import android.opengl.Matrix
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox

/**
 * Owns a Filament scene and everything in it. Subclasses build their meshes in
 * init and animate them in [onFrame]; [FilamentHost] drives the frame loop.
 *
 * Every material instance belongs to the scene, so per-scene settings (fog,
 * vertex snapping) never leak into another scene rendering at the same time.
 */
abstract class FilamentScene(
    protected val engine: Engine,
    private val skyColor: LinearColor,
    private val fog: Fog,
) {
    val scene: Scene = engine.createScene()

    /** Called once per rendered frame before drawing, with seconds since the last frame. */
    var frameListener: ((Float) -> Unit)? = null

    protected var viewportWidth = 1
        private set
    protected var viewportHeight = 1
        private set

    private val skybox = Skybox.Builder().color(skyColor.r, skyColor.g, skyColor.b, 1f).build(engine)
    private val meshes = mutableListOf<Mesh>()
    private val entities = mutableListOf<Int>()
    private val materialInstances = mutableListOf<MaterialInstance>()
    private val scratch = FloatArray(16)
    private var snapX = 1000f
    private var snapY = 1000f

    /** The untinted material most meshes use. */
    protected val baseMaterial: MaterialInstance

    init {
        scene.skybox = skybox
        baseMaterial = tintedMaterial()
    }

    abstract fun configureCamera(camera: Camera)

    open fun onFrame(dt: Float) {}

    fun resize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    /**
     * Sets how coarse the vertex snapping is, from the size the scene is
     * rendered at. Called by [FilamentHost] whenever that changes.
     */
    fun setRenderResolution(width: Int, height: Int) {
        snapX = width * RetroLook.SNAP_FRACTION / 2f
        snapY = height * RetroLook.SNAP_FRACTION / 2f
        materialInstances.forEach { it.setParameter("snapGrid", snapX, snapY) }
    }

    /** A material instance that multiplies everything drawn with it by [tint]. */
    protected fun tintedMaterial(tint: LinearColor = LinearColor(1f, 1f, 1f)): MaterialInstance =
        FilamentRuntime.flatMaterial.createInstance().also {
            setTint(it, tint)
            it.setParameter("snapGrid", snapX, snapY)
            it.setParameter("fogColor", skyColor.r, skyColor.g, skyColor.b)
            it.setParameter("fogRange", fog.near, fog.far)
            materialInstances += it
        }

    protected fun setTint(instance: MaterialInstance, tint: LinearColor) {
        instance.setParameter("tint", tint.r, tint.g, tint.b, 1f)
    }

    /** An empty entity with only a transform, for grouping. */
    protected fun addGroup(parent: Int? = null): Int {
        val entity = EntityManager.get().create()
        createTransform(entity, parent)
        entities += entity
        return entity
    }

    protected fun addMesh(
        builder: MeshBuilder,
        material: MaterialInstance = baseMaterial,
        parent: Int? = null,
    ): Int {
        val mesh = builder.build(engine).also { meshes += it }
        val entity = EntityManager.get().create()
        createTransform(entity, parent)
        RenderableManager.Builder(1)
            .boundingBox(mesh.bounds)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vertexBuffer, mesh.indexBuffer, 0, mesh.indexCount)
            .material(0, material)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
        entities += entity
        return entity
    }

    /** Sets an entity's local transform: translate, then rotate Y, X, Z, then scale. */
    protected fun place(
        entity: Int,
        x: Float = 0f, y: Float = 0f, z: Float = 0f,
        rotYDeg: Float = 0f, rotXDeg: Float = 0f, rotZDeg: Float = 0f,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
    ) {
        val m = scratch
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, y, z)
        if (rotYDeg != 0f) Matrix.rotateM(m, 0, rotYDeg, 0f, 1f, 0f)
        if (rotXDeg != 0f) Matrix.rotateM(m, 0, rotXDeg, 1f, 0f, 0f)
        if (rotZDeg != 0f) Matrix.rotateM(m, 0, rotZDeg, 0f, 0f, 1f)
        Matrix.scaleM(m, 0, sx, sy, sz)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), m)
    }

    private fun createTransform(entity: Int, parent: Int?) {
        val tm = engine.transformManager
        if (parent == null) {
            tm.create(entity)
        } else {
            val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
            tm.create(entity, tm.getInstance(parent), identity)
        }
    }

    open fun destroy() {
        // Children before parents, so no transform outlives its parent.
        for (entity in entities.asReversed()) {
            engine.destroyEntity(entity)
            EntityManager.get().destroy(entity)
        }
        entities.clear()
        meshes.forEach { it.destroy(engine) }
        meshes.clear()
        materialInstances.forEach { engine.destroyMaterialInstance(it) }
        materialInstances.clear()
        engine.destroySkybox(skybox)
        engine.destroyScene(scene)
    }
}

/**
 * A perspective camera whose matrices we also keep on the CPU, so screens can
 * project 3D points to place Compose labels over the scene.
 */
class ProjectingCamera {
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val point = FloatArray(4)
    private val clip = FloatArray(4)

    fun apply(
        camera: Camera,
        eye: V3, target: V3,
        fovYDeg: Float, aspect: Float,
        near: Float = 0.1f, far: Float = 100f,
    ) {
        camera.setProjection(fovYDeg.toDouble(), aspect.toDouble(), near.toDouble(), far.toDouble(), Camera.Fov.VERTICAL)
        camera.lookAt(
            eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble(),
            target.x.toDouble(), target.y.toDouble(), target.z.toDouble(),
            0.0, 1.0, 0.0,
        )
        Matrix.perspectiveM(projection, 0, fovYDeg, aspect, near, far)
        Matrix.setLookAtM(view, 0, eye.x, eye.y, eye.z, target.x, target.y, target.z, 0f, 1f, 0f)
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)
    }

    /** Projects a world point to pixel coordinates (origin top-left), or null if behind the camera. */
    fun project(x: Float, y: Float, z: Float, width: Int, height: Int): Pair<Float, Float>? {
        point[0] = x; point[1] = y; point[2] = z; point[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProjection, 0, point, 0)
        val w = clip[3]
        if (w <= 0f) return null
        val ndcX = clip[0] / w
        val ndcY = clip[1] / w
        return (ndcX + 1f) / 2f * width to (1f - ndcY) / 2f * height
    }
}
