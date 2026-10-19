package com.mikeyd.bezeltown.render

import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper

/**
 * Renders one [FilamentScene] into a TextureView. A TextureView (rather than a
 * SurfaceView) moves with Compose, so swipe-to-dismiss slides the 3D scene too.
 */
class FilamentHost(
    private val engine: Engine,
    textureView: TextureView,
    val scene: FilamentScene,
) : Choreographer.FrameCallback, UiHelper.RendererCallback {

    private val renderer = engine.createRenderer()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera = engine.createCamera(cameraEntity)
    // Linear tone mapping keeps the authored flat colors as they are.
    private val colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val choreographer = Choreographer.getInstance()

    private var swapChain: SwapChain? = null
    private var lastFrameNanos = 0L
    private var running = false
    private var destroyed = false

    init {
        view.scene = scene.scene
        view.camera = camera
        view.colorGrading = colorGrading
        view.setShadowingEnabled(false)
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

        scene.frameListener?.invoke(dt)
        scene.onFrame(dt)
        scene.configureCamera(camera)

        val chain = swapChain ?: return
        if (uiHelper.isReadyToRender && renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
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
        view.viewport = Viewport(0, 0, width, height)
        scene.resize(width, height)
    }

    fun destroy() {
        if (destroyed) return
        setRunning(false)
        destroyed = true
        uiHelper.detach()
        scene.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyColorGrading(colorGrading)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
    }
}
