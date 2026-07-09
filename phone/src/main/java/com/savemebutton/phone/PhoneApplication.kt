package com.savemebutton.phone

import android.app.Application
import com.savemebutton.phone.data.ConfigRepository
import com.savemebutton.phone.sos.PhoneLocationProvider
import com.savemebutton.phone.sos.PhoneLocationResponder
import com.savemebutton.phone.sos.PhoneSosHandler
import com.savemebutton.phone.sos.PhoneTelephony
import com.savemebutton.phone.sync.WatchSyncBridge
import com.savemebutton.shared.Siren
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class PhoneApplication : Application() {
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob()) }

    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }
    val premiumManager: PremiumManager by lazy { PremiumManager(this) }
    val telephony: PhoneTelephony by lazy { PhoneTelephony(this) }
    val locationProvider: PhoneLocationProvider by lazy { PhoneLocationProvider(this) }
    val locationResponder: PhoneLocationResponder by lazy {
        PhoneLocationResponder(this, locationProvider)
    }
    val watchSyncBridge: WatchSyncBridge by lazy { WatchSyncBridge(this) }
    val siren: Siren by lazy { Siren() }

    val sosHandler: PhoneSosHandler by lazy {
        PhoneSosHandler(
            scope = appScope,
            telephony = telephony,
            location = locationProvider,
            watchBridge = watchSyncBridge,
            siren = siren,
        )
    }

    override fun onCreate() {
        super.onCreate()
        watchSyncBridge.pushConfig(configRepository.config.value)
    }
}
