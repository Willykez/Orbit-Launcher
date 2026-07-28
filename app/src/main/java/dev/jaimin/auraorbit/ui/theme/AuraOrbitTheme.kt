package dev.jaimin.auraorbit.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * AuraOrbitTheme.kt — Shared Material3 theme for all Compose screens
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * The sphere itself is always rendered over a dark/blurred backdrop, so every
 * Compose surface hosted alongside it (App Drawer, and future screens) uses a
 * dark color scheme regardless of system theme. On Android 12+ we pick up the
 * user's wallpaper-derived dynamic color for accents, matching
 * `values-v31/colors_widget.xml`'s existing dynamic-color approach for the
 * widget.
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
