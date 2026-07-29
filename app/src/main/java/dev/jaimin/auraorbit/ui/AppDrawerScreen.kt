@file:OptIn(ExperimentalFoundationApi::class)

package dev.jaimin.auraorbit.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import dev.jaimin.auraorbit.AppFetcher
import dev.jaimin.auraorbit.IconPackManager
import dev.jaimin.auraorbit.R
import dev.jaimin.auraorbit.SphereWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * AppDrawerScreen.kt — Full "all installed apps" drawer (Compose)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Compose replacement for the old View/RecyclerView-based AppDrawerView. Shows
 * EVERY launchable app on the device (unlike the curated sphere, which only
 * shows hand-picked apps) — the way a normal Android launcher's app drawer
 * works. Long-pressing an icon offers App info / Uninstall / Add-or-remove
 * from the Sphere, keeping the drawer and the 3D sphere in sync.
 *
 * Hosted from [dev.jaimin.auraorbit.SphereModeActivity] via a ComposeView added
 * to the sphere's FrameLayout on swipe-up (see that class for the manual
 * ViewTreeLifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner wiring
 * required to host Compose inside a libGDX AndroidApplication activity).
 */

internal data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

@Composable
fun AppDrawerScreen(
    visible: Boolean,
    onCloseRequested: () -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf(listOf<AppEntry>()) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        allApps = loadInstalledApps(context)
    }

    val displayApps = remember(allApps, query) {
        if (query.isBlank()) {
            allApps
        } else {
            val q = query.trim().lowercase()
            allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    // Enter: gentle spring scale-up + fade, similar feel to the sphere's own
    // launch/return zoom (see AppFetcher.launchApp / sphere_launch_enter.xml)
    // so the drawer feels like part of the same design language.
    // Exit: quicker plain fade+scale-down — SphereModeActivity keeps the
    // ComposeView attached for EXIT_ANIM_DURATION_MS after onCloseRequested()
    // so this animation has time to finish before the view is torn down.
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
            .background(Color.Black.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 24) {
                        onCloseRequested()
                    }
                }
            }
            .padding(top = 12.dp)
    ) {
        // Drag handle — visual affordance, also a dedicated tap/swipe-down-to-close target
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCloseRequested
                )
                .padding(12.dp) // generous tap target beyond the visible pill
                .size(width = 36.dp, height = 4.dp)
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            placeholder = { Text(stringResource_search_apps_hint(context)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White
            )
        )

        if (displayApps.isEmpty() && allApps.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource_no_apps_found(context),
                    color = Color.Gray
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(displayApps, key = { it.packageName }) { entry ->
                AppGridItem(
                    entry = entry,
                    onClick = {
                        launchApp(context, entry) { onCloseRequested() }
                    },
                    onLongPressAction = { action ->
                        scope.launch {
                            handleDrawerAction(context, entry, action) { updated ->
                                allApps = updated
                            }
                        }
                    }
                )
            }
        }
    }
    }
}

private enum class DrawerAction { APP_INFO, UNINSTALL, TOGGLE_SPHERE }

