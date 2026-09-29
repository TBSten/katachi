package me.tbsten.katachi.intellij.ide

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import me.tbsten.katachi.intellij.data.NioProjectFileSystem
import me.tbsten.katachi.intellij.data.generate.SingleFileGenerationRequest
import me.tbsten.katachi.intellij.data.generate.entryTargetOf
import me.tbsten.katachi.intellij.data.placement.PlacementIndexState
import me.tbsten.katachi.intellij.ide.newmenu.KatachiNewGroup
import me.tbsten.katachi.intellij.ide.newmenu.TemplateItemAction
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.templatesOf
import me.tbsten.katachi.intellij.presentation.BusyState
import me.tbsten.katachi.intellij.presentation.ExpectedLocation
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerateBlocker
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.entry.LedgerEntry
import me.tbsten.katachi.intellij.presentation.expectedFilesOf
import me.tbsten.katachi.intellij.presentation.generateBlockerOf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * A template below a module capture (`":feature:${capture("feature")}".module { }`), generated from
 * the tool window and from New › katachi over a copy of sample/android: the definition module's JSON
 * is the sample's real `katachiInternalTemplatesJson` (`sample-android-with-captures`, with its
 * `modulePlacements`), and Gradle answers with the sample's real `katachiTemplate` output for
 * `feature=home name=IdePluginProbe` (`output/real-android-feature-component*.log`, recorded in a copy of
 * sample/android). What the plugin decides -- that [Generate] can be pressed, where the file goes, and
 * which `--arg`s it sends -- is checked against what katachi really wrote.
 *
 * The copy sits in `:arch-a`'s directory, which is the project root here as sample/android's own
 * directory is in the sample.
 */
internal class ModuleCaptureGenerationTest : EntryToGenerationTestBase() {
    private val homeSources: Path get() = archDir.resolve("feature/home/src/main/kotlin")
    private val homePackage: Path get() = homeSources.resolve("com/example/sample/feature/home")

    /** Where the real `katachiTemplate --arg feature=home --arg name=IdePluginProbe` wrote, in the copy. */
    private val written: Path get() = homePackage.resolve("component/HomeIdePluginProbe.kt")
    private val featureComponent: TemplateId get() = TemplateId(ModuleId(root, ":arch-a"), "feature.FeatureComponent")

    override fun setUp() {
        super.setUp()
        copySampleAndroid(archDir)
        gradle.fixtureOf["arch-a"] = ANDROID_FIXTURE
        gradle.contractRoot = archDir
        addSourceRoot(homeSources)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(archDir)?.refresh(false, true)
    }

