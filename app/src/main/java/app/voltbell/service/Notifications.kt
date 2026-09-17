package app.voltbell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import app.voltbell.MainActivity
import app.voltbell.R
import app.voltbell.engine.ActiveAlert
import app.voltbell.engine.BatterySnapshot

object Notifications {
    const val CHANNEL_WATCH = "voltbell.watch"
    const val CHANNEL_ALERT = "voltbell.alert"
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
        val alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Unplug and low-battery alerts"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    alarm,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
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
        val fullScreen = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHOW_ALERT, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_voltbell)
            .setContentTitle(alert.kind.title)
            .setContentText("${alert.kind.hint} · ${alert.level}%")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp(context))
            .setFullScreenIntent(fullScreen, true)
            .build()
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
