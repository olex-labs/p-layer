package app.brano.lyrics

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Test

class EmbeddedLyricsTest {
    private fun synchsafe(n: Int) = byteArrayOf((n shr 21 and 0x7F).toByte(), (n shr 14 and 0x7F).toByte(), (n shr 7 and 0x7F).toByte(), (n and 0x7F).toByte())
    private fun be32(n: Int) = byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())

    private fun id3(version: Int, vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = ByteArrayOutputStream()
        for ((id, data) in frames) {
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(if (version == 4) synchsafe(data.size) else be32(data.size))
            body.write(byteArrayOf(0, 0))
            body.write(data)
        }
        val b = body.toByteArray()
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), version.toByte(), 0, 0) + synchsafe(b.size) + b +
            ByteArray(64) // audio follows
    }

    @Test fun id3v23_usltLatin1() {
        val uslt = byteArrayOf(0) + "ita".toByteArray() + byteArrayOf(0) + "Caffè a mezzanotte".toByteArray(Charsets.ISO_8859_1)
        assertThat(EmbeddedLyrics.read(id3(3, "TIT2" to byteArrayOf(0, 65), "USLT" to uslt).inputStream()))
            .isEqualTo("Caffè a mezzanotte")
    }

    @Test fun id3v24_usltUtf16WithBom() {
        val text = "Notte sul Navigli\nseconda riga"
        val uslt = byteArrayOf(1) + "ita".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) +
            text.toByteArray(Charsets.UTF_16LE).let { byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + it }
        assertThat(EmbeddedLyrics.read(id3(4, "USLT" to uslt).inputStream())).isEqualTo(text)
    }

    @Test fun sylt_becomesLrcText() {
        val entries = ByteArrayOutputStream().apply {
            write("Uno".toByteArray()); write(0); write(be32(12_400))
            write("Due".toByteArray()); write(0); write(be32(62_345))
        }.toByteArray()
        val sylt = byteArrayOf(0) + "ita".toByteArray() + byteArrayOf(2, 1, 0) + entries
        val text = EmbeddedLyrics.read(id3(3, "SYLT" to sylt, "USLT" to (byteArrayOf(0) + "ita".toByteArray() + byteArrayOf(0) + "plain".toByteArray())).inputStream())!!
        val parsed = LyricsParser.parse(text, LyricsSource.EMBEDDED)!!
        assertThat(parsed.synced).isTrue()
        assertThat(parsed.lines.map { it.timeMs to it.text }).containsExactly(12_400L to "Uno", 62_345L to "Due").inOrder()
    }

    @Test fun flacVorbisLyrics() {
        val comments = listOf("TITLE=Brano", "lyrics=[00:01.00]Ciao")
        val vc = ByteArrayOutputStream().apply {
            val vendor = "test".toByteArray(); write(le32(vendor.size)); write(vendor)
            write(le32(comments.size))
            comments.forEach { c -> val b = c.toByteArray(); write(le32(b.size)); write(b) }
        }.toByteArray()
        val streamInfo = ByteArray(34)
        val flac = "fLaC".toByteArray() +
            byteArrayOf(0) + byteArrayOf(0, 0, 34) + streamInfo +
            byteArrayOf((0x80 or 4).toByte()) + byteArrayOf((vc.size shr 16).toByte(), (vc.size shr 8).toByte(), vc.size.toByte()) + vc
        assertThat(EmbeddedLyrics.read(flac.inputStream())).isEqualTo("[00:01.00]Ciao")
    }

    @Test fun garbage_isNull() {
        assertThat(EmbeddedLyrics.read(ByteArray(100) { it.toByte() }.inputStream())).isNull()
        assertThat(EmbeddedLyrics.read(id3(3, "TIT2" to byteArrayOf(0, 65)).inputStream())).isNull()
    }

    /** Hostile or truncated tags must give null, never an exception. */
    @Test fun hostileBytes_neverThrow() {
        val cases = listOf(
            // ID3 with the extended-header flag and a tag too small for it.
            byteArrayOf(73, 68, 51, 3, 0, 0x40, 0, 0, 0, 2, 1, 2),
            // v2.3 extended header size 0xFFFFFFF0.
            byteArrayOf(73, 68, 51, 3, 0, 0x40, 0, 0, 0, 20) + byteArrayOf(-1, -1, -1, -16) + ByteArray(16),
            // v2.3 frame size near Int.MAX_VALUE.
            byteArrayOf(73, 68, 51, 3, 0, 0, 0, 0, 0, 20) + "USLT".toByteArray() + byteArrayOf(0x7F, -1, -1, -8, 0, 0) + ByteArray(10),
            // FLAC Vorbis block shorter than 4 bytes.
            "fLaC".toByteArray() + byteArrayOf((0x80 or 4).toByte(), 0, 0, 2, 1, 2),
            // FLAC Vorbis block with a negative vendor length.
            "fLaC".toByteArray() + byteArrayOf((0x80 or 4).toByte(), 0, 0, 8) + byteArrayOf(-1, -1, -1, -1, 0, 0, 0, 0),
            // FLAC entry whose length overflows.
            "fLaC".toByteArray() + byteArrayOf((0x80 or 4).toByte(), 0, 0, 12) + le32(0) + le32(1) + byteArrayOf(-1, -1, -1, 0x7F),
        )
        cases.forEach { bytes -> assertThat(EmbeddedLyrics.read(bytes.inputStream())).isNull() }
    }

    @Test fun hugeCommentCount_returnsQuickly() {
        val bytes = "fLaC".toByteArray() + byteArrayOf((0x80 or 4).toByte(), 0, 0, 8) + le32(0) + le32(Int.MAX_VALUE)
        val start = System.nanoTime()
        assertThat(EmbeddedLyrics.read(bytes.inputStream())).isNull()
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(200L)
    }
}
