package me.tbsten.katachi.test

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Every `public` declaration of the constraint API carries a KDoc with a worked example.
 *
 * The rule is the repository's, not this spec's; what this spec adds is that forgetting it is
 * a red test rather than something a reviewer has to notice. It reads sources as text, the
 * same way `PackageDependencySpec` does, because the alternative — parsing Kotlin — would be a
 * far bigger thing to maintain than the rule it is guarding.
 *
 * **Deliberately not the whole repository.** Of the public declarations `:katachi` already
 * had, a large share carry no example, and turning them all red at once is not something this
 * change can do. The files below are the ones the constraint API is being written into, so
 * they start out holding the line. Widening it to everything is tracked in `open-issues.md`.
 */
private class CoveredDirectory(val path: String, val isCovered: (String) -> Boolean)

private val COVERED: List<CoveredDirectory> = listOf(
    CoveredDirectory("dsl") { name ->
        name.startsWith("Constraint") || name == "FileSetConstraint.kt" || name == "DeclaredConstraint.kt"
    },
    CoveredDirectory("scan") { name ->
        name.startsWith("Constraint") || name == "ViolationDetail.kt" || name == "UncheckedCheck.kt"
    },
    CoveredDirectory("check") { name -> name.startsWith("Constraint") },
)

/** What a KDoc has to hold. The number after it is the example's own. */
private const val EXAMPLE_HEADING: String = "## Example"

/** The directory the sources live in, found from wherever the test happens to be run. */
private fun sourceRoot(): File {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi")
    return if (fromModule.isDirectory) fromModule else File("katachi/src/main/kotlin/me/tbsten/katachi")
}

/**
 * The covered files that exist.
 *
 * A file the design has not been written yet is skipped rather than reported: the constraint
 * API arrives over several steps, and a spec that failed until the last of them would be a
 * spec nobody could keep green.
 */
private fun coveredFiles(): List<File> = COVERED.flatMap { covered ->
    val directory = File(sourceRoot(), covered.path)
    if (!directory.isDirectory) return@flatMap emptyList()
    directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "kt" && covered.isCovered(it.name) }
        .sortedBy { it.path }
}

/** One `public` declaration whose KDoc does not show how to use it. */
private class UndocumentedDeclaration(val file: File, val line: Int, val declaration: String) {
    fun describe(): String = "${file.path}:$line  $declaration"
}

/**
 * Whether the KDoc block ending just above [index] holds an example.
 *
 * Annotations and blank lines sit between a KDoc and what it documents, so they are stepped
 * over. A declaration with no KDoc at all reads as undocumented, which is the same failure.
 */
private fun hasExampleAbove(lines: List<String>, index: Int): Boolean {
    var cursor = index - 1
    while (cursor >= 0) {
        val trimmed = lines[cursor].trim()
        if (trimmed.isEmpty() || trimmed.startsWith("@")) {
            cursor--
            continue
        }
        break
    }
    if (cursor < 0 || lines[cursor].trim() != "*/") return false
    val end = cursor
    while (cursor >= 0 && !lines[cursor].trim().startsWith("/**")) cursor--
    if (cursor < 0) return false
    return lines.subList(cursor, end + 1).any { it.contains(EXAMPLE_HEADING) }
}

/**
 * Every `public` declaration of [file] with no example above it.
 *
 * A KDoc line is never a declaration: every line of one starts with `*` once trimmed, so a
 * `public …` written inside a fenced example cannot be mistaken for the real thing. The block
 * is tracked anyway, so that stays true of a KDoc written some other way.
 */
private fun undocumentedIn(file: File): List<UndocumentedDeclaration> {
    val lines = file.readLines()
    var inKdoc = false
    return lines.mapIndexedNotNull { index, line ->
        val trimmed = line.trim()
        if (!inKdoc && trimmed.startsWith("/**")) inKdoc = !trimmed.endsWith("*/")
        else if (inKdoc && trimmed.endsWith("*/")) inKdoc = false
        else if (!inKdoc && trimmed.startsWith("public ") && !hasExampleAbove(lines, index)) {
            return@mapIndexedNotNull UndocumentedDeclaration(file, index + 1, trimmed)
        }
        null
    }
}

class ConstraintApiKdocSpec : FreeSpec({
    "制約 API の KDoc" - {
        "public 宣言がすべて利用例つきの KDoc を持つ" {
            val undocumented = coveredFiles().flatMap { undocumentedIn(it) }.map { it.describe() }

            withClue("`$EXAMPLE_HEADING N: ...` + ```kt フェンスの無い public 宣言: $undocumented") {
                undocumented.shouldBeEmpty()
            }
        }

        "走査対象のファイルが1つ以上見つかっている" {
            withClue("対象が0件だと、この spec は何も見ないまま緑になる。") {
                coveredFiles().size shouldBeGreaterThan 0
            }
        }
    }
})
