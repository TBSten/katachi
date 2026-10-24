package me.tbsten.katachi.test.konsist

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Every `public` declaration of `:katachi-konsist`'s main sources carries a KDoc, and every one
 * written at the top level of its file -- a type or a top-level function -- a worked example.
 *
 * A member needs no example of its own, as `docs/internal/kotlin/kotlin.md` says: its type's
 * example shows how it is called, and the eleven `allow(...)` overloads of one kind would
 * otherwise repeat one example eleven times.
 *
 * The whole repository now carries this rule as `KDOC_EXAMPLE_RULE`, a `konsist { }` on every
 * role of `:katachi` and on this module's own role — see
 * `architecture-test/src/test/kotlin/me/tbsten/katachi/test/architecture/KdocExamples.kt`.
 * That is the rule; this spec is a second reading of it, kept because it is the one that fails
 * inside this module's own `test` task rather than in `:architecture-test`. It asks the same as
 * that rule, read off the text: every line starting with `public ` needs a KDoc, and one at the
 * top level of its file an example too.
 *
 * Reads sources as text: parsing Kotlin would be a far bigger thing to maintain than the rule
 * it guards.
 *
 * TODO: decide whether a second reading of the same rule is worth two implementations, or
 *   whether this spec should go the way `FileConstraintApiKdocSpec` did once the constraint landed.
 */

/** What a KDoc has to hold. The number after it is the example's own. */
private const val EXAMPLE_HEADING: String = "## Example"

/** The directory this module's main sources live in, found from wherever the test happens to run. */
private fun sourceRoot(): File {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi/konsist")
    return if (fromModule.isDirectory) {
        fromModule
    } else {
        File("katachi-konsist/src/main/kotlin/me/tbsten/katachi/konsist")
    }
}

private fun sourceFiles(): List<File> =
    sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()

/** One `public` declaration with no KDoc, or a top-level one whose KDoc does not show how to use it. */
private class UndocumentedDeclaration(val file: File, val line: Int, val declaration: String) {
    fun describe(): String = "${file.path}:$line  $declaration"
}

/**
 * Whether the KDoc block ending just above [index] holds an example -- or, when [exampleRequired]
 * is false, whether there is a KDoc at all.
 *
 * Blank lines, annotations and `context(...)` receivers sit between a KDoc and what it
 * documents, so they are stepped over. A single-line marker such as `@ExperimentalKatachiApi`
 * would need no more than that, but this module's shadowed Konsist assertions carry multi-line
 * `@Deprecated(...)` blocks, so a bare
 * `startsWith("@")` on one line is not enough: the line directly above the declaration is often
 * just the annotation's closing `)`. Paren depth is tracked back to whichever line opens that
 * block (an unbalanced text scan, same as the rest of this spec — good enough for source that
 * balances its own parentheses, which every annotation and `context(...)` here does).
 * A declaration with no KDoc at all reads as undocumented, which is the same failure.
 */
private fun hasExampleAbove(lines: List<String>, index: Int, exampleRequired: Boolean): Boolean {
    var cursor = index - 1
    while (cursor >= 0) {
        val trimmed = lines[cursor].trim()
        if (trimmed.isEmpty()) {
            cursor--
            continue
        }
        val depth = trimmed.count { it == ')' } - trimmed.count { it == '(' }
        if (depth > 0 && !trimmed.startsWith("@") && !trimmed.startsWith("context(")) {
            // The tail of a multi-line annotation or context block opened further up — walk
            // back to the line that opens it before deciding what came before that.
            var remaining = depth
            cursor--
            while (cursor >= 0 && remaining > 0) {
                val above = lines[cursor].trim()
                remaining += above.count { it == ')' } - above.count { it == '(' }
                cursor--
            }
            continue
        }
        if (trimmed.startsWith("@") || trimmed.startsWith("context(")) {
            cursor--
            continue
        }
        break
    }
    if (cursor < 0 || lines[cursor].trim() != "*/") return false
    val end = cursor
    while (cursor >= 0 && !lines[cursor].trim().startsWith("/**")) cursor--
    if (cursor < 0) return false
    return !exampleRequired || lines.subList(cursor, end + 1).any { it.contains(EXAMPLE_HEADING) }
}

/**
 * Every `public` declaration of [file] with no KDoc above it, or with no example in it when written
 * at the top level.
 *
 * A KDoc line is never a declaration: every line of one starts with `*` once trimmed, so a
 * `public …` written inside a fenced example cannot be mistaken for the real thing.
 */
private fun undocumentedIn(file: File): List<UndocumentedDeclaration> {
    val lines = file.readLines()
    var inKdoc = false
    return lines.mapIndexedNotNull { index, line ->
        val trimmed = line.trim()
        if (!inKdoc && trimmed.startsWith("/**")) {
            inKdoc = !trimmed.endsWith("*/")
        } else if (inKdoc && trimmed.endsWith("*/")) {
            inKdoc = false
        } else if (!inKdoc && trimmed.startsWith("public ") && !hasExampleAbove(lines, index, exampleRequired = line.startsWith("public "))) {
            return@mapIndexedNotNull UndocumentedDeclaration(file, index + 1, trimmed)
        }
        null
    }
}

class KonsistPublicApiSpec : FreeSpec({
    ":katachi-konsist の公開 API の KDoc" - {
        "public 宣言がすべて KDoc を持ち、トップレベルの宣言の KDoc は利用例を含む" {
            val undocumented = sourceFiles().flatMap { undocumentedIn(it) }.map { it.describe() }

            withClue("KDoc の無い public 宣言か、`$EXAMPLE_HEADING N: ...` + ```kt フェンスの無いトップレベルの public 宣言: $undocumented") {
                undocumented.shouldBeEmpty()
            }
        }

        "走査対象のファイルが1つ以上見つかっている" {
            withClue("対象が0件だと、この spec は何も見ないまま緑になる。") {
                sourceFiles().size shouldBeGreaterThan 0
            }
        }
    }
})
