package app.voltbell

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.voltbell.service.BatteryWatchService
import app.voltbell.ui.theme.Background
import app.voltbell.ui.theme.Card
import app.voltbell.ui.theme.Charge
import app.voltbell.ui.theme.Foreground
import app.voltbell.ui.theme.Muted
import app.voltbell.ui.theme.VoltbellTheme

class AlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val alert by VoltbellState.alert.collectAsState()
            LaunchedEffect(alert) {
                if (alert == null) finish()
            }
            val current = alert ?: return@setContent
            VoltbellTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xF209090B))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Card, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${current.level}%",
                            color = Foreground,
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            current.kind.title,
                            color = Charge,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            current.kind.hint,
                            color = Muted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = {
                                BatteryWatchService.send(this@AlertActivity, BatteryWatchService.ACTION_DISMISS)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Foreground, contentColor = Background),
                        ) {
                            Text("Dismiss", fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                BatteryWatchService.send(this@AlertActivity, BatteryWatchService.ACTION_SNOOZE, 15)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Snooze 15 min", color = Muted, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
