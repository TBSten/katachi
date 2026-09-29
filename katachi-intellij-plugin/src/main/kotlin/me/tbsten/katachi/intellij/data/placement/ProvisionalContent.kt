package me.tbsten.katachi.intellij.data.placement

/**
 * What is written into the output file before `katachiTemplate` finishes (design decision 16): for
 * a file type that can hold comments, a `package` line (.kt and .java, when the package is known)
 * and a comment with the [notice] and the real command; for any other, nothing. Generation
 * overwrites it, a failure leaves it as the instruction.
 *
 * Every line of the values carries the comment marker, and what would close a comment early is
 * escaped, so no value leaves the comment; the result is empty by [EmptyFileRule].
 *
 * ```
 * ProvisionalContent.of("Home.kt", "./gradlew :arch:katachiTemplate", "Generating...", "a.b")
 * // package a.b
 * //
 * // // Generating...
 * // // ./gradlew :arch:katachiTemplate
 * ```
 */
internal object ProvisionalContent {
    /**
     * The provisional content of [fileName]: [notice] (may span lines) then [command] as comment
     * lines; [packageName] only for .kt and .java, and only when it is a dotted identifier.
     * Empty when [fileName]'s type has no comment syntax.
     */
    fun of(fileName: String, command: String, notice: String, packageName: String? = null): String {
        val syntax = CommentSyntax.forFileName(fileName) ?: return ""
        val out = StringBuilder()
        packageLineOf(fileName, packageName)?.let { out.append(it).append("\n\n") }
        for (line in linesOf(notice) + linesOf(command)) out.append(commentLine(syntax, line)).append('\n')
        return out.toString()
    }

    /** Whether [current] is exactly what [of] wrote ([provisional]); one changed character is not. */
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
