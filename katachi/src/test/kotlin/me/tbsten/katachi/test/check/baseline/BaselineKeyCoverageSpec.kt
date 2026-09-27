package me.tbsten.katachi.test.check.baseline

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.io.File

/**
 * Every violation katachi declares has to be told what the baseline does with it: which values
 * make up its key, or that it is never held back. `baselineRuleOf` has an `else` branch for the
 * violations of other artifacts, so the compiler cannot notice a new katachi violation without a
 * branch of its own; this spec reads the sources and does.
 */
private val CLASS_HEADER = Regex("""(?m)^public class (\w+)""")
private val SUPERTYPE_LINE = Regex("""(?m)^\)\s*:\s*(.*?)\s*\{\s*$""")
private val VIOLATION_SUPERTYPE = Regex("""(?<!<)\bViolation\b""")
private val BRANCH_NAME = Regex("""is (\w+) ->""")

private fun sourceFile(relative: String): File {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi/$relative")
    return if (fromModule.exists()) fromModule else File("katachi/src/main/kotlin/me/tbsten/katachi/$relative")
}

private fun violationClassNames(): List<String> =
    sourceFile("check").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .flatMap { file ->
            val content = file.readText()
            val classes = CLASS_HEADER.findAll(content).toList()
            classes.mapIndexedNotNull { index, match ->
                val spanEnd = classes.getOrNull(index + 1)?.range?.first ?: content.length
                val span = content.substring(match.range.last + 1, spanEnd)
                val supertypes = SUPERTYPE_LINE.find(span)?.groupValues?.get(1)
                match.groupValues[1].takeIf { supertypes != null && VIOLATION_SUPERTYPE.containsMatchIn(supertypes) }
            }
        }
        .distinct()
        .toList()

private fun ruleBranches(): Set<String> {
    val content = sourceFile("check/internal/BaselineKey.kt").readText()
    val body = content.substringAfter("fun baselineRuleOf(").substringBefore("\n}")
    return BRANCH_NAME.findAll(body).map { it.groupValues[1] }.toSet()
}

class BaselineKeyCoverageSpec : FreeSpec({
    "katachi 自前の Violation の実装すべてに、キーの規則か対象外が割り当てられている" {
        val names = violationClassNames()
        names.shouldNotBeEmpty()
        val missing = names.filterNot { it in ruleBranches() }

        withClue("baselineRuleOf に `is <name> ->` が無い violation 実装: $missing") {
            missing.shouldBeEmpty()
        }
    }
})
