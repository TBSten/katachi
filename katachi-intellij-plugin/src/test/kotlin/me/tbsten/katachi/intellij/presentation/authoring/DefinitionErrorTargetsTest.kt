package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrorModel
import me.tbsten.katachi.intellij.model.DefinitionErrorPhase
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionProblem
import me.tbsten.katachi.intellij.model.DefinitionProblemKind
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.presentation.gutter.AnnotationSeverity
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Which lines of a definition file katachi's mistakes and problems underline, and how (ide-authoring design 2.4, D-7). */
class DefinitionErrorTargetsTest {
    private val arch = module(":arch").id
    private val here: (ModuleId, DeclarationSiteRef) -> Int? = { _, site -> site.line.takeIf { site.fileName == "ScreenRole.kt" } }

    private fun mistake(line: Int?, message: String = "boom\nsecond line", file: String = "ScreenRole.kt", module: ModuleId = arch) =
        DefinitionErrorModel(module, DefinitionErrorPhase.Processor, "internalDefinitionJson", "me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException", message, line?.let { DeclarationSiteRef(file, it) })

    private fun errors(vararg mistakes: DefinitionErrorModel, stale: Boolean = false) =
        DefinitionErrors(mistakes.toList(), Instant.parse("2026-10-18T00:00:00Z"), stale)

    private fun problem(kind: DefinitionProblemKind, site: DeclarationSiteRef?, detail: String? = "ui.Screen") =
        DefinitionProblem(kind, kind.name, "could not be previewed", detail, site, if (site != null) "me.tbsten.katachi.X" else null)

    private fun snapshotWith(vararg problems: DefinitionProblem, templateAt: Pair<String, Int>? = null): DefinitionSnapshot {
        val role = roleModel(
            "ui.Screen",
            layoutEntry("src/*Screen.kt").let { entry ->
                if (templateAt == null) entry else entry.copy(template = TemplateRef(templateAt.first, null, null, DeclarationSiteRef("ScreenRole.kt", templateAt.second)))
            },
        )
        return definitionSnapshot(definitionModel(role).copy(problems = problems.toList()), module(":arch"))
    }

    private fun marks(errors: DefinitionErrors, snapshots: List<DefinitionSnapshot> = emptyList(), definitionStale: Boolean = false, lineCount: Int = 20) =
        DefinitionErrorTargets.marksOf(errors, snapshots, definitionStale, here, lineCount)

    @Test
    fun `誤りは その行に error で 改行込みの全文がホバーになる`() {
        val mark = marks(errors(mistake(4))).single()

        assertEquals(4, mark.line)
        assertEquals(AnnotationSeverity.Error, mark.severity)
        assertEquals("katachi: boom\nsecond line", mark.hover)
        assertEquals(DefinitionMarkSource.Error, mark.source)
        assertEquals(0, mark.errorIndex)
    }

    @Test
    fun `変更の後の誤りは weak warning に下がり 先頭に stale の印が付く`() {
        val mark = marks(errors(mistake(4), stale = true)).single()

        assertEquals(AnnotationSeverity.WeakWarning, mark.severity)
        assertEquals("(stale) katachi: boom\nsecond line", mark.hover)
    }

    @Test
    fun `場所の無い誤りと 開いたファイルに無い場所の誤りは 注釈にならない`() {
        assertEquals(emptyList<DefinitionMark>(), marks(errors(mistake(null), mistake(3, file = "Other.kt"))))
    }

    @Test
    fun `文書より後ろの行は 最後の行に寄せ 空の文書には描かない`() {
        assertEquals(10, marks(errors(mistake(99)), lineCount = 10).single().line)
        assertEquals(emptyList<DefinitionMark>(), marks(errors(mistake(1)), lineCount = 0))
        assertEquals(emptyList<DefinitionMark>(), marks(errors(mistake(0)), lineCount = 10))
    }

    @Test
    fun `誤りの番号は 描かれなかった誤りを飛ばしても errors の中の位置のまま`() {
        val marks = marks(errors(mistake(null), mistake(5), mistake(3, file = "Other.kt"), mistake(7)))

        assertEquals(listOf(1, 3), marks.map { it.errorIndex })
    }

