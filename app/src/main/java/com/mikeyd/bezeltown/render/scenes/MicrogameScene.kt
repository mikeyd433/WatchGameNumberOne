package com.mikeyd.bezeltown.render.scenes

import com.google.android.filament.Engine
import com.mikeyd.bezeltown.core.microgame.Microgame
import com.mikeyd.bezeltown.render.FilamentScene
import com.mikeyd.bezeltown.render.Fog
import com.mikeyd.bezeltown.render.LinearColor

/** A building interior. Shows [game] while one is running, otherwise idles. */
abstract class MicrogameScene(engine: Engine, sky: LinearColor, fog: Fog) : FilamentScene(engine, sky, fog) {
    var game: Microgame? = null

    /** 0..1 how busy the building is idly, for lobby animation. */
    var activity: Float = 0f
}
