package app.brano.ui.components

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.brano.data.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Deleting other apps' files needs the system confirmation, available from Android 10. */
val canDeleteFiles: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/**
 * Returns a function that deletes tracks' files after the system confirmation and then calls
 * [onDeleted] with the tracks actually removed. Android 11+: one dialog, files go to the system
 * trash (recoverable for 30 days). Android 10: files are deleted one by one, each asking when needed.
 */
@Composable
fun rememberTrackDeleter(onDeleted: (List<Track>) -> Unit): (List<Track>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<Track>>(emptyList()) }
    // Android 10: the file waiting for the user's ok, and the ones still to do after it.
    var current by remember { mutableStateOf<Track?>(null) }
    val done = remember { mutableListOf<Track>() }

    lateinit var deleteNext: () -> Unit
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (result.resultCode == Activity.RESULT_OK) onDeleted(pending)
            pending = emptyList()
        } else {
            val track = current
            current = null
            if (result.resultCode == Activity.RESULT_OK && track != null) {
                // Granted: retry this file, then carry on.
                scope.launch {
                    if (withContext(Dispatchers.IO) { deleteQuietly(context, track) }) done += track
                    deleteNext()
                }
            } else {
                finish(done, onDeleted)
                pending = emptyList()
            }
        }
    }

    deleteNext = {
        scope.launch {
            while (pending.isNotEmpty()) {
                val track = pending.first()
                pending = pending.drop(1)
                val outcome = withContext(Dispatchers.IO) {
                    try {
                        if (deleteQuietly(context, track)) Outcome.Deleted else Outcome.Failed
                    } catch (e: SecurityException) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                            Outcome.NeedsUser(e.userAction.actionIntent.intentSender)
                        } else {
                            Outcome.Failed
                        }
                    }
                }
                when (outcome) {
                    Outcome.Deleted -> done += track
                    Outcome.Failed -> Unit
                    is Outcome.NeedsUser -> {
                        current = track
                        launcher.launch(IntentSenderRequest.Builder(outcome.sender).build())
                        return@launch
                    }
                }
            }
            finish(done, onDeleted)
        }
    }

    return { tracks ->
        if (tracks.isNotEmpty() && canDeleteFiles) {
            pending = tracks
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = tracks.map { Uri.parse(it.contentUri) }
                val request = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                done.clear()
                deleteNext()
            }
        }
    }
}

private sealed interface Outcome {
    data object Deleted : Outcome
    data object Failed : Outcome
    data class NeedsUser(val sender: IntentSender) : Outcome
}

private fun finish(done: MutableList<Track>, onDeleted: (List<Track>) -> Unit) {
    if (done.isNotEmpty()) onDeleted(done.toList())
    done.clear()
}

/** True when the row is gone. Throws SecurityException when the user must confirm (Android 10). */
private fun deleteQuietly(context: Context, track: Track): Boolean =
    context.contentResolver.delete(Uri.parse(track.contentUri), null, null) > 0
