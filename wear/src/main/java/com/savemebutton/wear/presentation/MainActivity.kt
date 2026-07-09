package com.savemebutton.wear.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.savemebutton.shared.SosState
import com.savemebutton.wear.WearApplication
import kotlinx.coroutines.launch

private const val TAG = "SmbKeys"

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SKIP_AUTO_TRIGGER = "smb_skip_auto_trigger"
    }


    private val viewModel: SosViewModel by viewModels {
        viewModelFactory {
            initializer { SosViewModel(application as WearApplication) }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val triggerRunnable = Runnable {
        Log.d(TAG, "hold threshold reached, firing trigger")
        viewModel.trigger()
    }
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTurnScreenOn(true)
        setShowWhenLocked(true)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        requestNonTelephonyPermissions()

        setContent {
            SosScreen(
                viewModel = viewModel,
                onTap = { viewModel.onScreenTap() }
            )
        }

        lifecycleScope.launch {
            viewModel.state.collect { st ->
                applyKeepScreenOn(st)
                if (st is SosState.Countdown) startVibration() else stopVibration()
                if (st is SosState.Countdown && st.secondsRemaining == viewModel.configCountdownSeconds()) {
                    requestTelephonyPermissionsIfNeeded()
                }
            }
        }

        val skipAuto = intent?.getBooleanExtra(EXTRA_SKIP_AUTO_TRIGGER, false) == true
        if (savedInstanceState == null && !skipAuto) {
            Log.d(TAG, "cold start, triggering SOS unconditionally")
            viewModel.trigger()
        } else if (skipAuto) {
            Log.d(TAG, "cold start with skip-auto-trigger, just opening UI to mirror")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val skipAuto = intent.getBooleanExtra(EXTRA_SKIP_AUTO_TRIGGER, false)
        if (skipAuto) {
            Log.d(TAG, "onNewIntent with skip-auto-trigger; not triggering")
            return
        }
        Log.d(TAG, "onNewIntent, re-triggering SOS, current state=${viewModel.state.value}")
        viewModel.trigger()
    }

    private fun isPanicLaunch(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
    }

    private fun applyKeepScreenOn(state: SosState) {
        val active = when (state) {
            is SosState.Countdown,
            is SosState.AcquiringLocation,
            is SosState.SendingSms,
            is SosState.Calling,
            is SosState.CallActive -> true
            else -> false
        }
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isTriggerKey(keyCode)) {
            if (event?.repeatCount == 0) {
                Log.d(TAG, "trigger key DOWN keyCode=$keyCode")
                handler.removeCallbacks(triggerRunnable)
                val holdMs = viewModel.configHoldSeconds() * 1000L
                handler.postDelayed(triggerRunnable, holdMs)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (isTriggerKey(keyCode)) return true
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isTriggerKey(keyCode)) {
            Log.d(TAG, "trigger key UP keyCode=$keyCode")
            handler.removeCallbacks(triggerRunnable)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isTriggerKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_STEM_PRIMARY ||
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    private fun startVibration() {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 800, 200)
        val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        vibrator?.cancel()
    }

    override fun onPause() {
        super.onPause()
        stopVibration()
        handler.removeCallbacks(triggerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
        handler.removeCallbacks(triggerRunnable)
    }

    private fun requestNonTelephonyPermissions() {
        val needed = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS,
        ).filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
        }
    }

    private fun requestTelephonyPermissionsIfNeeded() {
        val app = application as WearApplication
        if (app.capabilityProbe.resolveRoute() != com.savemebutton.shared.SosRoute.WATCH_SELF) return
        val needed = listOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
        ).filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 2)
        }
    }
}
