package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Path

class FileSummaryTest {
    private val rows = rowsOf("arch-a")
    private val repository = rows.row("data/Repository").template.detail ?: throw AssertionError()

    @Test
    fun `宣言順の最初のファイル名とほかの件数を要約にする`() {
        val files = expectedFilesOf(repository, mapOf("name" to "User"))
        assertEquals(FileSummary("UserRepository.kt", otherCount = 1, hasUnresolved = false), fileSummaryOf(files))
    }

    @Test
    fun `未入力ならプレースホルダのまま要約に出す`() {
        assertEquals("\${name}Repository.kt", fileSummaryOf(expectedFilesOf(repository, emptyMap())).firstFileName)
    }

    @Test
    fun `分岐でファイルが減ると要約の件数が追随する`() {
        val files = expectedFilesOf(repository, mapOf("name" to "User", "withImpl" to "false"))
        assertEquals(FileSummary("UserRepository.kt", otherCount = 0, hasUnresolved = false), fileSummaryOf(files))
    }

    @Test
    fun `分岐で増えたファイルは宣言順の末尾に足しパスは分からないとする`() {
        val useCase = rows.row("domain/UseCase").template.detail ?: throw AssertionError()
        val files = expectedFilesOf(useCase, mapOf("name" to "Login", "withTest" to "true"))
        assertEquals(listOf("LoginUseCase.kt", "LoginUseCaseTest.kt"), files.map { it.fileName })
        assertEquals(ExpectedLocation.FromBranch, files.last().location)
        assertNull(files.last().directory)
    }

    @Test
    fun `最初のファイルが分岐で消えたら残っている次のファイルを要約にする`() {
        val detail = template(
            "a/X",
            parameters = listOf(stringParam("name"), booleanParam("withApi")),
            files = listOf(file("\${name}Api.kt"), file("\${name}.kt")),
            branches = listOf(branch("withApi", "false", removedFiles = listOf("\${name}Api.kt"), addedFiles = listOf("\${name}Local.kt"))),
        ).detail ?: throw AssertionError()
        val files = expectedFilesOf(detail, mapOf("name" to "User", "withApi" to "false"))
        assertEquals(listOf("User.kt", "UserLocal.kt"), files.map { it.fileName })
        assertEquals("User.kt", fileSummaryOf(files).firstFileName)
    }

    @Test
    fun `生成先が決まらないファイルは印を付け候補パターンを持つ`() {
        val screen = rows.row("ui/Screen").template.detail ?: throw AssertionError()
        val files = expectedFilesOf(screen, mapOf("name" to "Home"))
        assertEquals(ExpectedLocation.Unresolved(listOf("ui/src/main/kotlin/**/\${name}Screen.kt")), files.single().location)
        assertEquals(true, fileSummaryOf(files).hasUnresolved)
    }

    @Test
    fun `パスとディレクトリと中身も入力値で置き換える`() {
        val first = expectedFilesOf(repository, mapOf("name" to "User")).first()
        assertEquals(ExpectedLocation.Known("data/src/main/kotlin/com/example/data/UserRepository.kt"), first.location)
        assertEquals("data/src/main/kotlin/com/example/data/", first.directory)
        assertEquals("package com.example.data\n\ninterface UserRepository\n", first.content)
    }

    @Test
    fun `長いディレクトリは中間を三点リーダで省略する`() {
        assertEquals("data/src/…/data/", abbreviateDirectory("data/src/main/kotlin/com/example/data/"))
        assertEquals("a/b/c/", abbreviateDirectory("a/b/c/"))
        assertEquals("", abbreviateDirectory(""))
    }

    @Test
    fun `ファイルが無ければ要約の最初のファイルは無い`() {
        assertEquals(FileSummary(null, 0, false), fileSummaryOf(emptyList()))
    }

    @Test
    fun `ファイルシステムが名前にできない予想パスは解決せずnullにする`() {
        val root = Path.of("/work/project")
        assertEquals(root.resolve("data/User.kt"), resolveExpectedPath(root, "data/User.kt"))
        // NUL is refused on every platform, like `*` or `:` on Windows.
        assertNull(resolveExpectedPath(root, "data/Us\u0000er.kt"))
    }
}
