package com.fareza.blokku

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.fareza.blokku.ads.Ads
import com.fareza.blokku.ads.Billing
import com.fareza.blokku.audio.Audio
import com.fareza.blokku.audio.Haptic
import com.fareza.blokku.data.Save
import com.fareza.blokku.render.GameView
import com.fareza.blokku.scenes.SplashScene
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        Save.init(this)
        applyLanguage()
        installSplashScreen()
        super.onCreate(savedInstanceState)

        Audio.init(this)
        Haptic.init(this)
        // ad SDK init is deferred ~1.5s so cold start stays instant
        com.fareza.blokku.reminder.Reminder.ensureChannel(this)
        if (Save.reminderOn) com.fareza.blokku.reminder.Reminder.schedule(this)

        gameView = GameView(this)
        setContentView(gameView)
        gameView.scenes.replace(SplashScene())
        hideSystemUi()
        gameView.postDelayed({
            Ads.init(this)
            Billing.init(this)
        }, 1500)
    }

    private fun applyLanguage() {
        val lang = Save.language
        if (lang.isEmpty()) return
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    override fun onResume() {
        super.onResume()
        Audio.onAppResume()
        hideSystemUi()
        gameView.wake()
        // rescheduling is cheap and survives reboots/time changes
        if (Save.reminderOn) com.fareza.blokku.reminder.Reminder.schedule(this)
    }

    override fun onPause() {
        super.onPause()
        Audio.onAppPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameView.onHostBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
