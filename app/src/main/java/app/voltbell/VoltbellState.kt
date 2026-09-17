package app.voltbell

import app.voltbell.engine.ActiveAlert
import app.voltbell.engine.BatterySnapshot
import app.voltbell.engine.LogEntry
import app.voltbell.engine.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object VoltbellState {
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _battery = MutableStateFlow(BatterySnapshot(percent = -1, charging = false))
    val battery: StateFlow<BatterySnapshot> = _battery.asStateFlow()

    private val _alert = MutableStateFlow<ActiveAlert?>(null)
    val alert: StateFlow<ActiveAlert?> = _alert.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    private val _serviceOn = MutableStateFlow(false)
    val serviceOn: StateFlow<Boolean> = _serviceOn.asStateFlow()

    private val _ignoringBatteryOpt = MutableStateFlow(false)
    val ignoringBatteryOpt: StateFlow<Boolean> = _ignoringBatteryOpt.asStateFlow()

    fun setSettings(value: Settings) {
        _settings.value = value
    }

    fun setBattery(value: BatterySnapshot) {
        _battery.value = value
    }

    fun setAlert(value: ActiveAlert?) {
        _alert.value = value
    }

    fun prependLog(entry: LogEntry) {
        _log.update { (listOf(entry) + it).take(40) }
    }

    fun replaceLog(entries: List<LogEntry>) {
        _log.value = entries
    }

    fun setServiceOn(on: Boolean) {
        _serviceOn.value = on
    }

    fun setIgnoringBatteryOpt(on: Boolean) {
        _ignoringBatteryOpt.value = on
    }
}
