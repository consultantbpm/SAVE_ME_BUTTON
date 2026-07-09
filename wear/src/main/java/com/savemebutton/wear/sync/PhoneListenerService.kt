package com.savemebutton.wear.sync

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SosConfig
import com.savemebutton.shared.SosState
import com.savemebutton.shared.WearPaths
import com.savemebutton.wear.WearApplication
import com.savemebutton.wear.presentation.MainActivity

private const val TAG = "SmbWear"

class PhoneListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val app = application as WearApplication
        when (event.path) {
            WearPaths.SOS_PROGRESS -> handleProgress(event.data)
            WearPaths.LOC_REPLY -> app.remoteLocationBridge.acceptReply(event.data)
            WearPaths.SIREN -> handleSiren(event.data)
            WearPaths.SOS_STOP -> app.sosOrchestrator.stop()
            WearPaths.OPEN_WATCH_UI -> openMainActivityForMirror()
            else -> Log.d(TAG, "ignored message ${event.path}")
        }
    }

    private fun openMainActivityForMirror() {
        Log.d(TAG, "open-watch-ui requested by phone, launching MainActivity")
        val i = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_SKIP_AUTO_TRIGGER, true)
        }
        applicationContext.startActivity(i)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (event in events) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val item = event.dataItem
            if (item.uri.path == WearPaths.CONFIG) {
                val raw = item.data ?: continue
                val cfg = runCatching {
                    SaveMeJson.decodeFromString(SosConfig.serializer(), String(raw))
                }.getOrNull() ?: continue
                (application as WearApplication).configRepository.save(cfg)
                Log.d(TAG, "config updated from phone")
            }
        }
    }

    private fun handleProgress(bytes: ByteArray) {
        val state = runCatching {
            SaveMeJson.decodeFromString(SosState.serializer(), String(bytes))
        }.getOrNull() ?: return
        (application as WearApplication).sosOrchestrator.updateRemoteState(state)
    }

    private fun handleSiren(bytes: ByteArray) {
        val cmd = runCatching {
            SaveMeJson.decodeFromString(SirenCommand.serializer(), String(bytes))
        }.getOrNull() ?: return
        val app = application as WearApplication
        if (cmd.start) app.siren.start(cmd.sound, cmd.volume, cmd.rampSeconds) else app.siren.stop()
    }
}
