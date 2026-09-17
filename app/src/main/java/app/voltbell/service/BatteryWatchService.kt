package app.voltbell.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import app.voltbell.VoltbellState
import app.voltbell.data.SettingsStore
import app.voltbell.engine.ActiveAlert
import app.voltbell.engine.AlertEngine
import app.voltbell.engine.AlertKind
import app.voltbell.engine.BatterySnapshot
import app.voltbell.engine.LogEntry
import java.util.Locale
import java.util.UUID

class BatteryWatchService : Service() {
    private lateinit var store: SettingsStore
    private var prev: BatterySnapshot? = null
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeat: Runnable? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            onBatteryIntent(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SettingsStore(this)
        VoltbellState.setSettings(store.read())
        VoltbellState.replaceLog(store.readLog())
        Notifications.ensureChannels(this)
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        startAsForeground(readSnapshot(sticky))
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) onBatteryIntent(sticky)
        VoltbellState.setServiceOn(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS -> dismissAlert()
            ACTION_SNOOZE -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 15)
                snoozeAlert(minutes)
            }
            ACTION_TEST -> fire(AlertKind.STOP, VoltbellState.battery.value.percent.coerceAtLeast(80), test = true)
            ACTION_REFRESH -> {
                val snap = VoltbellState.battery.value
                startAsForeground(snap)
            }
        }
        val settings = store.read()
        VoltbellState.setSettings(settings)
        if (!settings.armed) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        stopSignals()
        tts?.shutdown()
        tts = null
        VoltbellState.setServiceOn(false)
        VoltbellState.setAlert(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(snap: BatterySnapshot) {
        val settings = store.read()
        val notification = Notifications.watch(this, snap, settings.armed)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Notifications.ID_WATCH,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(Notifications.ID_WATCH, notification)
        }
    }

    private fun onBatteryIntent(intent: Intent) {
        val next = readSnapshot(intent)
        val last = prev
        VoltbellState.setBattery(next)
        startAsForeground(next)
        if (last == null) {
            prev = next
            return
        }
        val settings = store.read()
        VoltbellState.setSettings(settings)
        val kind = AlertEngine.evaluate(last, next, settings)
        prev = next
        if (kind != null) fire(kind, next.percent)
    }

    private fun readSnapshot(intent: Intent?): BatterySnapshot {
        if (intent == null) return BatterySnapshot(percent = -1, charging = false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = ((level * 100f) / scale).toInt().coerceIn(0, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(percent = percent, charging = charging)
    }

    private fun fire(kind: AlertKind, level: Int, test: Boolean = false) {
        val settings = store.read()
        if (!test && store.snoozedUntil(kind.name) > System.currentTimeMillis()) return
        if (!test && AlertEngine.isQuietNow(settings) && kind != AlertKind.LOW) return

        val alert = ActiveAlert(id = UUID.randomUUID().toString(), kind = kind, level = level)
        val entry = LogEntry(
            id = alert.id,
            kind = kind,
            level = level,
            at = alert.at,
            charging = VoltbellState.battery.value.charging,
        )
        VoltbellState.setAlert(alert)
        VoltbellState.prependLog(entry)
        store.writeLog(VoltbellState.log.value)

        if (settings.notify) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(Notifications.ID_ALERT, Notifications.alert(this, alert))
        }
        startSignals(alert, settings)
    }

    private fun startSignals(alert: ActiveAlert, settings: app.voltbell.engine.Settings) {
        stopSignals()
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voltbell:alert").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L)
        }
        if (settings.sound) playAlarm()
        if (settings.voice) speak(alert.kind.voice(alert.level))
        if (settings.vibrate) pulseVibrate()

        val repeatMs = (settings.repeatSeconds.coerceIn(6, 60) * 1000).toLong()
        val task = object : Runnable {
            override fun run() {
                val current = VoltbellState.alert.value ?: return
                val s = store.read()
                if (s.sound) playAlarm()
                if (s.voice) speak(current.kind.voice(current.level))
                if (s.vibrate) pulseVibrate()
                handler.postDelayed(this, repeatMs)
            }
        }
        repeat = task
        handler.postDelayed(task, repeatMs)
    }

    private fun playAlarm() {
        try {
            if (player?.isPlaying == true) return
            player?.release()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@BatteryWatchService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            player = null
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voltbell-alert")
    }

    private fun pulseVibrate() {
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 600), -1)
        if (Build.VERSION.SDK_INT >= 31) {
            val vm = getSystemService(VibratorManager::class.java)
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java).vibrate(effect)
        }
    }

    private fun dismissAlert() {
        VoltbellState.setAlert(null)
        stopSignals()
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(Notifications.ID_ALERT)
    }

    private fun snoozeAlert(minutes: Int) {
        val current = VoltbellState.alert.value
        if (current != null) {
            store.snooze(current.kind.name, System.currentTimeMillis() + minutes * 60_000L)
        }
        dismissAlert()
    }

    private fun stopSignals() {
        repeat?.let { handler.removeCallbacks(it) }
        repeat = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        player?.release()
        player = null
        tts?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "app.voltbell.STOP"
        const val ACTION_DISMISS = "app.voltbell.DISMISS"
        const val ACTION_SNOOZE = "app.voltbell.SNOOZE"
        const val ACTION_TEST = "app.voltbell.TEST"
        const val ACTION_REFRESH = "app.voltbell.REFRESH"
        const val EXTRA_MINUTES = "minutes"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatteryWatchService::class.java),
            )
        }

        fun send(context: Context, action: String, minutes: Int? = null) {
            val intent = Intent(context, BatteryWatchService::class.java).setAction(action)
            if (minutes != null) intent.putExtra(EXTRA_MINUTES, minutes)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
