package com.mikeyd.bezeltown.render

/** Knobs for the PS1-style look. */
object RetroLook {
    /** Screen pixels per rendered pixel. 1 turns the low-resolution effect off. */
    const val PIXEL_SCALE = 3

    /** Vertex snap grid as a fraction of the rendered resolution. Lower = more wobble. */
    const val SNAP_FRACTION = 0.5f

    /** Draw every Nth display frame. 2 caps rendering at 30 fps on a 60 Hz screen. */
    const val FRAME_INTERVAL = 2

    /** How much each face's brightness varies, for a grimy hand-made feel. */
    const val FACE_GRIME = 0.07f
}

/** Linear fog from [near] to [far] world units from the camera, fading to the sky color. */
data class Fog(val near: Float, val far: Float)
