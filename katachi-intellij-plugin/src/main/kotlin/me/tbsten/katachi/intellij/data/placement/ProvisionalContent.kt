package me.tbsten.katachi.intellij.data.placement

internal object ProvisionalContent {
    fun of(fileName: String, command: String, notice: String, packageName: String? = null): String {
        val syntax = CommentSyntax.forFileName(fileName) ?: return ""
        val out = StringBuilder()
        packageLineOf(fileName, packageName)?.let { out.append(it).append("\n\n") }
        for (line in linesOf(notice) + linesOf(command)) out.append(commentLine(syntax, line)).append('\n')
        return out.toString()
    }

    fun isStillProvisional(current: CharSequence, provisional: String): Boolean = current.contentEquals(provisional)

    private fun packageLineOf(fileName: String, packageName: String?): String? {
        if (packageName == null || !PACKAGE_NAME.matches(packageName)) return null
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "kt" -> "package $packageName"
            "java" -> "package $packageName;"
            else -> null
        }
    }

    private fun linesOf(text: String): List<String> = text.split(LINE_BREAK)

    private fun commentLine(syntax: CommentSyntax, line: String): String = when (syntax) {
        // A line comment cannot be closed early; only Java's `\uXXXX` translation could end it (a `\u000a`).
        CommentSyntax.Slash -> "// ${line.replace("\\u", "\\ u")}".trimEnd()
        CommentSyntax.Hash -> "# $line".trimEnd()
        // `--` cannot appear inside a comment (`-->` would close it), so each dash before another gets a space.
        CommentSyntax.Html -> "<!-- ${line.replace(DASH_BEFORE_DASH, "- ")} -->"
    }

    private val PACKAGE_NAME = Regex("""[\p{L}_][\p{L}\p{N}_]*(\.[\p{L}_][\p{L}\p{N}_]*)*""")
    private val LINE_BREAK = Regex("""\r\n|[\r\n\u0085  ]""")
    private val DASH_BEFORE_DASH = Regex("-(?=-)")
}
