package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.testing.ContractFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the plugin to the `templateSets[]` / `templateSetDetails[]` katachi really writes for the
 * samples: `contract/json/real-{jvm,android,kmp}-templates.json` are the unedited outputs of
 * `:architecture-test:katachiInternalTemplatesJson` of `sample/{jvm,android,kmp}`. Unlike
 * `TemplateDescriptionJsonTemplateSetTest` (katachi's synthetic JSON), a change in how a sample
 * declares its `templateSet` or in what katachi writes for it shows up here once they are taken again.
 */
class RealTemplateSetContractTest {
    private class Expected(val sample: String, val set: String, val parameter: String, val members: List<String>)

    private val samples = listOf(
        Expected("jvm", "api.Endpoint", "resource", listOf("api.Controller", "domain.Service")),
        Expected(
            "android", "data.NewRepository", "name",
            listOf("data.RepositoryInterface.user", "data.RepositoryImplementation.user", "testing.Fake"),
        ),
        Expected("kmp", "data.NewRepository", "name", listOf("data.RepositoryInterface", "data.RepositoryImplementation")),
    )

    private fun rowsOf(sample: String): List<TemplateModel> = ContractFixtures.templates("real-$sample-templates")

    @Test
    fun `実物の templates json に組の行が 1 つ出て メンバの template はどれも通常の行にある`() {
        samples.forEach { expected ->
            val rows = rowsOf(expected.sample)
            val sets = rows.filter { it.set != null }
            assertTrue("${expected.sample}: ${expected.set} not in ${sets.map { it.template }}", expected.set in sets.map { it.template })
            val plain = rows.filter { it.set == null }.map { it.template }.toSet()
            val set = sets.single { it.template == expected.set }
            val members = requireNotNull(set.set).members.map { it.template }
            assertEquals(expected.sample, expected.members, members)
            assertTrue("${expected.sample}: members $members not in $plain", plain.containsAll(expected.members))
        }
    }

    @Test
    fun `組の行は組のパラメータと全メンバのファイルと生成コマンドを持つ`() {
        samples.forEach { expected ->
            val set = rowsOf(expected.sample).single { it.template == expected.set }
            val detail = requireNotNull(set.detail) { "${expected.sample}: the set has no detail" }
            assertEquals(expected.sample, listOf(expected.parameter), detail.parameters.map { it.name })
            assertEquals(expected.sample, expected.members.size, requireNotNull(set.set).members.size)
            assertTrue("${expected.sample}: no files", detail.files.isNotEmpty())
            assertTrue(expected.sample, detail.exampleCommand.contains("--arg template=${expected.set}"))
            assertNull(expected.sample, set.unavailability)
        }
    }

    @Test
    fun `組の行は組の宣言した group の下に並び 通常の行より後ろに来る`() {
        samples.forEach { expected ->
            val rows = rowsOf(expected.sample)
            assertEquals(expected.sample, rows.indexOfFirst { it.set != null }, rows.indexOfLast { it.set == null } + 1)
            assertEquals(expected.sample, expected.set.substringBeforeLast('.'), rows.single { it.template == expected.set }.groupPath)
        }
    }
}
