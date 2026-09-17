package app.voltbell.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voltbell.engine.ActiveAlert
import app.voltbell.engine.AlertEngine
import app.voltbell.engine.BatterySnapshot
import app.voltbell.engine.LogEntry
import app.voltbell.engine.Settings
import app.voltbell.engine.Tab
import app.voltbell.ui.theme.Background
import app.voltbell.ui.theme.Border
import app.voltbell.ui.theme.Card
import app.voltbell.ui.theme.Card2
import app.voltbell.ui.theme.Charge
import app.voltbell.ui.theme.Danger
import app.voltbell.ui.theme.Foreground
import app.voltbell.ui.theme.Muted
import app.voltbell.ui.theme.Subtle
import app.voltbell.ui.theme.Warn
import java.util.Date
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun VoltbellScreen(
    settings: Settings,
    battery: BatterySnapshot,
    alert: ActiveAlert?,
    log: List<LogEntry>,
    serviceOn: Boolean,
    ignoringBatteryOpt: Boolean,
    onSettings: ((Settings) -> Settings) -> Unit,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    onTest: () -> Unit,
    onBatteryOpt: () -> Unit,
    onOpenSource: () -> Unit,
    onCoffee: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.WATCH) }
    val pct = battery.percent.coerceIn(0, 100)
    val status = when {
        battery.percent < 0 -> "Reading…"
        battery.charging && pct >= settings.stopAt -> "Unplug"
        battery.charging -> "Charging"
        pct <= settings.lowAt -> "Low"
        else -> "On battery"
    }
    val eta = if (battery.percent < 0) {
        "Waiting for battery"
    } else {
        AlertEngine.formatEta(
            if (battery.charging) battery.chargingTimeSec else battery.dischargingTimeSec,
            battery.charging,
            pct,
            settings.stopAt,
        )
    }
    val phase = when {
        !settings.armed -> "Service · stopped"
        serviceOn -> "Service · foreground"
        else -> "Service · paused"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 72.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        "VOLTBELL",
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 3.sp,
                    )
                    Text("Battery sentinel", color = Foreground, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(if (settings.armed) Charge.copy(alpha = 0.15f) else Card2)
                        .clickable { onSettings { it.copy(armed = !it.armed) } }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.NotificationsActive,
                        contentDescription = null,
                        tint = if (settings.armed) Charge else Muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (settings.armed) "Armed" else "Off",
                        color = if (settings.armed) Charge else Muted,
                        fontSize = 14.sp,
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                when (tab) {
                    Tab.WATCH -> WatchPane(
                        pct = pct,
                        charging = battery.charging,
                        status = status,
                        eta = eta,
                        phase = phase,
                        settings = settings,
                        onTest = onTest,
                        onSettings = onSettings,
                    )
                    Tab.SERVICE -> ServicePane(
                        phase = phase,
                        serviceOn = serviceOn,
                        ignoringBatteryOpt = ignoringBatteryOpt,
                        settings = settings,
                        onSettings = onSettings,
                        onBatteryOpt = onBatteryOpt,
                    )
                    Tab.RULES -> RulesPane(settings, onSettings)
                    Tab.LOG -> LogPane(log)
                }

                Spacer(Modifier.height(28.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = onCoffee) {
                        Icon(Icons.Outlined.Coffee, contentDescription = null, tint = Muted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buy me a coffee", color = Muted, fontSize = 14.sp)
                    }
                }
                TextButton(onClick = onOpenSource, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Open source · MIT", color = Subtle, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Background)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            NavItem("Watch", Icons.Outlined.Bolt, tab == Tab.WATCH) { tab = Tab.WATCH }
            NavItem("Service", Icons.Outlined.Shield, tab == Tab.SERVICE) { tab = Tab.SERVICE }
            NavItem("Rules", Icons.Outlined.Tune, tab == Tab.RULES) { tab = Tab.RULES }
            NavItem("Log", Icons.Outlined.History, tab == Tab.LOG) { tab = Tab.LOG }
        }

        if (alert != null) {
            AlertOverlay(alert = alert, onDismiss = onDismiss, onSnooze = onSnooze)
        }
    }
}

