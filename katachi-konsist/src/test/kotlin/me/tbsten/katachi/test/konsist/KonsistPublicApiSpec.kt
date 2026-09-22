package me.tbsten.katachi.test.konsist

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File

/**
 * Every `public` declaration of `:katachi-konsist`'s main sources carries a KDoc with a worked
 * example.
 *
 * `:katachi` has the same rule, checked by `ConstraintApiKdocSpec` — but that spec only covers
 * the files the constraint API was written into, because most of `:katachi`'s existing public
 * surface predates the rule. This module is new in its entirety, so there is no allowlist here:
 * every file under `src/main` is in scope from the start. This is also what replaces the
 * "目視確認" (an eyeballed completion condition) the earlier design draft settled for — forgetting
 * an example is now a red test, not something a reviewer has to remember to check.
 *
 * Reads sources as text, the same way `PackageDependencySpec` and `ConstraintApiKdocSpec` do:
 * parsing Kotlin would be a far bigger thing to maintain than the rule it guards.
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

/** One `public` declaration whose KDoc does not show how to use it. */
private class UndocumentedDeclaration(val file: File, val line: Int, val declaration: String) {
    fun describe(): String = "${file.path}:$line  $declaration"
}

/**
 * Whether the KDoc block ending just above [index] holds an example.
 *
 * Blank lines, annotations and `context(...)` receivers sit between a KDoc and what it
 * documents, so they are stepped over. Unlike `:katachi`'s `ConstraintApiKdocSpec` — whose
 * covered files only ever carry single-line markers such as `@ExperimentalKatachiApi` — this
 * module's shadowed Konsist assertions carry multi-line `@Deprecated(...)` blocks, so a bare
 * `startsWith("@")` on one line is not enough: the line directly above the declaration is often
 * just the annotation's closing `)`. Paren depth is tracked back to whichever line opens that
 * block (an unbalanced text scan, same as the rest of this spec — good enough for source that
 * balances its own parentheses, which every annotation and `context(...)` here does).
 * A declaration with no KDoc at all reads as undocumented, which is the same failure.
 */
private fun hasExampleAbove(lines: List<String>, index: Int): Boolean {
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
    return lines.subList(cursor, end + 1).any { it.contains(EXAMPLE_HEADING) }
}

/**
 * Every `public` declaration of [file] with no example above it.
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
        } else if (!inKdoc && trimmed.startsWith("public ") && !hasExampleAbove(lines, index)) {
            return@mapIndexedNotNull UndocumentedDeclaration(file, index + 1, trimmed)
        }
        null
    }
}

class KonsistPublicApiSpec : FreeSpec({
    ":katachi-konsist の公開 API の KDoc" - {
        "public 宣言がすべて利用例つきの KDoc を持つ" {
            val undocumented = sourceFiles().flatMap { undocumentedIn(it) }.map { it.describe() }

            withClue("`$EXAMPLE_HEADING N: ...` + ```kt フェンスの無い public 宣言: $undocumented") {
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
