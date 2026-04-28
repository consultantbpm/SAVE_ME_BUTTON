package com.savemebutton.shared

import kotlinx.serialization.Serializable

@Serializable
data class Contact(
    val name: String = "",
    val number: String = "",
)

@Serializable
enum class SirenSound { TWO_TONE, KLAXON, WHOOP, PULSE }

@Serializable
enum class SirenTarget { NONE, WATCH, PHONE, BOTH }

@Serializable
data class SosConfig(
    val contacts: List<Contact> = listOf(Contact(), Contact(), Contact()),
    val smsBody: String = "I need help. My location:",
    val holdSeconds: Int = 3,
    val cancelTaps: Int = 3,
    val countdownSeconds: Int = 5,
    val perContactWaitSeconds: Int = 30,
    val sirenTarget: SirenTarget = SirenTarget.BOTH,
    val sirenSound: SirenSound = SirenSound.TWO_TONE,
    val sirenVolume: Float = 1.0f,
    val loudMinutePulse: Boolean = false,
) {
    fun normalized(): SosConfig {
        val padded = (contacts + List(3) { Contact() }).take(3)
        return copy(
            contacts = padded,
            holdSeconds = holdSeconds.coerceIn(3, 5),
            cancelTaps = cancelTaps.coerceIn(3, 5),
            countdownSeconds = countdownSeconds.coerceIn(5, 10),
            perContactWaitSeconds = perContactWaitSeconds.coerceIn(15, 60),
            sirenVolume = sirenVolume.coerceIn(0f, 1f),
        )
    }
}

@Serializable
enum class SosRoute { WATCH_SELF, PHONE }

@Serializable
data class SosCoords(
    val lat: Double,
    val lon: Double,
) {
    fun mapsUrl(): String = "https://maps.google.com/?q=%.5f,%.5f".format(lat, lon)
}

@Serializable
data class SosTriggerPayload(
    val resolvedRoute: SosRoute,
    val config: SosConfig,
    val watchCoords: SosCoords? = null,
)

@Serializable
data class SirenCommand(
    val start: Boolean,
    val sound: SirenSound = SirenSound.TWO_TONE,
    val volume: Float = 1.0f,
)

@Serializable
sealed class SosState {
    @Serializable object Idle : SosState()
    @Serializable data class Countdown(val secondsRemaining: Int) : SosState()
    @Serializable object Canceled : SosState()
    @Serializable object AcquiringLocation : SosState()
    @Serializable data class SendingSms(val contactIndex: Int, val contactName: String) : SosState()
    @Serializable data class Calling(val contactIndex: Int, val contactName: String, val secondsLeft: Int, val cycle: Int = 0) : SosState()
    @Serializable data class CallActive(val contactIndex: Int, val contactName: String) : SosState()
    @Serializable object Stopped : SosState()
    @Serializable data class Done(val reachedIndex: Int, val answered: Boolean) : SosState()
}
