package app.brano.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexLetterTest {
    @Test fun letters_areNormalizedUppercase() {
        assertThat(indexLetter("banana")).isEqualTo("B")
        assertThat(indexLetter("Élan")).isEqualTo("E")
    }

    @Test fun nonLettersAndNull_becomeHash() {
        assertThat(indexLetter("1989")).isEqualTo("#")
        assertThat(indexLetter("")).isEqualTo("#")
        assertThat(indexLetter(null)).isEqualTo("#")
    }
}
