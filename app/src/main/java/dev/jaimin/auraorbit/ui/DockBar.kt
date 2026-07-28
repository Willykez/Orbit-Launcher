package dev.jaimin.auraorbit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.jaimin.auraorbit.AppFetcher

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DockBar.kt — Persistent bottom dock (3–7 user-chosen apps)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Sits at the bottom of the sphere Home screen at all times (hidden only
 * while the app drawer or settings overlay is open). Tap an icon to launch
 * it directly — via [AppFetcher.launchApp] for the same zoom+fade transition
 * used everywhere else in the launcher. Long-press ANYWHERE on the dock
 * (including empty space, so an empty/unconfigured dock is still reachable)
 * opens [LauncherSettingsScreen] to configure which apps are pinned.
 */
@Composable
fun DockBar(
    visible: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var dockApps by remember { mutableStateOf(listOf<AppEntry>()) }

    // Re-load whenever the dock becomes visible again (e.g. after the user
    // saves new picks in LauncherSettingsScreen and returns to the sphere).
    LaunchedEffect(visible) {
        if (visible) {
            val packages = loadDockPackages(context)
            dockApps = if (packages.isEmpty()) {
                emptyList()
            } else {
                val all = loadInstalledApps(context).associateBy { it.packageName }
                packages.mapNotNull { all[it] }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                    .combinedClickable(
                        onClick = {}, // absorb taps on empty dock space (don't fall through to the sphere)
                        onLongClick = onOpenSettings
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dockApps.isEmpty()) {
                    Text(
                        text = "Long-press to set up your Dock",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                } else {
                    dockApps.forEach { entry ->
                        Image(
                            bitmap = remember(entry.icon) { entry.icon.toBitmap().asImageBitmap() },
                            contentDescription = entry.label,
                            modifier = Modifier
                                .size(48.dp)
                                .combinedClickable(
                                    onClick = { AppFetcher.launchApp(context, entry.packageName) },
                                    onLongClick = onOpenSettings
                                )
                        )
                    }
                }
            }
        }
    }
}
