package com.k.hosken.djkayldownunder.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.k.hosken.djkayldownunder.navigation.Routes

/** How tapping a dock item behaves once it's placed in the dock. */
enum class DockActionType {
    /** One of the main tabs - navigates with popUpTo/launchSingleTop so tabs don't stack. */
    TAB,
    /** A one-off destination pushed onto the back stack normally (e.g. Search, Player). */
    PUSH,
    /** Not a navigation destination - opens the view-mode sheet in place. */
    VIEW_SHEET
}

/** One entry in the bottom dock's "icon library" - the full set the user can choose from. */
data class DockItemDef(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val actionType: DockActionType,
    val route: String? = null
)

/** Every icon available for the bottom dock, in its original default order. */
val ALL_DOCK_ITEMS: List<DockItemDef> = listOf(
    DockItemDef("playing", "Playing", Icons.Default.PlayCircleFilled, DockActionType.PUSH, Routes.PLAYER),
    DockItemDef("lists", "Lists", Icons.Default.QueueMusic, DockActionType.TAB, Routes.PLAYLISTS),
    DockItemDef("faves", "Faves", Icons.Default.Favorite, DockActionType.TAB, Routes.FAVORITES),
    DockItemDef("search", "Search", Icons.Default.Search, DockActionType.PUSH, Routes.SEARCH),
    DockItemDef("view", "View", Icons.Default.GridView, DockActionType.VIEW_SHEET),
    DockItemDef("create", "Create", Icons.Default.PlaylistAdd, DockActionType.TAB, Routes.CREATE_PLAYLIST),
    DockItemDef("library", "Library", Icons.Default.LibraryMusic, DockActionType.TAB, Routes.LIBRARY),
    DockItemDef("settings", "Settings", Icons.Default.Settings, DockActionType.TAB, Routes.SETTINGS)
)

val DEFAULT_DOCK_ORDER: List<String> = ALL_DOCK_ITEMS.map { it.id }
