package dev.jaimin.auraorbit;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * SphereModeActivity — Fullscreen Sphere Mode Entry Point
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * A fullscreen {@link AndroidApplication} that renders the same 3D sphere as the
 * live wallpaper, but with the key advantage that the activity owns ALL input.
 * There is no launcher fighting for one-finger swipes; every gesture goes directly
 * to the sphere.
 *
 * ─── Key differences from wallpaper mode ────────────────────────────────────
 *
 * - activityMode=true in SphereEngine: page-visibility is always 1, tap-to-launch
 *   is always direct (no command gating, no zoom/drawer guards, no edge exclusion).
 * - A floating gear button (top-right) opens LiveWallpaperSettings.
 * - Launched apps: the sphere fires startActivity, the launched app comes to the
 *   foreground. This activity remains in the back stack behind it; the user presses
 *   Back to return to Sphere Mode or Home to leave both. This is the simplest UX
 *   and requires no callback coordination.
 *
 * ─── Home screen mode ──────────────────────────────────────────────────────
 *
 * This activity now also declares MAIN + HOME + DEFAULT, so the user can pick
 * AuraOrbit in Android's "Select Home app" chooser and the sphere becomes the
 * actual home screen. When entered that way (see {@link #isHomeTask()}):
 *   - Back / tapping outside the sphere never finish()es — a Home task must
 *     always remain available. Back instead closes the app drawer if it's
 *     open, or moves the task to the back of the stack.
 *   - Swiping up on the sphere opens {@link dev.jaimin.auraorbit.ui.AppDrawerView},
 *     a full grid of every installed app (search + long-press for app info /
 *     uninstall / add-remove from the Sphere), the way a normal launcher's
 *     app drawer works. Swipe down, tap the handle, or Back closes it again.
 * When opened normally (app icon, widget tap — no HOME category), the
 * original tap-outside/back-to-dismiss behavior is unchanged.
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
public class SphereModeActivity extends AndroidApplication {

    private static final String TAG = "AuraOrbit.SphereMode";
    
    private SphereEngine sphereEngine;

    /** Root container the sphere + app drawer are added to. */
    private android.widget.FrameLayout rootContainer;

    /** The full-app-list drawer, lazily created on first swipe-up. */
    private dev.jaimin.auraorbit.ui.AppDrawerView appDrawer;

    /** GestureDetector that turns an upward fling on the sphere into "open drawer". */
    private android.view.GestureDetector swipeUpDetector;

    /**
     * Sticky flag: true once this task has EVER been entered as the actual
     * Android Home screen. Because SphereModeActivity is launchMode
     * "singleTask", a widget tap's plain (non-HOME) intent can land in the
     * very same task instance via onNewIntent() and would otherwise look
     * like "not Home" if we re-checked getIntent() every time. Once a task
     * is the system's Home task it must keep behaving like one — back/
     * outside-tap must never finish() it — so this only ever flips false→true,
     * never back.
     */
    private boolean isHomeTaskSticky = false;

    private void updateHomeTaskFlag(@Nullable Intent intent) {
        if (intent != null
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME)) {
            isHomeTaskSticky = true;
        }
    }

    private boolean isHomeTask() {
        return isHomeTaskSticky;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateHomeTaskFlag(getIntent());

        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        // No window-level FLAG_BLUR_BEHIND here.
        // We will apply blur to a specific View so it can dynamically resize.

        // ─── Fullscreen / edge-to-edge ──────────────────────────────────
        // Tell the decor not to fit system windows so the GL surface reaches
        // every pixel including display cutouts.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Keep screen on while Sphere Mode is open.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // ─── libGDX initialization ────────────────────────────────────────
        // Mirror MyWallpaperService's config: no sensors, depth 16, rgba8888,
        // no MSAA — identical rendering pipeline, just inside an activity.
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useGyroscope = false;
        config.depth = 16;
        config.stencil = 0;
        config.numSamples = 0;
        config.r = 8;
        config.g = 8;
        config.b = 8;
        config.a = 8;

        // Read group_name / widget_name extra if opened from a pinned widget
        String groupName = getIntent().getStringExtra("group_name");
        if (groupName == null) {
            groupName = getIntent().getStringExtra("widget_name");
        }

        // Initialize libGDX with activityMode=true so the engine bypasses all
        // wallpaper-specific guards (page isolation, edge exclusion, zoom revert,
        // command gating).
        sphereEngine = new SphereEngine(this, true, groupName);
        sphereEngine.applyPositionAndScale = true;
        View glView = initializeForView(sphereEngine, config);
        glView.setClickable(true); // Ensure glView consumes clicks
        if (graphics.getView() instanceof android.view.SurfaceView) {
            android.view.SurfaceView surfaceView = (android.view.SurfaceView) graphics.getView();
            surfaceView.getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
            surfaceView.setZOrderOnTop(true);
        }
        String scalePref = groupName != null ? "pref_sphere_scale_" + groupName : "pref_sphere_scale";
        String radiusPref = groupName != null ? "pref_blur_radius_" + groupName : "pref_blur_radius";
        String strengthPref = groupName != null ? "pref_blur_strength_" + groupName : "pref_blur_strength";
        String posPref = groupName != null ? "pref_sphere_position_" + groupName : "pref_sphere_position";
        String xPref = groupName != null ? "pref_sphere_x_" + groupName : "pref_sphere_x";
        String yPref = groupName != null ? "pref_sphere_y_" + groupName : "pref_sphere_y";

        float scale = prefs.getFloat(scalePref, 1.0f);
        String pos = prefs.getString(posPref, "center");
        int blurRadiusPref = prefs.getInt(radiusPref, 10);
        int blurStrengthPref = prefs.getInt(strengthPref, 50);
        // Migrate old pref_blur_amount if the new ones don't exist
        if (!prefs.contains(radiusPref) && groupName == null && prefs.contains("pref_blur_amount")) {
            int oldAmount = prefs.getInt("pref_blur_amount", 0);
            blurRadiusPref = oldAmount;
            blurStrengthPref = oldAmount > 0 ? 50 : 0;
        }

        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int sphereSize = (int) (screenWidth * scale);

        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        rootContainer = container;

        // ─── Swipe-up-to-open-drawer gesture ────────────────────────────
        // A fast upward fling anywhere on the sphere surface opens the full
        // app drawer. Small/slow drags are left alone so they keep rotating
        // the sphere as before.
        swipeUpDetector = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 80;
            private static final int SWIPE_MIN_VELOCITY = 200;

            @Override
            public boolean onFling(@Nullable android.view.MotionEvent e1, @NonNull android.view.MotionEvent e2,
                                    float velocityX, float velocityY) {
                if (e1 == null || isDrawerOpen()) return false;
                float deltaY = e1.getY() - e2.getY();
                if (deltaY > SWIPE_MIN_DISTANCE
                        && Math.abs(velocityY) > SWIPE_MIN_VELOCITY
                        && Math.abs(velocityY) > Math.abs(velocityX)) {
                    openAppDrawer();
                    return true;
                }
                return false;
            }
        });
        
        // ─── Window Bounds ────────────────────────────────────────────────
        int sphereCenterX, sphereCenterY;
        if ("custom".equals(pos)) {
            float defaultX = (screenWidth - (screenWidth * scale)) / 2f;
            float defaultY = (screenHeight - (screenWidth * scale)) / 2f;
            float sphereX = prefs.getFloat(xPref, defaultX);
            float sphereY = prefs.getFloat(yPref, defaultY);
            sphereCenterX = (int) (sphereX + (screenWidth * scale) / 2f);
            sphereCenterY = (int) (sphereY + (screenWidth * scale) / 2f);
        } else if ("top".equals(pos)) {
            sphereCenterX = screenWidth / 2;
            sphereCenterY = (int) (screenHeight * 0.25f);
        } else if ("bottom".equals(pos)) {
            sphereCenterX = screenWidth / 2;
            sphereCenterY = (int) (screenHeight * 0.75f);
        } else { // "center"
            sphereCenterX = screenWidth / 2;
            sphereCenterY = screenHeight / 2;
        }
        
        // Position glView to cover the full screen so that the engine renders matching the preview and wallpaper
        android.widget.FrameLayout.LayoutParams glParams = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        container.addView(glView, glParams);
        
        // Tapping the blurred background outside the sphere: in a normal
        // (non-Home) task this used to close Sphere Mode. As the actual Home
        // screen there is nothing to "close" to, so it either dismisses an
        // open drawer or is simply ignored.
        container.setOnClickListener(v -> {
            if (isDrawerOpen()) {
                closeAppDrawer();
            } else if (!isHomeTask()) {
                if (sphereEngine != null) {
                    sphereEngine.fanOutAndFinish();
                } else {
                    finish();
                }
            }
        });

        container.setOnTouchListener((v, event) -> {
            if (swipeUpDetector != null && !isDrawerOpen()) {
                swipeUpDetector.onTouchEvent(event);
            }
            return false; // never consume — let clicks/sphere drag still work
        });
        
        setContentView(container);


        
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.x = 0;
        params.y = 0;
        
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && blurStrengthPref > 0) {
            int radius = Math.min(blurStrengthPref * 2, 150);
            if (radius == 0) radius = 1;
            getWindow().setBackgroundBlurRadius(radius);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getWindow().setBackgroundBlurRadius(0);
        }
        
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        
        getWindow().setAttributes(params);

        // ─── Hide system bars (immersive fullscreen) ─────────────────────
        // Must be called AFTER super.onCreate / initialize so the window is
        // fully decorated and the insets controller is available.
        hideSystemBars();


    }

    /**
     * Hides status and navigation bars for a true fullscreen experience.
     *
     * Uses WindowInsetsControllerCompat (targetSdk 35 / AndroidX pattern).
     * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user temporarily reveal
     * the bars by swiping from the edge — they auto-hide after ~2 s.
     */
    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), decorView);

        // Swipe-to-reveal: transient bars appear on edge swipe then auto-hide.
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }


    /**
     * Re-apply immersive mode when the activity window focus returns
     * (e.g. after returning from Settings or a launched app).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateHomeTaskFlag(intent);

        // Pressing the Home button while an app is open re-delivers the HOME
        // intent here. Always land back on the bare sphere, not mid-drawer.
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            closeAppDrawer();
        }
        
        // If the activity was already running and another widget was clicked,
        // update the engine with the new group name!
        if (sphereEngine != null) {
            String groupName = intent.getStringExtra("group_name");
            if (groupName == null) {
                groupName = intent.getStringExtra("widget_name");
            }
            sphereEngine.setPinnedGroupName(groupName);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        Intent hideIntent = new Intent(this, SphereWidgetProvider.class);
        hideIntent.setAction("dev.jaimin.auraorbit.WIDGET_HIDE");
        sendBroadcast(hideIntent);

        // Always hide system bars on resume to ensure the activity stays immersive
        // if the user pulled down the notification shade.
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Intent showIntent = new Intent(this, SphereWidgetProvider.class);
        showIntent.setAction("dev.jaimin.auraorbit.WIDGET_SHOW");
        sendBroadcast(showIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (appDrawer != null) {
            appDrawer.shutdown();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_OUTSIDE) {
            if (isDrawerOpen()) {
                closeAppDrawer();
            } else if (!isHomeTask()) {
                if (sphereEngine != null) {
                    sphereEngine.fanOutAndFinish();
                } else {
                    finish();
                }
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (isDrawerOpen()) {
            closeAppDrawer();
            return;
        }
        if (isHomeTask()) {
            // A Home activity must never finish(); Back on the home screen
            // itself is a no-op (matches stock launcher behavior), but if
            // we're on top of another task, drop to the back of the stack.
            moveTaskToBack(false);
            return;
        }
        if (sphereEngine != null) {
            sphereEngine.fanOutAndFinish();
        } else {
            super.onBackPressed();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  App Drawer (swipe up from the sphere to reveal all installed apps)
    // ─────────────────────────────────────────────────────────────────────

    private boolean isDrawerOpen() {
        return appDrawer != null && appDrawer.getParent() != null;
    }

    private void openAppDrawer() {
        if (rootContainer == null || isDrawerOpen()) return;
        if (appDrawer == null) {
            appDrawer = new dev.jaimin.auraorbit.ui.AppDrawerView(this);
            appDrawer.setOnCloseRequested(this::closeAppDrawer);
        }
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        rootContainer.addView(appDrawer, params);
        appDrawer.animateIn();
    }

    private void closeAppDrawer() {
        if (!isDrawerOpen()) return;
        appDrawer.animateOut(() -> {
            if (rootContainer != null && appDrawer != null) {
                rootContainer.removeView(appDrawer);
            }
        });
    }

    private static class BlurBackgroundDrawable extends android.graphics.drawable.GradientDrawable {
        private final int customLeft;
        private final int customTop;
        private final int customRight;
        private final int customBottom;

        public BlurBackgroundDrawable(int left, int top, int right, int bottom) {
            super();
            setShape(OVAL);
            setColor(android.graphics.Color.TRANSPARENT);
            this.customLeft = left;
            this.customTop = top;
            this.customRight = right;
            this.customBottom = bottom;
            super.setBounds(left, top, right, bottom);
        }

        @Override
        public void setBounds(int left, int top, int right, int bottom) {
            super.setBounds(customLeft, customTop, customRight, customBottom);
        }

        @Override
        public void setBounds(@NonNull android.graphics.Rect bounds) {
            super.setBounds(customLeft, customTop, customRight, customBottom);
        }
    }
}
