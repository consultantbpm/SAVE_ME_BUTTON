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
    val perContactWaitSeconds: Int = 20,
    val sirenEnabled: Boolean = true,
    val sirenTarget: SirenTarget = SirenTarget.BOTH,
    val sirenSound: SirenSound = SirenSound.TWO_TONE,
    val sirenVolume: Float = 1.0f,
    val loudMinutePulse: Boolean = true,
    val voicemailTrapEscape: Boolean = true,
    val watchProfile: WatchProfile = WatchProfile.OTHER,
    val watchNativeProfile: WatchProfile = WatchProfile.OTHER,
    val watchManufacturer: String = "",
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

    /** Degrees-minutes-seconds, e.g. `44°27'39.7"N 26°07'22.7"E`. */
    fun dmsString(): String {
        fun part(value: Double, posHemi: Char, negHemi: Char): String {
            val absV = kotlin.math.abs(value)
            val deg = absV.toInt()
            val minFull = (absV - deg) * 60.0
            val min = minFull.toInt()
            val sec = (minFull - min) * 60.0
            val hemi = if (value >= 0) posHemi else negHemi
            return "%d°%02d'%.1f\"%c".format(deg, min, sec, hemi)
        }
        return "${part(lat, 'N', 'S')} ${part(lon, 'E', 'W')}"
    }

    /** What gets appended to the SMS body: human-readable DMS plus tap-to-map URL. */
    fun smsLine(): String = "${dmsString()} ${mapsUrl()}"
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
    val rampSeconds: Float = 0f,
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

@Serializable
enum class WatchProfile {
    ONEPLUS,
    SAMSUNG,
    OTHER,
    OTHER_ACCESSIBILITY,
    OTHER_NO_ACCESSIBILITY;

    fun isPowerButtonProfile(): Boolean = this == ONEPLUS || this == OTHER_NO_ACCESSIBILITY
    fun isNonPowerButtonProfile(): Boolean = this == SAMSUNG || this == OTHER_ACCESSIBILITY
}

fun detectNativeWatchProfile(manufacturer: String = android.os.Build.MANUFACTURER): WatchProfile = when (manufacturer.lowercase()) {
    "samsung" -> WatchProfile.SAMSUNG
    "oneplus" -> WatchProfile.ONEPLUS
    "google", "xiaomi", "oppo" -> WatchProfile.OTHER_ACCESSIBILITY
    else -> WatchProfile.OTHER
}

@Serializable
data class WatchProfileInfo(
    val nativeProfile: WatchProfile,
    val currentProfile: WatchProfile,
    val manufacturer: String = "",
    val model: String = "",
    val appVersion: Long = 0,
)
