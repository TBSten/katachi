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
        val controller = detailOf("sample-jvm-with-captures", "api/Controller")

        assertEquals(
            listOf(ParameterModel.CaptureParam("resource", listOf(CapturePlace("PathCapture", "src/main/kotlin/com/example/controller/*/*Controller.kt", 6)))),
            controller.captures,
        )
        assertEquals(listOf("name"), controller.parameters.map { it.name })
        assertEquals(listOf("resource", "name"), allParametersOf(controller).map { it.name })
    }

    @Test
    fun `一覧のcaptureの名前も読む`() {
        val summary = ContractFixtures.templates("sample-jvm-with-captures").single { it.roleName == "api/Controller" }.summary
        assertEquals(listOf("resource"), summary.captureNames)
        assertEquals(listOf("name"), summary.parameterNames)
    }

    @Test
    fun `capturesキーの無い今までのJSONはcaptureなしとして読む`() {
        for (fixture in listOf("arch-a", "sample-jvm", "sample-android", "sample-kmp")) {
            val templates = ContractFixtures.templates(fixture)
            assertTrue(fixture, templates.all { it.summary.captureNames.isEmpty() && it.detail?.captures.orEmpty().isEmpty() })
        }
        // One file may mix both: misc/Plain of capture.json has no key at all.
        assertEquals(emptyList<ParameterModel.CaptureParam>(), detailOf("capture", "misc/Plain").captures)
    }

    @Test
    fun `capturesが空の配列ならcaptureなし`() {
        val service = detailOf("sample-jvm-with-captures", "domain/Service")
        assertEquals(emptyList<ParameterModel.CaptureParam>(), service.captures)
    }

    @Test
    fun `複数のcaptureを順に読み同じ名前の場所は1つの入力にまとめる`() {
        val deep = detailOf("capture", "feature/Deep")

        assertEquals(listOf("area", "feature"), deep.captures.map { it.name })
        assertEquals(
            listOf(CapturePlace("PathCapture", "app/*/*/*Deep.kt", 2), CapturePlace("PathCapture", "test/*/*DeepTest.kt", 1)),
            deep.captures[1].places,
        )
    }

    @Test
    fun `モジュールのcaptureを読む`() {
        val place = detailOf("capture", "feature/Screen").captures.single().places.single()
        assertTrue(place.isModule)
        assertEquals(":feature:*", place.pattern)
    }

    @Test
    fun `captureは必須で既定値がなくプレビューの値はプレースホルダ`() {
        val feature = detailOf("capture", "feature/ViewModel").captures.single()
        assertTrue(feature.isRequired)
        assertNull(feature.default)
        assertEquals("\${feature}", feature.previewValue)
        assertEquals("String", feature.typeName)
    }

    @Test
    fun `知らないkindのcaptureも入力欄として使える`() {
        val json = ContractFixtures.json("capture").replace("\"ModuleCapture\"", "\"FutureCapture\"")
        val screen = parseTemplateDescriptionJson(json, file).single { it.roleName == "feature/Screen" }

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
     * The real outputs of sample/android and sample/kmp list FeatureComponent's capture. katachi
     * (58cec87) now matches the layout's `<feature>*.kt` and the template's captureValue("feature")
     * with the same stand-in value, so the preview succeeds and the role gets a details[] entry.
     */
    @Test
    fun `sample-kmpとsample-androidのFeatureComponentはcaptureが一覧に出てプレビューに失敗しない`() {
        for (fixture in listOf("sample-kmp-with-captures", "sample-android-with-captures")) {
            val component = ContractFixtures.templates(fixture).single { it.roleName == "feature/FeatureComponent" }
            assertEquals(fixture, listOf("feature"), component.summary.captureNames)
            assertNull(fixture, component.unavailability)
        }
    }
}
