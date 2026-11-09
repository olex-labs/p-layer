package app.brano.ui.icons

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppIconsTest {
    @Test
    fun `every icon comes from the Brano set and builds`() {
        assertThat(AppIcons.all).hasSize(32)
        AppIcons.all.forEach { icon ->
            assertThat(icon.name).startsWith("Brano.")
            assertThat(icon.viewportWidth).isEqualTo(24f)
            assertThat(icon.root.size).isGreaterThan(0)
        }
    }

    @Test
    fun `only the back arrow mirrors in right-to-left layouts`() {
        assertThat(AppIcons.all.filter { it.autoMirror }.map { it.name }).containsExactly("Brano.Back")
    }
}
