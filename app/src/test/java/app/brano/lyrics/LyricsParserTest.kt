package app.brano.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LyricsParserTest {
    private fun parse(text: String) = LyricsParser.parse(text, LyricsSource.FILE)!!

    @Test fun twoAndThreeDigitFractions() {
        val l = parse("[00:12.40] Uno\n[01:02.345]Due")
        assertThat(l.synced).isTrue()
        assertThat(l.lines.map { it.timeMs }).containsExactly(12_400L, 62_345L).inOrder()
        assertThat(l.lines.map { it.text }).containsExactly("Uno", "Due").inOrder()
    }

    @Test fun severalTagsOnOneLine_areSortedByTime() {
        val l = parse("[00:30.00][00:10.00]Ritornello\n[00:20.00]Strofa")
        assertThat(l.lines.map { it.timeMs to it.text })
            .containsExactly(10_000L to "Ritornello", 20_000L to "Strofa", 30_000L to "Ritornello").inOrder()
    }

    @Test fun positiveOffset_showsLinesEarlier() {
        val l = parse("[offset:500]\n[00:10.00]Uno")
        assertThat(l.lines.single().timeMs).isEqualTo(9_500L)
    }

    @Test fun metadataTagsAreIgnored() {
        val l = parse("[ar:Artista]\n[ti:Titolo]\n[length:03:00]\n[00:01.00]Uno")
        assertThat(l.lines.map { it.text }).containsExactly("Uno")
    }

    @Test fun plainText_isNotSynced() {
        val l = parse("Prima riga\n\nSeconda riga\n")
        assertThat(l.synced).isFalse()
        assertThat(l.lines.map { it.text }).containsExactly("Prima riga", "", "Seconda riga").inOrder()
        assertThat(l.lines.all { it.timeMs == null }).isTrue()
    }

    @Test fun blankText_isNull() {
        assertThat(LyricsParser.parse("  \n ", LyricsSource.FILE)).isNull()
        assertThat(LyricsParser.parse("[ar:x]\n[ti:y]", LyricsSource.FILE)).isNull()
    }

    @Test fun currentLine_beforeExactBetweenAfter() {
        val lines = parse("[00:10.00]a\n[00:20.00]b\n[00:30.00]c").lines
        assertThat(LyricsParser.currentLine(lines, 5_000)).isEqualTo(-1)
        assertThat(LyricsParser.currentLine(lines, 20_000)).isEqualTo(1)
        assertThat(LyricsParser.currentLine(lines, 25_000)).isEqualTo(1)
        assertThat(LyricsParser.currentLine(lines, 99_000)).isEqualTo(2)
    }
}
