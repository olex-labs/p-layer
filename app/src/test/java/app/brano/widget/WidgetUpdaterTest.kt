package app.brano.widget

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetUpdaterTest {
    private val pushed = mutableListOf<Triple<WidgetState, Bitmap?, Int>>()
    private val saved = mutableListOf<WidgetState>()
    private val coverCalls = mutableListOf<Long>()
    private val bitmapA: Bitmap = mockk()
    private val bitmapB: Bitmap = mockk()
    private var ids = intArrayOf(7)
    private val a = WidgetState("A", "x", albumId = 1L, isPlaying = true, hasTrack = true)
    private val b = WidgetState("B", "y", albumId = 2L, isPlaying = true, hasTrack = true)

    private fun TestScope.updater(
        cover: suspend (Long) -> Bitmap? = { coverCalls += it; if (it == 1L) bitmapA else bitmapB },
        push: (WidgetState, Bitmap?, Int) -> Unit = { s, bmp, bg -> pushed += Triple(s, bmp, bg) },
    ) = WidgetUpdater(
        scope = backgroundScope,
        widgetIds = { ids },
        push = push,
        save = { saved += it },
        cover = cover,
        background = { 42 },
        log = {},
    )

    @Test fun sameState_pushedOnce() = runTest(StandardTestDispatcher()) {
        val u = updater()
        u.update(a); runCurrent()
        u.update(a); runCurrent()
        assertThat(pushed).containsExactly(Triple(a, bitmapA, 42))
    }

    @Test fun noWidgets_savesButNeverRendersOrLoads() = runTest(StandardTestDispatcher()) {
        ids = IntArray(0)
        val u = updater()
        u.update(a); runCurrent()
        assertThat(saved).containsExactly(a)
        assertThat(pushed).isEmpty()
        assertThat(coverCalls).isEmpty()
    }

    @Test fun playPauseOnly_reusesCover_noReload() = runTest(StandardTestDispatcher()) {
        val u = updater()
        u.update(a); runCurrent()
        u.update(a.copy(isPlaying = false)); runCurrent()
        assertThat(pushed.map { it.second }).containsExactly(bitmapA, bitmapA)
        assertThat(coverCalls).containsExactly(1L)
    }

    @Test fun latestWins() = runTest(StandardTestDispatcher()) {
        val slow = CompletableDeferred<Bitmap?>()
        val u = updater(cover = { if (it == 1L) slow.await() else bitmapB })
        u.update(a); runCurrent()
        u.update(b); runCurrent()
        slow.complete(bitmapA); runCurrent()
        assertThat(pushed.last().first).isEqualTo(b)
        assertThat(pushed.map { it.second }).doesNotContain(bitmapA)
    }

    @Test fun quickBackAndForth_neverMixesCoverAndTrack() = runTest(StandardTestDispatcher()) {
        // X shown; skip to B whose colour is slow; back to X before B finishes: X keeps its own cover.
        val slowColour = CompletableDeferred<Int>()
        val u = WidgetUpdater(
            scope = backgroundScope,
            widgetIds = { ids },
            push = { s, bmp, bg -> pushed += Triple(s, bmp, bg) },
            save = {},
            cover = { if (it == 1L) bitmapA else bitmapB },
            background = { if (it == 2L) slowColour.await() else 42 },
            log = {},
        )
        u.update(a); runCurrent()
        u.update(b); runCurrent()
        u.update(a.copy(isPlaying = false)); runCurrent()
        assertThat(pushed.last()).isEqualTo(Triple(a.copy(isPlaying = false), bitmapA, 42))
    }

    @Test fun refresh_pushesTheSameStateAgain() = runTest(StandardTestDispatcher()) {
        // A widget added mid-playback: the service redraws it with cover and colour.
        val u = updater()
        u.update(a); runCurrent()
        u.refresh(a); runCurrent()
        assertThat(pushed.map { it.first }).containsExactly(a, a)
        assertThat(pushed.map { it.second }).containsExactly(bitmapA, bitmapA)
    }

    @Test fun failedPush_isRetriedWithTheSameState() = runTest(StandardTestDispatcher()) {
        var fail = true
        val u = updater(push = { s, bmp, bg -> if (fail) throw IllegalStateException("launcher gone") else pushed += Triple(s, bmp, bg) })
        u.update(a); runCurrent()
        fail = false
        u.update(a); runCurrent()
        assertThat(pushed).containsExactly(Triple(a, bitmapA, 42))
    }

    @Test fun coverThrows_stillPushesTheNewTrack() = runTest(StandardTestDispatcher()) {
        val u = updater(cover = { throw IllegalStateException("bad art") })
        u.update(a); runCurrent()
        assertThat(pushed).containsExactly(Triple(a, null, 42))
    }

    @Test fun coverNull_pushesWithNullBitmap() = runTest(StandardTestDispatcher()) {
        val u = updater(cover = { null })
        u.update(a); runCurrent()
        assertThat(pushed).containsExactly(Triple(a, null, 42))
    }

    @Test fun pushThrows_isSwallowed() = runTest(StandardTestDispatcher()) {
        val u = updater(push = { _, _, _ -> throw IllegalStateException("launcher gone") })
        u.update(a); runCurrent()
        u.update(b); runCurrent()
        assertThat(saved).containsExactly(a, b).inOrder()
    }
}
