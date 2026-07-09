package com.savemebutton.phone.presentation

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savemebutton.phone.PhoneApplication
import com.savemebutton.shared.Contact
import com.savemebutton.shared.SirenSound
import com.savemebutton.shared.SirenTarget
import com.savemebutton.shared.SosConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: PhoneApplication) : AndroidViewModel(app) {

    private val repo = app.configRepository
    private val watchBridge = app.watchSyncBridge
    private val sosHandler = app.sosHandler
    private val siren = app.siren
    private var previewJob: Job? = null

    val saved: StateFlow<SosConfig> = repo.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.config.value)

    private val _draft = MutableStateFlow(repo.config.value)
    val draft: StateFlow<SosConfig> = _draft

    val dirty: StateFlow<Boolean> = run {
        kotlinx.coroutines.flow.combine(saved, draft) { s, d -> s != d }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }

    fun updateContact(index: Int, name: String, number: String) {
        val current = _draft.value
        val updated = current.contacts.toMutableList()
        if (index in updated.indices) {
            updated[index] = Contact(name = name, number = number)
            _draft.value = current.copy(contacts = updated)
        }
    }

    fun updateSmsBody(body: String) {
        _draft.value = _draft.value.copy(smsBody = body)
    }

    fun setHoldSeconds(value: Int) { _draft.value = _draft.value.copy(holdSeconds = value) }
    fun setCancelTaps(value: Int) { _draft.value = _draft.value.copy(cancelTaps = value) }
    fun setCountdownSeconds(value: Int) { _draft.value = _draft.value.copy(countdownSeconds = value) }
    fun setPerContactWaitSeconds(value: Int) { _draft.value = _draft.value.copy(perContactWaitSeconds = value) }
    fun setSirenEnabled(value: Boolean) { _draft.value = _draft.value.copy(sirenEnabled = value) }
    fun setSirenTarget(value: SirenTarget) { _draft.value = _draft.value.copy(sirenTarget = value) }
    fun setSirenSound(value: SirenSound) { _draft.value = _draft.value.copy(sirenSound = value) }
    fun setSirenVolume(value: Float) { _draft.value = _draft.value.copy(sirenVolume = value) }
    fun setLoudMinutePulse(value: Boolean) { _draft.value = _draft.value.copy(loudMinutePulse = value) }
    fun setVoicemailTrapEscape(value: Boolean) { _draft.value = _draft.value.copy(voicemailTrapEscape = value) }

    private val _testDialogShown = MutableStateFlow(false)
    val testDialogShown: StateFlow<Boolean> = _testDialogShown

    /** TEST button now opens a confirmation dialog instead of running immediately. */
    fun onTestClicked() { _testDialogShown.value = true }
    fun dismissTestDialog() { _testDialogShown.value = false }

    /** Persists the draft and pushes it to the watch. Returns the saved config. */
    fun save(): SosConfig {
        val next = _draft.value.normalized()
        repo.save(next)
        watchBridge.pushConfig(next)
        _draft.value = next
        return next
    }

    /** Save then run real SOS on phone (real SMS, real call). Confirm dialog calls this. */
    fun runTest() {
        _testDialogShown.value = false
        val cfg = save()
        sosHandler.triggerLocal(cfg)
    }

    /** Plays a 3-second sample of the currently selected siren sound at the draft volume. */
    fun previewSiren() {
        previewJob?.cancel()
        siren.stop()
        val d = _draft.value
        siren.start(d.sirenSound, d.sirenVolume, rampSeconds = 0f)
        previewJob = viewModelScope.launch {
            delay(3000)
            siren.stop()
        }
    }
}
