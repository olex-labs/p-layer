package app.brano.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TreePathsTest {
    @Test fun primaryVolume() {
        assertThat(treeDocIdToPath("primary:Music/A")).isEqualTo("/storage/emulated/0/Music/A")
        assertThat(treeDocIdToPath("primary:")).isEqualTo("/storage/emulated/0")
    }

    @Test fun sdCard() {
        assertThat(treeDocIdToPath("1A2B-3C4D:Music")).isEqualTo("/storage/1A2B-3C4D/Music")
    }

    @Test fun rawPath() {
        assertThat(treeDocIdToPath("raw:/storage/emulated/0/Download")).isEqualTo("/storage/emulated/0/Download")
    }

    @Test fun unknownProviders_areRejected() {
        assertThat(treeDocIdToPath("msf:1234")).isNull()
        assertThat(treeDocIdToPath("home:Docs")).isNull()
        assertThat(treeDocIdToPath("")).isNull()
    }
}
