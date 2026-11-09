package app.brano.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverHeaderTest {
    @Test
    fun `under an hour shows minutes`() {
        assertThat(formatTotalDuration(42 * 60_000L + 29_000)).isEqualTo("42 min")
    }

    @Test
    fun `seconds round to the nearest minute`() {
        assertThat(formatTotalDuration(42 * 60_000L + 31_000)).isEqualTo("43 min")
    }

    @Test
    fun `an hour or more shows hours and padded minutes`() {
        assertThat(formatTotalDuration(65 * 60_000L)).isEqualTo("1 h 05 min")
    }

    @Test
    fun `very short lists still show one minute`() {
        assertThat(formatTotalDuration(10_000L)).isEqualTo("1 min")
        assertThat(formatTotalDuration(0L)).isEqualTo("0 min")
    }
}