    // covers: 論点2
    fun `test ツールウィンドウで feature に今あるモジュールを入れると生成できそのモジュールの下にファイルができる`() {
        val viewModel = loaded().viewModel
        KatachiToolWindowFactory().createToolWindowContent(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        viewModel.waitFor("loaded") { it.phase == ScreenPhase.Ready && it.loading == null }
        viewModel.dispatch(KatachiIntent.ToggleCheck(featureComponent))
        viewModel.dispatch(KatachiIntent.Input(FieldId(featureComponent, "name"), "IdePluginProbe"))
        assertEquals(GenerateBlocker.InvalidField(featureComponent, "feature", FieldError.Required), blockerOf(viewModel.state.value))

        viewModel.dispatch(KatachiIntent.Input(FieldId(featureComponent, "feature"), "hoem"))
        assertEquals(
            GenerateBlocker.InvalidField(featureComponent, "feature", FieldError.NotAnExistingModule(listOf("home", "settings"))),
            blockerOf(viewModel.state.value),
        )

        viewModel.dispatch(KatachiIntent.Input(FieldId(featureComponent, "feature"), "home"))
        assertNull(blockerOf(viewModel.state.value))
        assertEquals(listOf(ExpectedLocation.Known(archDir.relativize(written).joinToString("/"))), expectedLocations(viewModel.state.value))

        gradle.generations += "real-android-feature-component"
        viewModel.dispatch(KatachiIntent.Generate)
        val finished = viewModel.waitFor("generated") { it.generation is GenerationState.Finished }.generation as GenerationState.Finished

        assertTrue(finished.report.items.single().result is GenerationItemResult.Generated)
        assertEquals(REAL_RUN_ARGS.withOnExisting("fail"), gradle.generationRequests.single().tasks.single().args)
        assertTrue(Files.isRegularFile(written))
        assertEquals(listOf(written), openFiles())
    }

    // covers: 論点1
    // covers: 論点2
    fun `test New katachi からダイアログを経て FeatureComponent を生成するとそのモジュールの下にファイルができて開く`() {
        val group = KatachiNewGroup()
        val directory = directoryOf(homePackage)
        gradle.generations += "real-android-feature-component-overwrite"
        answerWith("name" to "IdePluginProbe")
        val event = menuEventOn(group, directory)

        group.update(event)
        waitForIndex("ready") { it is PlacementIndexState.Ready }
        settle()
        group.update(event)
        val item = group.getChildren(event).flattened().filterIsInstance<TemplateItemAction>()
            .single { it.templatePresentation.text.orEmpty().startsWith("FeatureComponent") }
        // katachi's spelling of the rest of the path for :feature:home: no `<feature>` left to guess.
        assertEquals("FeatureComponent \u2014 component/Home<name>.kt", item.templatePresentation.text)
        item.actionPerformed(event)

        val opened = dialogs.single()
        assertEquals("feature.FeatureComponent", opened.initialTemplate.template)
        assertEquals(mapOf("feature" to "home"), opened.seeds)
        assertEquals(written, targetOfDialog(opened.seeds + ("name" to "IdePluginProbe"), opened))
        assertEquals(LedgerEntry.Succeeded(opened.initialTemplate), awaitLedger(written))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(REAL_RUN_ARGS.withOnExisting("overwrite"), gradle.generationRequests.single().tasks.single().args)
        assertTrue(Files.isRegularFile(written))
        assertEquals(listOf(written), openFiles())
        assertEmpty(balloons)
    }

    private fun blockerOf(state: KatachiScreenState): GenerateBlocker? = generateBlockerOf(state.rows, state.form, BusyState.Idle)

    private fun expectedLocations(state: KatachiScreenState): List<ExpectedLocation> {
        val detail = state.rows.single { it.id == featureComponent }.template.detail ?: throw AssertionError("no detail")
        return expectedFilesOf(detail, state.form.inputsOf(featureComponent)).map { it.location }
    }

    /** The file the dialog shows and generates into for [args], by the rule the dialog uses. */
    private fun targetOfDialog(args: Map<String, String>, opened: me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest): Path? {
        val template = templatesOf(service.viewModel.state.value.snapshots).first { it.id == opened.initialTemplate }
        return entryTargetOf(template, SingleFileGenerationRequest(template, opened.origin, args.toList(), null), NioProjectFileSystem)
    }

    private fun List<Pair<String, String>>.withOnExisting(value: String): List<Pair<String, String>> =
        take(1) + ("onExisting" to value) + drop(1)

    private companion object {
        const val ANDROID_FIXTURE: String = "sample-android-with-captures"

        /** The `--arg`s the recorded runs were given, `onExisting` apart. */
        val REAL_RUN_ARGS: List<Pair<String, String>> =
            listOf("template" to "feature.FeatureComponent", "feature" to "home", "name" to "IdePluginProbe")

        /** Not the sample's own: what Gradle and the IDE leave behind, and settings that only hold a local path. */
        val NOT_COPIED: Set<String> = setOf("build", ".gradle", ".kotlin", ".local", ".idea", "local.properties")

        /** sample/android of this repository (the tests run in katachi-intellij-plugin/), without what [NOT_COPIED] names. */
        fun copySampleAndroid(to: Path) {
            val sample = Paths.get("../sample/android").toAbsolutePath().normalize()
            check(sample.resolve("settings.gradle.kts").let(Files::isRegularFile)) { "sample/android was not found at $sample" }
            Files.walk(sample).use { paths ->
                paths.filter { path -> sample.relativize(path).none { it.name in NOT_COPIED } }.forEach { path ->
                    val target = to.resolve(sample.relativize(path).toString())
                    if (path.isDirectory()) Files.createDirectories(target) else Files.copy(path, target)
                }
            }
        }
    }
}
