package me.tbsten.katachi.intellij.data.placement

/**
 * Whether a file's text counts as empty (design decision 13, widened to each file type's comment
 * syntax by [CommentSyntax]): only blanks, a leading BOM, comments of the file type and -- for
 * `//` file types -- `package` lines. An `import`, an identifier or anything else is content.
 *
 * Judged on the characters alone, no PSI. It stops at the first content character, so a huge file is
 * not read past its first content line. What a scan cannot tell apart (a comment-closing mark inside a string
 * literal, an unterminated comment, a `package` line with anything after the name) is content:
 * wrongly calling a file empty would let generation overwrite it.
 *
 * ```
 * EmptyFileRule.isEmpty("package a.b\n// note\n", CommentSyntax.Slash)   // true
 * EmptyFileRule.isEmpty("package a.b\nimport x.Y\n", CommentSyntax.Slash) // false
 * ```
 */
internal object EmptyFileRule {
    fun isEmpty(content: CharSequence, fileName: String): Boolean = isEmpty(content, CommentSyntax.forFileName(fileName))

    /** [syntax] `null`: a file type without comments, empty only when it is blank. */
    fun isEmpty(content: CharSequence, syntax: CommentSyntax?): Boolean {
        val n = content.length
        var i = if (n > 0 && content[0] == BOM) 1 else 0
        while (i < n) {
            val c = content[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            i = when {
                syntax == CommentSyntax.Slash && c == '/' -> skipSlashComment(content, i) ?: return false
                syntax == CommentSyntax.Slash && c == 'p' -> skipPackageLine(content, i) ?: return false
                syntax == CommentSyntax.Hash && c == '#' -> skipLine(content, i + 1)
                syntax == CommentSyntax.Html && c == '<' -> skipBlock(content, expect(content, i, "<!--") ?: return false, "-->") ?: return false
                else -> return false
            }
        }
        return true
    }

    /** After the `//` line or `/* */` range at [start]; `null` when it is neither or the range is not closed. */
    private fun skipSlashComment(content: CharSequence, start: Int): Int? {
        if (start + 1 >= content.length) return null
        return when (content[start + 1]) {
            '/' -> skipLine(content, start + 2)
            '*' -> skipBlock(content, start + 2, "*/")
            else -> null
        }
    }

    /** After the line-end of the line from [from]. */
    private fun skipLine(content: CharSequence, from: Int): Int {
        var i = from
        while (i < content.length && content[i] != '\n' && content[i] != '\r') i++
        return i
    }

    /** The index after [close], searching from [from]; `null` when it never comes. */
    private fun skipBlock(content: CharSequence, from: Int, close: String): Int? {
        var i = from
        while (i < content.length) {
            if (content[i] == close[0] && expect(content, i, close) != null) return i + close.length
            i++
        }
        return null
    }

    /** The index after [word] when it starts at [at], else `null`; stops reading at the first mismatch. */
    private fun expect(content: CharSequence, at: Int, word: String): Int? {
        for (k in word.indices) {
            if (at + k >= content.length || content[at + k] != word[k]) return null
        }
        return at + word.length
    }

    /**
     * After a line `package a.b.c` (or `package a.b.c;`): the keyword, blanks, then only name
     * characters and at most one closing `;`. Anything else on the line makes it content (`null`).
     */
    private fun skipPackageLine(content: CharSequence, start: Int): Int? {
        var i = expect(content, start, "package") ?: return null
        if (i < content.length && !content[i].isWhitespace()) return null
        var semicolon = false
        while (i < content.length && content[i] != '\n' && content[i] != '\r') {
            val c = content[i]
            when {
                c.isWhitespace() -> Unit
                semicolon -> return null
                c == ';' -> semicolon = true
                c.isLetterOrDigit() || c == '_' || c == '.' || c == '`' || c == '$' -> Unit
                else -> return null
            }
            i++
        }
        return i
    }

    private const val BOM = '﻿'
}
