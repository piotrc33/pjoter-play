package pl.pjoterplay.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Below Android 12 (no wallpaper colours): the download page's palette. Keep in sync with docs/style.css.
private val Light = lightColorScheme(
    primary = Color(0xFFC4623A),
    onPrimary = Color(0xFFFFFDF8),
    background = Color(0xFFF6F1E7),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFF6F1E7),
    onSurface = Color(0xFF1F1B16),
    surfaceContainer = Color(0xFFFFFDF8),
    onSurfaceVariant = Color(0xFF6B6259),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFD9784F),
    onPrimary = Color(0xFF1F1B16),
    background = Color(0xFF1F1B16),
    onBackground = Color(0xFFF6F1E7),
    surface = Color(0xFF1F1B16),
    onSurface = Color(0xFFF6F1E7),
    surfaceContainer = Color(0xFF2A251F),
    onSurfaceVariant = Color(0xFFB3A897),
)

@Composable
fun PjoterPlayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) Dark else Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
