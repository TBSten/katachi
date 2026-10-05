package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.lang.annotation.HighlightSeverity
import me.tbsten.katachi.intellij.ide.definition.FakeDefinitionModelService
import me.tbsten.katachi.intellij.ide.gutter.annotator.AnnotatorTestBase
import me.tbsten.katachi.intellij.ide.sarif.FakeViolationsService
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.AnnotationFixtures
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.GutterFixtures
import me.tbsten.katachi.intellij.testing.declarationModel
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.fileDeclarations
import me.tbsten.katachi.intellij.testing.fileRoles
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.roleFileDeclarations
import me.tbsten.katachi.intellij.testing.roleModel
import java.time.Instant

/**
 * The state table of the gutter (design 2.6) as a table: the state of the definition (and of the last check, and of the file)
 * × what the real daemon draws, each row on a world of its own with the real provider and annotator over the fakes. A row says
 * the ◆ it leaves (its tooltip, `null` for none), whether the gutter asked the definition to load, the first row of the ◆'s
 * menu, and the underlines of the check. Rows that disagree are listed together, so one run shows every miss.
 */
internal class GutterStateTableTest : AnnotatorTestBase() {
    private val service = roleModel("domain.Service", layoutEntry(GutterFixtures.SERVICE_PATH))
    private val serviceFile = "HealthService.kt"
    private val allowed = "katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)"
    private val allowedFirst = "◆ Allowed by class HealthService"
    private val refreshFirst = "⟳ Refresh the definition"

    private val ready get() = DefinitionState.Ready(listOf(snapshot), Freshness.Fresh)
    private fun readyStale(reason: StaleReason) = DefinitionState.Ready(listOf(snapshot), Freshness.Stale(DEFINITION_INSTANT, reason))
    private fun unavailable(reason: DefinitionUnavailableReason, previous: Boolean) =
        DefinitionState.Unavailable(reason, if (previous) ready else null)

    private val reloadFailed = StaleReason.ReloadFailed(LoadFailure.Gradle(GradleFailure.CompilationFailed(listOf("e: Architecture.kt:3 boom"))))

    /** What the daemon left for a row: read from the real thing, and compared with what the row says. */
    private data class Outcome(
        /** The ◆'s tooltip as the gutter shows it (HTML), or `null` when there is none. */
        val tooltip: String? = null,
        /** Whether the gutter asked the definition to load. */
        val asked: Boolean = false,
        /** The text of the first row of the ◆'s menu (whether it can be chosen is the menu's tests'), `null` when there is no ◆. */
        val menuFirst: String? = null,
        /** The severities of the check's underlines on declarations. */
        val underlines: List<HighlightSeverity> = emptyList(),
        /** Whether the hover of every underline begins with `(stale) `. */
        val underlinesStale: Boolean = false,
    )

    private class Row(
        val name: String,
        val definition: DefinitionState,
        val outcome: Outcome,
        val violations: ViolationsState? = null,
        val synced: Boolean = true,
        val autoLoad: Boolean = true,
        val modifiedAt: Instant? = null,
        val typing: Boolean = false,
    )

    private fun rejection(stale: Boolean = false) = ViolationsState.Ready(
        listOf(AnnotationFixtures.unsatisfied(pathOf(serviceFile), "HealthService", line = 7)),
        DEFINITION_INSTANT,
        stale,
        paths = listOf(LIGHT_SARIF),
    )

