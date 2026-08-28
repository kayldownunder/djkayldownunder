package com.djkaylfromdownunder.musicplayer.ui

/** One reorderable shortcut block on the Settings screen - long-press and drag it in place to reorder. */
data class SettingsBlockDef(val id: String, val label: String)

/** Every block the Settings screen can show, in its original default order. */
val ALL_SETTINGS_BLOCKS: List<SettingsBlockDef> = listOf(
    SettingsBlockDef("music_library", "Music Library Selected Folder"),
    SettingsBlockDef("metadata", "Fetch Metadata for All Playlists"),
    SettingsBlockDef("consolidate_artwork", "Consolidate Album Art"),
    SettingsBlockDef("library_background", "Library Background"),
    SettingsBlockDef("settings_background", "Settings Screen Background"),
    SettingsBlockDef("dock_visibility", "Show/Hide Dock Icons"),
    SettingsBlockDef("skip_review", "Review Skipped Songs"),
    SettingsBlockDef("shortcut_button_color", "Shortcut Button Color Selection")
)

val DEFAULT_SETTINGS_ORDER: List<String> = ALL_SETTINGS_BLOCKS.map { it.id }
