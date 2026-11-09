package app.brano.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeSeedTest {
    @Test
    fun `album seed wins when dynamic is on`() {
        assertThat(resolveSeed(true, Color.Red, true)).isEqualTo(Color.Red)
    }

    @Test
    fun `no music falls back to lilac`() {
        assertThat(resolveSeed(true, null, true)).isEqualTo(LilacSeed)
    }

    @Test
    fun `dynamic off keeps system scheme on Android 12+`() {
        assertThat(resolveSeed(false, Color.Red, true)).isNull()
    }

    @Test
    fun `dynamic off without system dynamic uses lilac`() {
        assertThat(resolveSeed(false, Color.Red, false)).isEqualTo(LilacSeed)
    }
}
