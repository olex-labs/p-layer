package app.brano.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SelectedTabTest {
    @Test
    fun topLevelRouteIsSelectedItself() {
        assertThat(selectedTopLevelRoute(listOf(null, Destinations.LIBRARY))).isEqualTo(Destinations.LIBRARY)
        assertThat(selectedTopLevelRoute(listOf(null, Destinations.LIBRARY, Destinations.SEARCH)))
            .isEqualTo(Destinations.SEARCH)
    }

    @Test
    fun detailRoutesKeepTheTabTheyWereOpenedFrom() {
        assertThat(selectedTopLevelRoute(listOf(null, Destinations.LIBRARY, Destinations.ALBUM)))
            .isEqualTo(Destinations.LIBRARY)
        assertThat(selectedTopLevelRoute(listOf(null, Destinations.LIBRARY, Destinations.SEARCH, Destinations.ALBUM, Destinations.ARTIST)))
            .isEqualTo(Destinations.SEARCH)
        assertThat(selectedTopLevelRoute(listOf(null, Destinations.LIBRARY, Destinations.PLAYLISTS, Destinations.SMART)))
            .isEqualTo(Destinations.PLAYLISTS)
        assertThat(selectedTopLevelRoute(listOf(null, Destinations.LIBRARY, Destinations.PLAYLISTS, Destinations.PLAYLIST)))
            .isEqualTo(Destinations.PLAYLISTS)
    }

    @Test
    fun emptyBackStackSelectsNothing() {
        assertThat(selectedTopLevelRoute(emptyList())).isNull()
    }
}
