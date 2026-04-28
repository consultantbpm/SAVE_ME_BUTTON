package com.savemebutton.wear.sos

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SosCoords
import com.savemebutton.shared.WearPaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmbWear"

class RemoteLocationBridge(private val context: Context) {

    @Volatile private var pending: CompletableDeferred<SosCoords?>? = null

    suspend fun requestFromPhone(): SosCoords? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<SosCoords?>()
        pending = deferred
        val nodes = runCatching {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        }.getOrElse {
            Log.d(TAG, "loc connectedNodes failed: $it")
            pending = null
            return@withContext null
        }
        if (nodes.isEmpty()) {
            pending = null
            return@withContext null
        }
        val msg = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching {
                Tasks.await(msg.sendMessage(node.id, WearPaths.LOC_REQUEST, ByteArray(0)))
            }.onFailure { Log.d(TAG, "loc request to ${node.id} failed: $it") }
        }
        deferred.await()
    }

    fun acceptReply(bytes: ByteArray) {
        val coords = runCatching {
            if (bytes.isEmpty()) null
            else SaveMeJson.decodeFromString(SosCoords.serializer(), String(bytes))
        }.getOrNull()
        Log.d(TAG, "loc reply=$coords")
        pending?.complete(coords)
        pending = null
    }
}
