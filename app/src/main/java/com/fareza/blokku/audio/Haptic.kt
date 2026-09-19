package com.fareza.blokku.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fareza.blokku.data.Save

object Haptic {

    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun buzz(ms: Long, amp: Int) {
        if (!Save.vibrationOn) return
        try {
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= 26) {
                    it.vibrate(VibrationEffect.createOneShot(ms, amp))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(ms)
                }
            }
        } catch (e: Exception) {}
    }

    fun tick() = buzz(18, 120)
    fun success() = buzz(40, 200)
    fun heavy() = buzz(70, 255)
    fun error() = buzz(60, 80)
}
