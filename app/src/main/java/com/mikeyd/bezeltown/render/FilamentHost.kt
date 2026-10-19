package com.mikeyd.bezeltown.render

import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Box
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderTarget
import com.google.android.filament.RenderableManager
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.ToneMapper
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders one [FilamentScene] into a TextureView. A TextureView (rather than a
 * SurfaceView) moves with Compose, so swipe-to-dismiss slides the 3D scene too.
 *
 * PS1 look: the scene renders into a small offscreen texture
 * ([RetroLook.PIXEL_SCALE] times smaller than the screen), then a second pass
 * draws that texture over the whole screen with nearest-neighbor sampling,
 * 15-bit color and ordered dithering (see retro_blit.mat).
 */
class FilamentHost(
    private val engine: Engine,
    textureView: TextureView,
    val scene: FilamentScene,
) : Choreographer.FrameCallback, UiHelper.RendererCallback {

    private val renderer = engine.createRenderer()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    // Linear tone mapping keeps the authored flat colors as they are.
    private val colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)

    /** Draws the scene into the low-resolution target. */
    private val sceneView: View = engine.createView()

    /** Draws the low-resolution frame onto the screen. */
    private val screenView: View = engine.createView()
    private val screenScene = engine.createScene()
    private val screenCameraEntity = EntityManager.get().create()
    private val screenCamera = engine.createCamera(screenCameraEntity)
    private val blitMaterial = FilamentRuntime.retroBlitMaterial.createInstance()
    private val quadEntity = EntityManager.get().create()
    private val quadVertices: VertexBuffer
    private val quadIndices: IndexBuffer

    private var colorTexture: Texture? = null
    private var depthTexture: Texture? = null
    private var renderTarget: RenderTarget? = null

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer = Choreographer.getInstance()

    private var swapChain: SwapChain? = null
    private var lastFrameNanos = 0L
    private var frameCount = 0L
    private var running = false
    private var destroyed = false

    init {
        sceneView.scene = scene.scene
        sceneView.camera = camera
        sceneView.colorGrading = colorGrading
        sceneView.setShadowingEnabled(false)
        // Blocky pixels are the point; smoothing would undo them.
        sceneView.antiAliasing = View.AntiAliasing.NONE
        sceneView.dithering = View.Dithering.NONE

        // The low-res frame is already display-ready, so the screen pass skips post-processing.
        screenView.scene = screenScene
        screenView.camera = screenCamera
        screenView.isPostProcessingEnabled = false
        screenView.setShadowingEnabled(false)

        val quad = fullScreenQuad()
        quadVertices = quad.first
        quadIndices = quad.second
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 1f, 1f, 1f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, quadVertices, quadIndices, 0, 6)
            .material(0, blitMaterial)
            .culling(false)
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, quadEntity)
        screenScene.addEntity(quadEntity)

        uiHelper.renderCallback = this
        uiHelper.attachTo(textureView)
    }

    /** Starts or stops the frame loop; stopped while the activity isn't resumed. */
    fun setRunning(run: Boolean) {
        if (destroyed || run == running) return
        running = run
        if (run) {
            lastFrameNanos = 0L
            choreographer.postFrameCallback(this)
        } else {
            choreographer.removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running || destroyed) return
        choreographer.postFrameCallback(this)
        val dt = if (lastFrameNanos == 0L) 0f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = frameTimeNanos

        // Game logic runs every display frame; drawing is capped (RetroLook.FRAME_INTERVAL).
        scene.frameListener?.invoke(dt)
        scene.onFrame(dt)
        if (frameCount++ % RetroLook.FRAME_INTERVAL != 0L) return

        scene.configureCamera(camera)
        val chain = swapChain ?: return
        if (renderTarget == null) return
        if (uiHelper.isReadyToRender && renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(sceneView)
            renderer.render(screenView)
            renderer.endFrame()
        }
    }

    override fun onNativeWindowChanged(surface: Surface) {
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = engine.createSwapChain(surface)
    }

    override fun onDetachedFromSurface() {
        swapChain?.let {
            engine.destroySwapChain(it)
            // Make sure the swap chain is gone before the surface is released.
            engine.flushAndWait()
            swapChain = null
        }
    }

    override fun onResized(width: Int, height: Int) {
        val lowWidth = (width / RetroLook.PIXEL_SCALE).coerceAtLeast(1)
        val lowHeight = (height / RetroLook.PIXEL_SCALE).coerceAtLeast(1)
        createRenderTarget(lowWidth, lowHeight)

        sceneView.viewport = Viewport(0, 0, lowWidth, lowHeight)
        screenView.viewport = Viewport(0, 0, width, height)
        // Labels and camera aspect use the full screen size; snapping uses the rendered size.
        scene.resize(width, height)
        scene.setRenderResolution(lowWidth, lowHeight)
    }

    private fun createRenderTarget(width: Int, height: Int) {
        val color = Texture.Builder()
            .width(width).height(height).levels(1)
            .usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.SAMPLEABLE)
            .format(Texture.InternalFormat.RGBA8)
            .build(engine)
        val depth = Texture.Builder()
            .width(width).height(height).levels(1)
            .usage(Texture.Usage.DEPTH_ATTACHMENT)
            .format(Texture.InternalFormat.DEPTH24)
            .build(engine)
        val target = RenderTarget.Builder()
            .texture(RenderTarget.AttachmentPoint.COLOR, color)
            .texture(RenderTarget.AttachmentPoint.DEPTH, depth)
            .build(engine)

        sceneView.renderTarget = target
        val sampler = TextureSampler(
            TextureSampler.MinFilter.NEAREST,
            TextureSampler.MagFilter.NEAREST,
            TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )
        blitMaterial.setParameter("frame", color, sampler)
        blitMaterial.setParameter("frameSize", width.toFloat(), height.toFloat())

        destroyRenderTarget()
        colorTexture = color
        depthTexture = depth
        renderTarget = target
    }

    private fun destroyRenderTarget() {
        renderTarget?.let { engine.destroyRenderTarget(it) }
        colorTexture?.let { engine.destroyTexture(it) }
        depthTexture?.let { engine.destroyTexture(it) }
        renderTarget = null
        colorTexture = null
        depthTexture = null
    }

    fun destroy() {
        if (destroyed) return
        setRunning(false)
        destroyed = true
        uiHelper.detach()
        scene.destroy()

        engine.destroyEntity(quadEntity)
        EntityManager.get().destroy(quadEntity)
        engine.destroyVertexBuffer(quadVertices)
        engine.destroyIndexBuffer(quadIndices)
        engine.destroyMaterialInstance(blitMaterial)
        engine.destroyScene(screenScene)
        engine.destroyView(screenView)
        engine.destroyCameraComponent(screenCameraEntity)
        EntityManager.get().destroy(screenCameraEntity)

        engine.destroyView(sceneView)
        destroyRenderTarget()
        engine.destroyRenderer(renderer)
        engine.destroyColorGrading(colorGrading)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
    }

    /** Two triangles covering the screen, with positions already in clip space. */
    private fun fullScreenQuad(): Pair<VertexBuffer, IndexBuffer> {
        val positions = floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f)
        val vertexData = ByteBuffer.allocateDirect(positions.size * 4).order(ByteOrder.nativeOrder())
        positions.forEach { vertexData.putFloat(it) }
        vertexData.flip()
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(4)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)
        vb.setBufferAt(engine, 0, vertexData)

        val indexData = ByteBuffer.allocateDirect(6 * 2).order(ByteOrder.nativeOrder())
        shortArrayOf(0, 1, 2, 0, 2, 3).forEach { indexData.putShort(it) }
        indexData.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, indexData)
        return vb to ib
    }
}
