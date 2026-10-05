package me.tbsten.katachi.intellij.data.definition

import me.tbsten.katachi.intellij.testing.DefinitionFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The port gives the answers katachi's `Glob` gives: every case of the `contract/glob` JSON files compiles and matches
 * (or fails to compile) the same. The files are generated from katachi's own `Glob` (`GlobConformanceFixtureSpec`);
 * nothing in them is written by hand here.
 */
class KatachiGlobConformanceTest {
    @Test
    fun `katachi が生成した適合ケースがあり 文法の誤りと一致と不一致のすべてを含む`() {
        assertTrue(DefinitionFixtures.globCaseFiles().contains("glob-cases"))
        val cases = DefinitionFixtures.globCases("glob-cases")
        assertTrue(cases.size > 200)
        assertTrue(cases.any { it.error })
        assertTrue(cases.any { it.matches == true })
        assertTrue(cases.any { it.matches == false })
    }

    @Test
    fun `生成した適合ケースは literalPrefix と hasWildcard の答えを持つ`() {
        val generated = DefinitionFixtures.globCases("glob-cases").filterNot { it.error }
        assertTrue(generated.isNotEmpty())
        assertTrue(generated.all { it.literalPrefix != null && it.hasWildcard != null })
        assertTrue("some pattern has a non-empty literal prefix", generated.any { it.literalPrefix.orEmpty().isNotEmpty() })
    }

    @Test
    fun `適合ケースのすべてで移植の答えが同じ`() {
        for (file in DefinitionFixtures.globCaseFiles()) {
            for (case in DefinitionFixtures.globCases(file)) {
                val label = "$file: `${case.pattern}`" + (case.path?.let { " against `$it`" } ?: "")
                val compiled = runCatching { KatachiGlob.compile(case.pattern) }
                if (case.error) {
                    assertTrue("$label should not compile", compiled.exceptionOrNull() is KatachiGlobSyntaxException)
                } else {
                    val glob = compiled.getOrElse { throw AssertionError("$label should compile", it) }
                    assertEquals(label, case.matches, glob.matches(case.path ?: error("no path in $label")))
                    case.literalPrefix?.let { assertEquals("$label: literalPrefix", it, glob.literalPrefix) }
                    case.hasWildcard?.let { assertEquals("$label: hasWildcard", it, glob.hasWildcard) }
                }
            }
        }
    }
}
