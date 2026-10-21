package com.mikeyd.bezeltown

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mikeyd.bezeltown.core.content.SettingDef
import com.mikeyd.bezeltown.core.content.Settings
import com.mikeyd.bezeltown.core.economy.AwayReport
import com.mikeyd.bezeltown.core.economy.Economy
import com.mikeyd.bezeltown.core.economy.LeaveReport
import com.mikeyd.bezeltown.core.economy.Portal
import com.mikeyd.bezeltown.core.economy.PlayMode
import com.mikeyd.bezeltown.core.economy.PlayOutcome
import com.mikeyd.bezeltown.core.model.BuildingId
import com.mikeyd.bezeltown.core.model.GameState
import com.mikeyd.bezeltown.core.model.GlobalUpgrade
import com.mikeyd.bezeltown.core.model.UpgradeTrack
import com.mikeyd.bezeltown.core.overworld.Walker
import com.mikeyd.bezeltown.data.SaveRepository
import com.mikeyd.bezeltown.input.Haptics
import com.mikeyd.bezeltown.render.scenes.OverworldScene
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SaveRepository(app)
    private val _state = MutableStateFlow<GameState?>(null)

    /** Null until the save has loaded. */
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _away = MutableStateFlow<AwayReport?>(null)

    /** What was earned while the game was closed, until the player dismisses it. */
    val away: StateFlow<AwayReport?> = _away.asStateFlow()

    private val _arrival = MutableStateFlow<LeaveReport?>(null)

    /** What the last trip through the portal earned, until the player dismisses it. */
    val arrival: StateFlow<LeaveReport?> = _arrival.asStateFlow()

    val haptics = Haptics(app)

    /** Overworld position survives trips into buildings. Starts at the Bakery. */
    val walker = Walker(slotCount = OverworldScene.SLOT_COUNT, startSlot = OverworldScene.FIRST_BUILDING_SLOT)

    init {
        viewModelScope.launch { _state.value = repository.load() }
    }

    fun setting(state: GameState): SettingDef = Settings.current(state)

    /**
     * Accrues idle money while the app is on screen. Runs only while started.
     * On start it first pays out whatever was earned while the game was closed.
     */
    suspend fun runTicker() {
        _state.filterNotNull().first()
        payOutAwayTime()
        var last = SystemClock.elapsedRealtime()
        var sinceSave = 0.0
        while (true) {
            delay(TICK_MILLIS)
            val now = SystemClock.elapsedRealtime()
            val seconds = (now - last) / 1000.0
            last = now
            val wallClock = System.currentTimeMillis()
            _state.update { s -> s?.let { Economy.tick(it, setting(it), seconds).copy(lastSeen = wallClock) } }
            sinceSave += seconds
            if (sinceSave >= AUTOSAVE_SECONDS) {
                sinceSave = 0.0
                save()
            }
        }
    }

    /**
     * Pays everything to the gatekeeper and steps through the portal. Arrives at
     * the far left of the fresh setting, so every run goes left to right.
     * Returns false if the requirements aren't met.
     */
    fun leaveThroughPortal(): Boolean {
        val current = _state.value ?: return false
        val (next, report) = Portal.leave(current, setting(current)) ?: return false
        _state.value = next
        _arrival.value = report
        walker.teleport(OverworldScene.ARCADE_SLOT)
        save()
        return true
    }

    fun buyGlobalUpgrade(upgrade: GlobalUpgrade): Boolean = change { Portal.buy(it, upgrade) }

    fun dismissArrival() {
        _arrival.value = null
    }

    fun dismissAway() {
        _away.value = null
    }

    private fun payOutAwayTime() {
        val current = _state.value ?: return
        val now = System.currentTimeMillis()
        // A brand-new game has never been seen; a clock that went backwards pays nothing.
        val seconds = if (current.lastSeen > 0) (now - current.lastSeen) / 1000.0 else 0.0
        val (next, report) = Economy.offlinePayout(current, setting(current), seconds)
        _state.value = next.copy(lastSeen = now)
        // Short gaps pay out quietly; only a real absence gets the welcome-back card.
        if (report.earned > 0.0 && seconds >= AWAY_CARD_SECONDS) _away.value = report
        save()
    }

    fun unlock(id: BuildingId): Boolean = change { Economy.unlock(it, setting(it), id) }

    fun upgrade(id: BuildingId, track: UpgradeTrack): Boolean = change { Economy.upgrade(it, setting(it), id, track) }

    fun recordPlay(id: BuildingId, score: Int): PlayOutcome? {
        val current = _state.value ?: return null
        val def = setting(current).building(id)
        val game = def.microgame ?: return null
        val outcome = Economy.recordPlay(current, setting(current), id, game, score, PlayMode.STORY)
        _state.value = outcome.state
        save()
        return outcome
    }

    fun save() {
        val snapshot = _state.value ?: return
        viewModelScope.launch {
            repository.save(snapshot.copy(lastSeen = System.currentTimeMillis()))
        }
    }

    private inline fun change(transform: (GameState) -> GameState?): Boolean {
        val current = _state.value ?: return false
        val next = transform(current) ?: return false
        _state.value = next
        save()
        return true
    }

    companion object {
        private const val TICK_MILLIS = 200L
        private const val AUTOSAVE_SECONDS = 15.0
        private const val AWAY_CARD_SECONDS = 60.0
    }
}
