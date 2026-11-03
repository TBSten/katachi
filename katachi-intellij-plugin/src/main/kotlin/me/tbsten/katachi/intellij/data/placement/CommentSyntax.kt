package me.tbsten.katachi.intellij.data.placement

internal sealed interface CommentSyntax {
    data object Slash : CommentSyntax

    data object Hash : CommentSyntax

    data object Html : CommentSyntax

    companion object {
        private val byExtension: Map<String, CommentSyntax> = buildMap {
            listOf("kt", "kts", "java", "gradle").forEach { put(it, Slash) }
            listOf("properties", "yaml", "yml", "toml", "sh").forEach { put(it, Hash) }
            listOf("xml", "md", "html").forEach { put(it, Html) }
        }

        fun forExtension(extension: String): CommentSyntax? = byExtension[extension.lowercase()]

        fun forFileName(fileName: String): CommentSyntax? {
            val name = fileName.substringAfterLast('/').substringAfterLast('\\')
            val dot = name.lastIndexOf('.')
            return if (dot < 0) null else forExtension(name.substring(dot + 1))
        }
    }
}
