package app.brano.lyrics

import app.brano.data.library.t
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val track = t(7)
    private val calls = mutableListOf<String>()
    private var now = 1_000_000_000L

    private fun repo(
        sidecar: String? = null,
        embedded: String? = null,
        online: LrclibClient.Result = LrclibClient.Result.NotFound,
        enabled: Boolean = true,
    ) = LyricsRepository(
        cacheDir = tmp.root,
        sidecar = { calls += "file"; sidecar },
        embedded = { calls += "embedded"; embedded },
        online = { calls += "online"; online },
        onlineEnabled = { enabled },
        now = { now },
    )

    @Test fun sidecarFirst() = runTest {
        val l = repo(sidecar = "[00:01.00]file", embedded = "emb").load(track)!!
        assertThat(l.source).isEqualTo(LyricsSource.FILE)
        assertThat(calls).containsExactly("file")
    }

    @Test fun thenEmbedded() = runTest {
        val l = repo(embedded = "plain text").load(track)!!
        assertThat(l.source).isEqualTo(LyricsSource.EMBEDDED)
        assertThat(l.synced).isFalse()
        assertThat(calls).containsExactly("file", "embedded").inOrder()
    }

    @Test fun onlineSkipped_whenDisabled() = runTest {
        assertThat(repo(enabled = false).load(track)).isNull()
        assertThat(calls).doesNotContain("online")
    }

    @Test fun online_writesCache_nextTimeFromCache() = runTest {
        val l = repo(online = LrclibClient.Result.Found("[00:02.00]rete")).load(track)!!
        assertThat(l.source).isEqualTo(LyricsSource.ONLINE)
        assertThat(File(tmp.root, "7.lrc").readText()).isEqualTo("[00:02.00]rete")
        calls.clear()
        val again = repo().load(track)!!
        assertThat(again.source).isEqualTo(LyricsSource.CACHE)
        assertThat(calls).isEmpty()
    }

    @Test fun recentMiss_skipsOnline_thenRetriesAfterAWeek() = runTest {
        repo(online = LrclibClient.Result.NotFound).load(track)
        calls.clear()
        repo().load(track)
        assertThat(calls).doesNotContain("online")
        now += 8L * 24 * 60 * 60 * 1000
        calls.clear()
        repo().load(track)
        assertThat(calls).contains("online")
    }

    @Test fun failedOnline_isNotRemembered() = runTest {
        repo(online = LrclibClient.Result.Failed).load(track)
        calls.clear()
        repo().load(track)
        assertThat(calls).contains("online")
    }
}
