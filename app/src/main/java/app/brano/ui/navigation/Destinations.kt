package app.brano.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.brano.R
import app.brano.ui.icons.AppIcons

object Destinations {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val PLAYLISTS = "playlists"
    const val SETTINGS = "settings"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{id}"
    const val FOLDER = "folder?path={path}"
    const val PLAYLIST = "playlist/{id}"
    const val SMART = "smart/{kind}"
    const val PLAYER = "player"

    fun album(id: Long) = "album/$id"
    fun artist(id: Long) = "artist/$id"
    fun folder(path: String? = null) = if (path == null) "folder" else "folder?path=${Uri.encode(path)}"
    fun playlist(id: Long) = "playlist/$id"
    fun smart(kind: String) = "smart/$kind"

    val topLevelRoutes = setOf(LIBRARY, SEARCH, PLAYLISTS, SETTINGS)
}

/**
 * The tab to highlight once [route] is shown: a top-level route selects itself; detail screens
 * (album, artist, folder, playlist, smart, player) keep [selected], the tab they were opened from.
 * Since every tab switch pops back to the start destination, this is the last top-level route in
 * the back stack (NavController.currentBackStack itself is a restricted API).
 */
fun nextSelectedTab(selected: String, route: String?): String =
    if (route != null && route in Destinations.topLevelRoutes) route else selected

data class TopLevelDestination(val route: String, @param:StringRes val label: Int, val icon: ImageVector)

val topLevelDestinations = listOf(
    TopLevelDestination(Destinations.LIBRARY, R.string.tab_library, AppIcons.Library),
    TopLevelDestination(Destinations.SEARCH, R.string.tab_search, AppIcons.Search),
    TopLevelDestination(Destinations.PLAYLISTS, R.string.tab_playlists, AppIcons.Playlist),
    TopLevelDestination(Destinations.SETTINGS, R.string.tab_settings, AppIcons.Settings),
)
