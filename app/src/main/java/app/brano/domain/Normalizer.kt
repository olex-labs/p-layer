package app.brano.domain

import java.text.Normalizer as JavaNormalizer

object Normalizer {
    private val diacritics = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun normalize(s: String): String =
        JavaNormalizer.normalize(s, JavaNormalizer.Form.NFD)
            .replace(diacritics, "")
            .lowercase()
            .replace(spaces, " ")
            .trim()
}
