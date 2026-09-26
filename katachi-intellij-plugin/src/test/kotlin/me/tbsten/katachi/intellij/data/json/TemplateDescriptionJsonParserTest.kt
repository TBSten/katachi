package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateUnavailability
import me.tbsten.katachi.intellij.testing.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

class TemplateDescriptionJsonParserTest {
    private val file = Paths.get("/work/arch/build/katachi/internalTemplatesJson/templateDescription.json")

    private fun parse(text: String) = parseTemplateDescriptionJson(text, file)

    @Test
    fun `sample-jvm の実出力を読める`() {
        val templates = ContractFixtures.templates("sample-jvm")
        val service = templates.single()
        assertEquals("domain/Service", service.roleName)
        assertEquals("サービス", service.summary.title)
        assertEquals(1, service.summary.fileCount)
        val detail = service.detail ?: throw AssertionError("detail missing")
        assertEquals(listOf("name", "kdoc"), detail.parameters.map { it.name })
        assertEquals("\${name} に関するアプリ固有の振る舞い。", detail.parameters[1].default)
        assertTrue(detail.files.single().content.contains("TODO(\"\${name}Service の実装\")"))
    }

    @Test
    fun `契約の全部の型を読みtemplatesの順に並べる`() {
        val templates = ContractFixtures.templates("arch-a")
        assertEquals(
            listOf("data/Repository", "domain/UseCase", "ui/Screen", "misc/Broken", "misc/Future", "misc/NoArgs", "misc/Label"),
            templates.map { it.roleName },
        )
        val useCase = templates[1].detail ?: throw AssertionError("detail missing")
        assertEquals(
            listOf(ParameterModel.StringParam::class, ParameterModel.EnumParam::class, ParameterModel.IntParam::class, ParameterModel.BooleanParam::class),
            useCase.parameters.map { it::class },
        )
        val visibility = useCase.parameters[1]
        assertEquals(listOf("Public", "Internal"), (visibility as? ParameterModel.EnumParam)?.acceptedValues)
        val withTest = useCase.branches.single { it.parameterName == "withTest" }
        assertEquals("testRunner", withTest.addedParameters.single().name)
        assertEquals("JUnit", withTest.addedParameters.single().default)
    }

    @Test
    fun `ifの中のパラメータの増減を分岐から読む`() {
        val repository = ContractFixtures.templates("arch-a").first().detail ?: throw AssertionError("detail missing")
        val branch = repository.branches.single()
        assertEquals("withImpl", branch.parameterName)
        assertEquals("false", branch.value)
        assertEquals(listOf("implSuffix"), branch.removedParameters)
        assertEquals(listOf("\${name}Repository\${implSuffix}.kt"), branch.removedFiles)
    }

    @Test
    fun `プレビューに失敗したテンプレートはdetailが無くチェックできない`() {
        val broken = ContractFixtures.templates("arch-a").single { it.roleName == "misc/Broken" }
        assertNull(broken.summary.fileCount)
        assertNull(broken.detail)
        assertEquals(TemplateUnavailability.PreviewFailed, broken.unavailability)
    }

    @Test
    fun `知らないkindはその1件だけ使えなくし残りは読む`() {
        val templates = ContractFixtures.templates("arch-a")
        val future = templates.single { it.roleName == "misc/Future" }
        assertEquals(TemplateUnavailability.UnknownParameterKind(listOf("ListParameter")), future.unavailability)
        assertTrue(templates.single { it.roleName == "data/Repository" }.isAvailable)
    }

    @Test
    fun `知らないキーと知らないpreviewValueSourceは無視する`() {
        val future = ContractFixtures.templates("arch-a").single { it.roleName == "misc/Future" }
        assertEquals("items", future.detail?.parameters?.single()?.name)
    }

    @Test
    fun `日本語と改行と引用符とバックスラッシュと制御文字をそのまま戻す`() {
        val label = ContractFixtures.templates("arch-a").single { it.roleName == "misc/Label" }
        assertEquals("ラベル \"引用\"\n改行", label.summary.title)
        assertEquals("C:\\path\\to\t/タブ", label.summary.summary)
        assertEquals("say \"\${name}\"\n\\end\u0001\n", label.detail?.files?.single()?.content)
    }

    @Test
    fun `生成先が決まらないファイルはpathがnullで候補パターンを持つ`() {
        val screen = ContractFixtures.templates("arch-a").single { it.roleName == "ui/Screen" }.detail?.files?.single()
        assertNull(screen?.path)
        assertEquals(listOf("ui/src/main/kotlin/**/\${name}Screen.kt"), screen?.unresolvedPatterns)
    }

    @Test
    fun `templatesに無いroleNameのdetailは捨てる`() {
        assertTrue(ContractFixtures.templates("arch-a").none { it.roleName == "ghost/Orphan" })
    }

