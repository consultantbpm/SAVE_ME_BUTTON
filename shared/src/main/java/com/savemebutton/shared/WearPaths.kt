package com.savemebutton.shared

import kotlinx.serialization.json.Json

object WearPaths {
    const val CONFIG = "/savemebutton/config"
    const val SOS_TRIGGER = "/savemebutton/sos_trigger"
    const val SOS_PROGRESS = "/savemebutton/sos_progress"
    const val SOS_CANCEL = "/savemebutton/sos_cancel"
    const val SOS_STOP = "/savemebutton/sos_stop"
    const val LOC_REQUEST = "/savemebutton/loc_request"
    const val LOC_REPLY = "/savemebutton/loc_reply"
    const val SIREN = "/savemebutton/siren"
}

val SaveMeJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
