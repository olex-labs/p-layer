package app.brano.domain.m3u

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class M3uParserTest {
    @Test fun parse_bomCrlfBackslashes() {
        val e = M3uParser.parse("﻿#EXTM3U\r\n#EXTINF:210,A - B\r\nMusic\\Rock\\b c.mp3\r\n\r\n")
        assertThat(e).containsExactly(M3uEntry("Music/Rock/b c.mp3", "A - B", 210))
    }

    @Test fun parse_fileUri() =
        assertThat(M3uParser.parse("file:///sdcard/Music/a%20b.mp3").single().path).isEqualTo("/sdcard/Music/a b.mp3")

    @Test fun parse_fileUriPreservesPlus() =
        assertThat(M3uParser.parse("file:///sdcard/a+b.mp3").single().path).isEqualTo("/sdcard/a+b.mp3")

    @Test fun parse_plainPathNotPercentDecoded() =
        assertThat(M3uParser.parse("/sdcard/100%25.mp3").single().path).isEqualTo("/sdcard/100%25.mp3")

    @Test fun parse_extinfAppliesOnlyToNextPath() {
        val e = M3uParser.parse("#EXTINF:10,T\na.mp3\nb.mp3\n")
        assertThat(e).containsExactly(M3uEntry("a.mp3", "T", 10), M3uEntry("b.mp3", null, null)).inOrder()
    }

    @Test fun parse_negativeOrBadDurationIsNull() {
        val e = M3uParser.parse("#EXTINF:-1,T\na.mp3\n#EXTINF:abc,U\nb.mp3\n# comment\n")
        assertThat(e.map { it.durationSec }).containsExactly(null, null)
        assertThat(e.map { it.title }).containsExactly("T", "U").inOrder()
    }

    @Test fun parse_titleKeepsCommas() =
        assertThat(M3uParser.parse("#EXTINF:10,Foo, Bar - Baz\na.mp3").single().title).isEqualTo("Foo, Bar - Baz")

    @Test fun parse_invalidPercentEscapeLeftUndecoded() =
        assertThat(M3uParser.parse("file:///a%zz.mp3").single().path).isEqualTo("/a%zz.mp3")
}
