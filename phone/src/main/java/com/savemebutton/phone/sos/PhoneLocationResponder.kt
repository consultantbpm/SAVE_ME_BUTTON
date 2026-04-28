package com.savemebutton.phone.sos

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.savemebutton.phone.sync.WatchSyncBridge
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SosCoords
import com.savemebutton.shared.WearPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "SmbWear"

class PhoneLocationResponder(
    private val context: Context,
    private val locationProvider: PhoneLocationProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun respond() {
        scope.launch {
            val coords = locationProvider.fetch()
            val bytes = if (coords == null) ByteArray(0)
            else SaveMeJson.encodeToString(SosCoords.serializer(), coords).toByteArray()
            runCatching {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                val msg = Wearable.getMessageClient(context)
                for (node in nodes) {
                    Tasks.await(msg.sendMessage(node.id, WearPaths.LOC_REPLY, bytes))
                }
                Log.d(TAG, "loc reply pushed coords=$coords")
            }.onFailure { Log.d(TAG, "loc reply failed: $it") }
        }
    }
}
