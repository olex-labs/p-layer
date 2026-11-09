package app.brano.lyrics

enum class LyricsSource { CACHE, FILE, EMBEDDED, ONLINE }

/** One line; [timeMs] is null for plain (unsynced) lyrics. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: LyricsSource)

object LyricsParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
    private val metaTag = Regex("""^\s*\[[A-Za-z#][A-Za-z]*:[^\]]*]\s*$""")

    /**
     * LRC (one or more [mm:ss.xx] tags per line, [offset:ms]) or plain text. Metadata tags such as
     * [ar:] are dropped. A positive offset shows lines earlier, as in the LRC convention. Null when
     * nothing readable is left.
     */
    fun parse(text: String, source: LyricsSource): Lyrics? {
        var offset = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            offsetTag.find(line)?.let {
                offset = it.groupValues[1].toLong()
                continue
            }
            var rest = line.trimStart()
            val times = ArrayList<Long>()
            while (true) {
                val m = timeTag.matchAt(rest, 0) ?: break
                val (min, sec, frac) = m.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                times += min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
                rest = rest.substring(m.range.last + 1)
            }
            when {
                times.isNotEmpty() -> times.forEach { timed += LyricLine(it, rest.trim()) }
                metaTag.matches(line) -> Unit
                else -> plain += line.trim()
            }
        }
        if (timed.isNotEmpty()) {
            val shifted = timed.map { it.copy(timeMs = (it.timeMs!! - offset).coerceAtLeast(0)) }.sortedBy { it.timeMs }
            return Lyrics(shifted, synced = true, source = source)
        }
        val first = plain.indexOfFirst { it.isNotBlank() }
        if (first < 0) return null
        val last = plain.indexOfLast { it.isNotBlank() }
        return Lyrics(plain.subList(first, last + 1).map { LyricLine(null, it) }, synced = false, source = source)
    }

    /** Index of the last line whose time is <= [positionMs], or -1 before the first one. */
    fun currentLine(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val t = lines[mid].timeMs ?: return -1
            if (t <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }
}
