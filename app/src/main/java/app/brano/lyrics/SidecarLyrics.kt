package app.brano.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.brano.data.db.MusicFolderEntity
import app.brano.data.library.Track
import java.io.File

/**
 * Document id of the .lrc next to a track, inside a music folder picked with the system picker
 * ([treeDocId] is that folder's tree id, [folderPath] its file system path).
 */
fun sidecarDocId(treeDocId: String, folderPath: String, trackFolder: String, trackPath: String): String {
    val rel = trackFolder.removePrefix(folderPath.trimEnd('/')).trim('/')
    val name = File(trackPath).nameWithoutExtension + ".lrc"
    val base = treeDocId.trimEnd('/')
    val sep = if (base.endsWith(":")) "" else "/"
    return base + sep + if (rel.isEmpty()) name else "$rel/$name"
}

/**
 * Reads "<track name>.lrc" from the music folder holding the track, through the read grant taken
 * when the folder was picked (Android 13+ gives apps no other way to read .lrc files). Null when the
 * track is in no chosen folder or there is no such file.
 */
fun readSidecarLyrics(context: Context, folders: List<MusicFolderEntity>, track: Track): String? {
    val folder = folders
        .filter { track.folder == it.path || track.folder.startsWith(it.path.trimEnd('/') + "/") }
        .maxByOrNull { it.path.length } ?: return null
    return runCatching {
        val tree = Uri.parse(folder.treeUri)
        val docId = sidecarDocId(DocumentsContract.getTreeDocumentId(tree), folder.path, track.folder, track.path)
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }.getOrNull()
}
