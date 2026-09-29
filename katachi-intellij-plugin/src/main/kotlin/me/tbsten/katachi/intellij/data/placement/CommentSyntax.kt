package me.tbsten.katachi.intellij.data.placement

/**
 * How a file type writes a comment: the one table [EmptyFileRule] (what counts as empty) and
 * [ProvisionalContent] (what is written into a file before generation finishes) share.
 *
 * ```
 * CommentSyntax.forFileName("Home.kt")     // Slash
 * CommentSyntax.forFileName("app.json")    // null: JSON has no comment
 * ```
 */
internal sealed interface CommentSyntax {
    /** `//` lines and `/* */` ranges (.kt, .kts, .java, .gradle). */
    data object Slash : CommentSyntax

    /** `#` lines (.properties, .yaml, .yml, .toml, .sh). */
    data object Hash : CommentSyntax

    /** `<!-- -->` ranges (.xml, .md, .html). */
    data object Html : CommentSyntax

    companion object {
        private val byExtension: Map<String, CommentSyntax> = buildMap {
            listOf("kt", "kts", "java", "gradle").forEach { put(it, Slash) }
            listOf("properties", "yaml", "yml", "toml", "sh").forEach { put(it, Hash) }
            listOf("xml", "md", "html").forEach { put(it, Html) }
        }

        /** The syntax of [extension] (no dot, any case); `null` for `json` and unknown ones: no comment can be written. */
        fun forExtension(extension: String): CommentSyntax? = byExtension[extension.lowercase()]

        /** The syntax of [fileName]'s extension (the text after its last dot); `null` when it has none. */
        fun forFileName(fileName: String): CommentSyntax? {
            val name = fileName.substringAfterLast('/').substringAfterLast('\\')
            val dot = name.lastIndexOf('.')
            return if (dot < 0) null else forExtension(name.substring(dot + 1))
        }
    }
}
