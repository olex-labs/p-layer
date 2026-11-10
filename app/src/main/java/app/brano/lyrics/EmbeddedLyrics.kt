package app.brano.lyrics

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Lyrics stored inside the audio file, read without any tag library: ID3v2.3/2.4 (MP3) frames SYLT
 * (synchronised, returned as LRC text) and USLT, or FLAC Vorbis comments LYRICS / UNSYNCEDLYRICS.
 * Only the tag is read, never the audio.
 */
object EmbeddedLyrics {
    /** ID3 tags bigger than this (huge cover art) are not read. */
    private const val MAX_TAG_BYTES = 16 * 1024 * 1024

    fun read(stream: InputStream): String? = try {
        val input = DataInputStream(stream.buffered())
        val magic = ByteArray(4)
        input.readFully(magic, 0, 3)
        when {
            magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte() -> readId3(input)
            magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() && magic[2] == 'a'.code.toByte() &&
                input.readByte() == 'C'.code.toByte() -> readFlac(input)
            else -> null
        }
    } catch (e: EOFException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        // Last line of defence: a malformed tag means "no lyrics", never a crash.
        null
    } catch (e: NegativeArraySizeException) {
        null
    }

    // ---- ID3v2

    private fun readId3(input: DataInputStream): String? {
        val version = input.readUnsignedByte()
        input.readUnsignedByte() // revision
        val flags = input.readUnsignedByte()
        val size = synchsafe(input.readInt())
        if (version !in 3..4 || size <= 0 || size > MAX_TAG_BYTES) return null
        val tag = ByteArray(size)
        input.readFully(tag)
        var pos = 0
        if (flags and 0x40 != 0) {
            if (size < 4) return null
            val ext = (be32(tag, 0).toLong() and 0xFFFFFFFFL).let { if (version == 4) synchsafe(it.toInt()).toLong() else it + 4 }
            if (ext > size) return null
            pos = ext.toInt()
        }
        var uslt: String? = null
        var sylt: String? = null
        while (pos + 10 <= size) {
            if (tag[pos] == 0.toByte()) break // padding
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            val frameSize = if (version == 4) synchsafe(be32(tag, pos + 4)).toLong() else be32(tag, pos + 4).toLong() and 0xFFFFFFFFL
            val start = pos + 10
            if (frameSize <= 0 || start + frameSize > size) break
            val end = start + frameSize.toInt()
            when (id) {
                "USLT" -> if (uslt == null) uslt = usltText(tag, start, end)
                "SYLT" -> if (sylt == null) sylt = syltAsLrc(tag, start, end)
            }
            pos = end
        }
        return sylt?.takeIf { it.isNotBlank() } ?: uslt?.takeIf { it.isNotBlank() }
    }

    /** USLT: encoding, language (3), description (terminated), text. */
    private fun usltText(b: ByteArray, from: Int, to: Int): String? {
        if (to - from < 5) return null
        val enc = b[from].toInt()
        val textStart = afterTerminator(b, from + 4, to, enc)
        return decode(b, textStart, to, enc).trimEnd('\u0000').trim()
    }

    /** SYLT with millisecond timestamps: every (text, time) pair becomes an LRC line. */
    private fun syltAsLrc(b: ByteArray, from: Int, to: Int): String? {
        if (to - from < 7) return null
        val enc = b[from].toInt()
        val timestampFormat = b[from + 4].toInt()
        if (timestampFormat != 2) return null // MPEG frames: not supported
        var pos = afterTerminator(b, from + 6, to, enc)
        val out = StringBuilder()
        while (pos < to) {
            val end = afterTerminator(b, pos, to, enc)
            val text = decode(b, pos, (end - terminatorLength(enc)).coerceAtLeast(pos), enc).trim('\u0000', '\n', '\r', ' ')
            if (end + 4 > to) break
            val ms = be32(b, end).toLong() and 0xFFFFFFFFL
            out.append('[').append(lrcTime(ms)).append(']').append(text).append('\n')
            pos = end + 4
        }
        return out.toString().ifBlank { null }
    }

