package com.savemebutton.wear.presentation

import android.Manifest
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.savemebutton.shared.SosState
import com.savemebutton.wear.WearApplication
import kotlinx.coroutines.launch

private const val TAG = "SmbKeys"

class MainActivity : ComponentActivity() {

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
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
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
                if (st is SosState.Countdown) startVibration() else stopVibration()
                if (st is SosState.Countdown && st.secondsRemaining == viewModel.configCountdownSeconds()) {
                    requestTelephonyPermissionsIfNeeded()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_PRIMARY) {
            if (event?.repeatCount == 0) {
                handler.removeCallbacks(triggerRunnable)
                val holdMs = viewModel.configHoldSeconds() * 1000L
                handler.postDelayed(triggerRunnable, holdMs)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_PRIMARY) return true
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_PRIMARY) {
            handler.removeCallbacks(triggerRunnable)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

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
        ).filter { perm ->
            checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 2)
        }
    }
}
