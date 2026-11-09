package app.brano

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SanityTest {
    @Test
    fun arithmeticWorks() {
        assertThat(1 + 1).isEqualTo(2)
    }
}
