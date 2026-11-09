package app.brano.data.library

fun t(
    id: Long = 1,
    title: String = "Title $id",
    artist: String? = "Artist",
    album: String? = "Album",
    albumId: Long = 1,
    artistId: Long = 1,
    dur: Long = 200_000,
    folder: String = "/sdcard/Music",
    path: String = "$folder/$id.mp3",
    dateAddedSec: Long = 0,
    trackNumber: Int = 0,
    year: Int = 0,
    contentUri: String = "content://media/external/audio/media/$id",
) = Track(
    id = id, title = title, artist = artist, album = album, albumId = albumId, artistId = artistId,
    durationMs = dur, path = path, folder = folder, dateAddedSec = dateAddedSec,
    trackNumber = trackNumber, year = year, contentUri = contentUri,
)
