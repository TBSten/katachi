package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.englishGutter
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/**
 * The parts of the state table (design 2.6) that are decided without the IDE: when a ◆ asks the definition to load, and the
 * line a ◆ adds when the definition could not be read again.
 */
class GutterStateTest {
    private val ready = DefinitionState.Ready(listOf(definitionSnapshot(definitionModel(roleModel("domain.Service")))), Freshness.Fresh)

    @Test
    fun `まだ何も読んでいなければ 自動で読む設定のときだけ読み込みを頼む`() {
        assertTrue(GutterFreshness.shouldEnsureLoaded(DefinitionState.NotLoaded, autoLoad = true))
        assertFalse(GutterFreshness.shouldEnsureLoaded(DefinitionState.NotLoaded, autoLoad = false))
    }

    @Test
    fun `読み込まない設定で止まっていた状態は 設定が戻ったら読み込みを頼む`() {
        val waiting = DefinitionState.Unavailable(DefinitionUnavailableReason.LoadingWithoutUserDisabled, previous = null)
        assertTrue(GutterFreshness.shouldEnsureLoaded(waiting, autoLoad = true))
        assertFalse(GutterFreshness.shouldEnsureLoaded(waiting, autoLoad = false))
    }

    @Test
    fun `読み込み中 読み終わり 読めない理由が別の状態では 読み込みを頼まない`() {
        val others = listOf(
            DefinitionState.Loading(previous = null),
            DefinitionState.Loading(previous = ready),
            ready,
            ready.copy(freshness = Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)),
        ) + listOf(
            DefinitionUnavailableReason.NotSynced,
            DefinitionUnavailableReason.NoDefinition,
            DefinitionUnavailableReason.KatachiTooOld,
            DefinitionUnavailableReason.LoadFailed,
        ).flatMap { reason -> listOf(DefinitionState.Unavailable(reason, null), DefinitionState.Unavailable(reason, ready)) }
        for (state in others) assertFalse("$state", GutterFreshness.shouldEnsureLoaded(state, autoLoad = true))
    }

    private fun tooltipAfter(freshness: Freshness): String {
        val service = roleModel("domain.Service", layoutEntry("a/A.kt"))
        val roles = fileRoles("a/A.kt", service, freshness = freshness)
        val marks = GutterMarks.of(GutterInput(roles, emptyList(), emptyList(), SourceFileKind.Other, stale = true), englishGutter)
        return marks.single().tooltip
    }

    @Test
    fun `読み直しに失敗した定義の ◆ は 失敗の理由を 2 行目に足す`() {
        val failure = LoadFailure.Gradle(GradleFailure.CompilationFailed(listOf("e: Architecture.kt:3 Unresolved reference")))
        val tooltip = tooltipAfter(Freshness.Stale(DEFINITION_INSTANT, StaleReason.ReloadFailed(failure)))
        assertEquals("katachi: domain.Service\nThe definition could not be read again: the definition does not compile", tooltip)
    }

    @Test
    fun `定義が変わっただけの古さには 失敗の行を足さず 新しい定義にも足さない`() {
        assertEquals("katachi: domain.Service", tooltipAfter(Freshness.Stale(DEFINITION_INSTANT, StaleReason.DefinitionChanged)))
        assertEquals("katachi: domain.Service", tooltipAfter(Freshness.Fresh))
    }

    @Test
    fun `失敗の理由は 失敗の種類ごとに 1 行で言う`() {
        val path = Paths.get("/w/definition.json")
        val expected = mapOf(
            LoadFailure.Gradle(GradleFailure.CompilationFailed(emptyList())) to "the definition does not compile",
            LoadFailure.Gradle(GradleFailure.TaskNotFound(":arch:katachiInternalDefinitionJson", emptyList())) to "Gradle does not know the task, so the project needs a sync",
            LoadFailure.Gradle(GradleFailure.ArchitectureNotSet(emptyList())) to "katachi { architecture } is not set",
            LoadFailure.Gradle(GradleFailure.ProcessorRejected("IllegalArgumentException", listOf("unknown arg"))) to "katachi refused the command line",
            LoadFailure.JsonMissing(path) to "the task wrote no definition JSON",
            LoadFailure.MalformedJson(path, "EOF") to "the definition JSON is cut off",
            LoadFailure.IncompatibleJson(path, "roles[0]", "missing key") to "the definition JSON does not fit this plugin, so katachi and the plugin differ in version",
            LoadFailure.Cancelled to "the read was cancelled",
        )
        for ((failure, summary) in expected) assertEquals("$failure", summary, GutterTooltips.summary(failure, englishGutter))
    }

    @Test
    fun `Gradle のその他の失敗は 最初の空でない行を 空白を詰めて 長すぎれば切って言う`() {
        val other = { lines: List<String> -> GutterTooltips.summary(LoadFailure.Gradle(GradleFailure.Other(lines)), englishGutter) }
        assertEquals("Could not connect to the daemon", other(listOf("  ", "Could not   connect\tto the daemon  ", "second")))
        assertEquals("Gradle failed", other(emptyList()))
        assertEquals("Gradle failed", other(listOf("  ", "")))
        assertEquals(120, other(listOf("x".repeat(500))).length)
        assertFalse(other(listOf("a\nb")).contains('\n'))
    }
}
