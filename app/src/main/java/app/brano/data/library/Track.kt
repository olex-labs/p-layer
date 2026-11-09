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
    // Defaults keep older library snapshots readable.
    val mimeType: String? = null,
    val bitrateKbps: Int = 0,
)

data class TrackRef(val mediaStoreId: Long, val path: String)

fun Track.ref(): TrackRef = TrackRef(id, path)
