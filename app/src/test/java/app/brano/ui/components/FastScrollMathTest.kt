package app.brano.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FastScrollMathTest {
    @Test fun thumbAtTopIsFirstItemAndAtBottomIsLast() {
        assertThat(fastScrollIndex(thumbTop = 0f, range = 1000f, itemCount = 101)).isEqualTo(0)
        assertThat(fastScrollIndex(thumbTop = 1000f, range = 1000f, itemCount = 101)).isEqualTo(100)
    }

    @Test fun thumbInTheMiddleIsTheMiddleItem() {
        assertThat(fastScrollIndex(thumbTop = 500f, range = 1000f, itemCount = 101)).isEqualTo(50)
    }

    @Test fun outOfRangeIsClamped() {
        assertThat(fastScrollIndex(thumbTop = -40f, range = 1000f, itemCount = 10)).isEqualTo(0)
        assertThat(fastScrollIndex(thumbTop = 4000f, range = 1000f, itemCount = 10)).isEqualTo(9)
        assertThat(fastScrollIndex(thumbTop = 10f, range = 0f, itemCount = 10)).isEqualTo(0)
    }

    @Test fun listPositionMapsBackToTheThumb() {
        assertThat(fastScrollFraction(firstVisible = 0, indexOffset = 1, itemCount = 11)).isEqualTo(0f)
        assertThat(fastScrollFraction(firstVisible = 6, indexOffset = 1, itemCount = 11)).isEqualTo(0.5f)
        assertThat(fastScrollFraction(firstVisible = 99, indexOffset = 1, itemCount = 11)).isEqualTo(1f)
    }
}
