package app.voltbell.data

import android.content.Context
import android.content.SharedPreferences
import app.voltbell.engine.LogEntry
import app.voltbell.engine.Settings
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("voltbell", Context.MODE_PRIVATE)

    fun read(): Settings = Settings(
        armed = prefs.getBoolean("armed", true),
        stopEnabled = prefs.getBoolean("stopEnabled", true),
        stopAt = prefs.getInt("stopAt", 80),
        lowEnabled = prefs.getBoolean("lowEnabled", true),
        lowAt = prefs.getInt("lowAt", 20),
        unplugAlert = prefs.getBoolean("unplugAlert", true),
        plugAlert = prefs.getBoolean("plugAlert", false),
        sound = prefs.getBoolean("sound", true),
        voice = prefs.getBoolean("voice", true),
        notify = prefs.getBoolean("notify", true),
        vibrate = prefs.getBoolean("vibrate", true),
        repeatSeconds = prefs.getInt("repeatSeconds", 12),
        quietEnabled = prefs.getBoolean("quietEnabled", false),
        quietStart = prefs.getString("quietStart", "22:00") ?: "22:00",
        quietEnd = prefs.getString("quietEnd", "07:00") ?: "07:00",
        backgroundWatch = prefs.getBoolean("backgroundWatch", true),
    )

    fun write(settings: Settings) {
        prefs.edit()
            .putBoolean("armed", settings.armed)
            .putBoolean("stopEnabled", settings.stopEnabled)
            .putInt("stopAt", settings.stopAt)
            .putBoolean("lowEnabled", settings.lowEnabled)
            .putInt("lowAt", settings.lowAt)
            .putBoolean("unplugAlert", settings.unplugAlert)
            .putBoolean("plugAlert", settings.plugAlert)
            .putBoolean("sound", settings.sound)
            .putBoolean("voice", settings.voice)
            .putBoolean("notify", settings.notify)
            .putBoolean("vibrate", settings.vibrate)
            .putInt("repeatSeconds", settings.repeatSeconds)
            .putBoolean("quietEnabled", settings.quietEnabled)
            .putString("quietStart", settings.quietStart)
            .putString("quietEnd", settings.quietEnd)
            .putBoolean("backgroundWatch", settings.backgroundWatch)
            .apply()
    }

    fun snoozedUntil(kind: String): Long = prefs.getLong("snooze_$kind", 0L)

    fun snooze(kind: String, untilMillis: Long) {
        prefs.edit().putLong("snooze_$kind", untilMillis).apply()
    }

    fun readLog(): List<LogEntry> {
        val raw = prefs.getString("log", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        LogEntry(
                            id = o.getString("id"),
                            kind = app.voltbell.engine.AlertKind.valueOf(o.getString("kind")),
                            level = o.getInt("level"),
                            at = o.getLong("at"),
                            charging = o.getBoolean("charging"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun writeLog(entries: List<LogEntry>) {
        val arr = JSONArray()
        entries.take(40).forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("kind", e.kind.name)
                    .put("level", e.level)
                    .put("at", e.at)
                    .put("charging", e.charging),
            )
        }
        prefs.edit().putString("log", arr.toString()).apply()
    }
}
