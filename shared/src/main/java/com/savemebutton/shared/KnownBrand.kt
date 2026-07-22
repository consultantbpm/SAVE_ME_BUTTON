package com.savemebutton.shared

import android.os.Build

/**
 * Identifies a known Wear OS watch manufacturer for UI purposes.
 *
 * This is a purely routing layer on top of [WatchProfile] — it is never
 * serialised or sent over the Wearable Data Layer. The phone module (which
 * owns the Android string resources) maps each [BrandKey] to its localized
 * display name, setup steps and Global-mode warning text.
 */
enum class BrandKey {
    SAMSUNG,
    ONEPLUS,
    GOOGLE,
    XIAOMI,
    OPPO,
    /** Fallback for any manufacturer that isn't one of the vendors above. */
    GENERIC
}

/**
 * Metadata about a known Wear OS watch manufacturer.
 *
 * This is a purely UI / metadata layer on top of [WatchProfile] — it is never
 * serialised or sent over the Wearable Data Layer. The profile enum values
 * (SAMSUNG, ONEPLUS, OTHER_ACCESSIBILITY, OTHER) remain the wire contract;
 * [KnownBrand] just enriches the phone companion UI by pointing at the
 * per-brand string resources (owned by the phone module, which is where
 * they're displayed) without touching serialization.
 *
 * @param key     Identifies the brand. The phone UI resolves this to a
 *                 localized display name, setup steps and Global-mode
 *                 warning — see `brandDisplayNameRes` / `brandSetupStepsRes` /
 *                 `brandGlobalWarningRes` in the phone module.
 * @param profile The [WatchProfile] automatically assigned to this brand.
 */
data class KnownBrand(
    val key: BrandKey,
    val profile: WatchProfile
)

/**
 * Detect the [KnownBrand] for a device manufacturer string.
 *
 * [manufacturer] defaults to the current device's [Build.MANUFACTURER] for
 * any first-launch / same-device callers, but the phone module's companion
 * UI must instead pass the *watch's* reported manufacturer (from
 * [WatchProfileInfo.manufacturer]) — detection has to reflect the connected
 * watch's hardware, not the phone's.
 *
 * The returned object drives:
 *  - The "Watch detected: <Brand>" banner in the phone ButtonMappingCard.
 *  - The expandable setup instructions specific to the brand.
 *  - The Global profile switch warning text.
 */
fun detectKnownBrand(manufacturer: String = Build.MANUFACTURER): KnownBrand =
    when (manufacturer.lowercase()) {
        "samsung" -> KnownBrand(BrandKey.SAMSUNG, WatchProfile.SAMSUNG)
        "oneplus" -> KnownBrand(BrandKey.ONEPLUS, WatchProfile.ONEPLUS)
        "google" -> KnownBrand(BrandKey.GOOGLE, WatchProfile.OTHER_ACCESSIBILITY)
        "xiaomi" -> KnownBrand(BrandKey.XIAOMI, WatchProfile.OTHER_ACCESSIBILITY)
        "oppo" -> KnownBrand(BrandKey.OPPO, WatchProfile.OTHER_ACCESSIBILITY)
        else -> KnownBrand(BrandKey.GENERIC, WatchProfile.OTHER)
    }
