package com.fareza.blokku.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fareza.blokku.MainActivity
import com.fareza.blokku.R
import com.fareza.blokku.data.Save
import java.util.Calendar

/**
 * Offline daily-reminder notification. Uses inexact AlarmManager
 * (setAndAllowWhileIdle) — no exact-alarm permission, battery friendly.
 * Rescheduled on boot via ReminderReceiver.
 */
object Reminder {

    const val ACTION = "com.fareza.blokku.DAILY_REMINDER"
    const val CHANNEL = "daily_reminder"
    const val REQ = 3001
    private const val HOUR = 18 // ~6pm local

    private fun pendingIntent(ctx: Context): PendingIntent {
        val i = Intent(ACTION).setPackage(ctx.packageName)
        return PendingIntent.getBroadcast(
            ctx, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Schedule (or re-schedule) the next ~6pm reminder. Idempotent. */
    fun schedule(ctx: Context) {
        cancelInternal(ctx)
        if (!Save.reminderOn) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, HOUR)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent(ctx))
    }

    private fun cancelInternal(ctx: Context) {
        (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pendingIntent(ctx))
    }

    fun cancel(ctx: Context) = cancelInternal(ctx)

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, ctx.getString(R.string.notif_daily_title), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Save.init(ctx) // safe: getSharedPreferences, cheap
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (Save.reminderOn) Reminder.schedule(ctx)
            return
        }
        if (!Save.reminderOn) return
        Reminder.ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, Reminder.CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(ctx.getString(R.string.notif_daily_title))
            .setContentText(ctx.getString(R.string.notif_daily_body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, n)
        Reminder.schedule(ctx) // roll forward one day
    }
}
