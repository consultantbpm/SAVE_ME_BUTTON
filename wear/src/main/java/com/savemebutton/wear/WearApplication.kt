package com.savemebutton.wear

import android.app.Application
import com.savemebutton.shared.Siren
import com.savemebutton.wear.data.ConfigRepository
import com.savemebutton.wear.sos.CapabilityProbe
import com.savemebutton.wear.sos.LocationProvider
import com.savemebutton.wear.sos.RemoteLocationBridge
import com.savemebutton.wear.sos.RemoteSosBridge
import com.savemebutton.wear.sos.SosOrchestrator
import com.savemebutton.wear.sos.WatchTelephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class WearApplication : Application() {
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob()) }

    val configRepository: ConfigRepository by lazy { ConfigRepository(this) }
    val capabilityProbe: CapabilityProbe by lazy { CapabilityProbe(this) }
    val remoteLocationBridge: RemoteLocationBridge by lazy { RemoteLocationBridge(this) }
    val locationProvider: LocationProvider by lazy { LocationProvider(this, remoteLocationBridge) }
    val watchTelephony: WatchTelephony by lazy { WatchTelephony(this) }
    val remoteSosBridge: RemoteSosBridge by lazy { RemoteSosBridge(this) }
    val siren: Siren by lazy { Siren() }

    val sosOrchestrator: SosOrchestrator by lazy {
        SosOrchestrator(
            scope = appScope,
            telephony = watchTelephony,
            location = locationProvider,
            remoteBridge = remoteSosBridge,
            capabilityProbe = capabilityProbe,
            configFlow = configRepository.config,
            siren = siren,
        )
    }
}
