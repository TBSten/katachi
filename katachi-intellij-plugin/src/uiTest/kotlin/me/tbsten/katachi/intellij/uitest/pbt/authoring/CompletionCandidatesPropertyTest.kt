package me.tbsten.katachi.intellij.uitest.pbt.authoring

import io.kotest.property.Arb
import io.kotest.property.PropTest
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.model.TemplateSetModel
import me.tbsten.katachi.intellij.presentation.authoring.argCandidates
import me.tbsten.katachi.intellij.presentation.authoring.bindTemplate
import me.tbsten.katachi.intellij.presentation.authoring.captureCandidates
import me.tbsten.katachi.intellij.presentation.authoring.templateSpecCandidates
import me.tbsten.katachi.intellij.testing.branch
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.definitionModel
import me.tbsten.katachi.intellij.testing.definitionSnapshot
import me.tbsten.katachi.intellij.testing.layoutEntry
import me.tbsten.katachi.intellij.testing.module
import me.tbsten.katachi.intellij.testing.roleModel
import me.tbsten.katachi.intellij.testing.stringParam
import me.tbsten.katachi.intellij.testing.template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * What the strings of a definition offer (ide-authoring design 3.1), over generated definitions: templates with
 * parameters, branch-added parameters and captures; roles whose layout entries carry `.template { }` sites in a
 * few files and lines; and a generated answer to "does this site resolve to the open file".
 *
 * - the template a `captureValue` stands for is on the open file, the line of the call when some site has it, else the
 *   nearest, the first declared on a tie; no site there, no template
 * - its candidates are that template's captures, each once
 * - `arg` offers the parameters of every branch and the captures, each name once; an unknown template offers nothing
 * - the specifiers offered are those of the templates, each once, and no alias repeats the specifier
 * - a template set's row is never offered as a member and has no `arg` candidates, though it sits among the templates
 * - `arg` reads a short specifier (the role's plain name, with `.id` for a template that has one) as the complete one
 */
class CompletionCandidatesPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261017L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    // covers: 補完 (a)
    @Test
    fun `captureValue が指すテンプレートは 同じ行を持つものがあればそれ 無ければ最も近いもの 同じ近さなら先に宣言されたもの`() = runBlocking<Unit> {
        checkAll(config, scenarioArb) { scenario ->
            val binding = bindTemplate(scenario.fileName, scenario.line, scenario.snapshots, scenario.resolves)
            val expected = scenario.sitesHere().minByOrNull { abs(it.line - scenario.line) }
            if (expected == null) {
                assertNull("$scenario", binding)
            } else {
                assertNotNull("$scenario", binding)
                assertEquals("$scenario", expected.spec, binding!!.template.template)
                assertEquals("$scenario", expected.snapshot, binding.snapshot)
            }
        }
    }

    // covers: 補完 (b)
    @Test
    fun `captureValue の候補は結んだテンプレートの capture を1つずつ`() = runBlocking<Unit> {
        checkAll(config, scenarioArb) { scenario ->
            val binding = bindTemplate(scenario.fileName, scenario.line, scenario.snapshots, scenario.resolves) ?: return@checkAll
            val names = captureCandidates(binding).map { it.text }
            assertEquals("$scenario", binding.template.detail!!.captures.map { it.name }.distinct(), names)
            assertTrue("$scenario", captureCandidates(binding).all { it.tailText == " (${binding.template.template})" })
        }
    }

    // covers: 補完 (c)
    @Test
    fun `arg の候補は どの分岐のパラメータも capture も 名前ごとに1つ 知らないテンプレートには無い`() = runBlocking<Unit> {
        checkAll(config, scenarioArb) { scenario ->
            for (template in scenario.allTemplates) {
                val detail = template.detail!!
                val expected = (detail.captures.map { it.name } + detail.parameters.map { it.name } + detail.branches.flatMap { b -> b.addedParameters.map { it.name } })
                    .toSet()
                val names = argCandidates(template.template, scenario.snapshots).map { it.text }
                assertEquals("$scenario ${template.template}", expected, names.toSet())
                assertEquals("$scenario ${template.template}", names.size, names.distinct().size)
            }
            assertEquals("$scenario", emptyList<String>(), argCandidates("no.such.template", scenario.snapshots).map { it.text })
        }
    }

    // covers: 補完 (d)
    @Test
    fun `template の候補は全テンプレートの指定を1つずつ 別名は指定そのものを繰り返さない`() = runBlocking<Unit> {
        checkAll(config, scenarioArb) { scenario ->
            val candidates = templateSpecCandidates(scenario.snapshots)
            assertEquals("$scenario", scenario.allTemplates.map { it.template }.distinct(), candidates.map { it.text })
            assertTrue("$scenario", candidates.all { it.text !in it.aliases })
            // The role's own name and the id always find the template.
            for (template in scenario.allTemplates) {
                val candidate = candidates.first { it.text == template.template }
                assertTrue("$scenario ${template.template}", template.roleName.substringAfterLast('.') in candidate.aliases + candidate.text.split('.'))
            }
        }
    }

    // covers: 補完 (e)
    @Test
    fun `組の行は template の候補にも arg の候補にも出ない`() = runBlocking<Unit> {
        checkAll(config, scenarioArb) { scenario ->
            val offered = templateSpecCandidates(scenario.snapshots).map { it.text }
            for (set in scenario.setRows) {
                assertTrue("$scenario ${set.template}", set.template !in offered)
                assertEquals("$scenario ${set.template}", emptyList<String>(), argCandidates(set.template, scenario.snapshots).map { it.text })
            }
        }
    }

    // covers: 補完 (f)
    @Test
    fun `arg の候補は 短い指定でも完全な指定と同じ`() = runBlocking<Unit> {
        checkAll(config, scenarioArb) { scenario ->
            for (template in scenario.allTemplates) {
                val full = argCandidates(template.template, scenario.snapshots).map { it.text }
                val plainRole = template.roleName.substringAfterLast('.')
                val short = listOfNotNull(plainRole, template.summary.id?.let { "$plainRole.$it" })
                for (spec in short) {
                    assertEquals("$scenario $spec", full, argCandidates(spec, scenario.snapshots).map { it.text })
                }
            }
        }
    }
}

