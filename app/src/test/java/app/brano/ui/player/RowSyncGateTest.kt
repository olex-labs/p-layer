package app.brano.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RowSyncGateTest {
    private val old = listOf(1, 2, 3)
    private val moved = listOf(2, 1, 3)

    @Test fun idle_appliesQueue() {
        assertThat(RowSyncGate<List<Int>>().shouldApply(old, dragging = false)).isTrue()
    }

    @Test fun dropThenStaleQueue_isIgnoredUntilQueueChanges() {
        val gate = RowSyncGate<List<Int>>()
        gate.dropped(old)
        assertThat(gate.shouldApply(old, dragging = false)).isFalse() // dragging flag cleared, queue still stale
        assertThat(gate.shouldApply(old, dragging = false)).isFalse()
        assertThat(gate.shouldApply(moved, dragging = false)).isTrue()
        assertThat(gate.shouldApply(moved, dragging = false)).isTrue() // later emissions flow normally
    }

    @Test fun midDragChange_isAppliedAfterDrop() {
        val gate = RowSyncGate<List<Int>>()
        assertThat(gate.shouldApply(listOf(1, 2), dragging = true)).isFalse()
        gate.dropped(old)
        assertThat(gate.shouldApply(listOf(1, 2), dragging = false)).isTrue()
    }
}
