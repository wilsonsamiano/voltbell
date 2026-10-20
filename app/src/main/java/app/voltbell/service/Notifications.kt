package app.voltbell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.voltbell.AlertActivity
import app.voltbell.R
import app.voltbell.engine.ActiveAlert
import app.voltbell.engine.BatterySnapshot

object Notifications {
    const val CHANNEL_WATCH = "voltbell.watch"
    const val CHANNEL_ALERT = "voltbell.alert.v2"
    const val ID_WATCH = 17
    const val ID_ALERT = 18

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WATCH,
                context.getString(R.string.channel_watch),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Ongoing battery watch while Voltbell is armed"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Unplug and low-battery alerts"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun watch(context: Context, battery: BatterySnapshot, armed: Boolean): Notification {
        val status = when {
            !armed -> "Off"
            battery.percent < 0 -> "Starting…"
            battery.charging -> "Charging · ${battery.percent}%"
            else -> "On battery · ${battery.percent}%"
        }
        return NotificationCompat.Builder(context, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_stat_voltbell)
            .setContentTitle(context.getString(R.string.watch_title))
            .setContentText(status)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun alert(context: Context, alert: ActiveAlert): Notification {
        val popup = popupIntent(context)
        return NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_voltbell)
            .setContentTitle(alert.kind.title)
            .setContentText("${alert.kind.hint} · ${alert.level}%")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${alert.kind.hint}. Battery is at ${alert.level}%. Tap Dismiss to silence."),
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(popup)
            .setFullScreenIntent(popup, true)
            .addAction(0, "Dismiss", serviceAction(context, BatteryWatchService.ACTION_DISMISS, 31))
            .addAction(0, "Snooze 15 min", serviceAction(context, BatteryWatchService.ACTION_SNOOZE, 32, 15))
            .build()
    }

    private fun popupIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            2,
            Intent(context, AlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun serviceAction(context: Context, action: String, request: Int, minutes: Int? = null): PendingIntent {
        val intent = Intent(context, BatteryWatchService::class.java).setAction(action)
        if (minutes != null) intent.putExtra(BatteryWatchService.EXTRA_MINUTES, minutes)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(context, request, intent, flags)
        } else {
            PendingIntent.getService(context, request, intent, flags)
        }
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, app.voltbell.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
