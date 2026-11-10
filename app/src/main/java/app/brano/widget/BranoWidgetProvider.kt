package app.brano.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Draws the widget when it is added or the launcher restarts, from the last saved state.
 * While music plays, the playback service keeps it current (WidgetUpdater).
 */
class BranoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = WidgetRenderer.render(context, WidgetStateStore(context).load(), cover = null, DEFAULT_WIDGET_BG)
        manager.updateAppWidget(appWidgetIds, views)
    }
}
