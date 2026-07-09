package com.savemebutton.wear.sos

import android.util.Log
import com.savemebutton.shared.Contact
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SirenTarget
import com.savemebutton.shared.Siren
import com.savemebutton.shared.SosConfig
import com.savemebutton.shared.SosCoords
import com.savemebutton.shared.SosRoute
import com.savemebutton.shared.SosState
import com.savemebutton.shared.SosTriggerPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SmbSos"
private const val CANCELED_HOLD_MS = 1500L
private const val MINUTE_PULSE_MS = 60_000L
private const val PULSE_BURST_MS = 2_000L
private const val SIREN_RAMP_SECONDS = 8f

class SosOrchestrator(
    private val scope: CoroutineScope,
    private val telephony: WatchTelephony,
    private val location: LocationProvider,
    private val remoteBridge: RemoteSosBridge,
    private val capabilityProbe: CapabilityProbe,
    private val configFlow: StateFlow<SosConfig>,
    private val siren: Siren,
) {
    private val _state = MutableStateFlow<SosState>(SosState.Idle)
    val state: StateFlow<SosState> = _state

    private var sequenceJob: Job? = null
    private var pulseJob: Job? = null
    private val skipFlag = AtomicBoolean(false)

    fun requestSkip() {
        Log.d(TAG, "skip requested (local or via remote)")
        skipFlag.set(true)
        scope.launch { runCatching { remoteBridge.pushSkip() } }
    }

    fun trigger() {
        if (_state.value !is SosState.Idle) {
            Log.d(TAG, "trigger ignored, state=${_state.value}")
            return
        }
        sequenceJob?.cancel()
        sequenceJob = scope.launch {
            val cfg = configFlow.value
            val route = capabilityProbe.resolveRoute()
            Log.d(TAG, "trigger route=$route")
            val playLocal = cfg.sirenEnabled &&
                (cfg.sirenTarget == SirenTarget.WATCH || cfg.sirenTarget == SirenTarget.BOTH)
            val playRemote = cfg.sirenEnabled &&
                (cfg.sirenTarget == SirenTarget.PHONE || cfg.sirenTarget == SirenTarget.BOTH)
            if (playLocal) siren.start(cfg.sirenSound, cfg.sirenVolume, SIREN_RAMP_SECONDS)
            if (playRemote) runCatching {
                remoteBridge.pushSiren(SirenCommand(true, cfg.sirenSound, cfg.sirenVolume, SIREN_RAMP_SECONDS))
            }
            try {
                if (!runCountdown(cfg.countdownSeconds)) {
                    Log.d(TAG, "canceled during countdown")
                    return@launch
                }
                if (cfg.loudMinutePulse) startMinutePulse(cfg)
                when (route) {
                    SosRoute.WATCH_SELF -> runLocally(cfg)
                    SosRoute.PHONE -> runRemote(cfg)
                }
            } finally {
                pulseJob?.cancel()
                pulseJob = null
                if (playLocal) siren.stop()
                if (playRemote) withContext(NonCancellable) {
                    runCatching { remoteBridge.pushSiren(SirenCommand(false)) }
                }
            }
        }
    }

    fun acceptTripleTap() {
        val st = _state.value
        if (st is SosState.Countdown) {
            sequenceJob?.cancel()
            scope.launch {
                pulseJob?.cancel()
                pulseJob = null
                siren.stop()
                // Tell peer to stop — covers the mirror case where phone owns the sequence.
                runCatching { remoteBridge.pushSiren(SirenCommand(false)) }
                runCatching { remoteBridge.pushStop() }
                _state.value = SosState.Canceled
                delay(CANCELED_HOLD_MS)
                _state.value = SosState.Idle
            }
        } else if (st is SosState.AcquiringLocation || st is SosState.SendingSms || st is SosState.Calling) {
            stop()
        }
    }

    fun stop() {
        scope.launch {
            sequenceJob?.cancelAndJoin()
            pulseJob?.cancel()
            pulseJob = null
            siren.stop()
            // Stop on the peer regardless of target — receiver no-ops if nothing playing.
            runCatching { remoteBridge.pushSiren(SirenCommand(false)) }
            runCatching { remoteBridge.pushStop() }
            _state.value = SosState.Stopped
            delay(CANCELED_HOLD_MS)
            _state.value = SosState.Idle
        }
    }

    fun reset() {
        sequenceJob?.cancel()
        pulseJob?.cancel()
        siren.stop()
        _state.value = SosState.Idle
    }

    /** Returns false if canceled. */
    private suspend fun runCountdown(seconds: Int): Boolean {
        for (sec in seconds downTo 1) {
            _state.value = SosState.Countdown(sec)
            delay(1000)
        }
        return _state.value is SosState.Countdown
    }

    private fun startMinutePulse(cfg: SosConfig) {
        pulseJob?.cancel()
        pulseJob = scope.launch {
            val pulse = Siren()
            try {
                while (true) {
                    delay(MINUTE_PULSE_MS)
                    pulse.start(cfg.sirenSound, cfg.sirenVolume)
                    delay(PULSE_BURST_MS)
                    pulse.stop()
                }
            } finally {
                pulse.stop()
            }
        }
    }

    private suspend fun runLocally(cfg: SosConfig) {
        _state.value = SosState.AcquiringLocation
        val coords = location.fetch()
        executeSequence(cfg, coords) { contact, body, sendSms ->
            if (sendSms) telephony.sendSms(contact.number, body)
            telephony.placeCall(contact.number)
            telephony.waitForAnswer(cfg.perContactWaitSeconds * 1000L)
        }
    }

    /** Local-route hook used after CallActive when voicemail trap escape is on. */
    private suspend fun localAwaitCallEndOrSkip(): Boolean {
        skipFlag.set(false)
        val skipped = telephony.awaitCallEndOrSkip(skipFlag)
        if (skipped) telephony.endCall()
        return skipped
    }

    private suspend fun runRemote(cfg: SosConfig) {
        _state.value = SosState.AcquiringLocation
        val coords = location.fetch()
        val payload = SosTriggerPayload(
            resolvedRoute = SosRoute.PHONE,
            config = cfg,
            watchCoords = coords,
        )
        val sent = remoteBridge.dispatch(payload)
        if (!sent) {
            Log.d(TAG, "remote dispatch failed")
            _state.value = SosState.Done(reachedIndex = -1, answered = false)
            return
        }
        // Phone takes over; states arrive via PhoneListenerService → updateRemoteState().
    }

    /** Called from PhoneListenerService when phone streams progress in PHONE route. */
    fun updateRemoteState(remote: SosState) {
        _state.value = remote
    }

    private suspend fun executeSequence(
        config: SosConfig,
        coords: SosCoords?,
        attempt: suspend (Contact, String, Boolean) -> Boolean,
    ) {
        val coordsLine = coords?.smsLine() ?: "(location unavailable)"
        val body = "${config.smsBody} $coordsLine"
        val smsSent = BooleanArray(config.contacts.size)
        val eligibleIndices = config.contacts
            .mapIndexedNotNull { i, c -> if (c.number.isNotBlank()) i else null }
        var reachedIndex = -1
        var answered = false
        var cycle = 0
        skipFlag.set(false)
        outer@ while (true) {
            if (eligibleIndices.isEmpty()) break
            for (idx in eligibleIndices) {
                val contact = config.contacts[idx]
                reachedIndex = idx
                val needSms = !smsSent[idx]
                if (needSms) {
                    _state.value = SosState.SendingSms(idx, contact.name.ifBlank { contact.number })
                    delay(300)
                }
                _state.value = SosState.Calling(
                    contactIndex = idx,
                    contactName = contact.name.ifBlank { contact.number },
                    secondsLeft = config.perContactWaitSeconds,
                    cycle = cycle,
                )
                val ok = attempt(contact, body, needSms)
                if (needSms) smsSent[idx] = true
                if (ok) {
                    answered = true
                    _state.value = SosState.CallActive(idx, contact.name.ifBlank { contact.number })
                    val isLast = idx == eligibleIndices.last()
                    if (config.voicemailTrapEscape && !isLast) {
                        val skipped = localAwaitCallEndOrSkip()
                        if (skipped) {
                            answered = false
                            delay(800)
                            continue
                        }
                    }
                    break@outer
                }
            }
            cycle++
        }
        _state.value = SosState.Done(reachedIndex = reachedIndex, answered = answered)
    }
}
