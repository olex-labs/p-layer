package app.brano.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import app.brano.R
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
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
import app.brano.ui.onboarding.PermissionScreen
import app.brano.ui.player.MiniPlayer

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
    LaunchedEffect(player.isPlaying) {
        if (player.isPlaying && !notificationsAsked && Build.VERSION.SDK_INT >= 33) {
            notificationsAsked = true
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
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
            placeholder(Destinations.LIBRARY, R.string.tab_library)
            placeholder(Destinations.SEARCH, R.string.tab_search)
            placeholder(Destinations.PLAYLISTS, R.string.tab_playlists)
            placeholder(Destinations.SETTINGS, R.string.tab_settings)
            placeholder(Destinations.ALBUM, R.string.tab_library, navArgument("id") { type = NavType.LongType })
            placeholder(Destinations.ARTIST, R.string.tab_library, navArgument("id") { type = NavType.LongType })
            placeholder(
                Destinations.FOLDER, R.string.tab_library,
                navArgument("path") { type = NavType.StringType; nullable = true; defaultValue = null },
            )
            placeholder(Destinations.PLAYLIST, R.string.tab_playlists, navArgument("id") { type = NavType.LongType })
            placeholder(Destinations.SMART, R.string.tab_playlists, navArgument("kind") { type = NavType.StringType })
            placeholder(Destinations.PLAYER, R.string.player_title)
        }
    }
}

/** Trivial stand-in; replaced by the real screen in a later task. */
private fun androidx.navigation.NavGraphBuilder.placeholder(
    route: String,
    @StringRes title: Int,
    vararg args: androidx.navigation.NamedNavArgument,
) {
    composable(route, arguments = args.toList()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(title)) }
    }
}
