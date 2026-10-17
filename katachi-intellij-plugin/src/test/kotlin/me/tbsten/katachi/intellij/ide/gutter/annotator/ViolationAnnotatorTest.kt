package me.tbsten.katachi.intellij.ide.gutter.annotator

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.DumbModeTestUtils
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import java.util.concurrent.TimeUnit

/** V2 of the gutter's P3: the violations of the last check as the real daemon draws them in a source file (design 2.5). */
internal class ViolationAnnotatorTest : AnnotatorTestBase() {
    private val fileName = "HealthService.kt"
    private val path get() = pathOf(fileName)

    private val source = """
        package com.example.service

        internal class HealthService(private val repository: Int) {
            fun load(): Int = repository
        }

        class HealthCache
    """.trimIndent()

    private fun openSource(text: String = source) = open(fileName, text)

    private val rejectedHover = "katachi: \"Must be public\" (domain.Service) rejected HealthService — declared at ServiceRole.kt:94"

    fun `test 拒否された宣言の名前に 検査の level の色で下線が付き ホバーは規則と宣言元を言う`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3)))
        val drawn = annotated().single()
        assertEquals(HighlightSeverity.ERROR, drawn.severity)
        assertEquals("HealthService", drawn.text)
        assertEquals(3, drawn.line)
        assertTrue(drawn.description, drawn.description.startsWith(rejectedHover))
        assertTrue("the report's message follows on the next line: ${drawn.description}", drawn.description.contains("\nRole: domain.Service / FileConstraint"))
        assertTrue("the hover is HTML with the lines kept: ${drawn.tooltip}", drawn.tooltip.orEmpty().contains("<br>"))
    }

    fun `test 警告の level の違反は 警告の色で下線が付く`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthCache", line = 7, level = SarifLevel.Warning)))
        assertEquals(listOf(HighlightSeverity.WARNING to "HealthCache"), annotated().map { it.severity to it.text })
    }

    fun `test 宣言を何行ずらしても 名前で結んで 同じ宣言に下線が付き 検査の行は手がかりに過ぎない`() {
        openSource("\n\n\n\n\n\n\n\n\n\n$source")
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthCache", line = 7)))
        val drawn = annotated().single()
        assertEquals("HealthCache", drawn.text)
        assertEquals(17, drawn.line)
    }

    fun `test konsist の素の名前は 同じ名前の top-level ではなく 検査が見た行のメンバに付く`() {
        openSource("package a\n\nfun foo() = 1\n\nclass Bar {\n    fun foo() = 2\n}\n")
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "foo", line = 6)))
        val drawn = annotated().single()
        assertEquals("foo", drawn.text)
        assertEquals("the member, not the top-level function", 6, drawn.line)
    }

    fun `test 同じ名前で形違いの宣言がファイルにあっても 足りない宣言は その宣言に下線を付けず ファイルの上に出る`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.missing(path, declaration = "HealthService", requires = "public class HealthService", found = "internal class HealthService (line 3)")))
        val drawn = annotated().single()
        assertNull("no underline on the declaration that is there", drawn.text)
        assertNull(drawn.line)
        assertTrue(drawn.description, drawn.description.startsWith("katachi: [UnsatisfiedFileConstraint] Missing: public class HealthService ("))
        assertFalse(drawn.description, "rejected" in drawn.description)
    }

    fun `test メンバの違反は Outer の中で最後の名前を探して そのメンバの名前に付く`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService.load", line = 4), AnnotationFixtures.unsatisfied(path, "HealthService.repository", line = 3)))
        assertEquals(listOf("repository" to 3, "load" to 4), annotated().map { it.text to it.line })
    }

    fun `test オーバーロードは 検査の行に近い方に付く`() {
        openSource(
            """
            package a

            fun load(a: Int) = a

            fun other() = 1

            fun load(a: String) = a
            """.trimIndent(),
        )
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "load", line = 7)))
        assertEquals(listOf(7), annotated().map { it.line })
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "load", line = 2)))
        assertEquals(listOf(3), annotated().map { it.line })
    }

    fun `test 宣言が見つからないときは 検査の行の 先頭の空白の後ろから行末に下線が付く`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "Renamed", line = 4)))
        val drawn = annotated().single()
        assertEquals("fun load(): Int = repository", drawn.text)
        assertEquals(4, drawn.line)
    }

    fun `test 宣言も行も見つからないときは ファイルの上の注釈になる`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "Renamed", line = 99)))
        val drawn = annotated().single()
        assertNull(drawn.text)
        assertTrue(drawn.description, drawn.description.startsWith(rejectedHover.replace("HealthService", "Renamed")))
    }

    fun `test 足りない宣言は 行も宣言も無いので ファイルの上の注釈 1 本になる`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.missing(path, declaredAt = AnnotationFixtures.RULE_AT)))
        val drawn = annotated().single()
        assertNull(drawn.line)
        assertEquals(HighlightSeverity.ERROR, drawn.severity)
        assertEquals("katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase (ServiceRole.kt:94)", drawn.description)
    }

    fun `test 違反 1 件につき 注釈はちょうど 1 本で 描き直しても 重ならない`() {
        openSource()
        checkRan(
            listOf(
                AnnotationFixtures.unsatisfied(path, "HealthService", line = 3),
                AnnotationFixtures.unsatisfied(path, "HealthCache", line = 7),
                AnnotationFixtures.missing(path),
                AnnotationFixtures.unchecked(path),
            ),
        )
        val first = annotated()
        assertEquals(4, first.size)
        repeat(3) { assertEquals("drawn again, still one each", first, annotated()) }
        assertEquals(2, first.count { it.text == null })
    }

    fun `test UncheckedFile もファイルの上の注釈で 検査をやり直す手しか出さない`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unchecked(path)))
        val drawn = annotated().single()
        assertNull(drawn.line)
        assertEquals("katachi: [UncheckedFile] Katachi failed while checking this file, so nothing is known about it.", drawn.description)
        assertEquals(listOf("Run the check again"), drawn.fixes)
    }

    fun `test UnexpectedFile は ソースのファイルに描かない`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unexpected(path)))
        assertEquals(emptyList<DrawnAnnotation>(), annotated())
    }

    fun `test 別のファイルの違反は描かず 同じ宣言への 2 つの違反は 2 本重ねて描く`() {
        openSource()
        checkRan(
            listOf(
                AnnotationFixtures.unsatisfied(pathOf("Other.kt"), "HealthService", line = 3),
                AnnotationFixtures.unsatisfied(path, "HealthService", line = 3, constraint = "Must be public"),
                AnnotationFixtures.unsatisfied(path, "HealthService", line = 3, constraint = "Must end with Impl"),
            ),
        )
        assertEquals(listOf("Must be public", "Must end with Impl"), annotated().map { it.description.substringAfter("\"").substringBefore("\"") })
    }

    fun `test コンパイルエラーのあるファイルにも 注釈が出る`() {
        openSource("package a\n\ninternal class Broken {\n    fun f( {\n}\n")
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "Broken", line = 3)))
        assertEquals(listOf("Broken"), annotated().filter { it.description.startsWith("katachi:") }.map { it.text })
    }

    fun `test 定義側の違反は 定義ファイルの報告された行に 検査の level の色で下線が付く`() {
        val definition = (1..8).joinToString("\n") { "// line $it" }
        open("UseCaseRole.kt", definition)
        val role = pathOf("UseCaseRole.kt")
        val labels = listOf("MissingFile", "MissingDescription", "AmbiguousLayout", "UncheckedFileConstraint")
        checkRan(labels.mapIndexed { index, label -> AnnotationFixtures.missingFile(role, line = 2 + index).copy(label = label, ruleId = "katachi.$label") })

        val drawn = annotated()

        assertEquals(listOf(2, 3, 4, 5), drawn.map { it.line })
        assertEquals(listOf("// line 2", "// line 3", "// line 4", "// line 5"), drawn.map { it.text })
        assertEquals(List(4) { HighlightSeverity.ERROR }, drawn.map { it.severity })
        assertEquals(labels.map { "katachi: [$it] The file is missing." }, drawn.map { it.description })
    }

    fun `test 定義側の違反は 検査が古ければ 1 段弱い色で stale と付く`() {
        open("UseCaseRole.kt", (1..8).joinToString("\n") { "// line $it" })
        checkRan(listOf(AnnotationFixtures.missingFile(pathOf("UseCaseRole.kt"), line = 4)), stale = true)

        val drawn = annotated().single()

        assertEquals(HighlightSeverity.WARNING, drawn.severity)
        assertTrue(drawn.description, drawn.description.startsWith("(stale) katachi: [MissingFile]"))
    }

    fun `test 定義側の違反で 報告に行が無いものは 描かない`() {
        open("UseCaseRole.kt", (1..8).joinToString("\n") { "// line $it" })
        val withoutLine = AnnotationFixtures.missingFile(pathOf("UseCaseRole.kt"))
        checkRan(listOf(withoutLine.copy(location = withoutLine.location?.copy(startLine = null))))

        assertEquals(emptyList<DrawnAnnotation>(), annotated())
    }

    fun `test 古い検査は 1 段弱い色で ホバーの先頭に stale と付く`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3), AnnotationFixtures.unsatisfied(path, "HealthCache", line = 7, level = SarifLevel.Warning)), stale = true)
        val drawn = annotated()
        assertEquals(listOf(HighlightSeverity.WARNING, HighlightSeverity.WEAK_WARNING), drawn.map { it.severity })
        assertTrue(drawn.all { it.description.startsWith("(stale) katachi: ") })
    }

    fun `test 読めなくなった SARIF の前の答えも 古いものとして描く`() {
        openSource()
        val before = ViolationsState.Ready(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3)), DEFINITION_INSTANT, stale = false, paths = listOf(LIGHT_SARIF))
        violations.emit(ViolationsState.Unreadable(LIGHT_SARIF, "cut off", before))
        val drawn = annotated().single()
        assertEquals(HighlightSeverity.WARNING, drawn.severity)
        assertTrue(drawn.description, drawn.description.startsWith("(stale) "))
    }

    fun `test 検査していない間と 読めない SARIF に前の答えが無いときは 何も描かない`() {
        openSource()
        violations.emit(ViolationsState.NotRun)
        assertEquals(emptyList<DrawnAnnotation>(), annotated())
        violations.emit(ViolationsState.Unreadable(LIGHT_SARIF, "cut off", previous = null))
        assertEquals(emptyList<DrawnAnnotation>(), annotated())
    }

    fun `test 設定で消すと 何も描かず 付けると また描く`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3)))
        showViolations = false
        assertEquals(emptyList<DrawnAnnotation>(), annotated())
        showViolations = true
        assertEquals(listOf("HealthService"), annotated().map { it.text })
    }

    fun `test 定義モジュールが無いプロジェクトでは 何も描かない`() {
        openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3)))
        hasDefinitionModule = false
        assertEquals(emptyList<DrawnAnnotation>(), annotated())
    }

    fun `test dumb mode でも 注釈を組み立てられ 注釈は DumbAware`() {
        val file = openSource()
        checkRan(listOf(AnnotationFixtures.unsatisfied(path, "HealthService", line = 3), AnnotationFixtures.unchecked(path)))
        assertTrue(DumbService.isDumbAware(annotator))
        // doHighlighting() does not return in dumb mode in the test environment (the daemon waits for smart mode): the annotator is driven directly.
        val planned = DumbModeTestUtils.computeInDumbModeSynchronously(project) {
            val input = runReadActionBlocking { annotator.collectInformation(file, myFixture.editor, false) }
            // The daemon calls doAnnotate on a background thread and holds no read action for it: the annotator takes its own.
            ApplicationManager.getApplication().executeOnPooledThread<AnnotatorOutput?> { annotator.doAnnotate(input) }.get(30, TimeUnit.SECONDS)
        }
        val annotations = planned?.annotations ?: throw AssertionError("nothing was worked out in dumb mode")
        assertEquals(listOf("HealthService", null), annotations.map { planned -> planned.range?.let { myFixture.editor.document.getText(it) } })
        assertEquals(listOf(AnnotationSeverity.Error, AnnotationSeverity.Error), annotations.map { it.severity })
    }
}
