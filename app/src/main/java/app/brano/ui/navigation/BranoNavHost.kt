package app.brano.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.brano.ui.AppViewModel
import app.brano.ui.library.AlbumScreen
import app.brano.ui.library.ArtistScreen
import app.brano.ui.library.FolderScreen
import app.brano.ui.playlists.PlaylistDetailScreen
import app.brano.ui.playlists.PlaylistsScreen
import app.brano.ui.library.LibraryScreen
import app.brano.ui.onboarding.PermissionScreen
import app.brano.ui.player.MiniPlayer
import app.brano.ui.player.PlayerScreen
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import app.brano.ui.search.SearchScreen
import app.brano.ui.settings.SettingsScreen

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/** Root: permission gate, then the navigation shell. The permission is re-checked on every resume. */
@Composable
fun BranoRoot(viewModel: AppViewModel) {
    val context = LocalContext.current
    fun hasAudio() = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasAudio()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAudio() }
    LaunchedEffect(granted) { if (granted) viewModel.onAudioPermissionGranted() }

    if (granted) {
        BranoShell(viewModel)
    } else {
        PermissionScreen(audioPermission) { granted = true }
    }
}

@Composable
private fun BranoShell(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val onPlayer = route == Destinations.PLAYER

    // POST_NOTIFICATIONS is requested the first time playback actually starts.
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(player.isPlaying) {
        if (player.isPlaying && !notificationsAsked && Build.VERSION.SDK_INT >= 33) {
            notificationsAsked = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!onPlayer) {
                Column {
                    MiniPlayer(
                        state = player,
                        onTogglePlay = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                        onOpen = { navController.navigate(Destinations.PLAYER) { launchSingleTop = true } },
                    )
                    NavigationBar {
                        topLevelDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(stringResource(dest.label)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.LIBRARY,
            modifier = Modifier.padding(padding),
        ) {
            val toAlbum: (Long) -> Unit = { navController.navigate(Destinations.album(it)) }
            val toArtist: (Long) -> Unit = { navController.navigate(Destinations.artist(it)) }
            val toFolder: (String) -> Unit = { navController.navigate(Destinations.folder(it)) }
            composable(Destinations.LIBRARY) {
                LibraryScreen(
                    onOpenAlbum = toAlbum,
                    onOpenArtist = toArtist,
                    onOpenFolder = toFolder,
                    onOpenSettings = { navController.navigate(Destinations.SETTINGS) { launchSingleTop = true } },
                )
            }
            composable(Destinations.SEARCH) { SearchScreen(onOpenAlbum = toAlbum, onOpenArtist = toArtist) }
            composable(Destinations.PLAYLISTS) {
                PlaylistsScreen(
                    onOpenPlaylist = { navController.navigate(Destinations.playlist(it)) },
                    onOpenSmart = { navController.navigate(Destinations.smart(it)) },
                )
            }
            composable(Destinations.SETTINGS) { SettingsScreen() }
            composable(Destinations.ALBUM, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                AlbumScreen(
                    albumId = it.arguments!!.getLong("id"),
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = toAlbum,
                    onOpenArtist = toArtist,
                )
            }
            composable(Destinations.ARTIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                ArtistScreen(
                    artistId = it.arguments!!.getLong("id"),
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = toAlbum,
                    onOpenArtist = toArtist,
                )
            }
            composable(
                Destinations.FOLDER,
                arguments = listOf(navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                FolderScreen(
                    path = it.arguments?.getString("path"),
                    onBack = { navController.popBackStack() },
                    onOpenFolder = toFolder,
                    onOpenAlbum = toAlbum,
                    onOpenArtist = toArtist,
                )
            }
            composable(Destinations.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PlaylistDetailScreen(onBack = { navController.popBackStack() }, onOpenAlbum = toAlbum, onOpenArtist = toArtist)
            }
            composable(Destinations.SMART, arguments = listOf(navArgument("kind") { type = NavType.StringType })) {
                PlaylistDetailScreen(onBack = { navController.popBackStack() }, onOpenAlbum = toAlbum, onOpenArtist = toArtist)
            }
            composable(Destinations.PLAYER) { PlayerScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
