package me.tbsten.katachi.intellij.presentation

import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.ROOT
import me.tbsten.katachi.intellij.testing.ScenarioHarness
import me.tbsten.katachi.intellij.testing.cast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tool window over a definition whose roles name their wildcards with `capture()`: the
 * captures are String fields at the head of the form, and their values reach `katachiTemplate` as
 * `--arg`s.
 */
class CaptureScenarioTest {
    private fun ScenarioHarness.id(roleName: String) = TemplateId(arch.id, roleName)

    private suspend fun ScenarioHarness.openCaptures() {
        loadJson = ContractFixtures.json("capture")
        open()
    }

    @Test
    fun `captureは文字列の欄と同じ必須の欄としてパラメータの前に出て置き場所の補足が付く`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        s.check(viewModel)

        assertEquals(listOf("feature", "name"), s.form(viewModel).fields.map { it.id.parameterName })
        val feature = s.textField(viewModel, "feature")
        val name = s.textField(viewModel, "name")
        assertTrue(feature.isRequired)
        assertEquals("feature", feature.label)
        assertEquals("生成先 feature/<feature>/src/*ViewModel.kt の <feature> に入るディレクトリ名", feature.hint)
        assertNull(name.hint)
        assertEquals(name.isNumber, feature.isNumber)
        assertNull("a value is one level: no multi-line mode", feature.isMultiline)
        assertNull("never typed: no error yet", feature.error)
    }

    @Test
    fun `未入力なら生成ボタンが押せずcaptureの名前で理由が出て押すとその欄へ行く`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        s.check(viewModel)
        s.input(viewModel, "name", "User")

        val footer = s.formFooter()
        assertFalse(footer.generateEnabled)
        assertEquals("ViewModel: feature が未入力です", footer.reason)
        assertEquals(FieldId(viewModel, "feature"), footer.reasonTarget)
    }

    @Test
    fun `区切りやドットを入れると欄にエラーが出て生成ボタンの理由も変わる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        s.check(viewModel)
        s.input(viewModel, "name", "User")

        s.input(viewModel, "feature", "home/list")
        assertEquals("1階層の名前にしてください（/ と \\ は使えません）", s.textField(viewModel, "feature").error)
        assertEquals("ViewModel: feature は1階層のディレクトリ名にしてください", s.formFooter().reason)

        s.input(viewModel, "feature", "..")
        assertEquals(". や .. は使えません", s.textField(viewModel, "feature").error)

        s.input(viewModel, "feature", "")
        assertEquals("入力してください", s.textField(viewModel, "feature").error)
    }

    @Test
    fun `入力したcaptureの値が--argで渡りその生成先にファイルが書かれる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        s.check(viewModel)
        s.input(viewModel, "feature", "home")
        s.input(viewModel, "name", "User")
        assertTrue(s.formFooter().generateEnabled)

        s.generate()

        assertEquals(
            mapOf("template" to "feature.ViewModel", "onExisting" to "fail", "feature" to "home", "name" to "User"),
            s.lastArgs(),
        )
        assertTrue(s.fs.exists(ROOT.resolve("feature/home/src/UserViewModel.kt")))
        assertEquals(listOf(ROOT.resolve("feature/home/src/UserViewModel.kt")), s.effects.opened)
    }

    @Test
    fun `同じ名前のcaptureを持つ2つの役割は1度の入力で両方に同じ値を送る`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        val screen = s.id("feature.Screen")
        s.check(viewModel)
        s.check(screen)
        s.input(viewModel, "feature", "home")
        s.input(viewModel, "name", "User")
        s.input(screen, "name", "User")

        assertEquals(LinkUi.Linked, s.textField(screen, "feature").link)
        assertEquals("モジュール :feature:<feature> の <feature> に入る、既存のモジュール名", s.textField(screen, "feature").hint)
        s.generate()

        // Both rows share the module, so they run together (design draft section 6, "IDE の複数選択"):
        // one build, the shared capture sent once.
        assertEquals(1, s.katachi.runs.size)
        assertEquals("home", s.lastArgs()["feature"])
        assertEquals("feature.ViewModel,feature.Screen", s.lastArgs()["template"])
    }

    @Test
    fun `続けて生成ではcaptureの値を残し名前だけを入れ直せばよい`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        s.check(viewModel)
        s.input(viewModel, "feature", "home")
        s.input(viewModel, "name", "User")
        s.generate()

        s.dispatch(KatachiIntent.ContinueGenerating)
        assertEquals("home", s.textField(viewModel, "feature").value)
        assertEquals("", s.textField(viewModel, "name").value)
        s.input(viewModel, "name", "Order")
        s.generate()

        assertEquals("home", s.lastArgs()["feature"])
        assertEquals("Order", s.lastArgs()["name"])
    }

    @Test
    fun `畳んだ行の入力済みの数にcaptureも数える`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val viewModel = s.id("feature.ViewModel")
        s.check(viewModel)
        s.input(viewModel, "feature", "home")
        s.dispatch(KatachiIntent.SetExpanded(viewModel, false))

        assertEquals("入力済み 1/2", s.row(viewModel).filled)
    }

    @Test
    fun `capturesキーの無い役割は今までどおりパラメータだけで生成できる`() = runBlocking {
        val s = ScenarioHarness(this)
        s.openCaptures()
        val plain = s.id("misc.Plain")
        s.check(plain)
        s.input(plain, "name", "User")

        assertEquals(listOf("name"), s.form(plain).fields.map { it.id.parameterName })
        s.generate().report.items.single().result.cast<GenerationItemResult.Generated>()
        assertEquals(mapOf("template" to "misc.Plain", "onExisting" to "fail", "name" to "User"), s.lastArgs())
    }
}
