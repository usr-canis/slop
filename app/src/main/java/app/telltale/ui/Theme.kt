package app.telltale.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.telltale.model.FieldStatus

val Accent = Color(0xFF0E7C6B)
private val AccentDark = Color(0xFF5CC9B4)

private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White, primaryContainer = Color(0xFFD5EFE9), onPrimaryContainer = Color(0xFF00201A),
    background = Color(0xFFFAFAF9), surface = Color(0xFFFAFAF9), surfaceContainer = Color(0xFFF0F1F0),
    surfaceContainerHigh = Color(0xFFE9EBEA), onSurface = Color(0xFF191C1B), onSurfaceVariant = Color(0xFF5B615F),
    outlineVariant = Color(0xFFD9DDDB),
)
private val Dark = darkColorScheme(
    primary = AccentDark, onPrimary = Color(0xFF00382E), primaryContainer = Color(0xFF0B4F44), onPrimaryContainer = Color(0xFFD5EFE9),
    background = Color(0xFF111413), surface = Color(0xFF111413), surfaceContainer = Color(0xFF1B1F1E),
    surfaceContainerHigh = Color(0xFF242928), onSurface = Color(0xFFE1E3E2), onSurfaceVariant = Color(0xFFA6ADAA),
    outlineVariant = Color(0xFF333937),
)

/** Tabular figures so numbers don't jiggle as they update. */
val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TelltaleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

@Composable
fun statusColor(s: FieldStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (s) {
        FieldStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
        FieldStatus.UNAVAILABLE -> if (dark) Color(0xFFE0B25C) else Color(0xFFB07A12)
        FieldStatus.NO_PERMISSION -> if (dark) Color(0xFFEF8A80) else Color(0xFFC2402F)
        FieldStatus.UNIMPLEMENTED, FieldStatus.UNSUPPORTED -> MaterialTheme.colorScheme.onSurfaceVariant
        FieldStatus.UNKNOWN, FieldStatus.WAITING -> MaterialTheme.colorScheme.outlineVariant
    }
}
