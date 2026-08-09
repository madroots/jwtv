package org.jw.phone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand palette shared with the TV UI (see :app VideoBrowserScreen).
private val JwDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF0033),
    background = Color(0xFF090B0E),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFFF5252)
)

@Composable
fun JwPhoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JwDarkColorScheme,
        content = content
    )
}
