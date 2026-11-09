package app.brano.playback

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackErrorsTest {
    @Test
    fun titleRoundTripsThroughTheCommandArgs() {
        assertThat(PlaybackErrors.titleOf(PlaybackErrors.args("Volare"))).isEqualTo("Volare")
    }

    @Test
    fun missingTitleFallsBackToTheGivenName() {
        assertThat(PlaybackErrors.titleOf(PlaybackErrors.args(null), fallback = "ERROR_CODE_IO_FILE_NOT_FOUND"))
            .isEqualTo("ERROR_CODE_IO_FILE_NOT_FOUND")
        assertThat(PlaybackErrors.titleOf(Bundle.EMPTY, fallback = "?")).isEqualTo("?")
    }
}
