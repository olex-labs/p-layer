package app.brano.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import app.brano.ui.theme.ArtworkSeedLoader
import app.brano.ui.theme.playerColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps the home screen widget in step with playback. Pushes only when the state changes, does no
 * work when no widget is placed, and lets the latest track win when covers load out of order.
 * Failures are logged and never reach the playback service.
 */
class WidgetUpdater(
    private val scope: CoroutineScope,
    private val widgetIds: () -> IntArray,
    private val push: (WidgetState, Bitmap?, Int) -> Unit,
    private val save: (WidgetState) -> Unit,
    private val cover: suspend (Long) -> Bitmap?,
    private val background: suspend (Long) -> Int,
    private val log: (Throwable) -> Unit = { Log.w(TAG, "Widget update failed", it) },
) {
    private var last: WidgetState? = null
    private var job: Job? = null

    // Cover and colour of the album shown last, so play/pause does not reload them.
    private var shownAlbum: Long? = null
    private var shownCover: Bitmap? = null
    private var shownBackground = DEFAULT_WIDGET_BG

    /** Pushes [s] when it differs from what the widget shows. Returns the drawing job, if any. */
    fun update(s: WidgetState): Job? {
        runCatching { save(s) }.onFailure(log)
        if (s == last) return null
        val ids = runCatching { widgetIds() }.onFailure(log).getOrNull() ?: return null
        if (ids.isEmpty()) return null
        last = s
        job?.cancel()
        return scope.launch {
            val album = s.albumId
            if (album != shownAlbum) {
                // Load first, then switch all three together: a cancelled load must never leave
                // one album's cover paired with another album's id. A failed load is just "none".
                val newCover = album?.let { orNull { cover(it) } }
                val newBackground = album?.let { orNull { background(it) } } ?: DEFAULT_WIDGET_BG
                shownCover = newCover
                shownBackground = newBackground
                shownAlbum = album
            }
            try {
                push(s, shownCover, shownBackground)
            } catch (e: Exception) {
                last = null // not shown: the same state must be tried again
                log(e)
            }
        }.also { job = it }
    }

    /** Redraws [s] even if unchanged, e.g. for a widget just added to the home screen. */
    fun refresh(s: WidgetState): Job? {
        last = null
        return update(s)
    }

    private suspend fun <T> orNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        log(e)
        null
    }
    companion object {
        private const val TAG = "BranoWidget"

        /**
         * Set by the playback service while it runs: redraws every widget with the current track.
         * Null means the service is off (the widget provider then draws the saved state itself).
         */
        @Volatile var liveRefresh: (() -> Unit)? = null

        fun create(context: Context, scope: CoroutineScope, seeds: ArtworkSeedLoader): WidgetUpdater {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val provider = ComponentName(app, BranoWidgetProvider::class.java)
            val store = WidgetStateStore(app)
            return WidgetUpdater(
                scope = scope,
                widgetIds = { manager.getAppWidgetIds(provider) },
                push = { s, bmp, bg -> manager.updateAppWidget(provider, WidgetRenderer.render(app, s, bmp, bg)) },
                save = store::save,
                cover = { loadWidgetCover(app, it) },
                background = { album -> seeds.seedFor(album)?.let { playerColors(it).deep.toArgb() } ?: DEFAULT_WIDGET_BG },
            )
        }
    }
}