private const val FILE_A = "ScreenRole.kt"
private const val FILE_B = "RepositoryRole.kt"
private val PARAMETER_NAMES = listOf("withState", "title", "count", "name", "kind")
private val CAPTURE_NAMES = listOf("feature", "name", "layer")

private data class Site(val snapshot: DefinitionSnapshot, val fileName: String, val line: Int, val spec: String)

private class Scenario(
    val snapshots: List<DefinitionSnapshot>,
    val fileName: String,
    val line: Int,
    private val sites: List<Site>,
    private val resolved: Set<Triple<String, String, Int>>,
) {
    val resolves: (DefinitionSnapshot, DeclarationSiteRef) -> Boolean =
        { snapshot, site -> Triple(snapshot.module.gradlePath, site.fileName, site.line) in resolved }

    /** The templates a member can run: every row that is not a template set's. */
    val allTemplates: List<TemplateModel> = snapshots.flatMap { it.templates }.filter { it.set == null }.distinctBy { it.template }

    /** The rows of template sets, which sit among the templates of the list. */
    val setRows: List<TemplateModel> = snapshots.flatMap { it.templates }.filter { it.set != null }.distinctBy { it.template }

    /** The sites on the open file, in declaration order. */
    fun sitesHere(): List<Site> =
        sites.filter { it.fileName == fileName && Triple(it.snapshot.module.gradlePath, it.fileName, it.line) in resolved }

    override fun toString(): String = "Scenario(file=$fileName, line=$line, sites=${sites.map { "${it.fileName}:${it.line}->${it.spec}" }}, resolved=$resolved)"
}

private val scenarioArb: Arb<Scenario> = arbitrary { rs ->
    val random = rs.random
    val roleNames = listOf("ui.Screen", "data.Repository", "domain.UseCase")
    val snapshots = (0 until random.nextInt(1, 3)).map { index ->
        val gradlePath = ":arch$index"
        val templates = roleNames.take(random.nextInt(1, roleNames.size + 1)).mapIndexed { n, roleName ->
            val captures = CAPTURE_NAMES.shuffled(random).take(random.nextInt(0, 3)).mapIndexed { i, name -> capture(name, module = i == 1) }
            val parameters = PARAMETER_NAMES.shuffled(random).take(random.nextInt(0, 3)).map { stringParam(it) }
            val branches = if (random.nextBoolean()) listOf(branch("kind", "A", addedParameters = PARAMETER_NAMES.shuffled(random).take(2).map { stringParam(it) })) else emptyList()
            template(roleName, parameters = parameters, branches = branches, captures = captures, id = if (n == 0) null else "t$n")
        }
        gradlePath to templates
    }
    val sites = mutableListOf<Site>()
    val built = snapshots.map { (gradlePath, templates) ->
        val roles = templates.map { template ->
            val entries = (0 until random.nextInt(1, 4)).map {
                val fileName = if (random.nextBoolean()) FILE_A else FILE_B
                val line = random.nextInt(1, 30)
                layoutEntry("src/${template.title}$it.kt").copy(template = TemplateRef(template.template, template.summary.id, null, DeclarationSiteRef(fileName, line)))
            }
            roleModel(template.roleName, *entries.toTypedArray())
        }
        val setRow = template("feature.NewFeature${gradlePath.drop(1)}", parameters = listOf(stringParam("title"))).copy(set = TemplateSetModel("NewFeature", emptyList()))
        definitionSnapshot(definitionModel(*roles.toTypedArray()), module(gradlePath)).copy(templates = templates + setRow)
    }
    for (snapshot in built) {
        for (role in snapshot.definition!!.roles) {
            for (entry in role.layout) {
                val ref = entry.template!!
                val at = ref.declaredAt!!
                sites += Site(snapshot, at.fileName, at.line, ref.specifier)
            }
        }
    }
    val resolved = sites.filter { random.nextInt(4) != 0 }.map { Triple(it.snapshot.module.gradlePath, it.fileName, it.line) }.toSet()
    Scenario(built, if (random.nextBoolean()) FILE_A else FILE_B, random.nextInt(1, 30), sites, resolved)
}
