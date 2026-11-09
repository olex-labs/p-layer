package app.brano.domain

import app.brano.data.db.FavoriteEntity
import app.brano.data.db.PlayStatEntity
import app.brano.data.library.Track

enum class SmartPlaylist { FAVORITES, RECENTLY_ADDED, MOST_PLAYED, RECENTLY_PLAYED }

object SmartPlaylists {
    private const val RECENT_DAYS = 30L
    private const val SECONDS_PER_DAY = 86_400L
    private const val LIMIT = 100

    fun build(
        kind: SmartPlaylist,
        tracks: List<Track>,
        favorites: List<FavoriteEntity>,
        stats: List<PlayStatEntity>,
        nowSec: Long,
    ): List<Track> = when (kind) {
        SmartPlaylist.FAVORITES -> {
            val resolver = Resolver(tracks)
            favorites.sortedByDescending { it.addedAt }
                .mapNotNull { resolver.resolve(it.mediaStoreId, it.path) }
                .distinct()
        }
        SmartPlaylist.RECENTLY_ADDED -> {
            val cutoff = nowSec - RECENT_DAYS * SECONDS_PER_DAY
            tracks.filter { it.dateAddedSec >= cutoff }.sortedByDescending { it.dateAddedSec }
        }
        SmartPlaylist.MOST_PLAYED -> {
            val resolver = Resolver(tracks)
            stats.sortedByDescending { it.playCount }
                .mapNotNull { resolver.resolve(it.mediaStoreId, it.path) }
                .distinct().take(LIMIT)
        }
        SmartPlaylist.RECENTLY_PLAYED -> {
            val resolver = Resolver(tracks)
            stats.sortedByDescending { it.lastPlayedAt }
                .mapNotNull { resolver.resolve(it.mediaStoreId, it.path) }
                .distinct().take(LIMIT)
        }
    }

    /** Resolves rows to tracks by mediaStoreId first, then by path (ids can go stale after re-index). */
    private class Resolver(tracks: List<Track>) {
        private val byId = tracks.associateBy { it.id }
        private val byPath = tracks.associateBy { it.path }
        fun resolve(id: Long, path: String): Track? = byId[id] ?: byPath[path]
    }
}
