package app.brano.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.brano.playback.PlaybackService

/**
 * Next/previous from the widget. Media3's MediaButtonReceiver ignores them on API 26+ (only "play"
 * may start the service in the foreground), so they reach the session through a short-lived
 * controller instead.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.takeIf { it == ACTION_NEXT || it == ACTION_PREVIOUS } ?: return
        // Service off: nothing is loaded to skip through, and binding would only start it for nothing.
        if (WidgetUpdater.liveRefresh == null) return
        val app = context.applicationContext
        val pending = goAsync()
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .buildAsync()
        future.addListener(
            {
                val controller = try {
                    future.get()
                } catch (e: Exception) {
                    Log.w(TAG, "Widget $action failed", e)
                    pending.finish()
                    return@addListener
                }
                // Releasing at once would drop the command still queued in the session: wait for the
                // track change (or a short timeout) first.
                val handler = Handler(Looper.getMainLooper())
                var done = false
                fun finish() {
                    if (done) return
                    done = true
                    handler.removeCallbacksAndMessages(null)
                    controller.release()
                    pending.finish()
                }
                controller.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = finish()
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) = finish()
                })
                handler.postDelayed(::finish, RELEASE_TIMEOUT_MS)
                if (action == ACTION_NEXT) controller.seekToNext() else controller.seekToPrevious()
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    companion object {
        const val ACTION_NEXT = "app.brano.widget.NEXT"
        const val ACTION_PREVIOUS = "app.brano.widget.PREVIOUS"
        private const val TAG = "BranoWidget"
        private const val RELEASE_TIMEOUT_MS = 2_000L
    }
}
