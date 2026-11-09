package app.brano.data.library

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: Long,
    val artistId: Long,
    val durationMs: Long,
    val path: String,
    val folder: String,
    val dateAddedSec: Long,
    val trackNumber: Int,
    val year: Int,
    val contentUri: String,
)

data class TrackRef(val mediaStoreId: Long, val path: String)

fun Track.ref(): TrackRef = TrackRef(id, path)
