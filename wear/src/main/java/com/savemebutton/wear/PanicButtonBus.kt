package com.savemebutton.wear

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process bridge from the
 * [com.savemebutton.wear.accessibility.SaveMeButtonAccessibilityService] to the
 * foreground [com.savemebutton.wear.presentation.MainActivity].
 *
 * The a11y service captures the hardware button hold (the only reliable way on
 * Samsung, where the system otherwise swallows the buttons before the activity
 * sees them) and, once the button has been held past the configured
 * `holdSeconds` threshold, emits a single event here. MainActivity collects it
 * and calls the SAME `viewModel.trigger()` the on-activity key handler uses, so
 * the panic escalation is identical no matter which path fired it.
 *
 * This is purely an ADDITIONAL capture path; it does not change the panic /
 * escalation / SMS / cancel logic in any way.
 */
object PanicButtonBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    /** Emitted when the upper button has been held past the panic threshold. */
    fun emitPanicHoldCompleted() {
        _events.tryEmit(Unit)
    }
}
