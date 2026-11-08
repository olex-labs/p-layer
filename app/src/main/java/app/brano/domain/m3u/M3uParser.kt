package app.brano.domain.m3u

import java.net.URLDecoder

data class M3uEntry(val path: String, val title: String?, val durationSec: Int?)

object M3uParser {
    fun parse(text: String): List<M3uEntry> {
        val result = mutableListOf<M3uEntry>()
        var pendingTitle: String? = null
        var pendingDuration: Int? = null
        for (raw in text.removePrefix("﻿").lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val body = line.substring("#EXTINF:".length)
                    val comma = body.indexOf(',')
                    val durText = (if (comma >= 0) body.substring(0, comma) else body).trim()
                    val dur = durText.toIntOrNull()
                    pendingDuration = if (dur != null && dur >= 0) dur else null
                    pendingTitle = if (comma >= 0) body.substring(comma + 1).trim().ifEmpty { null } else null
                }
                continue
            }
            result += M3uEntry(normalizePath(line), pendingTitle, pendingDuration)
            pendingTitle = null
            pendingDuration = null
        }
        return result
    }

    private fun normalizePath(line: String): String {
        val lower = line.lowercase()
        val decoded = when {
            lower.startsWith("file://") -> decode(line.substring(7))
            lower.startsWith("file:") -> decode(line.substring(5))
            else -> line
        }
        return decoded.replace('\\', '/')
    }

    private fun decode(s: String): String =
        try {
            URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
        } catch (e: IllegalArgumentException) {
            s
        }
}