    @Test
    fun `同じroleNameが2回出たら後のものを捨てる`() {
        val templates = parse(
            """
            {"templates": [
              {"roleName": "a/X", "title": "first", "summary": null, "parameterNames": [], "fileCount": 0},
              {"roleName": "a/X", "title": "second", "summary": null, "parameterNames": [], "fileCount": 0}
            ], "details": []}
            """.trimIndent(),
        )
        assertEquals(listOf("first"), templates.map { it.summary.title })
    }

    @Test
    fun `テンプレートが0件でも読める`() {
        assertEquals(emptyList<Any>(), parse("""{"templates": [], "details": []}"""))
    }

    @Test
    fun `必須のキーが無ければ場所を名指しして版の不一致にする`() {
        val error = assertThrows(KatachiIncompatibleTemplateJsonException::class.java) {
            parse("""{"templates": [{"roleName": "a/X", "title": null, "summary": null, "parameterNames": []}], "details": []}""")
        }
        assertEquals("$.templates[0].fileCount", error.location)
        assertNull(error.actual)
        assertTrue(error.message.orEmpty().contains("Missing key $.templates[0].fileCount"))
    }

    @Test
    fun `型が違えば期待した型と実際の型を名指しする`() {
        val error = assertThrows(KatachiIncompatibleTemplateJsonException::class.java) {
            parse("""{"templates": [{"roleName": "a/X", "title": 3, "summary": null, "parameterNames": [], "fileCount": 1}], "details": []}""")
        }
        assertEquals("$.templates[0].title", error.location)
        assertEquals("string or null", error.expected)
        assertEquals("number", error.actual)
    }

    @Test
    fun `nullを許さないキーがnullなら版の不一致にする`() {
        val error = assertThrows(KatachiIncompatibleTemplateJsonException::class.java) {
            parse("""{"templates": [{"roleName": null, "title": null, "summary": null, "parameterNames": [], "fileCount": 1}], "details": []}""")
        }
        assertEquals("null", error.actual)
    }

    @Test
    fun `fileCountが整数でなければ版の不一致にする`() {
        assertThrows(KatachiIncompatibleTemplateJsonException::class.java) {
            parse("""{"templates": [{"roleName": "a/X", "title": null, "summary": null, "parameterNames": [], "fileCount": 1.5}], "details": []}""")
        }
    }

    @Test
    fun `detailsが無ければ版の不一致にする`() {
        val error = assertThrows(KatachiIncompatibleTemplateJsonException::class.java) { parse("""{"templates": []}""") }
        assertEquals("$.details", error.location)
    }

    @Test
    fun `ルートが配列なら版の不一致にする`() {
        assertThrows(KatachiIncompatibleTemplateJsonException::class.java) { parse("[]") }
    }

    @Test
    fun `空のファイルは壊れたJSONにする`() {
        val error = assertThrows(KatachiMalformedTemplateJsonException::class.java) { parse("") }
        assertTrue(error.problem.contains("empty"))
        assertTrue(error.message.orEmpty().contains("file:///work/arch/build/"))
    }

    @Test
    fun `空白だけのファイルは壊れたJSONにする`() {
        assertThrows(KatachiMalformedTemplateJsonException::class.java) { parse("  \n") }
    }

    @Test
    fun `途中で切れたJSONは壊れたJSONにする`() {
        val whole = ContractFixtures.json("arch-a")
        for (cut in listOf(1, whole.length / 3, whole.length / 2, whole.length - 3)) {
            assertThrows("cut at $cut", KatachiMalformedTemplateJsonException::class.java) { parse(whole.substring(0, cut)) }
        }
    }

    @Test
    fun `文書の後ろにゴミがあれば壊れたJSONにする`() {
        assertThrows(KatachiMalformedTemplateJsonException::class.java) { parse("""{"templates": [], "details": []} x""") }
    }

    @Test
    fun `BOMつきでも読む`() {
        assertEquals(emptyList<Any>(), parse("\uFEFF{\"templates\": [], \"details\": []}"))
    }

    @Test
    fun `整形に頼らず1行のJSONも読む`() {
        val oneLine = ContractFixtures.json("arch-b").replace(Regex("""\n\s*"""), "")
        assertEquals("data/Repository", parse(oneLine).single().roleName)
    }

    @Test
    fun `ユニコードエスケープを戻す`() {
        val templates = parse(
            """{"templates": [{"roleName": "a/X", "title": "\u30ea\u30dd\u0001\/", "summary": null, "parameterNames": [], "fileCount": 0}], "details": []}""",
        )
        assertEquals("リポ\u0001/", templates.single().summary.title)
    }
}
