package com.savemebutton.shared

/**
 * Single source of truth for the demo AI dispatcher server (backend_python) target used by
 * both the phone and the wear app to POST SOS events.
 *
 * NOTE: an mDNS name like `ai-dispatcher.local` does NOT resolve through
 * `HttpURLConnection` on Android without an explicit `NsdManager` resolution step — and
 * that is out of scope here. [baseUrl] must stay a literal IP (or a plain hostname the
 * device's normal DNS/hosts resolution already handles) for the demo to work.
 */
object DispatcherConfig {
    var baseUrl: String = "http://192.168.100.225:8000"

    const val SOS_PATH = "/sos"

    fun sosUrl(): String = "$baseUrl$SOS_PATH"
}
