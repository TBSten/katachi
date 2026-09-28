package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.testing.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Paths

/** `captures` of `templates[]` / `details[]`: the named wildcards a run takes as `--arg`. */
class TemplateDescriptionJsonCaptureTest {
    private val file = Paths.get("/work/arch/build/katachi/internalTemplatesJson/templateDescription.json")

    private fun detailOf(fixture: String, roleName: String) =
        ContractFixtures.templates(fixture).single { it.roleName == roleName }.detail ?: throw AssertionError("$roleName has no detail")

    @Test
    fun `sample-jvmの実出力のパスのcaptureを名前と場所つきで読みパラメータとは分けて持つ`() {
        val controller = detailOf("sample-jvm-with-captures", "api.Controller")

        assertEquals(
            listOf(
                ParameterModel.CaptureParam(
                    "resource",
                    listOf(CapturePlace("PathCapture", "src/main/kotlin/com/example/controller/*/*Controller.kt", 6, "\${resource}")),
                ),
                ParameterModel.CaptureParam(
                    "name",
                    listOf(CapturePlace("PathCapture", "src/main/kotlin/com/example/controller/*/*Controller.kt", 7, "\${name}Controller.kt")),
                ),
            ),
            controller.captures,
        )
        assertEquals(emptyList<String>(), controller.parameters.map { it.name })
        assertEquals(listOf("resource", "name"), allParametersOf(controller).map { it.name })
    }

    @Test
    fun `一覧のcaptureの名前も読む`() {
        val summary = ContractFixtures.templates("sample-jvm-with-captures").single { it.roleName == "api.Controller" }.summary
        assertEquals(listOf("resource", "name"), summary.captureNames)
        assertEquals(emptyList<String>(), summary.parameterNames)
    }

    @Test
    fun `capturesキーの無いJSONはcaptureなしとして読む`() {
        // A katachi before `capture()` never wrote the key at all; the parser falls back the same
        // way `optionalArray` does for any key a future contract might add.
        val json = """
            {"templates": [{"template": "a.X", "id": null, "title": "X", "roleName": "a.X", "summary": null, "parameterNames": [], "conflict": false}],
             "details": [{"template": "a.X", "id": null, "title": "X", "roleName": "a.X", "summary": null, "parameters": [], "files": [], "branches": [], "exampleCommand": "x"}]}
        """.trimIndent()
        val template = parseTemplateDescriptionJson(json, file).single()
        assertTrue(template.summary.captureNames.isEmpty())
        assertTrue(template.detail?.captures.orEmpty().isEmpty())

        // arch-a is hand-written with an explicit empty array, which reads the same way.
        assertTrue(ContractFixtures.templates("arch-a").all { it.summary.captureNames.isEmpty() && it.detail?.captures.orEmpty().isEmpty() })
        // One file may mix both: misc.Plain of capture.json has no key at all.
        assertEquals(emptyList<ParameterModel.CaptureParam>(), detailOf("capture", "misc.Plain").captures)
    }

    @Test
    fun `capturesが空の配列ならcaptureなし`() {
        val json = """
            {"templates": [{"template": "a.X", "id": null, "title": "X", "roleName": "a.X", "summary": null, "parameterNames": [], "conflict": false, "captures": []}],
             "details": [{"template": "a.X", "id": null, "title": "X", "roleName": "a.X", "summary": null, "parameters": [], "files": [], "branches": [], "exampleCommand": "x", "captures": []}]}
        """.trimIndent()
        val template = parseTemplateDescriptionJson(json, file).single()
        assertEquals(emptyList<String>(), template.summary.captureNames)
        assertEquals(emptyList<ParameterModel.CaptureParam>(), template.detail?.captures)
    }

    @Test
    fun `複数のcaptureを順に読み同じ名前の場所は1つの入力にまとめる`() {
        val deep = detailOf("capture", "feature.Deep")

        assertEquals(listOf("area", "feature"), deep.captures.map { it.name })
        assertEquals(
            listOf(
                CapturePlace("PathCapture", "app/*/*/*Deep.kt", 2, "\${feature}"),
                CapturePlace("PathCapture", "test/*/*DeepTest.kt", 1, "\${feature}"),
            ),
            deep.captures[1].places,
        )
    }

    @Test
    fun `モジュールのcaptureを読む`() {
        val place = detailOf("capture", "feature.Screen").captures.single().places.single()
        assertTrue(place.isModule)
        assertEquals(":feature:*", place.pattern)
    }

    @Test
    fun `captureは必須で既定値がなくプレビューの値はプレースホルダ`() {
        val feature = detailOf("capture", "feature.ViewModel").captures.single()
        assertTrue(feature.isRequired)
        assertNull(feature.default)
        assertEquals("\${feature}", feature.previewValue)
        assertEquals("String", feature.typeName)
    }

    @Test
    fun `知らないkindのcaptureも入力欄として使える`() {
        val json = ContractFixtures.json("capture").replace("\"ModuleCapture\"", "\"FutureCapture\"")
        val screen = parseTemplateDescriptionJson(json, file).single { it.roleName == "feature.Screen" }

        assertNull(screen.unavailability)
        assertEquals("FutureCapture", screen.detail?.captures?.single()?.places?.single()?.kindName)
    }

    @Test
    fun `captureの形が違えば互換性のないJSONとして場所を示す`() {
        val json = ContractFixtures.json("capture").replace("\"position\": 0}", "\"position\": \"0\"}")
        val error = assertThrows(KatachiIncompatibleTemplateJsonException::class.java) { parseTemplateDescriptionJson(json, file) }
        assertEquals("$.details[1].captures[0].position", error.location)
    }

    /*
     * The real outputs of sample/android and sample/kmp list FeatureComponent's captures. Its file
     * name is `${wildcard("feature").pascalCase}${capture("name")}`, so it names two captures --
     * the module's `feature` and its own `name` -- and previews without conflict.
     */
    @Test
    fun `sample-kmpとsample-androidのFeatureComponentはcaptureが一覧に出てプレビューに失敗しない`() {
        for (fixture in listOf("sample-kmp-with-captures", "sample-android-with-captures")) {
            val component = ContractFixtures.templates(fixture).single { it.roleName == "feature.FeatureComponent" }
            assertEquals(fixture, listOf("feature", "name"), component.summary.captureNames)
            assertNull(fixture, component.unavailability)
        }
    }
}