    private fun lrcTime(ms: Long): String {
        val min = ms / 60_000
        val sec = ms / 1_000 % 60
        val milli = ms % 1_000
        return "%02d:%02d.%03d".format(java.util.Locale.ROOT, min, sec, milli)
    }

    private fun terminatorLength(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    /** Index right after the string terminator starting at [from] (end of data when none). */
    private fun afterTerminator(b: ByteArray, from: Int, to: Int, enc: Int): Int {
        if (terminatorLength(enc) == 1) {
            var i = from
            while (i < to && b[i] != 0.toByte()) i++
            return (i + 1).coerceAtMost(to)
        }
        var i = from
        while (i + 1 < to && !(b[i] == 0.toByte() && b[i + 1] == 0.toByte())) i += 2
        return (i + 2).coerceAtMost(to)
    }

    private fun decode(b: ByteArray, from: Int, to: Int, enc: Int): String {
        if (to <= from) return ""
        return when (enc) {
            0 -> String(b, from, to - from, Charsets.ISO_8859_1)
            1 -> when {
                to - from >= 2 && b[from] == 0xFF.toByte() && b[from + 1] == 0xFE.toByte() ->
                    String(b, from + 2, to - from - 2, Charsets.UTF_16LE)
                to - from >= 2 && b[from] == 0xFE.toByte() && b[from + 1] == 0xFF.toByte() ->
                    String(b, from + 2, to - from - 2, Charsets.UTF_16BE)
                else -> String(b, from, to - from, Charsets.UTF_16LE)
            }
            2 -> String(b, from, to - from, Charsets.UTF_16BE)
            else -> String(b, from, to - from, Charsets.UTF_8)
        }
    }

    private fun be32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF shl 24) or (b[at + 1].toInt() and 0xFF shl 16) or
            (b[at + 2].toInt() and 0xFF shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun synchsafe(v: Int): Int =
        (v shr 24 and 0x7F shl 21) or (v shr 16 and 0x7F shl 14) or (v shr 8 and 0x7F shl 7) or (v and 0x7F)

    // ---- FLAC

    private fun readFlac(input: DataInputStream): String? {
        while (true) {
            val header = input.readUnsignedByte()
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val length = (input.readUnsignedByte() shl 16) or (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
            if (type == 4) {
                val block = ByteArray(length)
                input.readFully(block)
                return vorbisLyrics(block)
            }
            var left = length.toLong()
            while (left > 0) {
                val skipped = input.skip(left)
                if (skipped <= 0) {
                    input.readByte()
                    left--
                } else {
                    left -= skipped
                }
            }
            if (last) return null
        }
    }

    private fun vorbisLyrics(b: ByteArray): String? {
        fun le32(at: Int) = (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8) or
            (b[at + 2].toInt() and 0xFF shl 16) or (b[at + 3].toInt() and 0xFF shl 24)
        if (b.size < 8) return null
        val vendor = le32(0).toLong() and 0xFFFFFFFFL
        if (4 + vendor + 4 > b.size) return null
        var pos = 4 + vendor.toInt()
        val count = le32(pos).toLong() and 0xFFFFFFFFL
        pos += 4
        val found = HashMap<String, String>()
        var left = count
        // Stops when the data runs out, whatever count claims.
        while (left-- > 0 && pos + 4 <= b.size) {
            val len = le32(pos).toLong() and 0xFFFFFFFFL
            pos += 4
            if (pos + len > b.size) break
            val entry = String(b, pos, len.toInt(), Charset.forName("UTF-8"))
            pos += len.toInt()
            val key = entry.substringBefore('=', "").uppercase(java.util.Locale.ROOT)
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS") found.putIfAbsent(key, entry.substringAfter('='))
        }
        return (found["LYRICS"] ?: found["UNSYNCEDLYRICS"])?.trim()?.ifBlank { null }
    }
}
