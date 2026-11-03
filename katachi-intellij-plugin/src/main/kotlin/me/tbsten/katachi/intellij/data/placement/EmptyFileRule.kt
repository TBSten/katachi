package me.tbsten.katachi.intellij.data.placement

internal object EmptyFileRule {
    fun isEmpty(content: CharSequence, fileName: String): Boolean = isEmpty(content, CommentSyntax.forFileName(fileName))

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

    private fun skipSlashComment(content: CharSequence, start: Int): Int? {
        if (start + 1 >= content.length) return null
        return when (content[start + 1]) {
            '/' -> skipLine(content, start + 2)
            '*' -> skipBlock(content, start + 2, "*/")
            else -> null
        }
    }

    private fun skipLine(content: CharSequence, from: Int): Int {
        var i = from
        while (i < content.length && content[i] != '\n' && content[i] != '\r') i++
        return i
    }

    private fun skipBlock(content: CharSequence, from: Int, close: String): Int? {
        var i = from
        while (i < content.length) {
            if (content[i] == close[0] && expect(content, i, close) != null) return i + close.length
            i++
        }
        return null
    }

    private fun expect(content: CharSequence, at: Int, word: String): Int? {
        for (k in word.indices) {
            if (at + k >= content.length || content[at + k] != word[k]) return null
        }
        return at + word.length
    }

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
