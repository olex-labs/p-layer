package app.brano.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SidecarLyricsTest {
    @Test fun fileInTheChosenFolder() {
        assertThat(sidecarDocId("primary:Music", "/storage/emulated/0/Music", "/storage/emulated/0/Music", "/storage/emulated/0/Music/Uno.mp3"))
            .isEqualTo("primary:Music/Uno.lrc")
    }

    @Test fun fileInASubfolder() {
        assertThat(sidecarDocId("primary:Music", "/storage/emulated/0/Music", "/storage/emulated/0/Music/Album", "/storage/emulated/0/Music/Album/Due.flac"))
            .isEqualTo("primary:Music/Album/Due.lrc")
    }

    @Test fun rootOfTheVolume() {
        assertThat(sidecarDocId("primary:", "/storage/emulated/0", "/storage/emulated/0/Music", "/storage/emulated/0/Music/Tre.mp3"))
            .isEqualTo("primary:Music/Tre.lrc")
    }
}
