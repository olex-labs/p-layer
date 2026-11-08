package app.brano.domain

import app.brano.data.db.FavoriteEntity
import app.brano.data.db.PlayStatEntity
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmartPlaylistsTest {
    private val now = 10_000_000L
    private val day = 86_400L

    private fun ids(l: List<app.brano.data.library.Track>) = l.map { it.id }

    @Test fun favorites_orderedByAddedAtDesc_skippingMissingTracks() {
        val tracks = listOf(t(id = 1), t(id = 2), t(id = 3))
        val favs = listOf(
            FavoriteEntity(1, "/sdcard/Music/1.mp3", 100),
            FavoriteEntity(99, "/gone/99.mp3", 500),
            FavoriteEntity(3, "/sdcard/Music/3.mp3", 300),
            FavoriteEntity(2, "/sdcard/Music/2.mp3", 200),
        )
        val r = SmartPlaylists.build(SmartPlaylist.FAVORITES, tracks, favs, emptyList(), now)
        assertThat(ids(r)).containsExactly(3L, 2L, 1L).inOrder()
    }

    @Test fun favorites_staleId_resolvesByPath() {
        val tracks = listOf(t(id = 7, path = "/sdcard/Music/a.mp3"), t(id = 8))
        val favs = listOf(
            FavoriteEntity(1, "/sdcard/Music/a.mp3", 100),
            FavoriteEntity(8, "/sdcard/Music/8.mp3", 50),
        )
        val r = SmartPlaylists.build(SmartPlaylist.FAVORITES, tracks, favs, emptyList(), now)
        assertThat(ids(r)).containsExactly(7L, 8L).inOrder()
    }

    @Test fun stats_staleId_resolvesByPath() {
        val tracks = listOf(t(id = 7, path = "/m/a.mp3"))
        val stats = listOf(PlayStatEntity(1, "/m/a.mp3", 3, 10))
        assertThat(ids(SmartPlaylists.build(SmartPlaylist.MOST_PLAYED, tracks, emptyList(), stats, now)))
            .containsExactly(7L)
        assertThat(ids(SmartPlaylists.build(SmartPlaylist.RECENTLY_PLAYED, tracks, emptyList(), stats, now)))
            .containsExactly(7L)
    }

    @Test fun duplicateResolution_keepsTrackOnce() {
        val tracks = listOf(t(id = 7, path = "/m/a.mp3"))
        val favs = listOf(
            FavoriteEntity(7, "/m/a.mp3", 100),
            FavoriteEntity(1, "/m/a.mp3", 200),
        )
        val r = SmartPlaylists.build(SmartPlaylist.FAVORITES, tracks, favs, emptyList(), now)
        assertThat(ids(r)).containsExactly(7L)
    }

    @Test fun recentlyAdded_30DaysIncluded_31Excluded_descending() {
        val tracks = listOf(
            t(id = 1, dateAddedSec = now - 30 * day),
            t(id = 2, dateAddedSec = now - 31 * day),
            t(id = 3, dateAddedSec = now - 1 * day),
            t(id = 4, dateAddedSec = now - 30 * day - 1),
        )
        val r = SmartPlaylists.build(SmartPlaylist.RECENTLY_ADDED, tracks, emptyList(), emptyList(), now)
        assertThat(ids(r)).containsExactly(3L, 1L).inOrder()
    }

    @Test fun mostPlayed_top100_byPlayCountDesc() {
        val tracks = (1L..150L).map { t(id = it) }
        val stats = (1L..150L).map { PlayStatEntity(it, "/sdcard/Music/$it.mp3", it.toInt(), 0) }
        val r = SmartPlaylists.build(SmartPlaylist.MOST_PLAYED, tracks, emptyList(), stats, now)
        assertThat(r).hasSize(100)
        assertThat(ids(r)).isEqualTo((150L downTo 51L).toList())
    }

    @Test fun recentlyPlayed_last100_byLastPlayedDesc() {
        val tracks = (1L..150L).map { t(id = it) }
        val stats = (1L..150L).map { PlayStatEntity(it, "/sdcard/Music/$it.mp3", 1, it * 10) }
        val r = SmartPlaylists.build(SmartPlaylist.RECENTLY_PLAYED, tracks, emptyList(), stats, now)
        assertThat(r).hasSize(100)
        assertThat(ids(r)).isEqualTo((150L downTo 51L).toList())
    }

    @Test fun mostPlayed_ties_keepInputOrder() {
        val tracks = listOf(t(id = 1), t(id = 2), t(id = 3))
        val stats = listOf(
            PlayStatEntity(2, "/sdcard/Music/2.mp3", 5, 0),
            PlayStatEntity(1, "/sdcard/Music/1.mp3", 5, 0),
            PlayStatEntity(3, "/sdcard/Music/3.mp3", 9, 0),
        )
        val r = SmartPlaylists.build(SmartPlaylist.MOST_PLAYED, tracks, emptyList(), stats, now)
        assertThat(ids(r)).containsExactly(3L, 2L, 1L).inOrder()
    }
}
