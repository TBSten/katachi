package me.tbsten.katachi.intellij.presentation.dialog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import me.tbsten.katachi.intellij.data.generate.TargetState
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.FieldError
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import me.tbsten.katachi.intellij.testing.FakeCaptureSeedPort
import me.tbsten.katachi.intellij.testing.ManualDispatcher
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.editorFile
import me.tbsten.katachi.intellij.testing.enumParam
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.newMenuDirectory
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.testing.underRoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class GenerateDialogViewModelTest {
    private val archA = module(":arch-a")
    private val archB = module(":arch-b")

    private val screen = ModuleTemplate(archA, patterned("ui.Screen", "feature/\${feature}/ui/\${name}Screen.kt", "feature", "name"))
    private val viewModel = ModuleTemplate(
        archA,
        patterned("ui.ViewModel", "feature/\${feature}/ui/\${name}ViewModel.kt", "feature", "name", parameters = listOf(enumParam("scope", listOf("Screen", "App")))),
    )
    private val repository = ModuleTemplate(archA, patterned("data.Repository", "data/src/\${name}Repository.kt", "name", parameters = listOf(stringParam("entity"))))
    private val component = ModuleTemplate(
        archA,
        patterned("ui.Component", "feature/\${feature}/src/kotlin/feature/<feature>/\${name}.kt", "feature", "name"),
    )
    private val otherScreen = ModuleTemplate(archB, patterned("ui.Screen", "app/\${name}Screen.kt", "name"))

    private val seeds = FakeCaptureSeedPort()
    private val edt = ManualDispatcher()
    private val background = ManualDispatcher()
    private var settle: suspend () -> Unit = {}
    private val existing = mutableMapOf<Path, TargetState>()
    private val checks = mutableListOf<Path?>()

    private fun patterned(role: String, pattern: String, vararg captures: String, parameters: List<ParameterModel> = emptyList()): TemplateModel =
        template(role, parameters = parameters, files = listOf(file("x.kt", path = null, pattern = pattern)), captures = captures.map { capture(it) })

    private fun dialog(
        initial: ModuleTemplate = screen,
        origin: EntryOrigin = newMenuDirectory("feature/home/ui"),
        initialSeeds: Map<String, String> = mapOf("feature" to "home"),
        templates: List<ModuleTemplate> = listOf(screen, viewModel, repository),
    ): GenerateDialogViewModel = GenerateDialogViewModel(
        scope = CoroutineScope(edt + Job()),
        request = GenerateDialogRequest(origin, initial.id, initialSeeds),
        candidates = templates,
        seeds = seeds,
        checkTarget = { target ->
            checks += target
            target?.let { existing[it] } ?: TargetState.Absent
        },
        rootOf = { module: KatachiModule -> module.linkedRootPath },
        checkContext = background,
        settle = { settle() },
    )

    /** Runs the EDT and the background until both are idle: the debounced check has answered. */
    private fun GenerateDialogViewModel.settled(): GenerateDialogState {
        while (edt.pending + background.pending > 0) {
            edt.runAll()
            background.runAll()
        }
        return state.value
    }

    private fun GenerateDialogViewModel.type(name: String, value: String): GenerateDialogViewModel = apply { dispatch(GenerateDialogIntent.Input(name, value)) }

    private fun GenerateDialogState.field(name: String): GenerateDialogField = fields.first { it.name == name }

    // covers: 論点1
    @Test
    fun `起点から決まる capture は初期値入りで、書き換えるとその値で見本パスが変わる`() {
        val vm = dialog()
        assertEquals("home", vm.state.value.field("feature").value)
        assertTrue(vm.state.value.field("feature").seeded)

        val state = vm.type("feature", "profile").type("name", "Profile").settled()

        assertEquals("profile", state.field("feature").value)
        assertEquals("feature/profile/ui/ProfileScreen.kt", state.targetPath)
        assertEquals(underRoot("feature/profile/ui/ProfileScreen.kt"), state.target)
    }

    // covers: 論点1
    @Test
    fun `起点から決まらない capture は空の必須項目で、埋まるまで生成を押せない`() {
        val vm = dialog()
        val name = vm.state.value.field("name")
        assertNull(name.value)
        assertTrue(name.slot.parameter.isRequired)
        assertFalse(name.seeded)
        assertFalse(vm.state.value.canGenerate)
        assertNull(vm.generationRequest())

        vm.type("name", "")
        assertEquals(FieldError.Required, vm.state.value.field("name").error)
        assertFalse(vm.state.value.canGenerate)

        assertTrue(vm.type("name", "Profile").settled().canGenerate)
    }

    // covers: 論点1
    @Test
    fun `見本パスは入力のたびに追随し、埋まっていない capture はそのまま残る`() {
        val vm = dialog()
        assertEquals("feature/home/ui/\${name}Screen.kt", vm.state.value.targetPath)
        assertNull(vm.state.value.target)

        assertEquals("feature/home/ui/PrScreen.kt", vm.type("name", "Pr").state.value.targetPath)
        assertEquals("feature/home/ui/ProfileScreen.kt", vm.type("name", "Profile").state.value.targetPath)
    }

    // covers: 論点0
    @Test
    fun `候補は起点に当たらないものも含む全候補で、起点のテンプレートが初期選択になる`() {
        val state = dialog(initial = viewModel).state.value

        assertEquals(listOf(screen.id, viewModel.id, repository.id), state.candidates.map { it.id })
        assertEquals(viewModel.id, state.selectedTemplate)
        assertEquals(1, dialogUiStateOf(state).selectedTemplate)
    }

    // covers: 論点0
    @Test
    fun `候補が1つでもテンプレートのセレクトボックスに1つ並ぶ`() {
        val ui = dialogUiStateOf(dialog(templates = listOf(screen)).state.value)

        assertEquals(listOf(screen.template.title), ui.templateOptions)
        assertEquals(0, ui.selectedTemplate)
    }

    // covers: 論点7
    @Test
    fun `定義が1つなら定義のセレクトボックスは出ない`() {
        val state = dialog().state.value

        assertTrue(state.definitions.isEmpty())
        assertNull(state.selectedDefinition)
        assertTrue(dialogUiStateOf(state).definitionOptions.isEmpty())
    }

    // covers: 論点7
    @Test
    fun `定義が2つなら初期選択はテンプレートが属する定義で、切り替えると候補がその定義のものに変わる`() {
        val vm = dialog(initial = otherScreen, origin = newMenuDirectory("app"), initialSeeds = emptyMap(), templates = listOf(screen, repository, otherScreen))
        assertEquals(archB.id, vm.state.value.selectedDefinition)
        assertEquals(listOf(otherScreen.id), vm.state.value.candidates.map { it.id })
        assertEquals(listOf(":arch-a", ":arch-b"), dialogUiStateOf(vm.state.value).definitionOptions)

        vm.dispatch(GenerateDialogIntent.SelectDefinition(archA.id))

        assertEquals(listOf(screen.id, repository.id), vm.state.value.candidates.map { it.id })
        assertEquals(screen.id, vm.state.value.selectedTemplate)
        assertEquals(archA.id, vm.state.value.selectedDefinition)
    }

    // covers: 論点6
    @Test
    fun `生成先に何も無ければ新しく作ると予告し、生成を押せる`() {
        val state = dialog().type("name", "Profile").settled()

        assertEquals(TargetNotice.WillCreate, state.targetNotice)
        assertTrue(state.canGenerate)
    }

    // covers: 論点6
    @Test
    fun `生成先が空なら上書きすると予告し、生成を押せる`() {
        existing[underRoot("feature/home/ui/ProfileScreen.kt")] = TargetState.Empty
        val state = dialog().type("name", "Profile").settled()

        assertEquals(TargetNotice.WillOverwriteEmpty, state.targetNotice)
        assertTrue(state.canGenerate)
    }

    // covers: 論点6
    @Test
    fun `生成先に中身があれば作れないと予告し、生成を押せない`() {
        existing[underRoot("feature/home/ui/ProfileScreen.kt")] = TargetState.HasContent
        val vm = dialog().type("name", "Profile")
        vm.settled()

        assertEquals(TargetNotice.CannotOverwrite, vm.state.value.targetNotice)
        assertFalse(vm.state.value.canGenerate)
        assertNull(vm.generationRequest())

        val moved = vm.type("name", "Settings").settled()
        assertEquals(TargetNotice.WillCreate, moved.targetNotice)
        assertTrue(moved.canGenerate)
    }

    // covers: 論点2
    @Test
    fun `生成の要求はいつも選んだテンプレート1つで、入力を引数に持つ`() {
        val vm = dialog(initial = repository, origin = newMenuDirectory("data/src"), initialSeeds = emptyMap())
        vm.type("name", "User").type("entity", "UserEntity").settled()

        val request = vm.generationRequest()!!

        assertEquals(repository.id, request.template.id)
        assertEquals(listOf("name" to "User", "entity" to "UserEntity"), request.args)
        assertEquals(underRoot("data/src/UserRepository.kt"), request.target)
    }

    // covers: 論点1
    @Test
    fun `通知から開くとモジュールの capture とファイル名を含むすべての capture が初期値入りで、見本パスが開いているファイルと一致する`() {
        val file = "feature/home/src/kotlin/feature/home/Card.kt"
        val vm = dialog(initial = component, origin = editorFile(file), initialSeeds = mapOf("feature" to "home", "name" to "Card"), templates = listOf(component, screen))

        val state = vm.settled()

        assertEquals(listOf("home", "Card"), state.fields.map { it.value })
        assertTrue(state.fields.all { it.seeded })
        assertEquals(file, state.targetPath)
        assertEquals(underRoot(file), state.target)
        assertEquals(listOf<Path?>(underRoot(file)), checks)
    }

    @Test
    fun `右クリックした位置より下にモジュールから決まる部分が残ると、生成先は katachi が決めると予告する`() {
        val vm = dialog(initial = component, origin = newMenuDirectory("feature"), initialSeeds = emptyMap(), templates = listOf(component))

        val state = vm.type("feature", "home").type("name", "Card").settled()

        assertEquals("feature/home/src/kotlin/feature/<feature>/Card.kt", state.targetPath)
        assertNull(state.target)
        assertEquals(TargetNotice.DecidedByKatachi, state.targetNotice)
        assertTrue(state.canGenerate)
        assertTrue(checks.isEmpty())
    }

    // covers: 論点1
    @Test
    fun `テンプレートを切り替えると起点から決まる capture は計算し直し、同名の入力は引き継ぐ`() {
        seeds.seeds = mapOf(viewModel.id to mapOf("feature" to "home"), repository.id to emptyMap())
        val vm = dialog().type("feature", "profile").type("name", "Profile")

        vm.dispatch(GenerateDialogIntent.SelectTemplate(viewModel.id))

        assertEquals("home", vm.state.value.field("feature").value)
        assertEquals("Profile", vm.state.value.field("name").value)
        assertNull(vm.state.value.field("scope").value)
        assertEquals(EntryOrigin.NewMenuDirectory(underRoot("feature/home/ui")) to viewModel.id, seeds.asked.last())

        vm.dispatch(GenerateDialogIntent.SelectTemplate(repository.id))
        assertEquals("Profile", vm.state.value.field("name").value)
        assertEquals("data/src/ProfileRepository.kt", vm.state.value.targetPath)
    }

    @Test
    fun `一覧が変わっても選んでいたテンプレートが残っていれば選択と入力はそのまま`() {
        val vm = dialog().type("name", "Profile")

        vm.dispatch(GenerateDialogIntent.ListChanged(listOf(repository, screen)))

        assertEquals(screen.id, vm.state.value.selectedTemplate)
        assertEquals("Profile", vm.state.value.field("name").value)
        assertEquals(listOf(repository.id, screen.id), vm.state.value.candidates.map { it.id })
        assertNull(vm.state.value.listNotice)
    }

    @Test
    fun `選んでいたテンプレートが一覧から消えたら同じ定義の最初の候補を選び、その旨を出す`() {
        seeds.seeds = mapOf(viewModel.id to mapOf("feature" to "home"))
        val vm = dialog(templates = listOf(screen, viewModel, repository, otherScreen)).type("name", "Profile")

        vm.dispatch(GenerateDialogIntent.ListChanged(listOf(otherScreen, viewModel, repository)))

        assertEquals(viewModel.id, vm.state.value.selectedTemplate)
        assertEquals(GenerateDialogListNotice.TemplateReplaced(screen.template.title), vm.state.value.listNotice)
        assertEquals("Profile", vm.state.value.field("name").value)
    }

    @Test
    fun `選んでいた定義の候補が一覧から無くなったら生成を押せなくなり、戻れば押せる`() {
        val vm = dialog(templates = listOf(screen, otherScreen)).type("name", "Profile")
        assertTrue(vm.settled().canGenerate)

        vm.dispatch(GenerateDialogIntent.ListChanged(listOf(otherScreen)))
        assertEquals(GenerateDialogListNotice.NoCandidates, vm.state.value.listNotice)
        assertTrue(vm.state.value.candidates.isEmpty())
        assertFalse(vm.settled().canGenerate)
        assertNull(vm.generationRequest())

        vm.dispatch(GenerateDialogIntent.ListChanged(listOf(screen, otherScreen)))
        assertNull(vm.state.value.listNotice)
        assertTrue(vm.settled().canGenerate)
    }

    // covers: 論点6
    @Test
    fun `予告は入力を間引いてから EDT の外で確かめ、続けて打った入力では最後の生成先を1回だけ確かめる`() {
        val pause = CompletableDeferred<Unit>()
        settle = { pause.await() }
        val vm = dialog()
        vm.type("name", "P").type("name", "Pr").type("name", "Profile")
        edt.runAll()
        background.runAll()
        assertTrue(checks.isEmpty())

        pause.complete(Unit)
        edt.runAll()
        assertTrue("the check runs on the background context, not the EDT", checks.isEmpty())
        vm.settled()

        assertEquals(listOf<Path?>(underRoot("feature/home/ui/ProfileScreen.kt")), checks)
    }

    // covers: 論点6
    @Test
    fun `一覧が変わったときと外から書かれたときは同じ生成先でも確かめ直す`() {
        val target = underRoot("feature/home/ui/ProfileScreen.kt")
        val vm = dialog().type("name", "Profile")
        vm.settled()
        assertEquals(TargetNotice.WillCreate, vm.state.value.targetNotice)

        existing[target] = TargetState.HasContent
        vm.dispatch(GenerateDialogIntent.FilesChangedOutside)
        assertEquals(TargetNotice.CannotOverwrite, vm.settled().targetNotice)

        existing[target] = TargetState.Empty
        vm.dispatch(GenerateDialogIntent.ListChanged(listOf(screen, viewModel, repository)))
        assertEquals(TargetNotice.WillOverwriteEmpty, vm.settled().targetNotice)
        assertEquals(listOf<Path?>(target, target, target), checks)
    }
}
