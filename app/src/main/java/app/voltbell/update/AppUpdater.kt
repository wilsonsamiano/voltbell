package app.voltbell.update

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import app.voltbell.BuildConfig
import app.voltbell.MainActivity
import app.voltbell.R
import app.voltbell.service.Notifications
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

sealed class UpdateUi {
    data object Idle : UpdateUi()
    data object Checking : UpdateUi()
    data object Current : UpdateUi()
    data class Downloading(val version: String, val progress: Int) : UpdateUi()
    data class Ready(val version: String, val file: File) : UpdateUi()
    data class Failed(val message: String) : UpdateUi()
}

object AppUpdater {
    const val ACTION_INSTALL = "app.voltbell.INSTALL_UPDATE"
    const val MIRROR = "https://cdn.jsdelivr.net/gh/wilsonsamiano/voltbell@main/android/dist/voltbell.apk"
    private const val API = "https://api.github.com/repos/wilsonsamiano/voltbell/releases/latest"
    private val MANIFESTS = listOf(
        "https://cdn.jsdelivr.net/gh/wilsonsamiano/voltbell@main/android/dist/version.json",
        "https://cdn.statically.io/gh/wilsonsamiano/voltbell/main/android/dist/version.json",
        "https://raw.githubusercontent.com/wilsonsamiano/voltbell/main/android/dist/version.json",
    )
    private const val PREFS = "voltbell.updates"
    private const val NOTE_ID = 19

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private var promptedFor: String? = null

    @Volatile
    var state: UpdateUi = UpdateUi.Idle
        private set

    var onChange: ((UpdateUi) -> Unit)? = null

    fun check(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (!force && !due(app)) return
        if (!busy.compareAndSet(false, true)) return
        publish(UpdateUi.Checking)
        io.execute {
            try {
                val remote = fetchLatest()
                val local = BuildConfig.VERSION_NAME.substringBefore("-")
                if (remote == null || !isNewer(remote.version, local)) {
                    prefs(app).edit().remove("ready").apply()
                    finish(app, UpdateUi.Current)
                    return@execute
                }
                val file = updateFile(app)
                val already = file.exists() && file.length() > 100_000 &&
                    prefs(app).getString("ready", null) == remote.version
                if (!already) {
                    publish(UpdateUi.Downloading(remote.version, 0))
                    file.parentFile?.mkdirs()
                    download(remote.url, file) { pct ->
                        publish(UpdateUi.Downloading(remote.version, pct))
                    }
                    prefs(app).edit().putString("ready", remote.version).apply()
                }
                finish(app, UpdateUi.Ready(remote.version, file))
                notifyReady(app, remote.version)
            } catch (e: Exception) {
                val why = e.message?.lineSequence()?.firstOrNull()?.take(160)?.trim().orEmpty()
                finish(app, UpdateUi.Failed(if (why.isBlank()) "Couldn't reach the update server" else why), remember = false)
            }
        }
    }

    fun promptIfReady(activity: Activity) {
        val ready = state as? UpdateUi.Ready ?: return
        if (promptedFor == ready.version) return
        promptedFor = ready.version
        launchInstaller(activity, ready)
    }

    fun install(context: Context) {
        val ready = state as? UpdateUi.Ready ?: run {
            check(context, force = true)
            return
        }
        if (launchInstaller(context, ready)) promptedFor = ready.version
    }

    private fun finish(app: Context, next: UpdateUi, remember: Boolean = true) {
        if (remember) prefs(app).edit().putLong("checkedAt", System.currentTimeMillis()).apply()
        busy.set(false)
        publish(next)
    }

    private fun publish(next: UpdateUi) {
        main.post {
            state = next
            onChange?.invoke(next)
        }
    }

    private fun due(context: Context): Boolean {
        val last = prefs(context).getLong("checkedAt", 0L)
        return System.currentTimeMillis() - last > 12 * 60 * 60 * 1000L
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun updateFile(context: Context) = File(context.cacheDir, "updates/voltbell-update.apk")

    private data class Remote(val version: String, val url: String)

    private fun fetchLatest(): Remote? {
        val errors = mutableListOf<String>()
        try {
            readGithubRelease()?.let { return it }
        } catch (e: Exception) {
            errors += "GitHub: ${brief(e)}"
        }
        for (manifest in MANIFESTS) {
            try {
                return readManifest(manifest)
            } catch (e: Exception) {
                errors += "${hostOf(manifest)}: ${brief(e)}"
            }
        }
        error(errors.joinToString(" · ").ifBlank { "Couldn't reach the update server" })
    }

    private fun readManifest(manifestUrl: String): Remote {
        val conn = open(manifestUrl)
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val version = json.optString("version").removePrefix("v").substringBefore("-")
        if (version.isBlank()) error("no version")
        val apkName = json.optString("apk").ifBlank { "voltbell.apk" }
        val base = manifestUrl.substringBeforeLast('/') + "/"
        return Remote(version, base + apkName)
    }

    private fun readGithubRelease(): Remote? {
        val conn = open(API)
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val version = json.optString("tag_name").removePrefix("v").substringBefore("-")
        if (version.isBlank()) return null
        val assets = json.optJSONArray("assets") ?: return null
        var url = ""
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk")) {
                url = asset.optString("browser_download_url")
                break
            }
        }
        if (url.isBlank()) return null
        return Remote(version, url)
    }

    private fun brief(e: Exception): String {
        val msg = e.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        val text = if (msg.isBlank()) e.javaClass.simpleName else msg
        return text.take(60)
    }

    private fun hostOf(url: String) = runCatching { URL(url).host }.getOrDefault(url)

    private fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = open(url)
        conn.setRequestProperty("Accept", "application/octet-stream")
        val total = conn.contentLengthLong
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(8192)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                }
            }
        }
        conn.disconnect()
        if (tmp.length() < 100_000) {
            tmp.delete()
            error("download too small")
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) tmp.copyTo(dest, overwrite = true)
    }

    private fun open(url: String, redirects: Int = 0): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 8_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("User-Agent", "Voltbell/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "*/*")
        conn.connect()
        if (conn.responseCode in 300..399 && redirects < 5) {
            val next = conn.getHeaderField("Location")
            conn.disconnect()
            if (next.isNullOrBlank()) error("redirect without location")
            val absolute = URL(URL(url), next).toString()
            return open(absolute, redirects + 1)
        }
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val code = conn.responseCode
            conn.disconnect()
            error("HTTP $code $err")
        }
        return conn
    }

    private fun launchInstaller(context: Context, ready: UpdateUi.Ready): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settings)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ready.file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        cancelNote(context)
        return true
    }

    private fun notifyReady(context: Context, version: String) {
        Notifications.ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            41,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_INSTALL)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, Notifications.CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_stat_voltbell)
            .setContentTitle("Voltbell $version is ready")
            .setContentText("Tap to install the update")
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Install", open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTE_ID, note)
    }

    private fun cancelNote(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
    }

    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(value: String) = value.split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }
}
