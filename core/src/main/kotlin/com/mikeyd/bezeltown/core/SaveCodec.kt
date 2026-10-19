package com.mikeyd.bezeltown.core

import com.mikeyd.bezeltown.core.content.Settings
import com.mikeyd.bezeltown.core.model.GameState
import kotlinx.serialization.json.Json

object SaveCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: GameState): String = json.encodeToString(GameState.serializer(), state)

    fun decode(text: String): GameState = Settings.normalize(json.decodeFromString(GameState.serializer(), text))
}
