package dev.jaimin.auraorbit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * SphereScreen.kt — the home screen
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Apps are arranged around a single horizontal ring. Each app's angle around
 * that ring is its index times (360° / count), plus a shared [rotation]
 * offset that horizontal dragging changes directly (1:1, no fling physics —
 * kept intentionally simple). Depth is simulated — NOT real 3D/OpenGL — by
 * scaling and fading each icon based on cos(angle): icons facing the viewer
 * (angle ≈ 0°) are large and opaque, icons at the back (angle ≈ 180°) are
 * small and faint. This reads as a rotating sphere/carousel without any of
 * the native rendering complexity the previous libGDX version had.
 *
 * A fast upward drag opens the app drawer (see [onOpenDrawer]).
 */
@Composable
fun SphereScreen(
    onOpenDrawer: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(listOf<AppInfo>()) }
    var rotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        apps = loadInstalledApps(context)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        // Horizontal drag rotates the ring. Sensitivity is a
                        // plain tuned constant, not derived from anything —
                        // it just needs to feel reasonable on a phone screen.
                        rotation += dragAmount.x * 0.4f
                    } else if (dragAmount.y < -12f) {
                        // Fast upward drag opens the drawer. Calling this
                        // repeatedly during one gesture is harmless — it
                        // just sets a boolean state to true each time.
                        onOpenDrawer()
                    }
                }
            }
    ) {
        if (apps.isEmpty()) {
            return@BoxWithConstraints
        }

        val density = LocalDensity.current
        val centerXPx = with(density) { (maxWidth / 2).toPx() }
        val centerYPx = with(density) { (maxHeight / 2).toPx() }
        val ringRadiusPx = with(density) { (maxWidth.value * 0.62f).dp.toPx() }
        // Deliberately smaller than ringRadiusPx (a flattened ellipse, not a
        // full circle) — a full circle would push the topmost/bottommost
        // icons uncomfortably close to the screen edges on tall phones.
        val verticalRadiusPx = with(density) { (maxHeight.value * 0.16f).dp.toPx() }

        val count = apps.size
        val step = 360f / count

        // Render back-to-front so nearer icons draw on top of farther ones
        // (simple painter's algorithm — no zIndex juggling needed).
        val ordered = apps.withIndex().sortedBy { (i, _) ->
            val angleDeg = i * step + rotation
            -cos(Math.toRadians(angleDeg.toDouble()))
        }

        for ((index, app) in ordered) {
            val angleDeg = index * step + rotation
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val depth = cos(angleRad).toFloat() // 1 = front (nearest), -1 = back (farthest)

            // Skip icons on the far back — they'd be tiny, overlapping
            // clutter that doesn't add anything visually.
            if (depth < -0.55f) continue

            val depthFraction = (depth + 1f) / 2f // 0 (back edge) .. 1 (front)
            val scale = 0.45f + 0.55f * depthFraction
            val alpha = 0.3f + 0.7f * depthFraction

            // Both X and Y vary with angle so icons trace an actual ellipse
            // on screen — front icons low and large, back icons high and
            // small — rather than a single flat horizontal line, which is
            // what made the sphere read as a plain strip instead of a ring.
            val xPx = centerXPx + ringRadiusPx * sin(angleRad).toFloat()
            val yPx = centerYPx - verticalRadiusPx * depth

            AppIcon(
                app = app,
                modifier = Modifier.graphicsLayer {
                    translationX = xPx - size.width / 2f
                    translationY = yPx - size.height / 2f
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
                onClick = { onLaunchApp(app.packageName) }
            )
        }
    }
}

@Composable
private fun AppIcon(
    app: AppInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() },
            contentDescription = app.label,
            modifier = Modifier.size(56.dp)
        )
    }
}