    @Test
    fun `problems の declaredAt があるものは warning で 定義が古ければ weak warning`() {
        val snapshot = snapshotWith(
            problem(DefinitionProblemKind.TemplatePreviewFailed, DeclarationSiteRef("ScreenRole.kt", 6)),
            problem(DefinitionProblemKind.TemplateSetPreviewFailed, DeclarationSiteRef("ScreenRole.kt", 8)),
            problem(DefinitionProblemKind.ExplanationFailed, DeclarationSiteRef("ScreenRole.kt", 9)),
        )

        val fresh = marks(errors(), listOf(snapshot))
        assertEquals(listOf(6, 8, 9), fresh.map { it.line })
        assertEquals(setOf(AnnotationSeverity.Warning), fresh.map { it.severity }.toSet())
        assertEquals(setOf(DefinitionMarkSource.Problem), fresh.map { it.source }.toSet())
        assertEquals(null, fresh.first().errorIndex)

        val stale = marks(errors(), listOf(snapshot), definitionStale = true)
        assertEquals(setOf(AnnotationSeverity.WeakWarning), stale.map { it.severity }.toSet())
        assertTrue(stale.all { it.hover.startsWith("(stale) katachi: [") })
    }

    @Test
    fun `declaredAt の無い TemplatePreviewFailed は detail の指定の template の行に 組は描かない`() {
        val snapshot = snapshotWith(
            problem(DefinitionProblemKind.TemplatePreviewFailed, null, detail = "ui.Screen"),
            problem(DefinitionProblemKind.TemplateSetPreviewFailed, null, detail = "NewFeature"),
            templateAt = "ui.Screen" to 12,
        )

        assertEquals(listOf(12), marks(errors(), listOf(snapshot)).map { it.line })
    }

    @Test
    fun `declaredAt の無い TemplatePreviewFailed が名指す template が無ければ描かない`() {
        val snapshot = snapshotWith(problem(DefinitionProblemKind.TemplatePreviewFailed, null, detail = "no.Such"), templateAt = "ui.Screen" to 12)

        assertEquals(emptyList<DefinitionMark>(), marks(errors(), listOf(snapshot)))
    }

    @Test
    fun `描かない種類の problem は declaredAt があっても描かない`() {
        val snapshot = snapshotWith(
            problem(DefinitionProblemKind.WalkFailed, DeclarationSiteRef("ScreenRole.kt", 2)),
            problem(DefinitionProblemKind.ModulesNotListed, DeclarationSiteRef("ScreenRole.kt", 3)),
            problem(DefinitionProblemKind.Unknown, DeclarationSiteRef("ScreenRole.kt", 4)),
        )

        assertEquals(emptyList<DefinitionMark>(), marks(errors(), listOf(snapshot)))
        assertFalse(DefinitionErrorTargets.isDrawn(snapshot.definition!!.problems.first()))
    }

    @Test
    fun `problem のホバーは 種類と katachi の文と 対象を言う`() {
        val hover = DefinitionErrorTargets.hoverOfProblem(problem(DefinitionProblemKind.TemplateSetPreviewFailed, null, detail = "NewFeature"))

        assertEquals("katachi: [TemplateSetPreviewFailed] could not be previewed\nNewFeature", hover)
    }

    @Test
    fun `通知は 最初の誤りの最初の行と 残りの件数と 場所のある誤りの位置を順に持つ`() {
        val model = DefinitionErrorTargets.notificationOf(errors(mistake(null, "first\nmore"), mistake(5), mistake(7)))!!

        assertEquals("first", model.firstLine)
        assertEquals(2, model.more)
        assertEquals(listOf(1, 2), model.targetCandidates)
        assertFalse(model.stale)
    }

    @Test
    fun `誤りが無ければ通知は無く どれにも場所が無ければ飛び先の候補は無い`() {
        assertNull(DefinitionErrorTargets.notificationOf(DefinitionErrors.None))
        val model = DefinitionErrorTargets.notificationOf(errors(mistake(null), stale = true))!!
        assertEquals(emptyList<Int>(), model.targetCandidates)
        assertTrue(model.stale)
    }
}
