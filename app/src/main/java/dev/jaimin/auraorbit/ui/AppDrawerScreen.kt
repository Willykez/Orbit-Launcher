package dev.jaimin.auraorbit.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.jaimin.auraorbit.R

/**
 * Full "all installed apps" grid: search box + tap to launch + long-press
 * for App info / Uninstall. Deliberately does not have a "sphere membership"
 * concept — the sphere just shows every launchable app directly (see
 * SphereScreen.kt), so there's nothing here to toggle.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    onClose: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val context = LocalContext.current
    var allApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        allApps = loadInstalledApps(context)
    }

    val displayApps = remember(allApps, query) {
        if (query.isBlank()) {
            allApps
        } else {
            val q = query.trim().lowercase()
            allApps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            placeholder = { Text(stringResourceSearchHint(context)) },
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
                Text(text = stringResourceNoAppsFound(context), color = Color.Gray)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(displayApps, key = { it.packageName }) { app ->
                DrawerAppItem(app = app, onLaunch = { onLaunchApp(app.packageName) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerAppItem(app: AppInfo, onLaunch: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onLaunch,
                    onLongClick = { menuExpanded = true }
                )
                .padding(vertical = 12.dp)
        ) {
            Image(
                bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() },
                contentDescription = app.label,
                modifier = Modifier.size(52.dp)
            )
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResourceAppInfo(context)) },
                onClick = {
                    menuExpanded = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResourceUninstall(context)) },
                onClick = {
                    menuExpanded = false
                    if (isSystemApp(context, app.packageName)) {
                        Toast.makeText(context, R.string.drawer_uninstall_system_app_blocked, Toast.LENGTH_SHORT).show()
                    } else {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${app.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }
    }
}

private fun stringResourceSearchHint(context: android.content.Context) = context.getString(R.string.search_apps_hint)
private fun stringResourceNoAppsFound(context: android.content.Context) = context.getString(R.string.drawer_no_apps_found)
private fun stringResourceAppInfo(context: android.content.Context) = context.getString(R.string.drawer_action_app_info)
private fun stringResourceUninstall(context: android.content.Context) = context.getString(R.string.drawer_action_uninstall)
