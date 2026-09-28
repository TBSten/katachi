package me.tbsten.katachi.intellij.presentation

import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.testing.booleanParam
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.row
import me.tbsten.katachi.intellij.testing.rows
import me.tbsten.katachi.intellij.testing.rowsOf
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A capture as a field of the form: validated, ordered, explained and substituted like a String parameter. */
class CaptureFieldTest {
    private val feature = capture("feature")

    @Test
    fun `未入力と空白だけのcaptureは必須のエラー`() {
        for (input in listOf(null, "", "  ")) {
            assertEquals("$input", FieldError.Required, validateField(feature, input))
        }
    }

    @Test
    fun `区切りを含む値は1階層でないエラー`() {
        for (input in listOf("home/list", "/home", "home/", "a\\b")) {
            assertEquals(input, FieldError.InvalidCapture(CaptureProblem.Separator), validateField(feature, input))
        }
    }

    @Test
    fun `ドットとドット2つは上に登るのでエラー`() {
        for (input in listOf(".", "..", " .. ")) {
            assertEquals(input, FieldError.InvalidCapture(CaptureProblem.Dot), validateField(feature, input))
        }
    }

    @Test
    fun `それ以外の値は通して最終的な検査は本体に任せる`() {
        for (input in listOf("home", "home-2", "...", ".hidden", "ホーム", "CON", "home.")) {
            assertNull(input, validateField(feature, input))
        }
    }

    @Test
    fun `パスのcaptureは自分の階層の星だけを名前で示す`() {
        val place = CapturePlace(CapturePlace.KIND_PATH, "feature/*/src/*ViewModel.kt", 1, "\${feature}")
        assertEquals("feature/<feature>/src/*ViewModel.kt", markedPatternOf("feature", place))
        assertEquals("app/*/<area>/x.kt", markedPatternOf("area", CapturePlace(CapturePlace.KIND_PATH, "app/*/*/x.kt", 2, "\${area}")))
    }

    @Test
    fun `同じ階層に複数のcaptureがあるときは自分の名前の場所だけ印し他はそのまま名前で示す`() {
        val place = CapturePlace(CapturePlace.KIND_PATH, "feature/*-*.kt", 1, "\${a}-\${b}.kt")
        assertEquals("feature/<a>-\${b}.kt", markedPatternOf("a", place))
        assertEquals("feature/\${a}-<b>.kt", markedPatternOf("b", place))
    }

    @Test
    fun `モジュールのcaptureは何番目の星かで示す`() {
        assertEquals(":feature:<feature>", markedPatternOf("feature", CapturePlace(CapturePlace.KIND_MODULE, ":feature:*", 0, "")))
        assertEquals(":*:<leaf>", markedPatternOf("leaf", CapturePlace(CapturePlace.KIND_MODULE, ":*:*", 1, "")))
    }

    @Test
    fun `範囲外の位置はパターンをそのまま出す`() {
        assertEquals("a/*.kt", markedPatternOf("x", CapturePlace(CapturePlace.KIND_PATH, "a/*.kt", 5, "")))
        assertEquals(":feature:*", markedPatternOf("x", CapturePlace(CapturePlace.KIND_MODULE, ":feature:*", 1, "")))
    }

    @Test
    fun `補足は置き場所の種類で言い分け複数の場所を並べる`() {
        val screen = capture("feature", ":feature:*", 0, module = true)
        assertEquals("モジュール :feature:<feature> の <feature> に入る、既存のモジュール名", captureHintOf(screen, JapaneseKatachiStrings))

        val deep = rowsOf("capture").row("feature.Deep").template.detail?.captures?.last() ?: throw AssertionError()
        assertEquals(
            "生成先 app/*/<feature>/*Deep.kt の <feature> に入るディレクトリ名 / 生成先 test/<feature>/*DeepTest.kt の <feature> に入るディレクトリ名",
            captureHintOf(deep, JapaneseKatachiStrings),
        )
    }

