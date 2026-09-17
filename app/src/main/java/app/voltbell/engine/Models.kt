package app.voltbell.engine

enum class AlertKind {
    STOP, FULL, LOW, UNPLUG, PLUG;

    val title: String
        get() = when (this) {
            STOP -> "Unplug now"
            FULL -> "Fully charged"
            LOW -> "Plug in now"
            UNPLUG -> "Charger removed"
            PLUG -> "Charging started"
        }

    val hint: String
        get() = when (this) {
            STOP -> "Stop charge to protect battery health"
            FULL -> "Unplug the charger"
            LOW -> "Battery is running low"
            UNPLUG -> "Charging stopped"
            PLUG -> "Power connected"
        }

    fun voice(level: Int): String = when (this) {
        STOP -> "Battery is at $level percent. Unplug to protect battery health."
        FULL -> "Battery is fully charged at $level percent. Unplug now."
        LOW -> "Battery is at $level percent. Plug in now."
        UNPLUG -> "Charging stopped. Battery is at $level percent."
        PLUG -> "Charging started. Battery is at $level percent."
    }
}

data class BatterySnapshot(
    val percent: Int,
    val charging: Boolean,
    val chargingTimeSec: Int = -1,
    val dischargingTimeSec: Int = -1,
)

data class Settings(
    val armed: Boolean = true,
    val stopEnabled: Boolean = true,
    val stopAt: Int = 80,
    val lowEnabled: Boolean = true,
    val lowAt: Int = 20,
    val unplugAlert: Boolean = true,
    val plugAlert: Boolean = false,
    val sound: Boolean = true,
    val voice: Boolean = true,
    val notify: Boolean = true,
    val vibrate: Boolean = true,
    val repeatSeconds: Int = 12,
    val quietEnabled: Boolean = false,
    val quietStart: String = "22:00",
    val quietEnd: String = "07:00",
    val backgroundWatch: Boolean = true,
)

data class ActiveAlert(
    val id: String,
    val kind: AlertKind,
    val level: Int,
    val at: Long = System.currentTimeMillis(),
)

data class LogEntry(
    val id: String,
    val kind: AlertKind,
    val level: Int,
    val at: Long,
    val charging: Boolean,
)

enum class Tab { WATCH, SERVICE, RULES, LOG }
