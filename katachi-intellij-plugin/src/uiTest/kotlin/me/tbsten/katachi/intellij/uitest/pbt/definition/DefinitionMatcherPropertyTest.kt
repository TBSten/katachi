package me.tbsten.katachi.intellij.uitest.pbt.definition

import io.kotest.property.PropTest
import io.kotest.property.checkAll
import io.kotest.property.toPropTestConfig
import kotlinx.coroutines.runBlocking
import me.tbsten.katachi.intellij.data.definition.DefinitionMatcher
import me.tbsten.katachi.intellij.data.definition.KatachiGlob
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.MatchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The definition matcher over generated definitions (roles with files, `anyFile()`, directories and `ignore()`,
 * their patterns with `**` and escaped `*`, some entries marked synthetic or place; constraints with their coverage)
 * and generated paths (some under `.idea` / `.gradle`), against [referenceAnswer], which works the answer out the way
 * katachi's `Scan` walks, one directory at a time, trying every entry. Together: the walk stops at the first directory
 * that is not walked, ignored or undeclared; a path nobody holds is unexpected; a held path has one entry per role
 * and the constraints that cover it; the places offered are the near ones, at most three.
 */
class DefinitionMatcherPropertyTest {
    private val seed = System.getProperty("katachi.pbt.seed")?.toLong() ?: 20261006L
    private val scale = System.getProperty("katachi.pbt.scale")?.toDouble() ?: 1.0
    private val config get() = PropTest(seed = seed, iterations = (300 * scale).toInt().coerceAtLeast(1)).toPropTestConfig()

    private inline fun forEachAnswer(scenario: MatcherScenario, check: (path: String, actual: me.tbsten.katachi.intellij.model.FileMatch, expected: ReferenceAnswer) -> Unit) {
        val matcher = DefinitionMatcher(scenario.model)
        for (path in scenario.paths) check(path, matcher.matchFile(path), referenceAnswer(scenario.model, path))
    }

    // covers: 照合器 (b)
    @Test
    fun `見ない名前か ignore の下か 知られていない階層の下なら 上から数えて最初のその階層で止まり 他の答えは付かない`() = runBlocking<Unit> {
        checkAll(config, matcherScenarioArb) { scenario ->
            forEachAnswer(scenario) { path, actual, expected ->
                val label = "$scenario\npath=$path"
                val stop = expected.stop
                if (stop == null) {
                    assertTrue("$label: ${actual.outcome}", actual.outcome == MatchOutcome.Matched || actual.outcome == MatchOutcome.Unexpected)
                } else {
                    // An entry is compared by its pattern: two `ignore()` entries of one pattern are one answer.
                    assertEquals(label, describe(stop), describe(actual.outcome))
                    assertTrue(label, actual.matches.isEmpty() && actual.constraints.isEmpty())
                }
            }
        }
    }

    // covers: 照合器 (c) (e)
    @Test
    fun `役割が当たらないのは Unexpected のときだけで 知られていない階層の下は最初のその階層を指す`() = runBlocking<Unit> {
        checkAll(config, matcherScenarioArb) { scenario ->
            forEachAnswer(scenario) { path, actual, expected ->
                val label = "$scenario\npath=$path"
                assertEquals(label, expected.claims.isNotEmpty(), actual.matches.isNotEmpty())
                assertEquals(label, expected.claims.isNotEmpty(), actual.outcome == MatchOutcome.Matched)
                if (expected.stop == null && expected.claims.isEmpty()) assertEquals(label, MatchOutcome.Unexpected, actual.outcome)
            }
        }
    }

    private fun describe(outcome: MatchOutcome?): String = when (outcome) {
        is MatchOutcome.Ignored -> "Ignored(${outcome.entry.pattern})"
        else -> outcome.toString()
    }

    // covers: 照合器 (a) (d)
    @Test
    fun `当たった役割は1つにつき1 entry で 名指しした役割が先 そして被覆の中の制約が全部付く`() = runBlocking<Unit> {
        checkAll(config, matcherScenarioArb) { scenario ->
            forEachAnswer(scenario) { path, actual, expected ->
                val label = "$scenario\npath=$path"
                assertEquals(label, expected.claims.map { (r, e) -> r.qualifiedName to e.index }, actual.matches.map { it.role.qualifiedName to it.entry.index })
                assertEquals(label, expected.constraints.map { (r, c) -> r.qualifiedName to c.name }, actual.constraints.map { it.role.qualifiedName to it.constraint.name })
                // Every entry chosen really matches: the path for a file, its directory for anyFile().
                for (match in actual.matches) {
                    val glob = KatachiGlob.compile(match.entry.pattern)
                    val target = if (match.entry.kind == LayoutEntryKind.AnyFile) path.substringBeforeLast('/', "") else path
                    assertTrue("$label: ${match.entry.pattern}", glob.matches(target))
                }
            }
        }
    }

    // covers: 照合器 (f)
    @Test
    fun `近い場所は Unexpected のときだけ付き 共有階層の多い順の最大3件で 参照実装と同じ`() = runBlocking<Unit> {
        checkAll(config, matcherScenarioArb) { scenario ->
            forEachAnswer(scenario) { path, actual, expected ->
                val label = "$scenario\npath=$path"
                if (actual.outcome is MatchOutcome.Matched || actual.outcome is MatchOutcome.Ignored || actual.outcome is MatchOutcome.NotWalked) {
                    assertTrue(label, actual.nearby.isEmpty())
                } else {
                    assertEquals(label, expected.nearby, actual.nearby.map { it.role.qualifiedName to it.directory })
                    assertTrue(label, actual.nearby.size <= 3 && actual.nearby.all { '*' !in it.directory && it.directory.isNotEmpty() })
                }
            }
        }
    }

    // covers: 照合器
    @Test
    fun `同じパスを何度聞いても同じ答えで 索引は一度作れば足りる`() = runBlocking<Unit> {
        checkAll(config, matcherScenarioArb) { scenario ->
            val matcher = DefinitionMatcher(scenario.model)
            for (path in scenario.paths) assertEquals("$scenario\npath=$path", matcher.matchFile(path), matcher.matchFile(path))
            // Roles are listed once each and keep the model's own instances.
            val first = scenario.paths.map { matcher.matchFile(it) }
            for (match in first) for (claim in match.matches) assertSame(scenario.model.roles.single { it.qualifiedName == claim.role.qualifiedName }, claim.role)
        }
    }
    // covers: 生成の偏り
    @Test
    fun `生成した定義とパスは どの答えの種類も 制約も nearby も十分な件数で含む`() = runBlocking<Unit> {
        val seen = HashMap<String, Int>()
        checkAll(config, matcherScenarioArb) { scenario ->
            val matcher = DefinitionMatcher(scenario.model)
            for (path in scenario.paths) {
                val match = matcher.matchFile(path)
                seen.merge(match.outcome::class.simpleName.orEmpty(), 1, Int::plus)
                if (match.constraints.isNotEmpty()) seen.merge("with constraints", 1, Int::plus)
                if (match.nearby.isNotEmpty()) seen.merge("with nearby", 1, Int::plus)
                if (match.matches.size > 1) seen.merge("several roles", 1, Int::plus)
            }
        }
        for (key in listOf("Matched", "Ignored", "NotWalked", "Unexpected", "UnexpectedUnder", "with constraints", "with nearby", "several roles")) {
            assertTrue("the generators never produced `$key`: $seen", (seen[key] ?: 0) >= 20 * scale.coerceAtMost(1.0))
        }
    }
}
