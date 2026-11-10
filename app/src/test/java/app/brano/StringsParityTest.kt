package app.brano

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** English (default) and Italian must have the same texts with the same placeholders. */
class StringsParityTest {
    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    /** name (plurals as name#quantity) to the sorted list of format placeholders. */
    private fun texts(dir: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$dir/strings.xml"))
        val out = sortedMapOf<String, List<String>>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> out[name] = placeholders(e.textContent)
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val item = items.item(j) as Element
                        out["$name#${item.getAttribute("quantity")}"] = placeholders(item.textContent)
                    }
                }
            }
        }
        return out
    }

    private fun placeholders(s: String) = Regex("""%(\d+\$)?[sd]""").findAll(s).map { it.value }.sorted().toList()

    @Test fun italianHasEveryEnglishText_withTheSamePlaceholders() {
        val en = texts("values")
        val it = texts("values-it")
        // Italian plurals need only "one" and "other", like English.
        assertThat(it.keys).containsExactlyElementsIn(en.keys)
        for ((name, args) in en) assertWithMessage(name).that(it[name]).isEqualTo(args)
    }

    @Test fun pluralsHaveOneAndOther() {
        val en = texts("values").keys.filter { '#' in it }
        val plurals = en.map { it.substringBefore('#') }.toSet()
        for (p in plurals) assertThat(en).containsAtLeast("$p#one", "$p#other")
    }
}
