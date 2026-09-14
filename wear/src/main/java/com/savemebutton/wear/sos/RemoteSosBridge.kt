package com.savemebutton.wear.sos

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.savemebutton.shared.DispatcherConfig
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SosTriggerPayload
import com.savemebutton.shared.WearPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

private const val TAG = "SmbWear"
private const val SOS_DISPATCH_TAG = "SOS_DISPATCH"

class RemoteSosBridge(private val context: Context) {

    suspend fun dispatch(payload: SosTriggerPayload): Boolean = withContext(Dispatchers.IO) {
        // Trimitem semnalul si catre serverul AI de Python de pe laptop (backend_python, /sos)
        Thread {
            var conn: java.net.HttpURLConnection? = null
            try {
                val url = java.net.URL(DispatcherConfig.sosUrl())
                conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 2000
                    readTimeout = 2000
                }
                // TODO: SosConfig nu expune încă group_id/uid — folosim valori demo până
                // când configul le adaugă.
                val groupId = "demo-group"
                val uid = Build.MODEL
                val coords = payload.watchCoords
                val body = JSONObject().apply {
                    put("source", "watch_button")
                    put("group_id", groupId)
                    put("uid", uid)
                    put("lat", coords?.lat ?: JSONObject.NULL)
                    put("lng", coords?.lon ?: JSONObject.NULL)
                    put("text", "SOS! Va rog ajutor!")
                    put("ts", Instant.now().toString())
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = conn.responseCode
                Log.i(SOS_DISPATCH_TAG, "dispatch ok, source=watch_button, responseCode=$code")
            } catch (e: Exception) {
                Log.e(SOS_DISPATCH_TAG, "dispatch failed, source=watch_button", e)
            } finally {
                conn?.disconnect()
            }
        }.start()

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

    suspend fun pushSkip() = withContext(Dispatchers.IO) {
        val nodes = runCatching {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes)
        }.getOrElse { return@withContext }
        val msgClient = Wearable.getMessageClient(context)
        for (node in nodes) {
            runCatching {
                Tasks.await(msgClient.sendMessage(node.id, WearPaths.SOS_SKIP, ByteArray(0)))
            }.onFailure { Log.d(TAG, "skip push to ${node.id} failed: $it") }
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
