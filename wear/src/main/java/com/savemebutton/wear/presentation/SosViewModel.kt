package com.savemebutton.wear.presentation

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savemebutton.shared.SosState
import com.savemebutton.wear.WearApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val TRIPLE_TAP_WINDOW_MS = 1200L

class SosViewModel(app: WearApplication) : AndroidViewModel(app) {

    private val orchestrator = app.sosOrchestrator
    private val configFlow = app.configRepository.config

    val state: StateFlow<SosState> = orchestrator.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, orchestrator.state.value)

    private val tapTimestamps = ArrayDeque<Long>()

    fun trigger() = orchestrator.trigger()

    fun stop() = orchestrator.stop()

    fun configHoldSeconds(): Int = configFlow.value.holdSeconds
    fun configCountdownSeconds(): Int = configFlow.value.countdownSeconds
    fun configCancelTaps(): Int = configFlow.value.cancelTaps

    fun onScreenTap(now: Long = System.currentTimeMillis()) {
        val st = state.value
        val tappable = st is SosState.Countdown ||
            st is SosState.AcquiringLocation ||
            st is SosState.SendingSms ||
            st is SosState.Calling
        if (!tappable) return
        tapTimestamps.addLast(now)
        while (tapTimestamps.isNotEmpty() && now - tapTimestamps.first() > TRIPLE_TAP_WINDOW_MS) {
            tapTimestamps.removeFirst()
        }
        val needed = configFlow.value.cancelTaps
        if (tapTimestamps.size >= needed) {
            tapTimestamps.clear()
            orchestrator.acceptTripleTap()
        }
    }

    fun reset() = orchestrator.reset()
}
