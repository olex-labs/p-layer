package app.brano.playback

import android.Manifest
import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.brano.data.library.LibraryRepository
import app.brano.data.library.Track
import app.brano.data.settings.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaybackResumeTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var controller: PlayerController
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var settings: SettingsRepository

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val inserted = mutableListOf<Uri>()
    private var originalMinDurationMs = 0L

    @Before
    fun setUp() = runBlocking {
        hilt.inject()
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.READ_MEDIA_AUDIO,
        )
        // The 8 s fixtures are below the default 30 s library filter.
        originalMinDurationMs = settings.settings.first().minDurationMs
        settings.update { it.copy(minDurationMs = 0) }
        context.contentResolver.delete( // leftovers of an interrupted run
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?", arrayOf("$PREFIX%"),
        )
        insertAudio("a1.mp3")
        insertAudio("a2.mp3")
    }

    @After
    fun tearDown() = runBlocking {
        controller.disconnect()
        context.stopService(Intent(context, PlaybackService::class.java))
        inserted.forEach { context.contentResolver.delete(it, null, null) }
        settings.update { it.copy(minDurationMs = originalMinDurationMs) }
    }

    @Test
    fun stoppedServiceResumesPausedAtSavedTrackAndPosition() = runBlocking {
        val tracks = withTimeout(20_000) {
            library.tracks.first { list -> FILES.all { f -> list.any { it.path.endsWith(f) } } }
        }.let { list -> FILES.map { f -> list.first { it.path.endsWith(f) } } }
        val first: Track = tracks[0]

        controller.connect()
        controller.play(tracks)
        withTimeout(10_000) { controller.state.first { it.isPlaying && it.current?.id == first.id } }

        controller.seekTo(3_000)
        delay(300)
        // Pause first: a playing session keeps itself in the foreground.
        controller.togglePlayPause()
        withTimeout(5_000) { controller.state.first { !it.isPlaying } }

        controller.disconnect()
        // Media3 may re-start the service on a late notification update, so repeat the stop.
        withTimeout(10_000) {
            while (serviceRunning()) {
                context.stopService(Intent(context, PlaybackService::class.java))
                delay(200)
            }
        }
        delay(500) // let the onDestroy save land

        controller.connect()
        val resumed = withTimeout(15_000) { controller.state.first { it.current != null } }

        assertEquals(first.id, resumed.current?.id)
        assertTrue("position ${resumed.positionMs}", resumed.positionMs in 2_000L..4_000L)
        assertFalse(resumed.isPlaying)
        assertEquals(tracks.map { it.id }, resumed.queue.map { it.id })
    }

    private fun insertAudio(asset: String) {
        val name = PREFIX + asset
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/BranoTest")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(collection, values)) { "insert failed for $name" }
        inserted += uri
        resolver.openOutputStream(uri)!!.use { out ->
            instrumentation.context.assets.open(asset).use { it.copyTo(out) }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    }

    @Suppress("DEPRECATION")
    private fun serviceRunning(): Boolean =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == PlaybackService::class.java.name }

    private companion object {
        const val PREFIX = "brano_test_"
        val FILES = listOf("${PREFIX}a1.mp3", "${PREFIX}a2.mp3")
    }
}
