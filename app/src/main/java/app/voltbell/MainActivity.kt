package app.voltbell

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import app.voltbell.data.SettingsStore
import app.voltbell.engine.Settings
import app.voltbell.service.BatteryWatchService
import app.voltbell.ui.VoltbellScreen
import app.voltbell.ui.theme.VoltbellTheme
import app.voltbell.update.AppUpdater
import app.voltbell.update.UpdateUi

class MainActivity : ComponentActivity() {
    private lateinit var store: SettingsStore
    private var updateState by mutableStateOf<UpdateUi>(UpdateUi.Idle)

    private val notifyPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* service still starts; alerts degrade without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = SettingsStore(this)
        VoltbellState.setSettings(store.read())
        VoltbellState.replaceLog(store.readLog())
        if (Build.VERSION.SDK_INT >= 33) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        syncService(store.read())
        AppUpdater.onChange = { updateState = it }
        updateState = AppUpdater.state
        AppUpdater.check(this, force = true)

        setContent {
            val settings by VoltbellState.settings.collectAsState()
            val battery by VoltbellState.battery.collectAsState()
            val alert by VoltbellState.alert.collectAsState()
            val log by VoltbellState.log.collectAsState()
            val serviceOn by VoltbellState.serviceOn.collectAsState()
            val ignoringOpt by VoltbellState.ignoringBatteryOpt.collectAsState()
            val update = updateState
            LaunchedEffect(update) {
                if (update is UpdateUi.Ready) AppUpdater.promptIfReady(this@MainActivity)
            }
            VoltbellTheme {
                VoltbellScreen(
                    settings = settings,
                    battery = battery,
                    alert = alert,
                    log = log,
                    serviceOn = serviceOn,
                    ignoringBatteryOpt = ignoringOpt,
                    update = update,
                    onSettings = { patch ->
                        val next = patch(settings)
                        store.write(next)
                        VoltbellState.setSettings(next)
                        syncService(next)
                    },
                    onDismiss = { BatteryWatchService.send(this, BatteryWatchService.ACTION_DISMISS) },
                    onSnooze = { BatteryWatchService.send(this, BatteryWatchService.ACTION_SNOOZE, it) },
                    onTest = { BatteryWatchService.send(this, BatteryWatchService.ACTION_TEST) },
                    onBatteryOpt = { requestUnrestrictedBattery() },
                    onOpenSource = { openUrl(getString(R.string.source_url)) },
                    onCoffee = { openUrl(getString(R.string.bmc_url)) },
                    onCheckUpdate = { AppUpdater.check(this, force = true) },
                    onInstallUpdate = { AppUpdater.install(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryOpt()
        syncService(store.read())
        if (intent?.action == AppUpdater.ACTION_INSTALL) {
            AppUpdater.install(this)
            intent.action = null
        } else {
            AppUpdater.promptIfReady(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun syncService(settings: Settings) {
        if (settings.armed && settings.backgroundWatch) {
            BatteryWatchService.start(this)
        } else if (!settings.armed) {
            stopService(Intent(this, BatteryWatchService::class.java))
            VoltbellState.setServiceOn(false)
        }
    }

    private fun refreshBatteryOpt() {
        val pm = getSystemService(PowerManager::class.java)
        VoltbellState.setIgnoringBatteryOpt(pm.isIgnoringBatteryOptimizations(packageName))
    }

    private fun requestUnrestrictedBattery() {
        val intent = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    companion object {
        const val EXTRA_SHOW_ALERT = "show_alert"
    }
}
