package dev.jaimin.auraorbit

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.preference.PreferenceManager
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import dev.jaimin.auraorbit.ui.AppDrawerScreen
import dev.jaimin.auraorbit.ui.DockBar
import dev.jaimin.auraorbit.ui.LauncherSettingsScreen
import dev.jaimin.auraorbit.ui.theme.AuraOrbitTheme
import kotlin.math.abs
import kotlin.math.min

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * SphereModeActivity — Fullscreen Sphere Mode Entry Point
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * A fullscreen [AndroidApplication] that renders the same 3D sphere as the live
 * wallpaper, but with the key advantage that the activity owns ALL input. There
 * is no launcher fighting for one-finger swipes; every gesture goes directly to
 * the sphere.
 *
 * ─── Key differences from wallpaper mode ────────────────────────────────────
 *
 * - activityMode=true in SphereEngine: page-visibility is always 1, tap-to-launch
 *   is always direct (no command gating, no zoom/drawer guards, no edge exclusion).
 * - Settings are reached via the bottom Dock's long-press (see Dock section
 *   below), not a floating in-scene button.
 * - Launched apps: the sphere fires startActivity, the launched app comes to the
 *   foreground. This activity remains in the back stack behind it; the user presses
 *   Back to return to Sphere Mode or Home to leave both. This is the simplest UX
 *   and requires no callback coordination.
 *
 * ─── Home screen mode ──────────────────────────────────────────────────────
 *
 * This activity also declares MAIN + HOME + DEFAULT, so the user can pick
 * AuraOrbit in Android's "Select Home app" chooser and the sphere becomes the
 * actual home screen. When entered that way (see [isHomeTask]):
 *   - Back / tapping outside the sphere never finish()es — a Home task must
 *     always remain available. Back instead closes whichever full-screen
 *     overlay (drawer or settings) is open, or moves the task to the back
 *     of the stack.
 *   - Swiping up on the sphere opens [AppDrawerScreen] (Compose), a full grid
 *     of every installed app (search + long-press for app info / uninstall /
 *     add-remove from the Sphere), the way a normal launcher's app drawer
 *     works. Swipe down, tap the handle, or Back closes it again.
 *   - A persistent [DockBar] sits at the bottom of the screen (3–7
 *     user-chosen apps, tap to launch). Long-pressing the dock opens
 *     [LauncherSettingsScreen], where the dock's apps are configured and the
 *     existing [LiveWallpaperSettings] (sphere appearance) is reachable.
 *     The dock hides itself while the drawer or settings overlay is open.
 * When opened normally (app icon, widget tap — no HOME category), the
 * original tap-outside/back-to-dismiss behavior is unchanged, and the dock
 * is not shown (it's a Home-screen-only affordance).
 *
 * ─── Compose interop ────────────────────────────────────────────────────────
 *
 * [AndroidApplication] (libGDX's Android backend base class) is a plain
 * [android.app.Activity], NOT a [androidx.activity.ComponentActivity], so it
 * doesn't automatically provide the three "ViewTree owner" contracts a
 * [ComposeView] needs (Lifecycle, ViewModelStore, SavedStateRegistry). We
 * implement those three interfaces directly here and wire them by hand — see
 * [lifecycleRegistry] / [viewModelStore] / [savedStateRegistryController] and
 * the propagation calls in each lifecycle callback below.
 *
 * ─── Immersive fullscreen ─────────────────────────────────────────────────────
 *
 * libGDX 1.13.0's AndroidApplicationConfiguration does not expose useImmersiveMode
 * as a public field (it was removed in earlier 1.1x releases). We use
 * WindowInsetsControllerCompat directly (targetSdk 35 pattern):
 *   - BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE: swipe in from edge to temporarily
 *     reveal status/nav bars (they auto-hide after a moment).
 *   - hide(statusBars | navigationBars) immediately after the window is decorated.
 *
 * Edge-to-edge is enabled via WindowCompat.setDecorFitsSystemWindows(window, false)
 * so the libGDX surface fills the entire display including cutout areas.
 */
class SphereModeActivity :
    AndroidApplication(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // ─── Manual ViewTree owner plumbing (see class doc above) ──────────────

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val viewModelStore = ViewModelStore()

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var sphereEngine: SphereEngine? = null

    /** Root container the sphere + app drawer are added to. */
    private var rootContainer: FrameLayout? = null

    /** The full-app-list drawer / settings ComposeView, lazily created on first use. */
    private var appDrawerComposeView: ComposeView? = null

    /** The persistent bottom Dock's ComposeView, created once when this becomes the Home task. */
    private var dockComposeView: ComposeView? = null

    /** GestureDetector that turns an upward fling on the sphere into "open drawer". */
    private var swipeUpDetector: GestureDetector? = null

    /**
     * Sticky flag: true once this task has EVER been entered as the actual
     * Android Home screen. Because SphereModeActivity is launchMode
     * "singleInstance", a widget tap's plain (non-HOME) intent can land in
     * this same activity via onNewIntent() and would otherwise look like
     * "not Home" if we re-checked getIntent() every time. Once a task is the
     * system's Home task it must keep behaving like one — back/outside-tap
     * must never finish() it — so this only ever flips false→true, never back.
     */
    private var isHomeTaskSticky = false

    private fun updateHomeTaskFlag(intent: Intent?) {
        if (intent != null &&
            Intent.ACTION_MAIN == intent.action &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            isHomeTaskSticky = true
        }
    }

    private fun isHomeTask(): Boolean = isHomeTaskSticky

    override fun onCreate(savedInstanceState: Bundle?) {
        savedStateRegistryController.performRestore(savedInstanceState)
        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        updateHomeTaskFlag(intent)

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // No window-level FLAG_BLUR_BEHIND here.
        // We will apply blur to a specific View so it can dynamically resize.

        // ─── Fullscreen / edge-to-edge ──────────────────────────────────
        // Tell the decor not to fit system windows so the GL surface reaches
        // every pixel including display cutouts.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Keep screen on while Sphere Mode is open.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // ─── libGDX initialization ────────────────────────────────────────
        // Mirror MyWallpaperService's config: no sensors, depth 16, rgba8888,
        // no MSAA — identical rendering pipeline, just inside an activity.
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            depth = 16
            stencil = 0
            numSamples = 0
            r = 8
            g = 8
            b = 8
            a = 8
        }

        // Initialize libGDX with activityMode=true so the engine bypasses all
        // wallpaper-specific guards (page isolation, edge exclusion, zoom revert,
        // command gating).
        val engine = SphereEngine(this, true)
        engine.applyPositionAndScale = true
        sphereEngine = engine

        val glView = initializeForView(engine, config)
        glView.isClickable = true // Ensure glView consumes clicks
        val gfxView = graphics.view
        if (gfxView is SurfaceView) {
            gfxView.holder.setFormat(PixelFormat.TRANSLUCENT)
            gfxView.setZOrderOnTop(true)
        }

        var blurRadiusPref = prefs.getInt("pref_blur_radius", 10)
        var blurStrengthPref = prefs.getInt("pref_blur_strength", 50)
        // Migrate old pref_blur_amount if the new ones don't exist
        if (!prefs.contains("pref_blur_radius") && prefs.contains("pref_blur_amount")) {
            val oldAmount = prefs.getInt("pref_blur_amount", 0)
            blurRadiusPref = oldAmount
            blurStrengthPref = if (oldAmount > 0) 50 else 0
        }

        val container = FrameLayout(this)
        rootContainer = container

        // Wire the manual ViewTree owners onto the root container so any
        // ComposeView added under it (i.e. the app drawer) can find them.
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeViewModelStoreOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        // ─── Swipe-up-to-open-drawer gesture ────────────────────────────
        // A fast upward fling anywhere on the sphere surface opens the full
        // app drawer. Small/slow drags are left alone so they keep rotating
        // the sphere as before.
        swipeUpDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val swipeMinDistance = 80
            private val swipeMinVelocity = 200

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || isOverlayOpen()) return false
                val deltaY = e1.y - e2.y
                if (deltaY > swipeMinDistance &&
                    abs(velocityY) > swipeMinVelocity &&
                    abs(velocityY) > abs(velocityX)
                ) {
                    openAppDrawer()
                    return true
                }
                return false
            }
        })

        // ─── Window Bounds ────────────────────────────────────────────────
        // Position glView to cover the full screen so that the engine renders matching the preview and wallpaper
        val glParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(glView, glParams)

        // Tapping the blurred background outside the sphere: in a normal
        // (non-Home) task this used to close Sphere Mode. As the actual Home
        // screen there is nothing to "close" to, so it either dismisses an
        // open drawer or is simply ignored.
        container.setOnClickListener {
            if (isOverlayOpen()) {
                closeOverlay()
            } else if (!isHomeTask()) {
                sphereEngine?.fanOutAndFinish() ?: finish()
            }
        }

        container.setOnTouchListener { _, event ->
            if (!isOverlayOpen()) {
                swipeUpDetector?.onTouchEvent(event)
            }
            false // never consume — let clicks/sphere drag still work
        }

        setContentView(container)

        val params = window.attributes
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.height = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurStrengthPref > 0) {
            val radius = min(blurStrengthPref * 2, 150).let { if (it == 0) 1 else it }
            window.setBackgroundBlurRadius(radius)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setBackgroundBlurRadius(0)
        }

        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.attributes = params

        // Only shown when this instance is actually the system Home task
        // (see isHomeTask()) — a normal app-icon/widget launch of Sphere
        // Mode has no dock, matching the class doc's Home-screen-mode note.
        setupDockIfHome()

        // ─── Hide system bars (immersive fullscreen) ─────────────────────
        // Must be called AFTER super.onCreate / initialize so the window is
        // fully decorated and the insets controller is available.
        hideSystemBars()
    }

    /**
     * Hides status and navigation bars for a true fullscreen experience.
     *
     * Uses WindowInsetsControllerCompat (targetSdk 35 / AndroidX pattern).
     * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user temporarily reveal
     * the bars by swiping from the edge — they auto-hide after ~2 s.
     */
    private fun hideSystemBars() {
        val decorView = window.decorView
        val controller: WindowInsetsControllerCompat =
            WindowCompat.getInsetsController(window, decorView)

        // Swipe-to-reveal: transient bars appear on edge swipe then auto-hide.
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Re-apply immersive mode when the activity window focus returns
     * (e.g. after returning from Settings or a launched app).
     */
    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        intent = newIntent
        updateHomeTaskFlag(newIntent)

        // Pressing the Home button while an app is open re-delivers the HOME
        // intent here. Always land back on the bare sphere, not mid-drawer.
        if (Intent.ACTION_MAIN == newIntent.action && newIntent.hasCategory(Intent.CATEGORY_HOME)) {
            closeOverlay()
            setupDockIfHome()
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // Always hide system bars on resume to ensure the activity stays immersive
        // if the user pulled down the notification shade.
        hideSystemBars()
    }

    override fun onPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        savedStateRegistryController.performSave(outState)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            if (isOverlayOpen()) {
                closeOverlay()
            } else if (!isHomeTask()) {
                sphereEngine?.fanOutAndFinish() ?: finish()
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("super.onBackPressed()"))
    override fun onBackPressed() {
        if (isOverlayOpen()) {
            closeOverlay()
            return
        }
        if (isHomeTask()) {
            // A Home activity must never finish(); Back on the home screen
            // itself is a no-op (matches stock launcher behavior), but if
            // we're on top of another task, drop to the back of the stack.
            moveTaskToBack(false)
            return
        }
        val engine = sphereEngine
        if (engine != null) {
            engine.fanOutAndFinish()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Overlays: App Drawer (swipe up) and Settings (long-press the Dock)
    // ─────────────────────────────────────────────────────────────────────

    private enum class OverlayMode { NONE, DRAWER, SETTINGS }

    /**
     * Single source of truth for which full-screen overlay is showing, if
     * any. A Compose [MutableState] so [AppDrawerScreen]/[LauncherSettingsScreen]
     * recompose when it changes, even though this field lives on a plain
     * (non-Composable) Activity.
     *
     * [overlayComposeView] is attached to [rootContainer] exactly once, on
     * first use, and left attached for the rest of the Activity's life —
     * AnimatedVisibility(visible = false) removes each screen's content from
     * the layout/hit-testing tree once its exit animation finishes, so
     * touches correctly fall through to the sphere/dock below without
     * needing manual addView/removeView choreography (and the race
     * conditions that would come with timing that against an animation's
     * duration).
     */
    private var overlayMode by mutableStateOf(OverlayMode.NONE)

    private fun isOverlayOpen(): Boolean = overlayMode != OverlayMode.NONE

    private fun ensureOverlayComposeView(): ComposeView {
        appDrawerComposeView?.let { return it }
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SphereModeActivity)
            setViewTreeViewModelStoreOwner(this@SphereModeActivity)
            setViewTreeSavedStateRegistryOwner(this@SphereModeActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@SphereModeActivity))
            setContent {
                AuraOrbitTheme {
                    AppDrawerScreen(
                        visible = overlayMode == OverlayMode.DRAWER,
                        onCloseRequested = { closeOverlay() }
                    )
                    LauncherSettingsScreen(
                        visible = overlayMode == OverlayMode.SETTINGS,
                        onCloseRequested = { closeOverlay() }
                    )
                }
            }
        }
        appDrawerComposeView = composeView
        rootContainer?.addView(
            composeView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        return composeView
    }

    private fun openAppDrawer() {
        ensureOverlayComposeView()
        overlayMode = OverlayMode.DRAWER
    }

    private fun openSettings() {
        ensureOverlayComposeView()
        overlayMode = OverlayMode.SETTINGS
    }

    private fun closeOverlay() {
        overlayMode = OverlayMode.NONE
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Dock: persistent 3–7-app row at the bottom, Home-screen mode only
    // ─────────────────────────────────────────────────────────────────────

    private fun setupDockIfHome() {
        if (!isHomeTask() || dockComposeView != null) return
        val container = rootContainer ?: return
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SphereModeActivity)
            setViewTreeViewModelStoreOwner(this@SphereModeActivity)
            setViewTreeSavedStateRegistryOwner(this@SphereModeActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(this@SphereModeActivity))
            setContent {
                AuraOrbitTheme {
                    DockBar(
                        visible = overlayMode == OverlayMode.NONE,
                        onOpenSettings = { openSettings() }
                    )
                }
            }
        }
        dockComposeView = composeView
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        container.addView(composeView, params)
    }
}
