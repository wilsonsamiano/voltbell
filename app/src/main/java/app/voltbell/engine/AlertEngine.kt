package app.voltbell.engine

import java.util.Calendar

object AlertEngine {
    fun isQuietNow(settings: Settings, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!settings.quietEnabled) return false
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val cur = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = toMin(settings.quietStart)
        val end = toMin(settings.quietEnd)
        if (start == end) return false
        return if (start < end) cur in start until end else cur >= start || cur < end
    }

    fun evaluate(prev: BatterySnapshot, next: BatterySnapshot, settings: Settings): AlertKind? {
        if (!settings.armed) return null
        val prevPct = prev.percent.coerceIn(0, 100)
        val nextPct = next.percent.coerceIn(0, 100)

        if (settings.unplugAlert && prev.charging && !next.charging) return AlertKind.UNPLUG
        if (settings.plugAlert && !prev.charging && next.charging) return AlertKind.PLUG

        if (
            next.charging &&
            settings.stopEnabled &&
            prevPct < settings.stopAt &&
            nextPct >= settings.stopAt
        ) {
            return if (nextPct >= 100) AlertKind.FULL else AlertKind.STOP
        }

        if (
            !next.charging &&
            settings.lowEnabled &&
            prevPct > settings.lowAt &&
            nextPct <= settings.lowAt
        ) {
            return AlertKind.LOW
        }

        return null
    }

    fun formatEta(seconds: Int, charging: Boolean, percent: Int, stopAt: Int): String {
        if (charging && percent >= 100) return "Full — safe to unplug"
        if (charging && percent >= stopAt) return "At unplug point"
        if (seconds < 0 || seconds > 60 * 60 * 48) {
            return if (charging) "Charging" else "On battery"
        }
        if (seconds == 0) return "now"
        if (seconds < 60) return "under a minute"
        val mins = (seconds + 30) / 60
        if (mins < 60) return "$mins min"
        val h = mins / 60
        val m = mins % 60
        return if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    private fun toMin(hhmm: String): Int {
        val parts = hhmm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }
}
