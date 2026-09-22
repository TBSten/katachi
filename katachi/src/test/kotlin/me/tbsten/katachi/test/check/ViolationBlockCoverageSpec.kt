package me.tbsten.katachi.test.check

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/**
 * `blockOf`'s `when` lost compile-time exhaustiveness the moment `Violation` stopped being
 * sealed (`else -> foreignBlock(violation)` compiles whether or not katachi wrote a dedicated
 * block for a new violation of its own). This spec puts that check back as a machine check
 * instead of a reviewer's memory, the same way `PackageDependencySpec` reads sources as text
 * rather than parsing them: every `public class` under `scan/` whose supertype list mentions
 * `Violation` has to have its own `is <name> ->` branch in `blockOf`.
 */
private val CLASS_HEADER = Regex("""(?m)^public class (\w+)""")
private val SUPERTYPE_LINE = Regex("""(?m)^\)\s*:\s*(.*?)\s*\{\s*$""")
private val VIOLATION_SUPERTYPE = Regex("""\bViolation\b""")
private val BRANCH_NAME = Regex("""is (\w+) ->""")

/** The `scan/` directory, found from wherever the test happens to be run. */
private fun scanSourceRoot(): File {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi/scan")
    return if (fromModule.isDirectory) fromModule else File("katachi/src/main/kotlin/me/tbsten/katachi/scan")
}

private fun violationReportFile(): File {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi/check/ViolationReport.kt")
    return if (fromModule.isFile) {
        fromModule
    } else {
        File("katachi/src/main/kotlin/me/tbsten/katachi/check/ViolationReport.kt")
    }
}

/**
 * The names of every `public class` in [content] whose primary constructor is followed by
 * `: ... Violation ... {` — i.e. that implements `me.tbsten.katachi.scan.Violation`.
 *
 * Reads the primary constructor's closing `)` at the start of a line, the way this codebase
 * formats it, rather than the text between the class name and the first `{`: a KDoc example
 * inside the constructor's parameter list (a `## Example` fenced ```kt block) can itself
 * contain a `{`, which would otherwise cut the header short before the real supertype list.
 */
private fun violationClassNamesIn(content: String): List<String> {
    val classes = CLASS_HEADER.findAll(content).toList()
    return classes.mapIndexedNotNull { index, match ->
        val name = match.groupValues[1]
        val spanStart = match.range.last + 1
        val spanEnd = classes.getOrNull(index + 1)?.range?.first ?: content.length
        val span = content.substring(spanStart, spanEnd)
        val supertypes = SUPERTYPE_LINE.find(span)?.groupValues?.get(1)
        name.takeIf { supertypes != null && VIOLATION_SUPERTYPE.containsMatchIn(supertypes) }
    }
}

private fun violationImplementationNames(): List<String> =
    scanSourceRoot().walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .sortedBy { it.path }
        .flatMap { file -> violationClassNamesIn(file.readText()) }
        .distinct()
        .toList()

/** The `is <name> ->` branches inside `blockOf`'s `when (violation) { ... }`. */
private fun blockOfBranchNames(): Set<String> {
    val content = violationReportFile().readText()
    val body = content.substringAfter("when (violation) {").substringBefore("\n}")
    return BRANCH_NAME.findAll(body).map { it.groupValues[1] }.toSet()
}

class ViolationBlockCoverageSpec : FreeSpec({
    "blockOf の網羅性" - {
        "scan 配下の Violation 実装クラスがそれぞれ専用の分岐を持つ" {
            val branches = blockOfBranchNames()
            val missing = violationImplementationNames().filterNot { it in branches }

            withClue("blockOf に `is <name> ->` が無い violation 実装: $missing") {
                missing.shouldBeEmpty()
            }
        }
    }
})
