package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.ModuleChoiceModel
import me.tbsten.katachi.intellij.model.ModulePlacementModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.testing.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Paths

/**
 * `modulePlacements[]` of `templateDescription.json`: where a file below a module capture lands in
 * each existing module, read onto the file (`path: null`) and onto the capture's field.
 */
class TemplateDescriptionJsonModulePlacementTest {
    private fun detailOf(fixture: String, template: String): TemplateDetailModel =
        ContractFixtures.templates(fixture).single { it.template == template }.detail ?: throw AssertionError("$template has no detail")

    private fun parse(json: String): List<TemplateDetailModel> =
        parseTemplateDescriptionJson(json, Paths.get("/inline.json")).mapNotNull { it.detail }

    @Test
    fun `sample-androidの実出力でモジュールのcaptureの下のファイルにモジュールごとの生成先が付く`() {
        val component = detailOf("sample-android-with-captures", "feature.FeatureComponent")

        val file = component.files.single()
        assertNull(file.path)
        assertEquals(
            ModulePlacementModel(
                modulePattern = ":feature:*",
                captureNames = listOf("feature"),
                modules = listOf(
                    ModuleChoiceModel(
                        listOf("home"),
                        ":feature:home",
                        "feature/home",
                        "feature/home/src/main/kotlin/com/example/sample/feature/home/component/Home\${name}.kt",
                    ),
                    ModuleChoiceModel(
                        listOf("settings"),
                        ":feature:settings",
                        "feature/settings",
                        "feature/settings/src/main/kotlin/com/example/sample/feature/settings/component/Settings\${name}.kt",
                    ),
                ),
            ),
            file.modulePlacement,
        )
    }

    @Test
    fun `モジュールのcaptureの欄には選べる値が付きほかのcaptureには付かない`() {
        val component = detailOf("sample-android-with-captures", "feature.FeatureComponent")

        assertEquals(listOf("home", "settings"), component.captures.single { it.name == "feature" }.existingModules)
        assertNull(component.captures.single { it.name == "name" }.existingModules)
    }

    @Test
    fun `パスの決まっているテンプレートには付かない`() {
        val fixed = detailOf("sample-android-with-captures", "feature.FeatureTest.home")

        assertNull(fixed.files.single().modulePlacement)
        assertEquals(listOf(null), fixed.captures.map { it.existingModules })
    }

    @Test
    fun `modulePlacementsの無い古いJSONはモジュールのcaptureの下を決まらないまま読む`() {
        val details = parse(jsonWith(placements = null))

        assertNull(details.single().files.single().modulePlacement)
        assertNull(details.single().captures.single { it.name == "feature" }.existingModules)
    }

    @Test
    fun `当てはまるモジュールが無ければ選べる値は空になる`() {
        val details = parse(jsonWith(placements = """[{"template": "feature.Screen", "modulePattern": ":feature:*", "captureNames": ["feature"], "modules": []}]"""))

        assertEquals(emptyList<ModuleChoiceModel>(), details.single().files.single().modulePlacement?.modules)
        assertEquals(emptyList<String>(), details.single().captures.single { it.name == "feature" }.existingModules)
    }

    @Test
    fun `一覧に無いテンプレートの配置は捨てる`() {
        val details = parse(jsonWith(placements = """[{"template": "other.Gone", "modulePattern": ":feature:*", "captureNames": ["feature"], "modules": []}]"""))

        assertNull(details.single().files.single().modulePlacement)
    }

    @Test(expected = KatachiIncompatibleTemplateJsonException::class)
    fun `modulePlacementsの要素にキーが欠けていれば互換の無いJSONとして落とす`() {
        parse(jsonWith(placements = """[{"template": "feature.Screen", "captureNames": ["feature"], "modules": []}]"""))
    }

    /** One template, `feature.Screen`, below `":feature:*"`; [placements] is the `modulePlacements` value, absent when `null`. */
    private fun jsonWith(placements: String?): String = """
        {
          "templates": [{"template": "feature.Screen", "id": null, "title": "feature.Screen", "roleName": "feature.Screen", "summary": null,
            "parameterNames": [], "captures": [], "conflict": false}],
          "details": [{
            "template": "feature.Screen", "id": null, "title": "feature.Screen", "roleName": "feature.Screen", "summary": null,
            "parameters": [],
            "files": [{"pattern": "feature/${'$'}{feature}/${'$'}{name}Screen.kt", "fileName": "${'$'}{name}Screen.kt", "path": null,
              "captures": ["feature", "name"], "parameters": [], "content": ""}],
            "branches": [], "exampleCommand": "",
            "captures": [
              {"name": "feature", "kind": "ModuleCapture", "pattern": ":feature:*", "position": 0, "segment": ":feature:${'$'}{feature}"},
              {"name": "name", "kind": "PathCapture", "pattern": "feature/*/*Screen.kt", "position": 2, "segment": "${'$'}{name}Screen.kt"}
            ]
          }]${placements?.let { ",\n\"modulePlacements\": $it" }.orEmpty()}
        }
    """.trimIndent()
}
