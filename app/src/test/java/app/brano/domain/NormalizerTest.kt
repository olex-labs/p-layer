package app.brano.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NormalizerTest {
    @Test fun normalize_removesAccentsAndCase() =
        assertThat(Normalizer.normalize("  Perché   NO ")).isEqualTo("perche no")

    @Test fun normalize_emptyStaysEmpty() =
        assertThat(Normalizer.normalize("   ")).isEmpty()
}
