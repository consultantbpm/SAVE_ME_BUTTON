package com.savemebutton.phone.billing

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Wraps Google Play Billing v7 for the one-time SKU `savemebutton_premium`
 * (2.99 EUR). Modeled on the Crown/Stopwatch pattern. Owned by the phone
 * Activity because [launchPurchaseFlow] needs an Activity context.
 *
 * IMPORTANT — SAFETY: this unlock is purely for the OPTIONAL, non-safety
 * cosmetic feature (editing the custom SMS message body). The panic/SOS
 * trigger, escalation, SMS, calls and cancel never depend on billing.
 *
 * On a completed / restored purchase [onPurchased] fires so the caller can
 * flip [com.savemebutton.phone.PremiumManager.setPurchased]. Price text is
 * surfaced through [onPrice].
 */
class SaveMeBilling(
    private val activity: Activity,
    private val onPurchased: () -> Unit,
    private val onPrice: (String) -> Unit = {},
    private val toast: (String) -> Unit = {}
) {
    companion object {
        const val SKU_PREMIUM = "savemebutton_premium"
        const val PRICE_FALLBACK = "2.99 EUR"
        private const val TAG = "SaveMeBilling"
    }

    private var billingReady = false
    private val pending = mutableListOf<(BillingClient) -> Unit>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        connect {
            queryPrice()
            restorePurchases(userInitiated = false)
        }
    }

    fun end() {
        runCatching { client.endConnection() }
    }

    private fun connect(onReady: (() -> Unit)? = null) {
        if (billingReady && client.isReady) {
            onReady?.invoke()
            return
        }
        onReady?.let { ready -> pending += { ready() } }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    pending.clear()
                    return
                }
                billingReady = true
                val actions = pending.toList()
                pending.clear()
                actions.forEach { it(client) }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
            }
        })
    }

    private fun productParams(): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(SKU_PREMIUM)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

    private fun ProductDetails.applyPrice() {
        oneTimePurchaseOfferDetails?.formattedPrice?.let { fp ->
            if (fp.isNotBlank()) activity.runOnUiThread { onPrice(fp) }
        }
    }

    private fun queryPrice() {
        client.queryProductDetailsAsync(productParams()) { result, details ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details.firstOrNull()?.applyPrice()
        }
    }

    /** Wired to the phone's "Unlock" button. */
    fun launchPurchaseFlow() {
        connect {
            client.queryProductDetailsAsync(productParams()) { result, details ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    activity.runOnUiThread { toast("Could not open Google Play") }
                    return@queryProductDetailsAsync
                }
                val product = details.firstOrNull()
                if (product == null) {
                    activity.runOnUiThread { toast("Premium is not available in Google Play") }
                    return@queryProductDetailsAsync
                }
                product.applyPrice()
                activity.runOnUiThread {
                    val params = BillingFlowParams.newBuilder()
                        .setProductDetailsParamsList(
                            listOf(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                    .setProductDetails(product)
                                    .build()
                            )
                        )
                        .build()
                    client.launchBillingFlow(activity, params)
                }
            }
        }
    }

    fun restorePurchases(userInitiated: Boolean) {
        connect {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            ) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (userInitiated) activity.runOnUiThread { toast("Restore failed") }
                    return@queryPurchasesAsync
                }
                val premiumPurchase = purchases.firstOrNull {
                    it.products.contains(SKU_PREMIUM) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (premiumPurchase != null) {
                    handlePurchase(premiumPurchase)
                    if (userInitiated) activity.runOnUiThread { toast("Premium restored") }
                } else if (userInitiated) {
                    activity.runOnUiThread { toast("No previous Premium purchase") }
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(SKU_PREMIUM)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { ack -> Log.d(TAG, "acknowledge premium result=${ack.responseCode}") }
        }
        activity.runOnUiThread { onPurchased() }
    }
}
