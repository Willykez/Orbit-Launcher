package dev.jaimin.auraorbit.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.jaimin.auraorbit.AppFetcher;
import dev.jaimin.auraorbit.IconPackManager;
import dev.jaimin.auraorbit.R;
import dev.jaimin.auraorbit.SphereWidgetProvider;

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * AppDrawerView.java — Full "all installed apps" drawer overlay
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * A self-contained FrameLayout that SphereModeActivity adds on top of the
 * sphere's glView when the user swipes up. Unlike the curated sphere (which
 * only shows hand-picked apps), this shows EVERY launchable app on the
 * device — the way a normal Android launcher's app drawer works.
 *
 * Long-pressing an icon offers App info / Uninstall / Add-or-remove from the
 * Sphere, so the drawer and the 3D sphere stay in sync with each other.
 */
public class AppDrawerView extends FrameLayout {

    private RecyclerView grid;
    private TextView emptyState;
    private AppAdapter adapter;
    private ExecutorService executor;
    private GestureDetector swipeDownDetector;
    private Runnable onCloseRequested;

    public AppDrawerView(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        LayoutInflater.from(getContext()).inflate(R.layout.view_app_drawer, this, true);

        grid = findViewById(R.id.drawer_app_grid);
        emptyState = findViewById(R.id.drawer_empty_state);
        TextInputEditText searchInput = findViewById(R.id.drawer_search_input);
        View handle = findViewById(R.id.drawer_handle);

        grid.setLayoutManager(new GridLayoutManager(getContext(), 4));
        adapter = new AppAdapter();
        grid.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s == null ? "" : s.toString());
            }
        });

        handle.setOnClickListener(v -> requestClose());

        // Swipe down anywhere on the drawer (outside the grid's own scroll)
        // closes it, mirroring the swipe-up gesture that opened it.
        swipeDownDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                    float velocityX, float velocityY) {
                if (e1 == null) return false;
                float deltaY = e2.getY() - e1.getY();
                if (deltaY > 80 && velocityY > 200 && Math.abs(velocityY) > Math.abs(velocityX)) {
                    requestClose();
                    return true;
                }
                return false;
            }
        });
        setOnTouchListener((v, event) -> {
            swipeDownDetector.onTouchEvent(event);
            return false;
        });

        executor = Executors.newSingleThreadExecutor();
        loadAppsAsync();
    }

    public void setOnCloseRequested(Runnable callback) {
        this.onCloseRequested = callback;
    }

    private void requestClose() {
        if (onCloseRequested != null) onCloseRequested.run();
    }

    /** Slide up from below the screen with a fade-in. */
    public void animateIn() {
        setTranslationY(getResources().getDisplayMetrics().heightPixels);
        setAlpha(0.4f);
        animate().translationY(0f).alpha(1f).setDuration(220).start();
    }

    /** Slide back down and fade out, then invoke onEnd (typically removeView). */
    public void animateOut(Runnable onEnd) {
        animate().translationY(getResources().getDisplayMetrics().heightPixels).alpha(0f)
                .setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        if (onEnd != null) onEnd.run();
                    }
                }).start();
    }

    /** Call when the drawer is being torn down for good (activity finishing). */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Data loading
    // ─────────────────────────────────────────────────────────────────────

    private void loadAppsAsync() {
        Context appCtx = getContext().getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.submit(() -> {
            PackageManager pm = appCtx.getPackageManager();
            List<ResolveInfo> resolvedApps = AppFetcher.getAllLaunchableApps(appCtx);
            IconPackManager iconPackManager = IconPackManager.getInstance(appCtx);

            List<AppEntry> entries = new ArrayList<>(resolvedApps.size());
            for (ResolveInfo ri : resolvedApps) {
                if (ri.activityInfo == null || ri.activityInfo.packageName == null) continue;
                String pkg = ri.activityInfo.packageName;
                String className = ri.activityInfo.name;
                String componentName = "ComponentInfo{" + pkg + "/" + className + "}";
                String label = ri.loadLabel(pm).toString();

                Drawable icon = iconPackManager.getIcon(componentName);
                if (icon == null) icon = ri.loadIcon(pm);

                entries.add(new AppEntry(pkg, className, label, icon));
            }

            mainHandler.post(() -> {
                adapter.setItems(entries);
                emptyState.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Launch / long-press actions
    // ─────────────────────────────────────────────────────────────────────

    private void launchApp(AppEntry entry) {
        PackageManager pm = getContext().getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(entry.packageName);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(launchIntent);
            requestClose();
        } else {
            Toast.makeText(getContext(), entry.label + " can't be opened", Toast.LENGTH_SHORT).show();
        }
    }

    private void showActionMenu(View anchor, AppEntry entry) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add(0, 1, 0, R.string.drawer_action_app_info);
        menu.getMenu().add(0, 2, 1, R.string.drawer_action_uninstall);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        boolean inSphere = isPackageInSphere(prefs, entry.packageName);
        menu.getMenu().add(0, 3, 2, inSphere
                ? R.string.drawer_action_remove_from_sphere
                : R.string.drawer_action_add_to_sphere);

        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                openAppInfo(entry.packageName);
                return true;
            } else if (id == 2) {
                requestUninstall(entry);
                return true;
            } else if (id == 3) {
                toggleSphereMembership(prefs, entry.packageName, !inSphere);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void openAppInfo(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    private void requestUninstall(AppEntry entry) {
        try {
            PackageManager pm = getContext().getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(entry.packageName, 0);
            boolean isSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            if (isSystemApp) {
                Toast.makeText(getContext(), R.string.drawer_uninstall_system_app_blocked, Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // fall through and let the system uninstall dialog decide
        }
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + entry.packageName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
    }

    private boolean isPackageInSphere(SharedPreferences prefs, String packageName) {
        Set<String> selected = prefs.getStringSet(AppFetcher.PREF_SELECTED_APPS, null);
        // Null/empty selection historically means "all apps are on the sphere".
        return selected == null || selected.isEmpty() || selected.contains(packageName);
    }

    private void toggleSphereMembership(SharedPreferences prefs, String packageName, boolean addToSphere) {
        Set<String> current = prefs.getStringSet(AppFetcher.PREF_SELECTED_APPS, null);
        Set<String> updated = new HashSet<>();
        if (current == null || current.isEmpty()) {
            // Expand the implicit "all apps" set into an explicit one before editing.
            for (ResolveInfo ri : AppFetcher.getAllLaunchableApps(getContext())) {
                if (ri.activityInfo != null && ri.activityInfo.packageName != null) {
                    updated.add(ri.activityInfo.packageName);
                }
            }
        } else {
            updated.addAll(current);
        }
        if (addToSphere) {
            updated.add(packageName);
        } else {
            updated.remove(packageName);
        }
        prefs.edit().putStringSet(AppFetcher.PREF_SELECTED_APPS, updated).apply();
        SphereWidgetProvider.updateAllWidgets(getContext());
        Toast.makeText(getContext(),
                addToSphere ? R.string.drawer_action_add_to_sphere : R.string.drawer_action_remove_from_sphere,
                Toast.LENGTH_SHORT).show();
    }

    // ═════════════════════════════════════════════════════════════════════
    //  Model + Adapter
    // ═════════════════════════════════════════════════════════════════════

    private static final class AppEntry {
        final String packageName;
        final String className;
        final String label;
        final Drawable icon;

        AppEntry(String packageName, String className, String label, Drawable icon) {
            this.packageName = packageName;
            this.className = className;
            this.label = label;
            this.icon = icon;
        }
    }

    private final class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

        private final List<AppEntry> allItems = new ArrayList<>();
        private final List<AppEntry> displayItems = new ArrayList<>();
        private String currentQuery = "";

        void setItems(List<AppEntry> items) {
            allItems.clear();
            allItems.addAll(items);
            filter(currentQuery);
        }

        void filter(@Nullable String query) {
            currentQuery = query == null ? "" : query.trim().toLowerCase();
            displayItems.clear();
            if (currentQuery.isEmpty()) {
                displayItems.addAll(allItems);
            } else {
                for (AppEntry e : allItems) {
                    if (e.label.toLowerCase().contains(currentQuery)
                            || e.packageName.toLowerCase().contains(currentQuery)) {
                        displayItems.add(e);
                    }
                }
            }
            notifyDataSetChanged();
            emptyState.setVisibility(displayItems.isEmpty() ? View.VISIBLE : View.GONE);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.row_app_drawer_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AppEntry entry = displayItems.get(position);
            holder.icon.setImageDrawable(entry.icon);
            holder.label.setText(entry.label);
            holder.itemView.setOnClickListener(v -> launchApp(entry));
            holder.itemView.setOnLongClickListener(v -> {
                showActionMenu(v, entry);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return displayItems.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView label;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.drawer_app_icon);
                label = itemView.findViewById(R.id.drawer_app_label);
            }
        }
    }
}
