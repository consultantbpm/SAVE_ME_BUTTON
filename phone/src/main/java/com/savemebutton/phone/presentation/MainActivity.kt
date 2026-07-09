package com.savemebutton.phone.presentation

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.savemebutton.phone.PhoneApplication
import com.savemebutton.phone.PremiumManager
import com.savemebutton.phone.billing.DriveEntitlementStore
import com.savemebutton.phone.billing.EntitlementBus
import com.savemebutton.phone.billing.SaveMeBilling
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer { MainViewModel(application as PhoneApplication) }
        }
    }

    // ── OPTIONAL, non-safety premium tier ───────────────────────────────────
    // Unlocks ONLY the cosmetic custom-SMS-body editor. The panic/SOS trigger,
    // escalation, SMS and calls to contacts never depend on any of this.
    private val premiumManager: PremiumManager by lazy {
        (application as PhoneApplication).premiumManager
    }
    private val driveEntitlementStore: DriveEntitlementStore by lazy {
        DriveEntitlementStore(applicationContext)
    }
    private var billing: SaveMeBilling? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result handled implicitly via runtime checks */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val perms = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        val missing = perms.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())

        // Touch the manager so the 24h trial clock is anchored at first run.
        premiumManager

        billing = SaveMeBilling(
            activity = this,
            onPurchased = {
                premiumManager.setPurchased(true)
                EntitlementBus.bump()
                lifecycleScope.launch { runCatching { driveEntitlementStore.sync(premiumManager) } }
            },
            onPrice = { viewModel.setPriceText(it) },
            toast = { msg ->
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
            },
        ).also { it.start() }

        // Best-effort anti-reset entitlement sync. No-op while the Apps Script
        // endpoint URL is blank (placeholder until the developer deploys it).
        // On any local change it bumps EntitlementBus, which the ViewModel's
        // premium flow already observes.
        lifecycleScope.launch {
            runCatching { driveEntitlementStore.sync(premiumManager) }
        }

        setContent {
            SaveMeButtonTheme {
                MainScreen(
                    viewModel = viewModel,
                    onUnlockPremium = { billing?.launchPurchaseFlow() },
                    onRestorePurchases = { billing?.restorePurchases(userInitiated = true) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Pick up a purchase completed elsewhere without any user action.
        billing?.restorePurchases(userInitiated = false)
    }

    override fun onDestroy() {
        billing?.end()
        super.onDestroy()
    }
}
