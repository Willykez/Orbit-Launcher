package dev.jaimin.auraorbit.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.jaimin.auraorbit.LiveWallpaperSettings

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * LauncherSettingsScreen.kt — Pure-launcher settings (Compose)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Deliberately scoped to just the two things a "pure launcher" needs:
 *   1. Which apps sit in the bottom Dock (3–7, tap to toggle; see DockPrefs.kt)
 *   2. A link out to the existing, already-thorough [LiveWallpaperSettings]
 *      for sphere appearance (blur, scale, position, icon pack, etc.) —
 *      deliberately NOT reimplemented here, to avoid a second, divergent copy
 *      of settings that already work well.
 *
 * This screen does not touch widget prefs, group-scoped keys, or anything in
 * SphereWidgetProvider — the Dock is a standalone Home-screen concept (see
 * DockPrefs.kt's header comment) so nothing here can reintroduce a
 * widget-related bug into the pure-launcher experience.
 */
@Composable
fun LauncherSettingsScreen(
    visible: Boolean,
    onCloseRequested: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf(listOf<AppEntry>()) }
    var dockPackages by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(visible) {
        if (visible) {
            allApps = loadInstalledApps(context)
            dockPackages = loadDockPackages(context)
        }
    }

    fun toggleDockApp(packageName: String) {
        val current = dockPackages
        dockPackages = if (packageName in current) {
            current - packageName
        } else {
            if (current.size >= DOCK_MAX_APPS) {
                Toast.makeText(context, "Dock is full — remove one first (max $DOCK_MAX_APPS)", Toast.LENGTH_SHORT).show()
                return
            }
            current + packageName
        }
        saveDockPackages(context, dockPackages)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
            ),
        exit = fadeOut(animationSpec = tween(160)) +
            scaleOut(targetScale = 0.94f, animationSpec = tween(160))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 24) onCloseRequested()
                    }
                }
                .padding(top = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onCloseRequested)
                    .padding(12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            )

            Text(
                text = "Launcher Settings",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, top = 8.dp, bottom = 4.dp)
            )

            // ─── Sphere appearance (delegates to the existing rich settings) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(context, LiveWallpaperSettings::class.java))
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = Color.White)
                Text(
                    text = "Sphere Appearance",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Divider(color = Color.White.copy(alpha = 0.15f))

            // ─── Dock apps ─────────────────────────────────────────────────
            Text(
                text = "Dock Apps  ·  ${dockPackages.size}/$DOCK_MAX_APPS  ·  recommended $DOCK_RECOMMENDED_MIN_APPS–$DOCK_MAX_APPS",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, top = 16.dp, bottom = 8.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(allApps, key = { it.packageName }) { entry ->
                    val selected = entry.packageName in dockPackages
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleDockApp(entry.packageName) }
                            .padding(vertical = 10.dp)
                    ) {
                        Box {
                            Image(
                                bitmap = remember(entry.icon) { entry.icon.toBitmap().asImageBitmap() },
                                contentDescription = entry.label,
                                modifier = Modifier.size(48.dp)
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .background(Color(0xFF4CAF50), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = entry.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