    @Test
    fun `フォームはcaptureをパラメータと分岐の欄より前にまとめて出す`() {
        val detail = template(
            "Screen",
            parameters = listOf(stringParam("name"), booleanParam("withTest", default = "false")),
            branches = listOf(branch("withTest", "true", addedParameters = listOf(stringParam("runner")))),
            captures = listOf(capture("area"), feature),
        ).detail ?: throw AssertionError()

        assertEquals(
            listOf("area", "feature", "name", "withTest", "runner"),
            fieldSlotsOf(detail, mapOf("withTest" to "true")).map { it.parameter.name },
        )
        assertEquals(listOf("area", "feature", "name", "withTest"), shownParametersOf(detail, emptyMap()).map { it.name })
    }

    @Test
    fun `予想する生成先にcaptureの値が入る`() {
        val detail = rowsOf("capture").row("feature.Screen").template.detail ?: throw AssertionError()
        assertEquals(
            ExpectedLocation.Known("feature/home/UserScreen.kt"),
            expectedFilesOf(detail, mapOf("feature" to "home", "name" to "User")).single().location,
        )
        assertEquals(
            ExpectedLocation.Known("feature/\${feature}/UserScreen.kt"),
            expectedFilesOf(detail, mapOf("name" to "User")).single().location,
        )
    }

    @Test
    fun `未入力のcaptureは生成ボタンを止める理由になりパラメータより先に出る`() {
        val rows = rowsOf("capture")
        val viewModel = rows.row("feature.ViewModel")
        val form = FormState(selected = listOf(viewModel.id))

        assertEquals(
            GenerateBlocker.InvalidField(viewModel.id, "feature", FieldError.Required),
            generateBlockerOf(rows, form, BusyState.Idle),
        )
        val badValue = form.withInput(FieldId(viewModel.id, "feature"), "a/b").withInput(FieldId(viewModel.id, "name"), "User")
        assertEquals(
            GenerateBlocker.InvalidField(viewModel.id, "feature", FieldError.InvalidCapture(CaptureProblem.Separator)),
            generateBlockerOf(rows, badValue, BusyState.Idle),
        )
        assertNull(generateBlockerOf(rows, badValue.withInput(FieldId(viewModel.id, "feature"), "home"), BusyState.Idle))
    }

    @Test
    fun `同じ名前のcaptureは行をまたいで連動し同じ名前の文字列パラメータとは連動しない`() {
        val list = rows(
            template("A", captures = listOf(feature)),
            template("B", captures = listOf(capture("feature", ":feature:*", 0, module = true))),
            template("C", parameters = listOf(stringParam("feature"))),
        )
        var form = FormState()
        for (row in list) form = toggleCheck(form, list, row.id)

        form = inputField(form, list, FieldId(list[0].id, "feature"), "home")

        assertEquals(listOf("home", "home", null), list.map { form.inputOf(FieldId(it.id, "feature")) })
    }

    @Test
    fun `続けて生成でもcaptureの値は残し名前だけ空にする`() {
        val list = rowsOf("capture")
        val viewModel = list.row("feature.ViewModel").id
        val form = toggleCheck(FormState(), list, viewModel)
            .withInput(FieldId(viewModel, "feature"), "home")
            .withInput(FieldId(viewModel, "name"), "User")

        val next = continueGenerating(form, list)

        assertEquals(mapOf("feature" to "home"), next.inputsOf(viewModel))
    }

    @Test
    fun `captureの名前でも検索できる`() {
        val list = rows(template("Plain"), template("Screen", captures = listOf(capture("area"))))
        assertEquals(listOf("Screen"), searchTemplates(list, "AREA", FormState()).map { it.row.template.roleName })
    }

    @Test
    fun `再読み込みで残るcaptureの入力は残り消えたcaptureの入力は捨てる`() {
        val before = rows(template("Screen", captures = listOf(capture("area"), feature)))
        val after = rows(template("Screen", captures = listOf(feature)))
        val id = before.single().id
        val form = toggleCheck(FormState(), before, id).withInput(FieldId(id, "area"), "app").withInput(FieldId(id, "feature"), "home")

        assertEquals(mapOf("feature" to "home"), mergeAfterReload(form, after).form.inputsOf(id))
    }

    @Test
    fun `captureは知らない型ではないので行を使えなくしない`() {
        val model = template("Screen", captures = listOf(feature))
        assertNull(model.unavailability)
        assertEquals(ParameterModel.KIND_CAPTURE, feature.kindName)
    }
}
