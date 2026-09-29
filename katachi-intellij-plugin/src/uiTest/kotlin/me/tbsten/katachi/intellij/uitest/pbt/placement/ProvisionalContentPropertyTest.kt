package me.tbsten.katachi.intellij.uitest.pbt.placement

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.toPropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.gradle.GradleTaskInvocation
import me.tbsten.katachi.intellij.data.placement.EmptyFileRule
import me.tbsten.katachi.intellij.data.placement.ProvisionalContent
import me.tbsten.katachi.intellij.data.placement.generateCommandOf
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.data.generate.templateArgsOf
import me.tbsten.katachi.intellij.data.generate.templateInvocationOf
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The provisional content and the empty-file rule over generated values: whatever the values hold
 * (`*` `/`, `-->`, quotes, `$`, backquotes, line breaks, Japanese), a provisional content stays inside
 * its comments and is empty; the command splits back into the arguments it came from; and the
 * scan of a big input stops at the first content line.
 */
class ProvisionalContentPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20260929L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private fun config() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private val tokens = listOf(
        "*/", "/*", "//", "-->", "--", "<!--", "--!>", "#", "'", "\"", "$", "`", " ", "\t", "\n", "\r\n", "\r", "\u2028",
        "日本語", "\\", "\\u000a", "package a", "import x", "a", "Z", "0", "-", "<", ">", "!", ";", "{", "}",
    )
    private val valueArb: Arb<String> = Arb.list(Arb.element(tokens), 0..12).map { it.joinToString("") }

    private val extensions = listOf("kt", "kts", "java", "gradle", "properties", "yaml", "yml", "toml", "sh", "xml", "md", "html", "KT", "Md")
    private val silentNames = listOf("a.json", "a.JSON", "a.abc", "Makefile", "a.")
    private val fileNameArb: Arb<String> = Arb.element(extensions.map { "File.$it" } + silentNames)

    private val packageArb: Arb<String?> = Arb.element(
        listOf(null, "a", "a.b.c", "app_1.feature", "a..b", "a b", "1a", "a;", "", "a\nimport x", "日本語.x"),
    )

    /** The oracle's own table, apart from CommentSyntax: which marker a file name's comments use. */
    private fun markerOf(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "kt", "kts", "java", "gradle" -> "//"
        "properties", "yaml", "yml", "toml", "sh" -> "#"
        "xml", "md", "html" -> "<!--"
        else -> null
    }

    @Test
    fun `どの値でもコメントを書ける拡張子の仮の中身はコメントとpackage行だけで空に当たる`() {
        runBlocking {
            checkAll(config(), fileNameArb, valueArb, valueArb, packageArb) { fileName, notice, command, packageName ->
                val content = ProvisionalContent.of(fileName, command, notice, packageName)
                val marker = markerOf(fileName)
                if (marker == null) {
                    assertEquals(fileName, "", content)
                    return@checkAll
                }
                val label = "$fileName / notice=$notice / command=$command / package=$packageName"
                assertTrue("$label\n$content", EmptyFileRule.isEmpty(content, fileName))
                assertTrue(label, content.endsWith("\n"))
                assertFalse(label, content.any { it == '\r' || it == '\u0085' || it == '\u2028' || it == '\u2029' })
                if (marker == "//") assertFalse("a Java unicode escape could end the comment: $label", "\\u" in content)
                var lines = content.removeSuffix("\n").split("\n")
                val validPackage = packageName != null && Regex("[\\p{L}_][\\p{L}\\p{N}_]*(\\.[\\p{L}_][\\p{L}\\p{N}_]*)*").matches(packageName)
                val ext = fileName.substringAfterLast('.').lowercase()
                val expectsPackage = validPackage && (ext == "kt" || ext == "java")
                if (expectsPackage) {
                    assertEquals(label, "package $packageName" + if (ext == "java") ";" else "", lines[0])
                    assertEquals(label, "", lines[1])
                    lines = lines.drop(2)
                } else {
                    assertFalse(label, lines.first().startsWith("package"))
                }
                assertTrue(label, lines.isNotEmpty())
                for (line in lines) {
                    when (marker) {
                        "<!--" -> {
                            assertTrue("$label: $line", line.startsWith("<!-- ") && line.endsWith(" -->"))
                            assertFalse("$label: $line", "--" in line.removePrefix("<!--").removeSuffix("-->"))
                        }
                        else -> assertTrue("$label: $line", line == marker || line.startsWith("$marker "))
                    }
                }
            }
        }
    }

    @Test
    fun `仮の中身はそのままなら仮のままで1文字挿入削除置換すれば違う`() {
        runBlocking {
            val edit = arbitrary {
                Triple(Arb.int(0..3).bind(), Arb.int(0..10_000).bind(), Arb.element(listOf('a', ' ', '\n', '/', '日')).bind())
            }
            checkAll(config(), fileNameArb, valueArb, valueArb, edit) { fileName, notice, command, (kind, at, char) ->
                val written = ProvisionalContent.of(fileName, command, notice, "a.b")
                assertTrue(ProvisionalContent.isStillProvisional(StringBuilder(written), written))
                val position = if (written.isEmpty()) 0 else at % (written.length + 1)
                val changed = when {
                    kind == 0 -> written.substring(0, position) + char + written.substring(position)
                    written.isEmpty() -> return@checkAll
                    kind == 1 -> written.removeRange(minOf(position, written.length - 1), minOf(position, written.length - 1) + 1)
                    else -> {
                        val p = minOf(position, written.length - 1)
                        if (written[p] == char) return@checkAll
                        written.substring(0, p) + char + written.substring(p + 1)
                    }
                }
                assertFalse("$fileName: $changed", ProvisionalContent.isStillProvisional(changed, written))
            }
        }
    }

    @Test
    fun `コマンドをPOSIXシェルの規則で分割するとtemplateArgsBuilderの引数と一致する`() {
        runBlocking {
            val rows = rowsOf("arch-a")
            val row = rows.row("data.Repository")
            val detail = row.template.detail ?: throw AssertionError()
            val module: KatachiModule = module()
            checkAll(config(), valueArb, valueArb, Arb.element(OnExistingChoice.entries)) { name, item, onExisting ->
                val args = templateArgsOf("data.Repository", detail, mapOf("name" to name, "item" to item), onExisting)
                val invocation: GradleTaskInvocation = templateInvocationOf(module, args)
                val command = generateCommandOf(invocation)
                val expected = listOf("./gradlew", invocation.taskPath) + args.flatMap { (k, v) -> listOf("--arg", "$k=$v") }
                assertEquals(command, expected, posixSplit(command))
            }
        }
    }

    private sealed interface Piece {
        val text: String
        data class Empty(override val text: String) : Piece
        data class Content(override val text: String) : Piece
    }

    /** Whole-line pieces that are empty (or content) for [marker]'s syntax, joined by line breaks. */
    private fun piecesArb(marker: String): Arb<List<Piece>> {
        val empties = when (marker) {
            "//" -> listOf("", "   ", "package a.b", "package a.b;", "  package `x`", "// note", "//", "/* one */", "/* a\n b\n */ /* c */", "\t// t")
            "#" -> listOf("", "  ", "# note", "#", "   # deep", "#!/bin/sh")
            else -> listOf("", "  ", "<!-- note -->", "<!-- a\n b -->", "<!----> <!-- x -->")
        }
        val contents = when (marker) {
            "//" -> listOf("import a.b.C", "class A", "val x = 1", "x", "package a.b; import c", "/* a */ fun f() {}", "@Anno", "; ")
            "#" -> listOf("key=value", "a: b", "x", "package a", "// c")
            else -> listOf("<html>", "text", "# title", "<?xml version=\"1.0\"?>", "<a/> <!-- c -->")
        }
        return arbitrary {
            val n = Arb.int(0..8).bind()
            List(n) { if (Arb.int(0..3).bind() == 0) Piece.Content(Arb.element(contents).bind()) else Piece.Empty(Arb.element(empties).bind()) }
        }
    }

    @Test
    fun `空白改行BOMとpackageとコメントの組み合わせは空で識別子やimportが1つあれば中身あり`() {
        runBlocking {
            val fileFor = mapOf("//" to "A.kt", "#" to "a.properties", "<!--" to "a.xml")
            for ((marker, fileName) in fileFor) {
                val eol = Arb.element(listOf("\n", "\r\n", "\r"))
                checkAll(config(), piecesArb(marker), eol, Arb.element(listOf("", "\uFEFF"))) { pieces, lineBreak, bom ->
                    val text = bom + pieces.joinToString(lineBreak) { it.text }
                    val hasContent = pieces.any { it is Piece.Content }
                    // The oracle counts the first piece of a package line as empty only for the "//" table.
                    assertEquals("$fileName: ${text.replace("\r", "\\r").replace("\n", "\\n")}", !hasContent, EmptyFileRule.isEmpty(text, fileName))
                }
            }
        }
    }

    /** A CharSequence over [inner] that remembers the highest index it was asked for. */
    private class CountingChars(private val inner: String) : CharSequence {
        var maxIndex = -1
            private set
        override val length: Int get() = inner.length
        override fun get(index: Int): Char {
            if (index > maxIndex) maxIndex = index
            return inner[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = throw AssertionError("the rule reads by index only")
        override fun toString(): String = throw AssertionError("the rule must not copy the text")
    }

    @Test
    fun `大きな入力でも最初の中身の行までしか読まない`() {
        runBlocking {
            val prefixes = listOf("", "package a.b\n", "// c\n\n", "/* a\n b */\n", "\uFEFF\n\n  \n")
            val contentLines = listOf("import x.Y", "class A {", "val v = 1", "package a; import c", "/* a */ fun f()", "x")
            checkAll(config(), Arb.list(Arb.element(prefixes), 0..4), Arb.element(contentLines), Arb.int(0..3)) { before, line, tailKind ->
                val head = before.joinToString("") + line
                val tail = "\n" + when (tailKind) {
                    0 -> "x".repeat(300_000)
                    1 -> "// c\n".repeat(50_000)
                    2 -> "/* never closed " + "*".repeat(200_000)
                    else -> " \n".repeat(100_000)
                }
                val chars = CountingChars(head + tail)
                assertFalse(EmptyFileRule.isEmpty(chars, "A.kt"))
                // Up to the end of the content line: its terminator may be looked at, nothing after it.
                assertTrue("read ${chars.maxIndex} of ${head.length}", chars.maxIndex <= head.length)
            }
        }
    }

    /** POSIX shell word splitting for the words [generateCommandOf] quotes; an unquoted char the shell would expand is rejected. */
    private fun posixSplit(command: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var inWord = false
        var i = 0
        while (i < command.length) {
            val c = command[i]
            when {
                c == '\'' -> {
                    val end = command.indexOf('\'', i + 1)
                    require(end >= 0) { "unterminated quote in $command" }
                    word.append(command, i + 1, end)
                    inWord = true
                    i = end + 1
                }
                c == ' ' -> {
                    if (inWord) words += word.toString()
                    word.clear()
                    inWord = false
                    i++
                }
                c == '\\' -> {
                    require(i + 1 < command.length) { "dangling backslash in $command" }
                    word.append(command[i + 1])
                    inWord = true
                    i += 2
                }
                c.isLetterOrDigit() && c.code < 128 || c in "_./:=@%+,-" -> {
                    word.append(c)
                    inWord = true
                    i++
                }
                else -> throw AssertionError("unquoted '$c' would be interpreted by the shell in $command")
            }
        }
        if (inWord) words += word.toString()
        return words
    }
}
