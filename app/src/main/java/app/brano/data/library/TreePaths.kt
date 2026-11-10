package app.brano.data.library

/**
 * File system path of a folder picked with ACTION_OPEN_DOCUMENT_TREE, from its tree document id
 * (DocumentsContract.getTreeDocumentId): "primary:Music" on the phone, "1A2B-3C4D:Music" on an SD
 * card, "raw:/path" on some devices. [primaryRoot] is this user's shared storage (another user or
 * a work profile is /storage/emulated/10, not 0). Null for providers that are not plain storage (Drive, media
 * collections...), which the library cannot match against track paths.
 */
fun treeDocIdToPath(docId: String, primaryRoot: String = "/storage/emulated/0"): String? {
    if (docId.startsWith("raw:")) return docId.removePrefix("raw:").takeIf { it.startsWith("/") }?.trimEnd('/')
    val volume = docId.substringBefore(':', missingDelimiterValue = "")
    if (volume.isEmpty()) return null
    val rel = docId.substringAfter(':').trim('/')
    val root = when {
        volume == "primary" -> primaryRoot.trimEnd('/')
        Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}").matches(volume) -> "/storage/$volume"
        else -> return null
    }
    return if (rel.isEmpty()) root else "$root/$rel"
}