@Composable
private fun AppGridItem(
    entry: AppEntry,
    onClick: () -> Unit,
    onLongPressAction: (DrawerAction) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val inSphere = remember(entry.packageName) { isPackageInSphere(context, entry.packageName) }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(vertical = 12.dp)
        ) {
            Image(
                bitmap = remember(entry.icon) { entry.icon.toBitmap().asImageBitmap() },
                contentDescription = entry.label,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = entry.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource_app_info(context)) },
                onClick = {
                    menuExpanded = false
                    onLongPressAction(DrawerAction.APP_INFO)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource_uninstall(context)) },
                onClick = {
                    menuExpanded = false
                    onLongPressAction(DrawerAction.UNINSTALL)
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (inSphere) stringResource_remove_from_sphere(context)
                        else stringResource_add_to_sphere(context)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onLongPressAction(DrawerAction.TOGGLE_SPHERE)
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Data loading
// ─────────────────────────────────────────────────────────────────────────

internal suspend fun loadInstalledApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val pm = appContext.packageManager
    val resolvedApps: List<ResolveInfo> = AppFetcher.getAllLaunchableApps(appContext)
    val iconPackManager = IconPackManager.getInstance(appContext)

    resolvedApps.mapNotNull { ri ->
        val activityInfo = ri.activityInfo ?: return@mapNotNull null
        val pkg = activityInfo.packageName ?: return@mapNotNull null
        val className = activityInfo.name
        val componentName = "ComponentInfo{$pkg/$className}"
        val label = ri.loadLabel(pm).toString()
        val icon = iconPackManager.getIcon(componentName) ?: ri.loadIcon(pm)
        AppEntry(pkg, label, icon)
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  Launch / long-press actions
// ─────────────────────────────────────────────────────────────────────────

private fun launchApp(context: Context, entry: AppEntry, onLaunched: () -> Unit) {
    // Reuses the same transition as tapping an app on the sphere itself
    // (ActivityOptions scale+fade via R.anim.sphere_launch_enter/exit) so
    // launching from the drawer feels consistent with the rest of the launcher.
    val launched = AppFetcher.launchApp(context, entry.packageName)
    if (launched) {
        onLaunched()
    } else {
        Toast.makeText(context, "${entry.label} can't be opened", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun handleDrawerAction(
    context: Context,
    entry: AppEntry,
    action: DrawerAction,
    onSphereMembershipChanged: (List<AppEntry>) -> Unit
) {
    when (action) {
        DrawerAction.APP_INFO -> openAppInfo(context, entry.packageName)
        DrawerAction.UNINSTALL -> requestUninstall(context, entry)
        DrawerAction.TOGGLE_SPHERE -> {
            val nowInSphere = !isPackageInSphere(context, entry.packageName)
            toggleSphereMembership(context, entry.packageName, nowInSphere)
            onSphereMembershipChanged(loadInstalledApps(context))
        }
    }
}

private fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun requestUninstall(context: Context, entry: AppEntry) {
    try {
        val appInfo = context.packageManager.getApplicationInfo(entry.packageName, 0)
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (isSystemApp) {
            Toast.makeText(context, R.string.drawer_uninstall_system_app_blocked, Toast.LENGTH_SHORT).show()
            return
        }
    } catch (ignored: PackageManager.NameNotFoundException) {
        // fall through and let the system uninstall dialog decide
    }
    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.parse("package:${entry.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun isPackageInSphere(context: Context, packageName: String): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val selected = prefs.getStringSet(AppFetcher.PREF_SELECTED_APPS, null)
    // Null/empty selection historically means "all apps are on the sphere".
    return selected == null || selected.isEmpty() || selected.contains(packageName)
}

private fun toggleSphereMembership(context: Context, packageName: String, addToSphere: Boolean) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val current = prefs.getStringSet(AppFetcher.PREF_SELECTED_APPS, null)
    val updated = HashSet<String>()
    if (current.isNullOrEmpty()) {
        // Expand the implicit "all apps" set into an explicit one before editing.
        for (ri in AppFetcher.getAllLaunchableApps(context)) {
            ri.activityInfo?.packageName?.let { updated.add(it) }
        }
    } else {
        updated.addAll(current)
    }
    if (addToSphere) updated.add(packageName) else updated.remove(packageName)
    prefs.edit().putStringSet(AppFetcher.PREF_SELECTED_APPS, updated).apply()
    SphereWidgetProvider.updateAllWidgets(context)
    Toast.makeText(
        context,
        if (addToSphere) R.string.drawer_action_add_to_sphere else R.string.drawer_action_remove_from_sphere,
        Toast.LENGTH_SHORT
    ).show()
}

// ─────────────────────────────────────────────────────────────────────────
//  String resource helpers (keeps this file readable above; Compose's
//  stringResource() composable works too, these small wrappers just avoid
//  needing @Composable context in a couple of non-composable call sites)
// ─────────────────────────────────────────────────────────────────────────

private fun stringResource_search_apps_hint(context: Context) = context.getString(R.string.search_apps_hint)
private fun stringResource_no_apps_found(context: Context) = context.getString(R.string.drawer_no_apps_found)
private fun stringResource_app_info(context: Context) = context.getString(R.string.drawer_action_app_info)
private fun stringResource_uninstall(context: Context) = context.getString(R.string.drawer_action_uninstall)
private fun stringResource_add_to_sphere(context: Context) = context.getString(R.string.drawer_action_add_to_sphere)
private fun stringResource_remove_from_sphere(context: Context) = context.getString(R.string.drawer_action_remove_from_sphere)
