# Voltbell

Open-source Android battery sentinel. Arm it, set an unplug point (default 80%) and a low-battery point (default 20%), and Voltbell rings, speaks, notifies, and vibrates when those lines are crossed.

This is a **real APK** with an Android foreground service (`specialUse`) listening to `ACTION_BATTERY_CHANGED`. It is not a wrapped website. The watch keeps running after you leave the screen, with a sticky notification, optional unrestricted-battery prompt, voice, and a repeating alarm until you dismiss or snooze.

MIT licensed. [Buy me a coffee](https://buymeacoffee.com/wilsonsamiano)

## Install the APK

1. Open **Releases** (or the **Actions** artifact after a build) and download `voltbell-debug.apk`.
2. On the phone: Settings → Security → allow install from this source.
3. Open the APK. Grant **notifications** when asked.
4. Open the Service tab and tap **Allow unrestricted battery** so OEMs do not freeze the watch.

Sideload only — this is not on Play Store.

## What it does

| Rule | Default |
| --- | --- |
| Unplug alert | 80% while charging |
| Low battery | 20% on battery |
| Charger removed | on |
| Charger connected | off |
| Sound / voice / notify / vibrate | on |
| Quiet hours | off (22:00–07:00 when enabled) |
| Repeat | every 12 seconds until dismissed |

Alerts fire on **threshold crossing**, not while you sit at 100%. Test alarm is on the Watch tab.

## Build from source

Needs JDK 17 and Android SDK 35.

```bash
# from this directory
echo "sdk.dir=/path/to/Android/sdk" > local.properties
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions on `main` produces the same debug APK as an artifact.

Release signing is intentionally omitted in v1 so anyone can build a debug APK. Add your own keystore for a Play/sideload release.

## Permissions

- `POST_NOTIFICATIONS` — sticky watch + alert
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — keep watching off-screen
- `VIBRATE`, `WAKE_LOCK`, `USE_FULL_SCREEN_INTENT` — alarm that can break through the lock screen
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — optional, from the Service tab

Battery level itself needs no extra permission; Android broadcasts it.

## Project layout

```
app/src/main/java/app/voltbell/
  engine/     threshold logic (no Android APIs)
  service/    BatteryWatchService + notifications
  data/       SharedPreferences settings + log
  ui/         Compose screens
```

## License

[MIT](LICENSE) © 2026 Wilson Samiano
