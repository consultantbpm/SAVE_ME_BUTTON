package com.savemebutton.phone.sos

import android.util.Log
import com.savemebutton.phone.sync.WatchSyncBridge
import com.savemebutton.shared.Contact
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SirenTarget
import com.savemebutton.shared.Siren
import com.savemebutton.shared.SosConfig
import com.savemebutton.shared.SosCoords
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

class PhoneSosHandler(
    private val scope: CoroutineScope,
    private val telephony: PhoneTelephony,
    private val location: PhoneLocationProvider,
    private val watchBridge: WatchSyncBridge,
    private val siren: Siren,
) {
    private var sequenceJob: Job? = null
    private var pulseJob: Job? = null
    private val skipFlag = AtomicBoolean(false)

    private val _localState = MutableStateFlow<SosState>(SosState.Idle)
    val localState: StateFlow<SosState> = _localState

    fun requestSkip() {
        Log.d(TAG, "skip requested")
        skipFlag.set(true)
    }

    fun handleTrigger(payload: SosTriggerPayload) {
        Log.d(TAG, "phone handling SOS trigger from watch")
        runSequence(payload.config, payload.watchCoords, fromWatch = true)
    }

    /** Used by the phone Test button. Runs the same sequence locally and wakes the watch UI so the user can monitor / cancel from the wrist. */
    fun triggerLocal(config: SosConfig) {
        Log.d(TAG, "phone test trigger")
        watchBridge.pushOpenWatch()
        runSequence(config, null, fromWatch = false)
    }

    fun stop() {
        scope.launch {
            sequenceJob?.cancelAndJoin()
            pulseJob?.cancel()
            pulseJob = null
            siren.stop()
            runCatching { watchBridge.pushSiren(SirenCommand(false)) }
            push(SosState.Stopped)
            delay(CANCELED_HOLD_MS)
            push(SosState.Idle)
        }
    }

    private fun runSequence(config: SosConfig, presetCoords: SosCoords?, fromWatch: Boolean) {
        sequenceJob?.cancel()
        sequenceJob = scope.launch {
            val playLocal = config.sirenEnabled &&
                (config.sirenTarget == SirenTarget.PHONE || config.sirenTarget == SirenTarget.BOTH)
            val playRemote = config.sirenEnabled &&
                (config.sirenTarget == SirenTarget.WATCH || config.sirenTarget == SirenTarget.BOTH)
            if (playLocal) siren.start(config.sirenSound, config.sirenVolume, SIREN_RAMP_SECONDS)
            if (playRemote) runCatching {
                watchBridge.pushSiren(SirenCommand(true, config.sirenSound, config.sirenVolume, SIREN_RAMP_SECONDS))
            }
            try {
                if (!fromWatch) {
                    if (!runCountdown(config.countdownSeconds)) {
                        Log.d(TAG, "canceled during phone countdown")
                        return@launch
                    }
                }
                if (config.loudMinutePulse) startMinutePulse(config)
                push(SosState.AcquiringLocation)
                val coords = presetCoords ?: location.fetch()
                executeSequence(config, coords)
            } finally {
                pulseJob?.cancel()
                pulseJob = null
                if (playLocal) siren.stop()
                if (playRemote) withContext(NonCancellable) {
                    runCatching { watchBridge.pushSiren(SirenCommand(false)) }
                }
            }
        }
    }

    private suspend fun runCountdown(seconds: Int): Boolean {
        for (sec in seconds downTo 1) {
            push(SosState.Countdown(sec))
            delay(1000)
        }
        return _localState.value is SosState.Countdown
    }

    private fun startMinutePulse(config: SosConfig) {
        pulseJob?.cancel()
        pulseJob = scope.launch {
            val pulse = Siren()
            try {
                while (true) {
                    delay(MINUTE_PULSE_MS)
                    pulse.start(config.sirenSound, config.sirenVolume)
                    delay(PULSE_BURST_MS)
                    pulse.stop()
                }
            } finally {
                pulse.stop()
            }
        }
    }

    private suspend fun executeSequence(config: SosConfig, coords: SosCoords?) {
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
                    push(SosState.SendingSms(idx, contact.name.ifBlank { contact.number }))
                    telephony.sendSms(contact.number, body, coords, config)
                    smsSent[idx] = true
                    delay(300)
                }
                push(SosState.Calling(
                    contactIndex = idx,
                    contactName = contact.name.ifBlank { contact.number },
                    secondsLeft = config.perContactWaitSeconds,
                    cycle = cycle,
                ))
                telephony.placeCall(contact.number, coords, config)
                val ok = telephony.waitForAnswer(config.perContactWaitSeconds * 1000L)
                if (ok) {
                    answered = true
                    push(SosState.CallActive(idx, contact.name.ifBlank { contact.number }))
                    val isLast = idx == eligibleIndices.last()
                    if (config.voicemailTrapEscape && !isLast) {
                        skipFlag.set(false)
                        val skipped = telephony.awaitCallEndOrSkip(skipFlag)
                        if (skipped) {
                            telephony.endCall()
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
        push(SosState.Done(reachedIndex = reachedIndex, answered = answered))
    }

    private fun push(state: SosState) {
        _localState.value = state
        watchBridge.pushProgress(state)
    }
}
