package app.brano.playback

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app's single [DeleteFlow]: deleted tracks leave the queue and the library is re-read. */
@Singleton
class TrackDeleter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val player: PlayerController,
    private val library: LibraryRepository,
) {
    /** Deleting other apps' files needs the system confirmation, available from Android 10. */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val flow = DeleteFlow<IntentSender>(
        scope = scope,
        trash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        trashRequest = { tracks ->
            // Only called when trash is true, i.e. Android 11+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) trashRequest(tracks) else error("trash needs Android 11")
        },
        deleteFile = { track -> withContext(Dispatchers.IO) { deleteOne(track) } },
        onDeleted = { tracks ->
            player.removeTracksFromQueue(tracks.mapTo(HashSet()) { it.id })
            scope.launch { library.refresh() }
        },
    )

    val requests: Flow<IntentSender> = flow.requests

    fun delete(tracks: List<Track>) {
        if (supported) flow.start(tracks)
    }

    fun onResult(ok: Boolean) = flow.onResult(ok)

    @RequiresApi(Build.VERSION_CODES.R)
    private fun trashRequest(tracks: List<Track>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, tracks.map { Uri.parse(it.contentUri) }, true).intentSender

    /** Android 10 path. */
    private fun deleteOne(track: Track): DeleteFlow.Outcome<IntentSender> = try {
        if (context.contentResolver.delete(Uri.parse(track.contentUri), null, null) > 0) {
            DeleteFlow.Outcome.Deleted
        } else {
            DeleteFlow.Outcome.Failed
        }
    } catch (e: SecurityException) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
            DeleteFlow.Outcome.NeedsUser(e.userAction.actionIntent.intentSender)
        } else {
            DeleteFlow.Outcome.Failed
        }
    }
}
