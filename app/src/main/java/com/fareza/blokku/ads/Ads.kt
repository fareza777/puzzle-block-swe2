package com.fareza.blokku.ads

import android.app.Activity
import android.content.Context
import android.view.View
import com.fareza.blokku.data.Save
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Monetization layer — AdMob rewarded + paced interstitials.
 * Uses Google's official TEST ad unit IDs; swap for real ones before release.
 * No banners, ever. Interstitials appear at most every 3 finished rounds
 * and never more often than every 120 seconds. Removed entirely by the
 * one-time remove_ads purchase.
 */
object Ads {

    // Google test IDs — https://developers.google.com/admob/android/test-ads
    private const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    private const val MIN_GAMES_BETWEEN = 3
    private const val MIN_MS_BETWEEN = 120_000L

    private var initialized = false
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    var rewardedReady = false
        private set

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            MobileAds.initialize(context) {}
            loadInterstitial(context)
            loadRewarded(context)
        } catch (e: Exception) {
            // ads unavailable (e.g. no play services) — game stays fully playable
        }
    }

    private fun loadInterstitial(context: Context) {
        if (Save.adsRemoved) return
        InterstitialAd.load(context, INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null }
            })
    }

    private fun loadRewarded(context: Context) {
        RewardedAd.load(context, REWARDED_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedReady = true }
                override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; rewardedReady = false }
            })
    }

    /** Called after a round ends. Only fires when pacing rules allow. */
    fun maybeInterstitial(context: Context) {
        if (Save.adsRemoved) return
        Save.gamesSinceAd++
        val due = Save.gamesSinceAd >= MIN_GAMES_BETWEEN &&
            System.currentTimeMillis() - Save.lastAdTime > MIN_MS_BETWEEN
        if (!due) return
        val ad = interstitial
        if (ad == null) { loadInterstitial(context); return }
        val act = context as? Activity ?: return
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadInterstitial(context) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { loadInterstitial(context) }
        }
        Save.gamesSinceAd = 0
        Save.lastAdTime = System.currentTimeMillis()
        ad.show(act)
    }

    /** Rewarded ad for continue / free coins. callback(ok=true) when reward earned. */
    fun showRewarded(context: Context, cb: (Boolean) -> Unit) {
        val ad = rewarded
        val act = context as? Activity
        if (ad == null || act == null) {
            rewardedReady = false
            loadRewarded(context)
            cb(false)
            return
        }
        rewarded = null
        rewardedReady = false
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { loadRewarded(context); cb(earned) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { loadRewarded(context); cb(false) }
        }
        ad.show(act) { earned = true }
    }

    /** One-time purchase to remove all ads forever. */
    fun buyRemoveAds(view: View) {
        val act = view.context as? Activity ?: return
        Billing.purchaseRemoveAds(act)
    }
}
