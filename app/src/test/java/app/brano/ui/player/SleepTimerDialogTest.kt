package app.brano.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SleepTimerDialogTest {
    @Test fun parseCustomMinutes_acceptsOneToSixHundred() {
        assertThat(parseCustomMinutes("1")).isEqualTo(1)
        assertThat(parseCustomMinutes(" 600 ")).isEqualTo(600)
    }

    @Test fun parseCustomMinutes_rejectsBlankZeroAndOutOfRange() {
        assertThat(parseCustomMinutes("")).isNull()
        assertThat(parseCustomMinutes("  ")).isNull()
        assertThat(parseCustomMinutes("0")).isNull()
        assertThat(parseCustomMinutes("601")).isNull()
        assertThat(parseCustomMinutes("x")).isNull()
    }
}
