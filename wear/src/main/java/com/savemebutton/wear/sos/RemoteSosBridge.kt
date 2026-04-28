package com.savemebutton.wear.sos

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SosTriggerPayload
import com.savemebutton.shared.WearPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmbWear"

class RemoteSosBridge(private val context: Context) {

    suspend fun dispatch(payload: SosTriggerPayload): Boolean = withContext(Dispatchers.IO) {
        val nodes = runCatching {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        }.getOrElse {
            Log.d(TAG, "connectedNodes failed: $it")
            return@withContext false
        }
        if (nodes.isEmpty()) return@withContext false
        val bytes = SaveMeJson.encodeToString(SosTriggerPayload.serializer(), payload).toByteArray()
        val msgClient = Wearable.getMessageClient(context)
        var anySent = false
        for (node in nodes) {
            runCatching {
                Tasks.await(msgClient.sendMessage(node.id, WearPaths.SOS_TRIGGER, bytes))
                anySent = true
            }.onFailure { Log.d(TAG, "send to ${node.id} failed: $it") }
        }
        anySent
    }

    suspend fun pushSiren(command: SirenCommand) = withContext(Dispatchers.IO) {
        val nodes = runCatching {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        }.getOrElse { return@withContext }
        val bytes = SaveMeJson.encodeToString(SirenCommand.serializer(), command).toByteArray()
        val msgClient = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching {
                Tasks.await(msgClient.sendMessage(node.id, WearPaths.SIREN, bytes))
            }.onFailure { Log.d(TAG, "siren push to ${node.id} failed: $it") }
        }
    }

    suspend fun pushStop() = withContext(Dispatchers.IO) {
        val nodes = runCatching {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        }.getOrElse { return@withContext }
        val msgClient = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching {
                Tasks.await(msgClient.sendMessage(node.id, WearPaths.SOS_STOP, ByteArray(0)))
            }.onFailure { Log.d(TAG, "stop push to ${node.id} failed: $it") }
        }
    }
}
