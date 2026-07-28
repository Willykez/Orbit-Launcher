package dev.jaimin.auraorbit.ui

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * ═══════════════════════════════════════════════════════════════════════════════
 * DockPrefs.kt — Persistence for the bottom Dock's chosen apps
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Stored as an ordered, comma-joined package-name string rather than a
 * SharedPreferences StringSet, because StringSet does not preserve insertion
 * order — and dock order matters (it's a fixed row, not a searchable grid).
 *
 * Deliberately a standalone, minimal pref key — not part of SphereEngine's
 * RELEVANT_KEYS / group-scoped widget prefs, and not read by anything in the
 * widget code path. The dock is a pure-launcher (Home screen) concept only;
 * it must stay decoupled from the widget system so a bug in one can never
 * leak into the other.
 */
private const val PREF_DOCK_APPS = "pref_dock_apps"
const val DOCK_MAX_APPS = 7
const val DOCK_RECOMMENDED_MIN_APPS = 3

fun loadDockPackages(context: Context): List<String> {
    val raw = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_DOCK_APPS, null)
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(DOCK_MAX_APPS)
}

fun saveDockPackages(context: Context, packages: List<String>) {
    val trimmed = packages.take(DOCK_MAX_APPS)
    PreferenceManager.getDefaultSharedPreferences(context).edit()
        .putString(PREF_DOCK_APPS, trimmed.joinToString(","))
        .apply()
}
