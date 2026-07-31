package dev.jaimin.auraorbit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.jaimin.auraorbit.ui.AppDrawerScreen
import dev.jaimin.auraorbit.ui.SphereScreen
import dev.jaimin.auraorbit.ui.theme.AuraOrbitTheme

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * MainActivity — the entire launcher
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Deliberately small: this is a from-scratch rewrite (no code carried over
 * from the previous libGDX/OpenGL version) after repeated build failures in
 * a much larger, harder-to-verify codebase. Two screens, pure Compose:
 *
 *   - SphereScreen: apps arranged in a draggable ring with a 3D-illusion
 *     depth effect (see SphereScreen.kt for exactly how — it's plain
 *     graphicsLayer scale/alpha math, not real 3D/OpenGL).
 *   - AppDrawerScreen: full searchable grid of every installed app.
 *
 * Because this extends [ComponentActivity] (not libGDX's AndroidApplication),
 * Compose works normally via [setContent] — no manual ViewTreeLifecycleOwner/
 * ViewModelStoreOwner/SavedStateRegistryOwner wiring is needed, unlike the
 * previous version.
 *
 * Home-task handling: declares MAIN + HOME + DEFAULT in the manifest so it
 * can be picked as the default Home app. A Home task must never finish() —
 * back closes the drawer if it's open, otherwise moves the task to the back
 * of the stack instead of finishing.
 */
class MainActivity : ComponentActivity() {

    private var drawerOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AuraOrbitTheme {
                // Home task: never finish(). Closing the drawer takes priority;
                // with nothing open, drop to the back of the stack instead of
                // finishing, matching standard launcher back behavior.
                BackHandler(enabled = true) {
                    if (drawerOpen) {
                        drawerOpen = false
                    } else {
                        moveTaskToBack(false)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    SphereScreen(
                        onOpenDrawer = { drawerOpen = true },
                        onLaunchApp = { packageName -> launchApp(packageName) }
                    )

                    AnimatedVisibility(
                        visible = drawerOpen,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        AppDrawerScreen(
                            onClose = { drawerOpen = false },
                            onLaunchApp = { packageName ->
                                drawerOpen = false
                                launchApp(packageName)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }
}
