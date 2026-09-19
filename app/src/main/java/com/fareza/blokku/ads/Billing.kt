package com.fareza.blokku.ads

import android.app.Activity
import android.content.Context
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
import com.fareza.blokku.data.Save

/**
 * One-time "remove_ads" in-app purchase. If Play Billing is unreachable
 * (debug build, no product configured), the flow just ends quietly and
 * the game keeps working.
 */
object Billing {

    private const val PRODUCT_ID = "remove_ads"

    private var client: BillingClient? = null
    private var product: ProductDetails? = null
    private var connecting = false

    private val listener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) handlePurchase(p)
        }
    }

    private fun ensureClient(context: Context, onReady: () -> Unit) {
        client?.let { if (it.isReady) { onReady(); return } }
        if (connecting) return
        connecting = true
        client = BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() { connecting = false }
        })
    }

    fun init(context: Context) {
        ensureClient(context) { restorePurchases() ; queryProduct() }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
        ).build()
        client?.queryProductDetailsAsync(params) { _, details ->
            product = details?.firstOrNull()
        }
    }

    fun restorePurchases() {
        val c = client ?: return
        c.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                for (p in purchases) handlePurchase(p)
            }
        }
    }

    private fun handlePurchase(p: Purchase) {
        if (p.products.contains(PRODUCT_ID) && p.purchaseState == Purchase.PurchaseState.PURCHASED) {
            Save.adsRemoved = true
            if (!p.isAcknowledged) {
                val c = client ?: return
                c.acknowledgePurchase(
                    com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken).build()
                ) {}
            }
        }
    }

    fun purchaseRemoveAds(activity: Activity) {
        ensureClient(activity) {
            val details = product
            if (details == null) {
                queryProduct()
                return@ensureClient
            }
            val flow = BillingFlowParams.newBuilder().setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            ).build()
            client?.launchBillingFlow(activity, flow)
        }
    }
}