@Composable
private fun WatchPane(
    pct: Int,
    charging: Boolean,
    status: String,
    eta: String,
    phase: String,
    settings: Settings,
    onTest: () -> Unit,
    onSettings: ((Settings) -> Settings) -> Unit,
) {
    Gauge(pct = pct, charging = charging, label = status)
    Text(eta, color = Muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text("Live from this device", color = Subtle, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Text(
        phase.uppercase(),
        color = if (phase.contains("stopped")) Muted else Charge,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Unplug at", "${settings.stopAt}%", settings.stopEnabled, Modifier.weight(1f))
        StatCard("Plug in at", "${settings.lowAt}%", settings.lowEnabled, Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onTest,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Foreground, contentColor = Background),
    ) {
        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Test alarm", fontSize = 16.sp)
    }
    Spacer(Modifier.height(20.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        Text("SIGNALS", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalChip("Sound", settings.sound, Modifier.weight(1f)) { onSettings { it.copy(sound = !it.sound) } }
            SignalChip("Voice", settings.voice, Modifier.weight(1f)) { onSettings { it.copy(voice = !it.voice) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SignalChip("Notify", settings.notify, Modifier.weight(1f)) { onSettings { it.copy(notify = !it.notify) } }
            SignalChip("Vibrate", settings.vibrate, Modifier.weight(1f)) { onSettings { it.copy(vibrate = !it.vibrate) } }
        }
    }
}

@Composable
private fun ServicePane(
    phase: String,
    serviceOn: Boolean,
    ignoringBatteryOpt: Boolean,
    settings: Settings,
    onSettings: ((Settings) -> Settings) -> Unit,
    onBatteryOpt: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        Text("FOREGROUND SERVICE", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Text(phase.removePrefix("Service · ").replaceFirstChar { it.uppercase() }, color = Foreground, fontSize = 22.sp)
        Text(
            if (serviceOn) "Sticky notification is up. Battery broadcasts keep flowing."
            else "Arm Voltbell to start the watch service.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(16.dp))
        SettingRow("Background watch", settings.backgroundWatch) {
            onSettings { it.copy(backgroundWatch = it.backgroundWatch.not()) }
        }
        SettingRow("Armed", settings.armed) { onSettings { it.copy(armed = !it.armed) } }
    }
    Spacer(Modifier.height(12.dp))
    Layer("Foreground service", serviceOn)
    Layer("Sticky watch notification", serviceOn)
    Layer("Battery broadcasts", true)
    Layer("Unrestricted battery", ignoringBatteryOpt)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onBatteryOpt,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Card2, contentColor = Foreground),
    ) {
        Text(if (ignoringBatteryOpt) "Battery already unrestricted" else "Allow unrestricted battery")
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "This is a real Android foreground service (specialUse). It is why the APK can keep watching after you leave the screen — unlike the web preview.",
        color = Subtle,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

@Composable
private fun RulesPane(settings: Settings, onSettings: ((Settings) -> Settings) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        SettingRow("Unplug alert", settings.stopEnabled) { onSettings { it.copy(stopEnabled = !it.stopEnabled) } }
        Text("Stop at ${settings.stopAt}%", color = Foreground, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = settings.stopAt.toFloat(),
            onValueChange = { v -> onSettings { it.copy(stopAt = v.toInt()) } },
            valueRange = 50f..100f,
            colors = SliderDefaults.colors(thumbColor = Charge, activeTrackColor = Charge),
        )
        Spacer(Modifier.height(8.dp))
        SettingRow("Low battery", settings.lowEnabled) { onSettings { it.copy(lowEnabled = !it.lowEnabled) } }
        Text("Plug in at ${settings.lowAt}%", color = Foreground, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Slider(
            value = settings.lowAt.toFloat(),
            onValueChange = { v -> onSettings { it.copy(lowAt = v.toInt()) } },
            valueRange = 5f..40f,
            colors = SliderDefaults.colors(thumbColor = Warn, activeTrackColor = Warn),
        )
        SettingRow("Charger removed", settings.unplugAlert) { onSettings { it.copy(unplugAlert = !it.unplugAlert) } }
        SettingRow("Charger connected", settings.plugAlert) { onSettings { it.copy(plugAlert = !it.plugAlert) } }
        SettingRow("Quiet hours", settings.quietEnabled) { onSettings { it.copy(quietEnabled = !it.quietEnabled) } }
        if (settings.quietEnabled) {
            Text(
                "${settings.quietStart} – ${settings.quietEnd}",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LogPane(log: List<LogEntry>) {
    if (log.isEmpty()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = Subtle, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("No alerts yet", color = Foreground)
            Text("Fired alarms land here.", color = Muted, fontSize = 13.sp)
        }
        return
    }
    log.forEach { entry ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Card)
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(entry.kind.title, color = Foreground, fontSize = 15.sp)
                Text("${entry.level}% · ${if (entry.charging) "charging" else "on battery"}", color = Muted, fontSize = 12.sp)
            }
            Text(
                DateFormat.format("h:mm a", Date(entry.at)).toString(),
                color = Subtle,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun AlertOverlay(alert: ActiveAlert, onDismiss: () -> Unit, onSnooze: (Int) -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "a",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20A0A0C))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Charge.copy(alpha = alpha), style = Stroke(width = 10.dp.toPx()))
                    drawCircle(Charge, radius = size.minDimension / 2 - 18.dp.toPx(), style = Stroke(width = 3.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${alert.level}", color = Foreground, fontSize = 56.sp, fontWeight = FontWeight.Medium)
                    Text("%", color = Muted, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(alert.kind.title.uppercase(), color = Charge, letterSpacing = 2.sp, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(alert.kind.hint, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Foreground, contentColor = Background),
            ) { Text("Dismiss") }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { onSnooze(15) }) { Text("Snooze 15 min", color = Muted) }
        }
    }
}

@Composable
private fun Gauge(pct: Int, charging: Boolean, label: String) {
    val color = when {
        pct <= 20 -> Danger
        charging -> Charge
        pct >= 80 -> Warn
        else -> Charge
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(240.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2 + 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(Border, -210f, 240f, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val sweep = 240f * (pct.coerceIn(0, 100) / 100f)
            drawArc(color, -210f, sweep, false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val cx = size.width / 2
            val cy = size.height / 2
            val r = min(cx, cy) - 2.dp.toPx()
            for (i in 0..12) {
                val ang = Math.toRadians((-210f + i * 20f).toDouble())
                val inner = r - 18.dp.toPx()
                val outer = r - 8.dp.toPx()
                drawLine(
                    Subtle,
                    Offset(cx + (inner * cos(ang)).toFloat(), cy + (inner * sin(ang)).toFloat()),
                    Offset(cx + (outer * cos(ang)).toFloat(), cy + (outer * sin(ang)).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (pct < 0) "—" else "$pct", color = Foreground, fontSize = 64.sp, fontWeight = FontWeight.Medium)
            Text(label.uppercase(), color = color, fontSize = 13.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, on: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Power, contentDescription = null, tint = if (on) Charge else Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Muted, fontSize = 12.sp)
        }
        Text(value, color = Foreground, fontSize = 22.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SignalChip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Charge.copy(alpha = 0.14f) else Card2)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) Charge else Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SettingRow(title: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Foreground, fontSize = 15.sp)
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = Charge, checkedThumbColor = Background),
        )
    }
}

@Composable
private fun Layer(name: String, on: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, color = Foreground, fontSize = 14.sp)
        Text(if (on) "On" else "Off", color = if (on) Charge else Muted, fontSize = 13.sp)
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Charge else Muted, modifier = Modifier.size(22.dp))
        Text(label, color = if (selected) Charge else Muted, fontSize = 11.sp)
    }
}
