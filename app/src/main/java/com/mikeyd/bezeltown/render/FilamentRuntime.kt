package com.mikeyd.bezeltown.render

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.Material
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One Filament engine and its materials for the whole app. Every screen's
 * surface shares it, which lets two screens render at once during a
 * swipe-to-dismiss.
 */
object FilamentRuntime {
    private var engine: Engine? = null
    lateinit var flatMaterial: Material
        private set
    lateinit var retroBlitMaterial: Material
        private set

    fun engine(context: Context): Engine {
        engine?.let { return it }
        Filament.init()
        val created = Engine.create()
        flatMaterial = loadMaterial(created, context, "materials/flat.filamat")
        retroBlitMaterial = loadMaterial(created, context, "materials/retro_blit.filamat")
        engine = created
        return created
    }

    private fun loadMaterial(engine: Engine, context: Context, asset: String): Material {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buffer.flip()
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }
}
