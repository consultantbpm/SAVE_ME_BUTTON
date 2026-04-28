package com.savemebutton.phone.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SosConfig
import com.savemebutton.shared.SosState
import com.savemebutton.shared.WearPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "SmbWear"

class WatchSyncBridge(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun pushConfig(config: SosConfig) {
        scope.launch {
            runCatching {
                val req = PutDataMapRequest.create(WearPaths.CONFIG).apply {
                    dataMap.putByteArray("payload",
                        SaveMeJson.encodeToString(SosConfig.serializer(), config).toByteArray())
                    dataMap.putLong("ts", System.currentTimeMillis())
                }
                Tasks.await(Wearable.getDataClient(context).putDataItem(req.asPutDataRequest().setUrgent()))
                Log.d(TAG, "config pushed to watch")
            }.onFailure { Log.d(TAG, "pushConfig failed: $it") }
        }
    }

    fun pushProgress(state: SosState) {
        scope.launch {
            runCatching {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                val bytes = SaveMeJson.encodeToString(SosState.serializer(), state).toByteArray()
                val msgClient = Wearable.getMessageClient(context)
                for (node in nodes) {
                    Tasks.await(msgClient.sendMessage(node.id, WearPaths.SOS_PROGRESS, bytes))
                }
                Log.d(TAG, "progress pushed: $state")
            }.onFailure { Log.d(TAG, "pushProgress failed: $it") }
        }
    }

    suspend fun pushSiren(command: SirenCommand) {
        runCatching {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
            val bytes = SaveMeJson.encodeToString(SirenCommand.serializer(), command).toByteArray()
            val msgClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                Tasks.await(msgClient.sendMessage(node.id, WearPaths.SIREN, bytes))
            }
            Log.d(TAG, "siren pushed: $command")
        }.onFailure { Log.d(TAG, "pushSiren failed: $it") }
    }
}