    private fun rows(): List<Row> {
        val fresh = Outcome(allowed, menuFirst = allowedFirst)
        val old = Outcome("$allowed (stale)", menuFirst = refreshFirst)
        val nothing = Outcome()
        val asks = Outcome(asked = true)
        val underlined = listOf(HighlightSeverity.ERROR)
        val tooOld = unavailable(DefinitionUnavailableReason.KatachiTooOld, previous = true)
        val noAnswer = unavailable(DefinitionUnavailableReason.LoadFailed, previous = false)
        val waiting = unavailable(DefinitionUnavailableReason.LoadingWithoutUserDisabled, previous = false)
        return listOf(
            Row("同期データに定義モジュールが無い", ready, nothing, synced = false),
            Row("KatachiTooOld は前の答えがあっても何も出さない", tooOld, nothing),
            Row("NoDefinition でも前の答えがあれば stale で出す", unavailable(DefinitionUnavailableReason.NoDefinition, previous = true), old),
            Row("NotSynced でも前の答えがあれば stale で出す", unavailable(DefinitionUnavailableReason.NotSynced, previous = true), old),
            Row("LoadFailed でも前の答えがあれば stale で出す", unavailable(DefinitionUnavailableReason.LoadFailed, previous = true), old),
            Row("LoadFailed で前の答えが無ければ何も出さない", noAnswer, nothing),
            Row("NoDefinition で前の答えが無ければ何も出さない", unavailable(DefinitionUnavailableReason.NoDefinition, previous = false), nothing),
            Row("NotLoaded は何も出さず 読み込みを頼む", DefinitionState.NotLoaded, asks),
            Row("NotLoaded で自動で読まない設定なら 頼まない", DefinitionState.NotLoaded, nothing, autoLoad = false),
            Row("読まない設定で止まった状態は 設定が自動に戻っていれば頼む", waiting, asks),
            Row("読まない設定で止まった状態は 設定が切れたままなら頼まない", waiting, nothing, autoLoad = false),
            Row("Loading で前の答えが無ければ何も出さず 頼みもしない", DefinitionState.Loading(previous = null), nothing),
            Row("Loading は前の答えで出し stale は付けない", DefinitionState.Loading(previous = ready), fresh),
            Row("Loading の前の答えが古ければ stale のまま", DefinitionState.Loading(previous = readyStale(StaleReason.DefinitionChanged)), old),
            Row("Ready(Fresh) は許可元を出す", ready, fresh),
            Row("Ready(Stale 定義が変わった) は stale で 更新を先頭に出す", readyStale(StaleReason.DefinitionChanged), old),
            Row(
                "Ready(Stale 読み直しに失敗) は stale と失敗の 1 行",
                readyStale(reloadFailed),
                old.copy(tooltip = "$allowed (stale)<br>The definition could not be read again: the definition does not compile"),
            ),
            Row("ファイルが定義より後に書かれていれば このファイルだけ stale", ready, old, modifiedAt = DEFINITION_INSTANT.plusSeconds(1)),
            Row("ファイルが定義より前に書かれたままなら stale にしない", ready, fresh, modifiedAt = DEFINITION_INSTANT.minusSeconds(1)),
            Row("保存していない編集があれば stale", ready, old, typing = true),
            Row("検査がまだ走っていない(NotRun)ときは ◆ だけ", ready, fresh, violations = ViolationsState.NotRun),
            Row("SARIF が読めない(Unreadable)ときは ◆ だけ", ready, fresh, violations = ViolationsState.Unreadable(LIGHT_SARIF, "cut off", previous = null)),
            Row("検査の違反は ◆ と別に 下線で出る", ready, fresh.copy(underlines = underlined), violations = rejection()),
            Row(
                "検査が古ければ 下線は 1 段弱くなり stale が付く",
                ready,
                fresh.copy(underlines = listOf(HighlightSeverity.WARNING), underlinesStale = true),
                violations = rejection(stale = true),
            ),
            Row("定義が NotLoaded でも SARIF の下線は出る(定義と独立)", DefinitionState.NotLoaded, asks.copy(underlines = underlined), violations = rejection()),
            Row("定義の読み込みに失敗して前の答えが無くても SARIF の下線は出る", noAnswer, nothing.copy(underlines = underlined), violations = rejection()),
            Row("KatachiTooOld は下線も出さない", tooOld, nothing, violations = rejection()),
            Row("同期データに定義モジュールが無ければ下線も出さない", ready, nothing, violations = rejection(), synced = false),
        )
    }

    private fun resetWorld() {
        definitions = FakeDefinitionModelService()
        violations = FakeViolationsService()
        hasDefinitionModule = true
        autoLoadTemplates = true
        showViolations = true
        modifiedAt = null
        writtenAt = null
        snapshot = definitionSnapshot(definitionModel(projectRoot = LIGHT_ROOT))
    }

    private fun explained() = listOf(
        roleFileDeclarations(
            service,
            fileDeclarations(
                GutterFixtures.SERVICE_PATH,
                declarationModel(SourceDeclarationKind.Class, "HealthService", 7, rules = listOf(GutterFixtures.SERVICE_RULE)),
            ),
        ),
    )

    /** Sets the world as [row] says, opens the file, lets the daemon run, and reads what is on screen. */
    private fun observe(row: Row): Outcome {
        resetWorld()
        hasDefinitionModule = row.synced
        autoLoadTemplates = row.autoLoad
        modifiedAt = row.modifiedAt
        val path = lightPath(serviceFile)
        definitions.answer(path, fileRoles(GutterFixtures.SERVICE_PATH, service))
        definitions.explain(path, explained())
        definitions.emit(row.definition)
        row.violations?.let { violations.emit(it) }
        open(serviceFile, GutterFixtures.HEALTH_SERVICE_KT)
        if (row.typing) typeAtStart("// a note\n")
        val mark = redrawn().singleOrNull()
        val menuFirst = menus().firstOrNull()?.shown()?.firstOrNull()?.removePrefix("(disabled) ")?.substringBefore(" | ")
        val underlines = annotated().filter { it.line != null }
        return Outcome(
            tooltip = mark?.tooltip,
            asked = definitions.ensureLoadedCalls > 0,
            menuFirst = menuFirst,
            underlines = underlines.map { it.severity },
            underlinesStale = underlines.isNotEmpty() && underlines.all { it.description.startsWith("(stale) ") },
        )
    }

    // covers: 状態ごとの見え方
    fun `test 定義と検査とファイルの状態の表のとおりに ◆ と下線が出る`() {
        val rows = rows()
        val failures = rows.mapNotNull { row ->
            val actual = observe(row)
            if (actual == row.outcome) null else "${row.name}\n    expected ${row.outcome}\n    actual   $actual"
        }
        assertTrue("${failures.size} of ${rows.size} rows differ:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
