package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.djkaylfromdownunder.musicplayer.navigation.Routes
import com.djkaylfromdownunder.musicplayer.ui.theme.SurfaceDark

@Composable
fun AppBottomNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onViewClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    // alwaysShowLabel = true on every tab so all seven labels are always visible, not
    // just the selected one - taller than the Material3 default (80dp) to give the
    // icon+label pairs room to breathe with that many items.
    NavigationBar(containerColor = SurfaceDark, modifier = Modifier.height(144.dp)) {
        NavigationBarItem(
            selected = currentRoute == Routes.PLAYLISTS,
            onClick = { onNavigate(Routes.PLAYLISTS) },
            icon = { Icon(Icons.Default.QueueMusic, contentDescription = "Play Lists") },
            label = { Text("Lists") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = currentRoute == Routes.FAVORITES,
            onClick = { onNavigate(Routes.FAVORITES) },
            icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
            label = { Text("Faves") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SEARCH,
            onClick = onSearchClick,
            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            label = { Text("Search") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        // Not a navigation destination - opens the view-mode menu in place.
        NavigationBarItem(
            selected = false,
            onClick = onViewClick,
            icon = { Icon(Icons.Default.GridView, contentDescription = "Change View") },
            label = { Text("View") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = currentRoute == Routes.CREATE_PLAYLIST,
            onClick = { onNavigate(Routes.CREATE_PLAYLIST) },
            icon = { Icon(Icons.Default.PlaylistAdd, contentDescription = "Create Play List") },
            label = { Text("Create") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = currentRoute == Routes.LIBRARY,
            onClick = { onNavigate(Routes.LIBRARY) },
            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
            label = { Text("Library") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = { onNavigate(Routes.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            alwaysShowLabel = true,
            colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
        )
    }
}
