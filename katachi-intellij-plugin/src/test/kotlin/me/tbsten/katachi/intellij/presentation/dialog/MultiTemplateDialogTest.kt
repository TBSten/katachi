package me.tbsten.katachi.intellij.presentation.dialog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.snapshotOf
import me.tbsten.katachi.intellij.testing.underRoot
import me.tbsten.katachi.intellij.ui.dialog.PropertiesGenerateDialogStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generate dialog over the real output of sample/android (11 templates, four of one role, two
 * sharing the title `settings`), opened from a file that fits one or several of them.
 */
class MultiTemplateDialogTest {
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")
    private val android = ContractFixtures.templates("sample-android-with-captures")
    private val edt = ManualDispatcher()
    private val background = ManualDispatcher()

    private fun index(vararg modules: me.tbsten.katachi.intellij.model.KatachiModule): TemplatePlacementIndex =
        TemplatePlacementIndex.build(modules.map { snapshotOf(it, android) }) { it.linkedRootPath }

    private fun open(index: TemplatePlacementIndex, origin: EntryOrigin, templates: List<ModuleTemplate>, initial: TemplateId): GenerateDialogViewModel =
        GenerateDialogViewModel(
            scope = CoroutineScope(edt + Job()),
            request = GenerateDialogRequest(origin, initial, index.seedsFor(origin, initial)),
            candidates = templates,
            seeds = { o, t -> index.seedsFor(o, t) },
            checkTarget = { TargetState.Absent },
            rootOf = { it.linkedRootPath },
            checkContext = background,
            settle = {},
        )

    private fun GenerateDialogViewModel.settled(): GenerateDialogState {
        while (edt.pending + background.pending > 0) {
            edt.runAll()
            background.runAll()
        }
        return state.value
    }

    private val userFile = underRoot("data/src/main/kotlin/com/example/sample/data/user/UserProfileRepository.kt")

    @Test
    fun `テンプレートのセレクトボックスに全候補が出て、どの2つも同じ表示にならない`() {
        val index = index(archA)
        val templates = android.map { ModuleTemplate(archA, it) }
        val origin = EntryOrigin.EditorFile(userFile)
        val first = index.matchesForFile(userFile).first()
        val ui = dialogUiStateOf(open(index, origin, templates, first.id).settled(), PropertiesGenerateDialogStrings.english())

        assertEquals(11, ui.templateOptions.size)
        val duplicated = ui.templateOptions.groupBy { it }.filterValues { it.size > 1 }.keys
        assertTrue("select box entries shown twice: $duplicated in ${ui.templateOptions}", duplicated.isEmpty())
    }

    @Test
    fun `同じ役割の id 違いへ切り替えても name の入力は引き継がれ見本のパスが id ごとの置き場所になる`() {
        val index = index(archA)
        val templates = android.map { ModuleTemplate(archA, it) }
        val first = index.matchesForFile(userFile).single()
        assertEquals("data.Repository.user", first.id.template)
        val vm = open(index, EntryOrigin.EditorFile(userFile), templates, first.id)
        assertEquals("Profile", vm.settled().fields.first { it.name == "name" }.value)
        assertEquals("data/src/main/kotlin/com/example/sample/data/user/UserProfileRepository.kt", vm.state.value.targetPath)

        fun pick(template: String): GenerateDialogState {
            vm.dispatch(GenerateDialogIntent.SelectTemplate(templates.first { it.id.template == template }.id))
            return vm.settled()
        }
        assertEquals("data/src/main/kotlin/com/example/sample/data/user/UserProfileRepositoryImpl.kt", pick("data.Repository.userImpl").targetPath)
        assertEquals("Profile", vm.state.value.fields.first { it.name == "name" }.value)
        assertEquals("data/src/main/kotlin/com/example/sample/data/settings/SettingsProfileRepository.kt", pick("data.Repository.settings").targetPath)
        assertEquals("data/src/main/kotlin/com/example/sample/data/settings/SettingsProfileRepositoryImpl.kt", pick("data.Repository.settingsImpl").targetPath)
        // Back to the first one: the seeds are asked again and give the file's own value.
        assertEquals("data/src/main/kotlin/com/example/sample/data/user/UserProfileRepository.kt", pick("data.Repository.user").targetPath)
    }

    @Test
    fun `別の役割の同じタイトルへ切り替えると入力と見本が正しく変わる`() {
        val index = index(archA)
        val templates = android.map { ModuleTemplate(archA, it) }
        val settingsRepo = templates.first { it.id.template == "data.Repository.settings" }
        val settingsTest = templates.first { it.id.template == "feature.FeatureTest.settings" }
        val vm = open(index, EntryOrigin.NewMenuDirectory(underRoot("data/src/main/kotlin/com/example/sample/data")), templates, settingsRepo.id)
        vm.settled()
        vm.dispatch(GenerateDialogIntent.Input("name", "Cache"))
        assertEquals("data/src/main/kotlin/com/example/sample/data/settings/SettingsCacheRepository.kt", vm.settled().targetPath)

        vm.dispatch(GenerateDialogIntent.SelectTemplate(settingsTest.id))
        val state = vm.settled()
        assertEquals(settingsTest.id, state.selectedTemplate)
        assertEquals("Cache", state.fields.first { it.name == "name" }.value)
        assertEquals("feature/settings/src/test/kotlin/com/example/sample/feature/settings/SettingsCacheTest.kt", state.targetPath)
    }

    @Test
    fun `定義が2つのときだけ定義のセレクトボックスが出て切り替えると同じ名前の候補が並び直る`() {
        val single = dialogUiStateOf(
            open(index(archA), EntryOrigin.EditorFile(userFile), android.map { ModuleTemplate(archA, it) }, TemplateId(archA.id, "data.Repository.user")).settled(),
            PropertiesGenerateDialogStrings.english(),
        )
        assertTrue(single.definitionOptions.isEmpty())

        val both = index(archA, archB)
        val templates = listOf(archA, archB).flatMap { m -> android.map { ModuleTemplate(m, it) } }
        val first = both.matchesForFile(userFile)
        assertEquals(listOf(":arch-a", ":arch-b"), first.map { it.definition.gradlePath })
        val vm = open(both, EntryOrigin.EditorFile(userFile), templates, first.first().id)
        val ui = dialogUiStateOf(vm.settled(), PropertiesGenerateDialogStrings.english())
        assertEquals(listOf(":arch-a", ":arch-b"), ui.definitionOptions)
        assertEquals(11, ui.templateOptions.size)

        vm.dispatch(GenerateDialogIntent.SelectDefinition(archB.id))
        val state = vm.settled()
        assertEquals(archB.id, state.selectedDefinition)
        assertEquals(archB.id, state.selectedTemplate.module)
        // The first candidate of the other definition is selected, not the template that was open (design: selectDefinition).
        assertEquals("feature.FeatureComponent", state.selectedTemplate.template)
        assertEquals(11, dialogUiStateOf(state, PropertiesGenerateDialogStrings.english()).templateOptions.size)
        assertEquals("Profile", state.fields.first { it.name == "name" }.value)
    }
}
