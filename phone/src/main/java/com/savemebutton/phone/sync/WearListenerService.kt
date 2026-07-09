package com.savemebutton.phone.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.savemebutton.phone.PhoneApplication
import com.savemebutton.shared.SaveMeJson
import com.savemebutton.shared.SirenCommand
import com.savemebutton.shared.SosTriggerPayload
import com.savemebutton.shared.WearPaths

private const val TAG = "SmbWear"

class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val app = application as PhoneApplication
        when (event.path) {
            WearPaths.SOS_TRIGGER -> handleTrigger(event.data)
            WearPaths.LOC_REQUEST -> app.locationResponder.respond()
            WearPaths.SIREN -> handleSiren(event.data)
            WearPaths.SOS_STOP -> app.sosHandler.stop()
            WearPaths.SOS_SKIP -> app.sosHandler.requestSkip()
            else -> Log.d(TAG, "ignored message ${event.path}")
        }
    }

    private fun handleTrigger(bytes: ByteArray) {
        val payload = runCatching {
            SaveMeJson.decodeFromString(SosTriggerPayload.serializer(), String(bytes))
        }.getOrNull() ?: run {
            Log.d(TAG, "could not decode SOS_TRIGGER payload")
            return
        }
        (application as PhoneApplication).sosHandler.handleTrigger(payload)
    }

    private fun handleSiren(bytes: ByteArray) {
        val cmd = runCatching {
            SaveMeJson.decodeFromString(SirenCommand.serializer(), String(bytes))
        }.getOrNull() ?: return
        val app = application as PhoneApplication
        if (cmd.start) app.siren.start(cmd.sound, cmd.volume, cmd.rampSeconds) else app.siren.stop()
    }
}
