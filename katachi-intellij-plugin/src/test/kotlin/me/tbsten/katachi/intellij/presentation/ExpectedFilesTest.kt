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

/** The paths a template may write: never shown, but behind E-27, E-44 and the result's fallbacks. */
class ExpectedFilesTest {
    private val rows = rowsOf("arch-a")
    private val repository = rows.row("data/Repository").template.detail ?: throw AssertionError()

    @Test
    fun `パスとファイル名を入力値で置き換える`() {
        val first = expectedFilesOf(repository, mapOf("name" to "User")).first()
        assertEquals("UserRepository.kt", first.fileName)
        assertEquals(ExpectedLocation.Known("data/src/main/kotlin/com/example/data/UserRepository.kt"), first.location)
    }

    @Test
    fun `分岐でファイルが減ると一覧から外れる`() {
        val files = expectedFilesOf(repository, mapOf("name" to "User", "withImpl" to "false"))
        assertEquals(listOf("UserRepository.kt"), files.map { it.fileName })
    }

    @Test
    fun `分岐で増えたファイルは宣言順の末尾に足しパスは分からないとする`() {
        val useCase = rows.row("domain/UseCase").template.detail ?: throw AssertionError()
        val files = expectedFilesOf(useCase, mapOf("name" to "Login", "withTest" to "true"))
        assertEquals(listOf("LoginUseCase.kt", "LoginUseCaseTest.kt"), files.map { it.fileName })
        assertEquals(ExpectedLocation.FromBranch, files.last().location)
    }

    @Test
    fun `分岐で消えたファイルと足されたファイルを同時に扱う`() {
        val detail = template(
            "a/X",
            parameters = listOf(stringParam("name"), booleanParam("withApi")),
            files = listOf(file("\${name}Api.kt"), file("\${name}.kt")),
            branches = listOf(branch("withApi", "false", removedFiles = listOf("\${name}Api.kt"), addedFiles = listOf("\${name}Local.kt"))),
        ).detail ?: throw AssertionError()
        val files = expectedFilesOf(detail, mapOf("name" to "User", "withApi" to "false"))
        assertEquals(listOf("User.kt", "UserLocal.kt"), files.map { it.fileName })
    }

    @Test
    fun `生成先が決まらないファイルは候補パターンを持つ`() {
        val screen = rows.row("ui/Screen").template.detail ?: throw AssertionError()
        val files = expectedFilesOf(screen, mapOf("name" to "Home"))
        assertEquals(ExpectedLocation.Unresolved(listOf("ui/src/main/kotlin/**/\${name}Screen.kt")), files.single().location)
    }

    @Test
    fun `ファイルシステムが名前にできない予想パスは解決せずnullにする`() {
        val root = Path.of("/work/project")
        assertEquals(root.resolve("data/User.kt"), resolveExpectedPath(root, "data/User.kt"))
        // NUL is refused on every platform, like `*` or `:` on Windows.
        assertNull(resolveExpectedPath(root, "data/Us\u0000er.kt"))
    }
}
