@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.core.util.KotlinFileParser
import java.io.File
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.DeclarationFact
import me.tbsten.katachi.konsist.declarations.internal.KtFileFacts
import me.tbsten.katachi.konsist.declarations.internal.failureOf
import me.tbsten.katachi.konsist.declarations.internal.judgeKtFile
import me.tbsten.katachi.konsist.declarations.internal.ktFileFactsOf
import me.tbsten.katachi.konsist.declarations.internal.renderFact
import me.tbsten.katachi.test.konsist.fixtureProject

/*
 * What the specs of the matching share: one Kotlin source parsed by Konsist into the facts the
 * matching reads, and one file judged against one block without building an architecture.
 *
 * NOTE: このパッケージを `me.tbsten.katachi.konsist.…` にしてはいけない（KtFileContentSpecSupport.kt と同じ理由）。
 */

/** Where a source is put when the spec does not care: a file of `com.example` under `src/main/kotlin/`. */
internal const val SAMPLE_PATH: String = "src/main/kotlin/com/example/Sample.kt"

/** Konsist's parse of [source], written to [path] in a throwaway project. */
internal fun <T> parsed(source: String, path: String = SAMPLE_PATH, use: (KoFileDeclaration) -> T): T =
    fixtureProject(path to source) { root -> use(KotlinFileParser.getKoFile(File(root, path))) }

/** The facts the matching reads off [source], put at [path]. */
internal fun factsOf(source: String, path: String = SAMPLE_PATH): KtFileFacts = parsed(source, path) { ktFileFactsOf(it, path) }

/** The declarations of [source], each as the report writes it, members indented under their class. */
internal fun renderedFactsOf(source: String): List<String> = factsOf(source).declarations.flatMap { renderTree(it, "") }

private fun renderTree(fact: DeclarationFact, indent: String): List<String> =
    listOf(indent + renderFact(fact)) + fact.members.orEmpty().flatMap { renderTree(it, "$indent  ") }

/**
 * [source] at [path] judged against [block] evaluated for that file, one line per failure:
 * `<declaration> (line <n>) | <label>: <text> | ...`, the allowed list and the fixes left out.
 */
internal fun judged(
    source: String,
    path: String = SAMPLE_PATH,
    captures: Map<String, String> = emptyMap(),
    block: KtFileContentScope.() -> Unit,
): List<String> {
    val facts = factsOf(source, path)
    val rules = rulesOf(fileName = path.substringAfterLast('/').substringBefore('.'), captures = captures, block = block)
    return judgeKtFile(facts, rules).map { failureOf(it, path, rules) }.map { failure ->
        val head = failure.declaration + (failure.line?.let { " (line $it)" } ?: "")
        (listOf(head) + failure.reasons.map { "${it.label}: ${it.text}" }).joinToString(" | ")
    }
}

/** The `[UnsatisfiedFileConstraint]` of [violations], each as [judged] writes a failure. */
internal fun List<Violation>.unsatisfiedLines(): List<String> = filterIsInstance<UnsatisfiedFileConstraint>().map { violation ->
    val head = violation.path + ": " + violation.declaration + (violation.line?.let { " (line $it)" } ?: "")
    (listOf(head) + violation.reasons.map { "${it.label}: ${it.text}" }).joinToString(" | ")
}
