package me.tbsten.katachi.intellij.data.placement

import org.junit.Assert.assertEquals
import org.junit.Test

class EmptyFileRuleTest {
    private fun assertEmpty(expected: Boolean, fileName: String, text: String) =
        assertEquals("$fileName: ${text.replace("\n", "\\n")}", expected, EmptyFileRule.isEmpty(text, fileName))

    // covers: 論点13
    @Test
    fun `空の文字列と空白と改行だけとBOMだけは全拡張子で空`() {
        for (name in listOf("A.kt", "A.java", "a.properties", "a.xml", "a.json", "a.unknown", "noext")) {
            assertEmpty(true, name, "")
            assertEmpty(true, name, " \t\r\n\n  ")
            assertEmpty(true, name, "﻿")
            assertEmpty(true, name, "﻿ \n")
        }
    }

    // covers: 論点13
    @Test
    fun `Slash系はpackage行とスラッシュ2本の行とブロックコメントだけなら空`() {
        assertEmpty(true, "A.kt", "package a.b\n")
        assertEmpty(true, "A.kt", "package a.b;\n")
        assertEmpty(true, "A.java", "package a.b;")
        assertEmpty(true, "A.kt", "﻿package a.b\n\n// note\n/* block\n over lines */\n")
        assertEmpty(true, "A.kts", "  // only\r\n/* a */ /* b */\r\n")
        assertEmpty(true, "A.gradle", "package `a`.b\n")
    }

    // covers: 論点13
    @Test
    fun `Slash系はimportか識別子が1つでもあれば中身あり`() {
        assertEmpty(false, "A.kt", "package a.b\nimport x.Y\n")
        assertEmpty(false, "A.kt", "// c\nclass A\n")
        assertEmpty(false, "A.java", "package a.b;\nimport x.Y;")
        assertEmpty(false, "A.kt", "x")
        assertEmpty(false, "A.kt", "/* a */ val x = 1")
    }

    // covers: 論点13
    @Test
    fun `package行の後ろに何かあるか名前が壊れていれば中身あり`() {
        assertEmpty(false, "A.kt", "package a.b; import x.Y")
        assertEmpty(false, "A.kt", "package a.b {")
        assertEmpty(false, "A.kt", "packageFoo = 1")
        assertEmpty(false, "A.kt", "package a;;")
        assertEmpty(false, "A.kt", "package a.b // trailing")
    }

    // covers: 論点13
    @Test
    fun `閉じていないブロックコメントと単独のスラッシュは中身あり`() {
        assertEmpty(false, "A.kt", "/* never closed")
        assertEmpty(false, "A.kt", "/")
        assertEmpty(false, "A.kt", "/x")
        assertEmpty(false, "A.kt", "/* a */ */")
    }

    // covers: 論点13
    @Test
    fun `Hash系はシャープの行だけなら空でpackageは中身あり`() {
        assertEmpty(true, "a.properties", "# a\n  # b\n\n")
        assertEmpty(true, "a.sh", "#!/bin/sh\n")
        assertEmpty(true, "a.yaml", "# c")
        assertEmpty(false, "a.properties", "# a\nkey=value\n")
        assertEmpty(false, "a.toml", "package a.b\n")
        assertEmpty(false, "a.yml", "// c\n")
    }

    // covers: 論点13
    @Test
    fun `Html系はコメントの範囲だけなら空で閉じていなければ中身あり`() {
        assertEmpty(true, "a.xml", "<!-- a -->\n<!-- b\n c -->\n")
        assertEmpty(true, "a.md", "<!-- x -->")
        assertEmpty(false, "a.html", "<!-- a -->\n<html>")
        assertEmpty(false, "a.xml", "<!-- never closed")
        assertEmpty(false, "a.md", "# title")
        assertEmpty(false, "a.xml", "<?xml version=\"1.0\"?>")
    }

    // covers: 論点13
    @Test
    fun `jsonと知らない拡張子ではコメントの形でも中身あり`() {
        assertEmpty(false, "a.json", "// c")
        assertEmpty(false, "a.json", "{}")
        assertEmpty(false, "a.unknown", "# c")
        assertEmpty(false, "noext", "package a")
    }

    // covers: 論点13
    @Test
    fun `拡張子は大文字でもパスの中でも最後の点で決まる`() {
        assertEmpty(true, "dir.d/A.KT", "package a")
        assertEmpty(false, "dir.kt/A", "package a")
    }

    // covers: 論点13
    @Test
    fun `ファイルの型に依らず走査の結果が同じ形は同じ答えになる`() {
        // The syntax overload is what the file-name one delegates to.
        assertEquals(true, EmptyFileRule.isEmpty("# c", CommentSyntax.Hash))
        assertEquals(false, EmptyFileRule.isEmpty("# c", null))
        assertEquals(true, EmptyFileRule.isEmpty("  ", null))
    }
}
