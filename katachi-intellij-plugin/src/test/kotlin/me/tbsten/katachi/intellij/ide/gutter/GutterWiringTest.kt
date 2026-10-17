package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.data.detect.SyncedProject
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.ide.PlannedCheck
import com.intellij.lang.annotation.HighlightSeverity
import me.tbsten.katachi.intellij.ide.gutter.annotator.DrawnAnnotation
import me.tbsten.katachi.intellij.ide.gutter.annotator.allHighlights
import me.tbsten.katachi.intellij.ide.gutter.annotator.quickFixesOf
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.testing.KtFileContentGolden
import me.tbsten.katachi.intellij.testing.SarifResult
import me.tbsten.katachi.intellij.testing.sarifText
import java.time.Instant

/**
 * The gutter through the services the platform creates (plan P3, "wiring"): the provider `plugin.xml` registers draws from
 * the real definition and violations services over a fake Gradle, and the menu's rows reach the real
 * `katachi.RefreshDefinition` and `katachi.RunCheck`. Each test is a sequence a user goes through; what the gutter draws and the
 * menu shows after every step is read from the real daemon, and the fake Gradle writes what the real one writes (a definition
 * generated at the time of the run, a SARIF the check leaves).
 */
internal class GutterWiringTest : GutterWiringTestBase() {
    private val ERROR = HighlightSeverity.ERROR
    private val WARNING = HighlightSeverity.WARNING

    private fun isStale(mark: DrawnMark) = mark.tooltip.orEmpty().endsWith("(stale)")

    fun `test 定義を読み込みファイルを開くと ◆ が出て 保存で古くなりメニューの更新で読み直されて新しくなる`() {
        installReal()
        loaded()
        open()

        val first = redrawn().single()
        assertEquals(3, first.line)
        assertEquals("HealthService", first.leaf)
        assertTrue(first.tooltip.orEmpty(), first.tooltip.orEmpty().startsWith("katachi: Service"))
        assertFalse("the definition was generated after the file's last write", isStale(first))
        assertEquals(1, gradle.loadRequests.size)

        // The user types and saves: the file is now newer than what the definition answers for.
        typeAtEnd("// a note\n")
        myFixture.doHighlighting()
        assertTrue("an unsaved edit makes the answer old", isStale(marks().single()))
        save()
        assertTrue(isStale(redrawn().single()))
        val menu = menu()
        assertEquals("⟳ Reload Definition", menu.shown().first())

        // The row is the real katachi.RefreshDefinition: Gradle runs again, and the gutter keeps the previous answer meanwhile.
        val gate = CompletableDeferred<Unit>()
        gradle.loads += LoadAnswer(definitionJson = definitionGeneratedAt(Instant.now().plusSeconds(1)), gate = gate)
        choose(menu.row("⟳ Reload Definition"))
        val loading = waitState("loading") { it is DefinitionState.Loading } as DefinitionState.Loading
        assertNotNull(loading.previous)
        val whileLoading = redrawn().single()
        assertEquals(3, whileLoading.line)
        assertTrue("the previous answer stays, still old", isStale(whileLoading))

        gate.complete(Unit)
        val ready = waitState("reloaded") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning } as DefinitionState.Ready
        assertEquals(Freshness.Fresh, ready.freshness)
        assertEquals(2, gradle.loadRequests.size)
        assertTrue("the second load is the newer definition", ready.definitions.single().definition!!.generatedAt.isAfter(firstDefinitionAt))

