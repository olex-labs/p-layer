package app.brano.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SelectedTabTest {
    /** Applies the routes shown, in order, starting from the Library tab. */
    private fun after(vararg routes: String?): String = routes.fold(Destinations.LIBRARY, ::nextSelectedTab)

    @Test
    fun topLevelRouteIsSelectedItself() {
        assertThat(after(Destinations.LIBRARY)).isEqualTo(Destinations.LIBRARY)
        assertThat(after(Destinations.SEARCH)).isEqualTo(Destinations.SEARCH)
        assertThat(after(Destinations.SEARCH, Destinations.SETTINGS)).isEqualTo(Destinations.SETTINGS)
    }

    @Test
    fun detailRoutesKeepTheTabTheyWereOpenedFrom() {
        assertThat(after(Destinations.LIBRARY, Destinations.ALBUM)).isEqualTo(Destinations.LIBRARY)
        assertThat(after(Destinations.SEARCH, Destinations.ALBUM, Destinations.ARTIST)).isEqualTo(Destinations.SEARCH)
        assertThat(after(Destinations.PLAYLISTS, Destinations.SMART)).isEqualTo(Destinations.PLAYLISTS)
        assertThat(after(Destinations.PLAYLISTS, Destinations.PLAYLIST, Destinations.PLAYER)).isEqualTo(Destinations.PLAYLISTS)
    }

    @Test
    fun backToATabSelectsIt() {
        // Search -> album -> back to search -> back to library
        assertThat(after(Destinations.SEARCH, Destinations.ALBUM, Destinations.SEARCH, Destinations.LIBRARY))
            .isEqualTo(Destinations.LIBRARY)
    }

    @Test
    fun noRouteKeepsTheSelection() {
        assertThat(nextSelectedTab(Destinations.SEARCH, null)).isEqualTo(Destinations.SEARCH)
    }
}
