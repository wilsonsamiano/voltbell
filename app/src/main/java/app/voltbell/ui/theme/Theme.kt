package app.voltbell.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF09090B)
val Foreground = Color(0xFFECECE8)
val Card = Color(0xFF121316)
val Card2 = Color(0xFF191B1E)
val Muted = Color(0xFF8C8E94)
val Subtle = Color(0xFF6A6C72)
val Border = Color(0xFF2A2C31)
val Charge = Color(0xFF5DCEA8)
val Warn = Color(0xFFD4A054)
val Danger = Color(0xFFD46A5C)

private val Scheme = darkColorScheme(
    primary = Charge,
    onPrimary = Background,
    background = Background,
    onBackground = Foreground,
    surface = Card,
    onSurface = Foreground,
    surfaceVariant = Card2,
    onSurfaceVariant = Muted,
    error = Danger,
    outline = Border,
)

@Composable
fun VoltbellTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
