package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.util.ThrowableRunnable
import me.tbsten.katachi.intellij.AnalysisTestBase
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.presentation.gutter.SourceDeclaration
import org.jetbrains.kotlin.psi.KtFile

/** V1's PSI side: the top-level declarations [SourceDeclarations] reads, as katachi names them (design 2.4). */
internal class SourceDeclarationsTest : AnalysisTestBase() {
    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        ignoreUnrelatedLoggedErrors { super.runTestRunnable(testRunnable) }
    }

    private fun declarationsOf(fileName: String, text: String): List<SourceDeclaration> {
        val file = myFixture.configureByText(fileName, text) as? KtFile ?: throw AssertionError("not a Kotlin file: $fileName")
        return runReadActionBlocking { SourceDeclarations.of(file).map { it.declaration } }
    }

    private val everyKind = """
        @file:JvmName("Every")
        package a

        import kotlin.math.abs

        class Plain
        interface Port
        object Single
        enum class Color { Red }
        annotation class Marker
        data class Value(val x: Int)
        fun top() = 1
        fun String.shout() = uppercase()
        val constant = 1
        var counter = 0
        typealias Name = String
        expect class Platform
        fun `with spaces`() = Unit
        /** KDoc above. */
        @Deprecated("x")
        @Suppress("y")
        class Annotated
    """.trimIndent()

    fun `test 種類ごとの top-level 宣言を名前と開始行で読み @file と import は読まない`() {
        assertEquals(
            listOf(
                SourceDeclaration(SourceDeclarationKind.Class, "Plain", 6),
                SourceDeclaration(SourceDeclarationKind.Interface, "Port", 7),
                SourceDeclaration(SourceDeclarationKind.Object, "Single", 8),
                SourceDeclaration(SourceDeclarationKind.Class, "Color", 9),
                SourceDeclaration(SourceDeclarationKind.Class, "Marker", 10),
                SourceDeclaration(SourceDeclarationKind.Class, "Value", 11),
                SourceDeclaration(SourceDeclarationKind.Fun, "top", 12),
                SourceDeclaration(SourceDeclarationKind.Fun, "shout", 13),
                SourceDeclaration(SourceDeclarationKind.Val, "constant", 14),
                SourceDeclaration(SourceDeclarationKind.Var, "counter", 15),
                SourceDeclaration(SourceDeclarationKind.TypeAlias, "Name", 16),
                SourceDeclaration(SourceDeclarationKind.Class, "Platform", 17),
                SourceDeclaration(SourceDeclarationKind.Fun, "with spaces", 18),
                // The first annotation's line, after the KDoc: where katachi counts a declaration from.
                SourceDeclaration(SourceDeclarationKind.Class, "Annotated", 20),
            ),
            declarationsOf("Every.kt", everyKind),
        )
    }

    fun `test 宣言の鍵は名前の識別子のリーフ`() {
        myFixture.configureByText("Leaf.kt", "package a\n\nclass HealthService\nfun load() = 1\n")
        val file = myFixture.file as? KtFile ?: throw AssertionError("not a Kotlin file")
        val leaves = runReadActionBlocking { SourceDeclarations.of(file).map { it.leaf.element } }
        assertEquals(listOf("HealthService", "load"), leaves.map { it?.text })
        assertTrue(leaves.all { it?.firstChild == null })
    }

    fun `test Kotlin script の宣言は読まない`() {
        assertEquals(emptyList<SourceDeclaration>(), declarationsOf("build.gradle.kts", "plugins { }\nclass Local\nfun helper() = 1\n"))
    }

    fun `test 書きかけで名前の無い宣言は飛ばす`() {
        assertEquals(listOf("Ok"), declarationsOf("Broken.kt", "package a\n\nfun () = 1\n\nclass Ok\n").map { it.name })
    }

    fun `test dumb mode でも同じ宣言を読む`() {
        val smart = declarationsOf("Dumb.kt", everyKind)
        val dumb = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
            val file = myFixture.file as? KtFile ?: throw AssertionError("not a Kotlin file")
            runReadActionBlocking { SourceDeclarations.of(file).map { it.declaration } }
        }
        assertEquals(smart, dumb)
    }

    fun `test 200 個の宣言も読む`() {
        val text = (1..200).joinToString("\n", prefix = "package a\n\n") { "fun f$it() = $it" }
        val declarations = declarationsOf("Many.kt", text)
        assertEquals(200, declarations.size)
        assertEquals(SourceDeclaration(SourceDeclarationKind.Fun, "f200", 202), declarations.last())
    }
}
