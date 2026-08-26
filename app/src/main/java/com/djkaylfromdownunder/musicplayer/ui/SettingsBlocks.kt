package com.djkaylfromdownunder.musicplayer.ui

/** One reorderable shortcut block on the Settings screen - see RearrangeSettingsScreen. */
data class SettingsBlockDef(val id: String, val label: String)

/** Every block the Settings screen can show, in its original default order. */
val ALL_SETTINGS_BLOCKS: List<SettingsBlockDef> = listOf(
    SettingsBlockDef("music_library", "Music Library Selected Folder"),
    SettingsBlockDef("metadata", "Fetch Metadata for All Playlists"),
    SettingsBlockDef("consolidate_artwork", "Consolidate Album Art"),
    SettingsBlockDef("library_background", "Library Background"),
    SettingsBlockDef("settings_background", "Settings Screen Background"),
    SettingsBlockDef("customize_dock", "Customise Bottom Dock"),
    SettingsBlockDef("rearrange_settings", "Rearrange Settings Layout"),
    SettingsBlockDef("skip_review", "Review Skipped Songs")
)

val DEFAULT_SETTINGS_ORDER: List<String> = ALL_SETTINGS_BLOCKS.map { it.id }
