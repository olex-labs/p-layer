package app.brano.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver
import app.brano.MainActivity
import app.brano.R

/** Lilac-black of the app icon: the widget background when there is no album colour. */
const val DEFAULT_WIDGET_BG = 0xFF14101F.toInt()

object WidgetRenderer {

    fun render(context: Context, s: WidgetState, cover: Bitmap?, background: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_bar).apply {
            setInt(R.id.widget_bg, "setColorFilter", background)

            if (s.hasTrack) {
                setTextViewText(R.id.widget_title, s.title)
                setTextViewText(R.id.widget_artist, s.artist)
            } else {
                setTextViewText(R.id.widget_title, context.getString(R.string.widget_empty_title))
                setTextViewText(R.id.widget_artist, context.getString(R.string.widget_empty_subtitle))
            }

            if (cover != null) {
                setImageViewBitmap(R.id.widget_cover, cover)
                setViewPadding(R.id.widget_cover, 0, 0, 0, 0)
            } else {
                setImageViewResource(R.id.widget_cover, R.drawable.ic_widget_music_note)
                val pad = (12 * context.resources.displayMetrics.density).toInt()
                setViewPadding(R.id.widget_cover, pad, pad, pad, pad)
            }

            setImageViewResource(R.id.widget_play, if (s.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            setContentDescription(
                R.id.widget_play,
                context.getString(if (s.isPlaying) R.string.cd_widget_pause else R.string.cd_widget_play),
            )

            setOnClickPendingIntent(R.id.widget_prev, buttonIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            setOnClickPendingIntent(R.id.widget_play, buttonIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            setOnClickPendingIntent(R.id.widget_next, buttonIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_cover, open)
            setOnClickPendingIntent(R.id.widget_text, open)
        }

    /** Same path as a headset button: Media3 starts the service if needed and forwards the key. */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun buttonIntent(context: Context, keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return PendingIntent.getBroadcast(context, keyCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