        // No restart from the test: the refresher draws the open file again when the state changed.
        val fresh = redrawnByRefresher().single()
        assertFalse(fresh.tooltip.orEmpty(), isStale(fresh))
        assertEquals("⟳ Reload Definition", menu().shown().last())
    }

    fun `test メニューの検査を実行は本物の RunCheck を引き 走っている間は押せず 終わると違反が答えられる`() {
        installReal()
        loaded()
        open()
        redrawn().single()
        val gate = CompletableDeferred<Unit>()
        val check = PlannedCheck(sarif = sarifText(root, listOf(SarifResult("UnexpectedFile", SOURCE, 3))), fails = true, gate = gate)
        gradle.checks += check

        choose(menu().row("▶ Run the check (:arch:test)"))
        waitUntil("the check reached Gradle") { check.reachedGate.isCompleted }
        assertEquals(listOf(":arch:test"), gradle.checkRequests.single().taskNames)

        // While it runs the menu cannot start another.
        redrawn()
        val running = menu().shown()
        assertTrue(running.toString(), "(disabled) ▶ Checking…" in running)
        choose(menu().rows().first { it.templatePresentation.text.startsWith("▶") })
        idle(300)
        assertEquals(1, gradle.checkRequests.size)

        gate.complete(Unit)
        waitUntil("the SARIF was read") { violations.state.value is ViolationsState.Ready && violations.runningChecks.value.isEmpty() }
        assertEquals(listOf("katachi.UnexpectedFile"), violations.forFile(sourceFile()).map { it.ruleId })
        redrawn()
        assertTrue(menu().shown().toString(), "▶ Run the check (:arch:test)" in menu().shown())
        assertEquals(1, gradle.checkRequests.size)
    }

    fun `test Gradle の同期でまだ定義モジュールが見えない間は ◆ も Gradle も無く 同期が終わると出る`() {
        synced = SyncedProject.NotGradle
        installReal()
        open()
        assertEquals(emptyList<DrawnMark>(), redrawn())
        assertEquals("nothing of katachi runs in a project without a definition module", 0, gradle.requests.size)

        // The sync ends with the definition module in the data. Nothing here restarts the daemon or loads the definition by hand:
        // the cache's listener forgets the answer and draws the open file again, the provider then asks for the definition, and
        // the end of that load draws the file once more (the refresher).
        synced = syncedWithDefinition(":arch")
        assertEquals("the answer is kept until a sync ends", emptyList<DrawnMark>(), redrawn())
        val since = redrawSeq(project)
        project.messageBus.syncPublisher(ProjectDataImportListener.TOPIC).onImportFinished(null)
        awaitAnyRedraw(project, since)
        myFixture.doHighlighting()
        waitState("the definition the gutter asked for") { it is DefinitionState.Ready && !projectService.viewModel.isLoadRunning }
        assertEquals(1, gradle.loadRequests.size)
        assertEquals(3, redrawnByRefresher().single().line)
    }

    private fun rejected(declaration: String, level: String = "error") = SarifResult(
        "UnsatisfiedFileConstraint", SOURCE, 3, "Role: domain.Service / FileConstraint: Must be public",
        properties = mapOf("role" to "domain.Service", "constraint" to "Must be public", "declaration" to declaration, "declaredAt" to "ServiceRole.kt:94"),
        level = level,
    )

    /** A declaration the file lacks, as `ktFileContent { }` really reports it (katachi-konsist's golden): a `declaration`, no line, a `Missing:` line. */
    private val goldenMissing = KtFileContentGolden.byDeclaration("DefaultGetUserUseCase")
    private val missingDeclaration = KtFileContentGolden.sarifResultAt(goldenMissing, SOURCE)

    /** Starts the check from the menu's row and waits until Gradle has answered and the SARIF (if any) was read. */
    private fun runFromMenu(check: PlannedCheck, expectNotRun: Boolean = false) {
        val requests = gradle.checkRequests.size + 1
        gradle.checks += check
        choose(menu().row("▶ Run the check"))
        waitUntil("the check reached Gradle") { gradle.checkRequests.size == requests }
        waitUntil("the check ended") { violations.runningChecks.value.isEmpty() && (violations.state.value is ViolationsState.NotRun) == expectNotRun }
    }

    fun `test 検査の列 終わると違反に下線が付き 検査をやり直すで消えてから付き直し SARIF が無ければ何も残らない`() {
        installReal()
        loaded()
        open()
        redrawn().single()
        assertEquals("no check has run", emptyList<DrawnAnnotation>(), annotations())

        // The menu's row runs the check; its SARIF has a rejected declaration and a declaration the file lacks.
        runFromMenu(PlannedCheck(sarif = sarifText(root, listOf(rejected("HealthService"), missingDeclaration)), fails = true))
        val first = annotatedByRefresher()
        assertEquals(listOf(null to ERROR, "HealthService" to ERROR), first.map { it.text to it.severity })
        assertEquals(3, first.last().line)
        assertTrue(first.last().description, first.last().description.startsWith("katachi: \"Must be public\" (domain.Service) rejected HealthService — declared at ServiceRole.kt:94"))
        assertEquals(
            "katachi: [UnsatisfiedFileConstraint] Missing: class DefaultGetUserUseCase : GetUserUseCase (${goldenMissing.properties.getValue("declaredAt")})",
            first.first().description,
        )

        // "Run the check again" from the underline is the real katachi.RunCheck: the old results stay while it runs, the new ones replace them when it ends.
        val gate = CompletableDeferred<Unit>()
        val again = PlannedCheck(sarif = sarifText(root, listOf(rejected("HealthService", level = "warning"))), fails = true, gate = gate)
        gradle.checks += again
        val fix = quickFixesOf(allHighlights(project, myFixture.file, myFixture.editor.document).first { it.description?.contains("rejected HealthService") == true })
            .first { it.text == "Run the check again" }
        fix.invoke(project, myFixture.editor, myFixture.file)
        waitUntil("the second check reached Gradle") { again.reachedGate.isCompleted }
        // The check deleted the last reports as it started, but the store keeps the answer of a module whose check runs: the underlines stay until the new SARIF is read.
        violations.refresh()
        assertEquals(listOf(null to ERROR, "HealthService" to ERROR), annotatedByRefresher().map { it.text to it.severity })

        gate.complete(Unit)
        waitUntil("the second SARIF was read") { violations.runningChecks.value.isEmpty() && violations.state.value is ViolationsState.Ready }
        assertEquals(listOf("HealthService" to WARNING), annotatedByRefresher().map { it.text to it.severity })

        // A check that leaves no SARIF (`report { sarif = true }` is not set): nothing of the old results stays.
        runFromMenu(PlannedCheck(sarif = null), expectNotRun = true)
        assertEquals(emptyList<DrawnAnnotation>(), annotatedByRefresher())
    }
}
