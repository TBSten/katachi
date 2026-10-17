package me.tbsten.katachi.intellij.ide.definition

import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.ide.LoadAnswer
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.testing.ContractFixtures
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant

/**
 * The mistakes of the definition through the real project service (ide-authoring design 2.3): a reload that katachi
 * rejects puts them in [KatachiDefinitionService.definitionErrors] while the definition keeps its answer, another failure
 * leaves them, a save marks them stale, and a reload that reads the definition clears them.
 */
internal class DefinitionErrorsServiceTest : DefinitionServiceTestBase() {
    private val rejected = ContractFixtures.text("authoring/definition-errors.json")

    private fun errorsNow(): DefinitionErrors = definition.definitionErrors.value

    private fun waitErrors(what: String, condition: (DefinitionErrors) -> Boolean): DefinitionErrors {
        PlatformTestUtil.waitWithEventsDispatching({ "$what: ${errorsNow()}" }, { condition(errorsNow()) }, 10)
        return errorsNow()
    }

    fun `test 読み直しを katachi が拒否すると誤りが出て 定義は前の答えを持ち続け 次の成功で消える`() {
        install()
        loaded()
        assertEquals(DefinitionErrors.None, errorsNow())

        gradle.loads += LoadAnswer(fails = true, definitionErrorsJson = rejected)
        definition.refresh(RefreshReason.UserAction)
        val errors = waitErrors("rejected") { it.errors.isNotEmpty() }

        assertEquals(2, errors.errors.size)
        assertEquals(DeclarationSiteRef("ScreenRole.kt", 4), errors.errors.first().site)
        assertEquals(false, errors.stale)
        waitState("stale answer kept") { it is DefinitionState.Ready && it.freshness is Freshness.Stale }

        definition.refresh(RefreshReason.UserAction)
        waitErrors("cleared") { it.errors.isEmpty() }
        waitState("fresh again") { (it as? DefinitionState.Ready)?.freshness == Freshness.Fresh }
    }

    fun `test 別の理由の失敗は誤りを変えず 定義ファイルの保存は古い印を付ける`() {
        install(autoReloadDelayMillis = 60_000)
        loaded()
        gradle.loads += LoadAnswer(fails = true, definitionErrorsJson = rejected)
        definition.refresh(RefreshReason.UserAction)
        val rejectedErrors = waitErrors("rejected") { it.errors.isNotEmpty() }

        // The next run fails without writing the JSON: the file of the run before is untouched, however soon this run comes.
        gradle.loads += LoadAnswer(fails = true)
        val requests = gradle.loadRequests.size
        definition.refresh(RefreshReason.UserAction)
        PlatformTestUtil.waitWithEventsDispatching({ "third load" }, { gradle.loadRequests.size > requests && !projectService.viewModel.isLoadRunning }, 10)
        assertEquals(rejectedErrors, errorsNow())

        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// edited\n")
        val stale = waitErrors("stale") { it.stale }
        assertEquals(rejectedErrors.errors, stale.errors)
    }

    fun `test 誤りの JSON が前の実行のものなら使わない`() {
        install()
        loaded()
        // A failure with no JSON of this run: nothing to report even though an hour-old one sits there.
        val old = root.resolve("arch").resolve(KatachiModule.DEFINITION_ERRORS_JSON)
        Files.createDirectories(old.parent)
        Files.writeString(old, rejected)
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minusSeconds(3_600)))
        gradle.loads += LoadAnswer(fails = true)
        definition.refresh(RefreshReason.UserAction)
        waitState("failed") { (it as? DefinitionState.Ready)?.freshness is Freshness.Stale }
        assertEquals(DefinitionErrors.None, errorsNow())
    }

    fun `test 一度も成功せず キャッシュも無いまま最初の読み込みが拒否されても 誤りの場所と定義モジュールのファイルが答えられる`() {
        install()
        gradle.loads += LoadAnswer(fails = true, definitionErrorsJson = rejected)
        definition.ensureLoaded()
        val errors = waitErrors("rejected at the first load") { it.errors.isNotEmpty() }
        waitState("no answer") { !projectService.viewModel.isLoadRunning && it !is DefinitionState.Loading }
        assertNull("there is no answer to ask", definition.state.value.answer)

        val testRoot = addTestSourceRoot(root.resolve("arch/src/test/kotlin"))
        val file = writeFile("arch/src/test/kotlin/ScreenRole.kt", "// role\n\n\n\n\n")
        val error = errors.errors.first()
        val site = error.site ?: throw AssertionError("the mistake has a site")

        // The annotator draws on resolve, the banner decides on definitionModuleOf: neither may need a loaded answer.
        assertEquals(error.module, definition.definitionModuleOf(file))
        assertEquals(ResolvedSite.Found(file, site.line), definition.resolve(error.module, site))
        assertNotNull(testRoot)
    }

    fun `test 読み込み中に定義を保存し その読み込みが拒否で終わると 誤りは古い印が付いたまま出る`() {
        install(autoReloadDelayMillis = 60_000)
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v1\n")
        loaded()
        val gate = CompletableDeferred<Unit>()
        gradle.loads += LoadAnswer(fails = true, definitionErrorsJson = rejected, gate = gate)
        definition.refresh(RefreshReason.UserAction)
        waitState("loading") { it is DefinitionState.Loading }

        // The load began with the file as it was: what it found is from before this save.
        writeFile("arch/src/test/kotlin/ServiceRole.kt", "// v2\n")
        gate.complete(Unit)

        val errors = waitErrors("rejected") { it.errors.isNotEmpty() }
        assertEquals(true, errors.stale)
        waitState("stale answer kept") { it is DefinitionState.Ready && it.freshness is Freshness.Stale }
    }
}
