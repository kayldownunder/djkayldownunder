package com.k.hosken.djkayldownunder.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector

/** One reorderable shortcut block on the Settings screen - long-press it, then use the up/down arrows to reorder. */
data class SettingsBlockDef(val id: String, val label: String, val icon: ImageVector)

/** Every block the Settings screen can show, in its original default order. */
val ALL_SETTINGS_BLOCKS: List<SettingsBlockDef> = listOf(
    SettingsBlockDef("font_appearance", "Fonts", Icons.Default.TextFields),
    SettingsBlockDef("music_library", "Music Library Selected Folder", Icons.Default.FolderOpen),
    SettingsBlockDef("metadata", "Fetch Metadata for All Playlists", Icons.Default.CloudDownload),
    SettingsBlockDef("consolidate_artwork", "Consolidate Album Art", Icons.Default.PhotoLibrary),
    SettingsBlockDef("library_background", "Library Background", Icons.Default.Image),
    SettingsBlockDef("settings_background", "Settings Screen Background", Icons.Default.Wallpaper),
    SettingsBlockDef("dock_visibility", "Dock Bar Settings", Icons.Default.Apps),
    SettingsBlockDef("skip_review", "Review Skipped Songs", Icons.Default.History),
    SettingsBlockDef("shortcut_button_color", "Shortcut Button Color Selection", Icons.Default.Palette),
    SettingsBlockDef("audio_normalization", "Audio Normalisation", Icons.Default.GraphicEq)
)

val DEFAULT_SETTINGS_ORDER: List<String> = ALL_SETTINGS_BLOCKS.map { it.id }
