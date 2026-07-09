package com.savemebutton.phone.billing

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide signal that entitlement (trial start / purchased flag)
 * changed on disk so Compose can re-read
 * [com.savemebutton.phone.PremiumManager]. Bumped on purchase / restore /
 * Drive reconcile.
 */
object EntitlementBus {
    val version = MutableStateFlow(0)
    fun bump() { version.value = version.value + 1 }
}
