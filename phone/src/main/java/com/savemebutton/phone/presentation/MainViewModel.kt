package com.savemebutton.phone.presentation

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.savemebutton.phone.PhoneApplication
import com.savemebutton.phone.PremiumManager
import com.savemebutton.phone.billing.EntitlementBus
import com.savemebutton.phone.billing.SaveMeBilling
import com.savemebutton.shared.Contact
import com.savemebutton.shared.SirenSound
import com.savemebutton.shared.SirenTarget
import com.savemebutton.shared.SosConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI-facing snapshot of the OPTIONAL, non-safety premium tier. Used ONLY to
 * gate the cosmetic custom-SMS-body editor; never consulted on the SOS path.
 */
data class PremiumUiState(
    val isPremium: Boolean = false,
    val hasFullAccess: Boolean = true,
    val trialRemainingMs: Long = 0L,
)

class MainViewModel(app: PhoneApplication) : AndroidViewModel(app) {

    private val repo = app.configRepository
    private val watchBridge = app.watchSyncBridge
    private val sosHandler = app.sosHandler
    private val siren = app.siren
    private val premiumManager: PremiumManager = app.premiumManager
    private var previewJob: Job? = null

    /** The immutable default SMS body free users always keep (fully functional). */
    val defaultSmsBody: String = SosConfig().smsBody

    private fun readPremium() = PremiumUiState(
        isPremium = premiumManager.isPremium,
        hasFullAccess = premiumManager.hasFullAccess,
        trialRemainingMs = premiumManager.remainingTrialTimeMs,
    )

    /** Re-emits whenever [EntitlementBus] is bumped (purchase / restore / sync). */
    val premium: StateFlow<PremiumUiState> =
        EntitlementBus.version
            .map { readPremium() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, readPremium())

    private val _priceText = MutableStateFlow(SaveMeBilling.PRICE_FALLBACK)
    val priceText: StateFlow<String> = _priceText

    /** Called by the Activity's billing client once Play returns the real price. */
    fun setPriceText(value: String) { _priceText.value = value }

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

    /**
     * Edits the custom SMS body. This is the ONLY premium-gated (cosmetic)
     * control: free users keep the default message. No-op without full access;
     * the sending path is unaffected and always works.
     */
    fun updateSmsBody(body: String) {
        if (!premiumManager.hasFullAccess) return
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
