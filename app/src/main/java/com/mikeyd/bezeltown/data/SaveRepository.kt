package com.mikeyd.bezeltown.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.mikeyd.bezeltown.core.SaveCodec
import com.mikeyd.bezeltown.core.content.Settings
import com.mikeyd.bezeltown.core.model.GameState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

private object GameStateSerializer : Serializer<GameState> {
    override val defaultValue: GameState get() = Settings.newGame()

    override suspend fun readFrom(input: InputStream): GameState = try {
        SaveCodec.decode(input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable save", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable save", e)
    }

    override suspend fun writeTo(t: GameState, output: OutputStream) {
        output.write(SaveCodec.encode(t).encodeToByteArray())
    }
}

private val Context.saveStore: DataStore<GameState> by dataStore(
    fileName = "save.json",
    serializer = GameStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { Settings.newGame() },
)

class SaveRepository(private val context: Context) {
    suspend fun load(): GameState = context.saveStore.data.first()

    suspend fun save(state: GameState) {
        context.saveStore.updateData { state }
    }
}
