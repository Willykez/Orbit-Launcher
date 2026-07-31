package dev.jaimin.auraorbit.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Always dark, regardless of system theme — a launcher's home screen and
 * drawer read better against a dark background at any time of day. On
 * Android 12+, accent colors are still derived from the user's wallpaper via
 * dynamic color.
 */
@Composable
fun AuraOrbitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
