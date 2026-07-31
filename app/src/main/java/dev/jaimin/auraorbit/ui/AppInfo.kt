package dev.jaimin.auraorbit.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A single installed, launchable app — just enough to show and launch it.
 * Deliberately minimal: no icon packs, no grouping/color metadata, no
 * per-widget scoping. That complexity is exactly what kept causing build
 * failures in the previous version; this rewrite leaves it out entirely.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

/**
 * Queries every launchable app on the device (MAIN + LAUNCHER), sorted by
 * label. Runs on [Dispatchers.IO] since PackageManager queries and icon
 * loading both do real I/O.
 */
suspend fun loadInstalledApps(context: Context): List<AppInfo> = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val pm = appContext.packageManager

    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved: List<ResolveInfo> = pm.queryIntentActivities(launcherIntent, 0)

    resolved
        .mapNotNull { ri ->
            val activityInfo = ri.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName ?: return@mapNotNull null
            val label = try {
                ri.loadLabel(pm).toString()
            } catch (e: Exception) {
                packageName
            }
            val icon = try {
                ri.loadIcon(pm)
            } catch (e: Exception) {
                return@mapNotNull null
            }
            AppInfo(packageName, label, icon)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Launches [packageName]'s default launch activity, if it has one. */
fun launchApp(context: Context, packageName: String): Boolean {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    return true
}

/** Whether [packageName] is a system app (used to block uninstall attempts on them). */
fun isSystemApp(context: Context, packageName: String): Boolean {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
