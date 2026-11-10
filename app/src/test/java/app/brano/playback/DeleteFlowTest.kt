package app.brano.playback

import app.brano.data.library.Track
import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteFlowTest {
    private val a = t(1)
    private val b = t(2)
    private val deleted = mutableListOf<List<Track>>()

    private fun TestScope.flow(
        trash: Boolean,
        outcomes: MutableMap<Long, MutableList<DeleteFlow.Outcome<String>>> = mutableMapOf(),
    ): Pair<DeleteFlow<String>, MutableList<String>> {
        val requests = mutableListOf<String>()
        val f = DeleteFlow<String>(
            scope = backgroundScope,
            trash = trash,
            trashRequest = { tracks -> "trash:" + tracks.joinToString { it.id.toString() } },
            deleteFile = { track ->
                outcomes[track.id]?.removeFirstOrNull()?.let { if (it is DeleteFlow.Outcome.Boom) throw SecurityException("no") else it }
                    ?: DeleteFlow.Outcome.Deleted
            },
            onDeleted = { deleted += it },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.requests.toList(requests) }
        return f to requests
    }

    @Test fun trash_oneSystemDialog_thenReportsEveryTrack() = runTest(UnconfinedTestDispatcher()) {
        val (f, requests) = flow(trash = true)
        f.start(listOf(a, b))
        assertThat(requests).containsExactly("trash:1, 2")
        f.onResult(ok = true)
        assertThat(deleted).containsExactly(listOf(a, b))
    }

    @Test fun trash_refused_reportsNothing() = runTest(UnconfinedTestDispatcher()) {
        val (f, _) = flow(trash = true)
        f.start(listOf(a))
        f.onResult(ok = false)
        assertThat(deleted).isEmpty()
    }

    @Test fun android10_deletesDirectlyWhenAllowed() = runTest(UnconfinedTestDispatcher()) {
        val (f, requests) = flow(trash = false)
        f.start(listOf(a, b))
        assertThat(requests).isEmpty()
        assertThat(deleted).containsExactly(listOf(a, b))
    }

    @Test fun android10_asksForOneFile_retriesAfterOk_andContinues() = runTest(UnconfinedTestDispatcher()) {
        val (f, requests) = flow(trash = false, outcomes = mutableMapOf(1L to mutableListOf(DeleteFlow.Outcome.NeedsUser("ask:1"))))
        f.start(listOf(a, b))
        assertThat(requests).containsExactly("ask:1")
        assertThat(deleted).isEmpty()
        f.onResult(ok = true)
        assertThat(deleted).containsExactly(listOf(a, b))
    }

    @Test fun android10_retryThatFailsAgain_doesNotCrash_andKeepsGoing() = runTest(UnconfinedTestDispatcher()) {
        val (f, _) = flow(
            trash = false,
            outcomes = mutableMapOf(1L to mutableListOf(DeleteFlow.Outcome.NeedsUser("ask:1"), DeleteFlow.Outcome.Boom)),
        )
        f.start(listOf(a, b))
        f.onResult(ok = true)
        assertThat(deleted).containsExactly(listOf(b))
    }

    @Test fun android10_refused_stopsAndReportsWhatWasDone() = runTest(UnconfinedTestDispatcher()) {
        val (f, _) = flow(trash = false, outcomes = mutableMapOf(2L to mutableListOf(DeleteFlow.Outcome.NeedsUser("ask:2"))))
        f.start(listOf(a, b))
        f.onResult(ok = false)
        assertThat(deleted).containsExactly(listOf(a))
    }
}
