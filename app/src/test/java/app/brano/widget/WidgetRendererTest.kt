package app.brano.widget

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.session.MediaButtonReceiver
import androidx.test.core.app.ApplicationProvider
import app.brano.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WidgetRendererTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun playIntent_isAPlayPauseMediaButton() {
        // Media3's receiver can start the service (and resume the saved queue) only for "play".
        val intent = shadowOf(WidgetRenderer.playIntent(ctx)).savedIntent
        assertThat(intent.action).isEqualTo(Intent.ACTION_MEDIA_BUTTON)
        assertThat(intent.component?.className).isEqualTo(MediaButtonReceiver::class.java.name)
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)!!
        assertThat(event.keyCode).isEqualTo(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    @Test fun skipIntents_goToBranosOwnReceiver() {
        // Media3's receiver drops next/previous on API 26+, so they go through WidgetActionReceiver.
        for (action in listOf(WidgetActionReceiver.ACTION_NEXT, WidgetActionReceiver.ACTION_PREVIOUS)) {
            val intent = shadowOf(WidgetRenderer.skipIntent(ctx, action)).savedIntent
            assertThat(intent.action).isEqualTo(action)
            assertThat(intent.component?.className).isEqualTo(WidgetActionReceiver::class.java.name)
        }
    }

    @Test fun render_emptyState_usesAppNameCopy() {
        val v = WidgetRenderer.render(ctx, WidgetState.EMPTY, null, DEFAULT_WIDGET_BG).apply(ctx, FrameLayout(ctx))
        assertThat(v.findViewById<TextView>(R.id.widget_title).text.toString()).isEqualTo("P-layer")
        assertThat(v.findViewById<TextView>(R.id.widget_artist).text.toString()).isEqualTo("Tocca ▶ per iniziare")
    }

    @Test fun render_playing_showsPauseIcon() {
        val playing = WidgetState("T", "A", 1L, isPlaying = true, hasTrack = true)
        val v = WidgetRenderer.render(ctx, playing, null, DEFAULT_WIDGET_BG).apply(ctx, FrameLayout(ctx))
        assertThat(v.findViewById<ImageButton>(R.id.widget_play).contentDescription)
            .isEqualTo(ctx.getString(R.string.cd_widget_pause))
        val paused = WidgetRenderer.render(ctx, playing.copy(isPlaying = false), null, DEFAULT_WIDGET_BG).apply(ctx, FrameLayout(ctx))
        assertThat(paused.findViewById<ImageButton>(R.id.widget_play).contentDescription)
            .isEqualTo(ctx.getString(R.string.cd_widget_play))
    }
}
