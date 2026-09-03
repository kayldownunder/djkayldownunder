package com.djkaylfromdownunder.musicplayer.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.djkaylfromdownunder.musicplayer.data.BackgroundTarget
import com.djkaylfromdownunder.musicplayer.ui.*

object Routes {
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val CREATE_PLAYLIST = "create_playlist"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val SKIP_REVIEW = "skip_review"
    const val SEARCH = "search"
    const val DOCK_SETTINGS = "dock_settings"
    const val FOLDER_PATTERN = "folder/{encodedUri}/{encodedName}"

    /** Builds a navigable route for a specific folder, URL-encoding its URI and name. */
    fun folderRoute(uri: Uri, name: String): String {
        return "folder/${Uri.encode(uri.toString())}/${Uri.encode(name)}"
    }
}

@Composable
fun AppNavHost() {
    val navController: NavHostController = rememberNavController()

    // Shared across screens so playback state and library state persist during navigation.
    val libraryViewModel: MusicLibraryViewModel = viewModel()
    val playerViewModel: PlayerViewModel = viewModel()
    val metadataViewModel: MetadataViewModel = viewModel()
    val themeViewModel: ThemeViewModel = viewModel()
    val skipListViewModel: SkipListViewModel = viewModel()
    val viewPreferencesViewModel: ViewPreferencesViewModel = viewModel()
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val customPlaylistViewModel: CustomPlaylistViewModel = viewModel()
    val dockPreferencesViewModel: DockPreferencesViewModel = viewModel()
    val fontPreferencesViewModel: FontPreferencesViewModel = viewModel()
    val settingsLayoutViewModel: SettingsLayoutViewModel = viewModel()
    val buttonColorViewModel: ButtonColorViewModel = viewModel()
    val audioNormalizationViewModel: AudioNormalizationViewModel = viewModel()

    var showViewSheet by remember { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Nested folder screens use the FOLDER_PATTERN route, so the bottom bar's "which tab
    // is selected" check needs to match the pattern, not an exact route string.
    val isFolderRoute = currentRoute == Routes.FOLDER_PATTERN

    // Full-screen destinations that hide the mini-player/bottom nav entirely.
    val hideBottomBarRoutes = setOf(
        Routes.PLAYER, Routes.SKIP_REVIEW, Routes.SEARCH, Routes.CREATE_PLAYLIST, Routes.DOCK_SETTINGS
    )

    // "Random Skip All Albums" is a single shared toggle (PlayerViewModel.uiState) rather
    // than something each screen's shortcut owns independently - collected once here so
    // the Library, Play Lists, and Now Playing screens' shortcuts all stay in sync:
    // turning it on/off from any one of them highlights (or un-highlights) the button on
    // every other screen it appears on too.
    val playerUiState by playerViewModel.uiState.collectAsState()
    val isShuffleAllActive = playerUiState.isShuffleAllActive

    val visibleDockIds by dockPreferencesViewModel.visibleIds.collectAsState()
    val dockItems = remember(visibleDockIds) { dockPreferencesViewModel.resolve(visibleDockIds) }

    Scaffold(
            bottomBar = {
                if (currentRoute !in hideBottomBarRoutes) {
                    val bar: @Composable () -> Unit = {
                        Column {
                            MiniPlayerBar(
                                playerViewModel = playerViewModel,
                                metadataViewModel = metadataViewModel,
                                onExpand = { navController.navigate(Routes.PLAYER) }
                            )
                            AppBottomNav(
                                dockItems = dockItems,
                                currentRoute = if (isFolderRoute) Routes.LIBRARY else currentRoute,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(Routes.LIBRARY) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                onPushNavigate = { route -> navController.navigate(route) },
                                onViewClick = { showViewSheet = true }
                            )
                        }
                    }
                    // Auto-hide-after-idle only on the Library screens (root + nested
                    // folder browsing) and Play Lists - every other tab keeps the bar
                    // permanently visible.
                    if (isFolderRoute || currentRoute == Routes.LIBRARY || currentRoute == Routes.PLAYLISTS) {
                        AutoHideBottomBar(content = bar)
                    } else {
                        bar()
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
            ) {
                composable(Routes.LIBRARY) {
                    TargetedBackground(target = BackgroundTarget.LIBRARY, themeViewModel = themeViewModel) {
                        val rootUri by libraryViewModel.rootUri.collectAsState()
                        val currentRootUri = rootUri
                        if (currentRootUri == null) {
                            NoLibraryChosenScreen(libraryViewModel = libraryViewModel)
                        } else {
                            val libraryState by libraryViewModel.state.collectAsState()
                            val allPlaylists = (libraryState as? LibraryState.Loaded)?.playlists.orEmpty()
                            FolderBrowserScreen(
                                folderUri = currentRootUri,
                                folderName = "Your Library",
                                libraryViewModel = libraryViewModel,
                                metadataViewModel = metadataViewModel,
                                favoritesViewModel = favoritesViewModel,
                                viewPreferencesViewModel = viewPreferencesViewModel,
                                buttonColorViewModel = buttonColorViewModel,
                                onNavigateToSubfolder = { uri, name ->
                                    navController.navigate(Routes.folderRoute(uri, name))
                                },
                                onPlayLeaf = { playlist ->
                                    playerViewModel.playPlaylist(playlist)
                                    navController.navigate(Routes.PLAYER)
                                },
                                isShuffleAllActive = isShuffleAllActive,
                                onShuffleAll = if (allPlaylists.isNotEmpty()) {
                                    {
                                        playerViewModel.toggleShuffleAllAlbums(allPlaylists)
                                        navController.navigate(Routes.PLAYER)
                                    }
                                } else null
                            )
                        }
                    }
                }
                composable(
                    route = Routes.FOLDER_PATTERN,
                    arguments = listOf(
                        navArgument("encodedUri") { type = NavType.StringType },
                        navArgument("encodedName") { type = NavType.StringType }
                    )
                ) { entry ->
                    // Navigation Compose already URL-decodes route arguments once when
                    // extracting them here - decoding again would corrupt the %-encoded
                    // characters that are legitimately part of the document URI itself
                    // (e.g. %3A/%2F inside the document ID), making it resolve to the
                    // wrong folder (silently falling back to the tree's root).
                    val uriArg = entry.arguments?.getString("encodedUri").orEmpty()
                    val nameArg = entry.arguments?.getString("encodedName").orEmpty()
                    val folderUri = Uri.parse(uriArg)
                    val folderName = nameArg

                    TargetedBackground(target = BackgroundTarget.LIBRARY, themeViewModel = themeViewModel) {
                        FolderBrowserScreen(
                            folderUri = folderUri,
                            folderName = folderName,
                            libraryViewModel = libraryViewModel,
                            metadataViewModel = metadataViewModel,
                            favoritesViewModel = favoritesViewModel,
                            viewPreferencesViewModel = viewPreferencesViewModel,
                            buttonColorViewModel = buttonColorViewModel,
                            onNavigateToSubfolder = { uri, name ->
                                navController.navigate(Routes.folderRoute(uri, name))
                            },
                            onPlayLeaf = { playlist ->
                                playerViewModel.playPlaylist(playlist)
                                navController.navigate(Routes.PLAYER)
                            }
                        )
                    }
                }
                composable(Routes.PLAYLISTS) {
                    PlayListsScreen(
                        libraryViewModel = libraryViewModel,
                        metadataViewModel = metadataViewModel,
                        favoritesViewModel = favoritesViewModel,
                        customPlaylistViewModel = customPlaylistViewModel,
                        viewPreferencesViewModel = viewPreferencesViewModel,
                        buttonColorViewModel = buttonColorViewModel,
                        isShuffleAllActive = isShuffleAllActive,
                        onPlaylistClick = { playlist ->
                            playerViewModel.playPlaylist(playlist)
                            navController.navigate(Routes.PLAYER)
                        },
                        onRandomSkipAllAlbums = { playlists ->
                            playerViewModel.toggleShuffleAllAlbums(playlists)
                            navController.navigate(Routes.PLAYER)
                        }
                    )
                }
                composable(Routes.FAVORITES) {
                    FavoritesScreen(
                        libraryViewModel = libraryViewModel,
                        metadataViewModel = metadataViewModel,
                        favoritesViewModel = favoritesViewModel,
                        customPlaylistViewModel = customPlaylistViewModel,
                        viewPreferencesViewModel = viewPreferencesViewModel,
                        onPlaylistClick = { playlist ->
                            playerViewModel.playPlaylist(playlist)
                            navController.navigate(Routes.PLAYER)
                        }
                    )
                }
                composable(Routes.CREATE_PLAYLIST) {
                    CreatePlaylistScreen(
                        libraryViewModel = libraryViewModel,
                        metadataViewModel = metadataViewModel,
                        customPlaylistViewModel = customPlaylistViewModel,
                        onDone = { navController.navigate(Routes.PLAYLISTS) { popUpTo(Routes.LIBRARY) } },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.PLAYER) {
                    PlayerScreen(
                        viewModel = playerViewModel,
                        metadataViewModel = metadataViewModel,
                        skipListViewModel = skipListViewModel,
                        libraryViewModel = libraryViewModel,
                        customPlaylistViewModel = customPlaylistViewModel,
                        buttonColorViewModel = buttonColorViewModel,
                        onCollapse = { navController.popBackStack() },
                        onPlayRecommendation = { playlist ->
                            playerViewModel.playPlaylist(playlist, forceRestart = true)
                        }
                    )
                }
                composable(Routes.SETTINGS) {
                    TargetedBackground(target = BackgroundTarget.SETTINGS, themeViewModel = themeViewModel) {
                        SettingsScreen(
                            libraryViewModel = libraryViewModel,
                            metadataViewModel = metadataViewModel,
                            themeViewModel = themeViewModel,
                            fontPreferencesViewModel = fontPreferencesViewModel,
                            settingsLayoutViewModel = settingsLayoutViewModel,
                            buttonColorViewModel = buttonColorViewModel,
                            audioNormalizationViewModel = audioNormalizationViewModel,
                            onNavigateToSkipReview = { navController.navigate(Routes.SKIP_REVIEW) },
                            onNavigateToDockSettings = { navController.navigate(Routes.DOCK_SETTINGS) }
                        )
                    }
                }
                composable(Routes.DOCK_SETTINGS) {
                    DockSettingsScreen(
                        dockPreferencesViewModel = dockPreferencesViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SKIP_REVIEW) {
                    SkipReviewScreen(
                        libraryViewModel = libraryViewModel,
                        skipListViewModel = skipListViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        libraryViewModel = libraryViewModel,
                        metadataViewModel = metadataViewModel,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onResultSelected = { navController.navigate(Routes.PLAYER) }
                    )
                }
            }
        }

        if (showViewSheet) {
            val currentMode by viewPreferencesViewModel.viewMode.collectAsState()
            ViewModeSheet(
                currentMode = currentMode,
                onModeSelected = { mode ->
                    viewPreferencesViewModel.setViewMode(mode)
                    showViewSheet = false
                },
                onDismiss = { showViewSheet = false }
            )
        }
}

@Composable
private fun NoLibraryChosenScreen(libraryViewModel: MusicLibraryViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "No music folder chosen yet. Head to Settings to pick your root music directory.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        ChooseMusicFolderButton(viewModel = libraryViewModel)
    }
}
