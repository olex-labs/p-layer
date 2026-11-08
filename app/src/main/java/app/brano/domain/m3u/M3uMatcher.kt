package app.brano.domain.m3u

import app.brano.data.library.Track

data class M3uImportResult(val matched: List<Track>, val notFound: Int)

object M3uMatcher {
    fun match(entries: List<M3uEntry>, m3uDir: String?, library: List<Track>): M3uImportResult {
        val byPath = HashMap<String, Track>()
        val byName = HashMap<String, Track>()
        for (t in library) {
            byPath.putIfAbsent(t.path, t)
            byName.putIfAbsent(fileName(t.path).lowercase(), t)
        }
        val matched = mutableListOf<Track>()
        var notFound = 0
        for (e in entries) {
            val track = lookup(e, m3uDir, byPath, byName)
            if (track != null) matched += track else notFound++
        }
        return M3uImportResult(matched, notFound)
    }

    private fun lookup(
        e: M3uEntry,
        m3uDir: String?,
        byPath: Map<String, Track>,
        byName: Map<String, Track>,
    ): Track? {
        if (e.path.startsWith("/")) {
            byPath[normalize(e.path)]?.let { return it }
        } else if (m3uDir != null) {
            byPath[normalize("$m3uDir/${e.path}")]?.let { return it }
        }
        return byName[fileName(e.path).lowercase()]
    }

    private fun fileName(path: String): String = path.substringAfterLast('/')

    private fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (seg in path.replace('\\', '/').split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(seg)
            }
        }
        return "/" + out.joinToString("/")
    }
}
