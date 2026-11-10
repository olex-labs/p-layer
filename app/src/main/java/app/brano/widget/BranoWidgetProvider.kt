package app.brano.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import app.brano.ui.theme.ArtworkSeedLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Draws the widget when it is added, after a reboot or when the launcher restarts. While the
 * playback service runs it redraws with the live track; otherwise this draws the saved track,
 * with its cover and colour, as paused (nothing can be playing without the service).
 */
class BranoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.liveRefresh?.let { refresh ->
            refresh()
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val saved = WidgetStateStore(app).load().copy(isPlaying = false)
        val job = WidgetUpdater.create(app, scope, ArtworkSeedLoader(app)).update(saved)
        if (job == null) {
            scope.cancel()
            pending.finish()
        } else {
            job.invokeOnCompletion {
                scope.cancel()
                pending.finish()
            }
        }
    }
}
