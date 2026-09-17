package app.voltbell

import android.app.Application
import app.voltbell.data.SettingsStore
import app.voltbell.service.Notifications

class VoltbellApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        val store = SettingsStore(this)
        VoltbellState.setSettings(store.read())
        VoltbellState.replaceLog(store.readLog())
    }
}
