@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.test.konsist.declarations

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.KtFileContentConstraint
import me.tbsten.katachi.konsist.declarations.internal.explainKtFile
import me.tbsten.katachi.konsist.declarations.internal.failureOf
import me.tbsten.katachi.konsist.declarations.internal.judgeKtFile
import me.tbsten.katachi.test.konsist.fixtureProject

/*
 * What `DeclarationExplanationSpec` shares: one file explained and judged from the same facts and
 * rules, one run of a real check that also explains, and the invariant that ties the two answers
 * together (plan V8). NOTE: このパッケージを `me.tbsten.katachi.konsist.…` にしてはいけない
 * （KtFileContentSpecSupport.kt と同じ理由）。
 */

/** One file judged as the check judges it and explained as the IDE's JSON explains it. */
internal class JudgedAndExplained(val failures: List<FileConstraintFailure>, val explanation: FileDeclarationsExplanation)

/** [source] at [path] judged and explained against [block] evaluated for that file. */
internal fun judgedAndExplained(
    source: String,
    path: String = SAMPLE_PATH,
    block: KtFileContentScope.() -> Unit,
): JudgedAndExplained {
    val facts = factsOf(source, path)
    val rules = rulesOf(fileName = path.substringAfterLast('/').substringBefore('.'), block = block)
    return JudgedAndExplained(
        failures = judgeKtFile(facts, rules).map { failureOf(it, path, rules) },
        explanation = explainKtFile(path, facts, rules),
    )
}

/**
 * Where the explanation and the check's failures for one file disagree, one line each; empty when
 * they agree. The file's own declarations only: a member, an import and the package are the
 * check's alone.
 *
 * - a declaration is not allowed ⇔ the check rejects it (a failure naming it at its line)
 * - a line is missing ⇔ the check says `Missing:` for it
 */
internal fun inconsistenciesOf(failures: List<FileConstraintFailure>, explanation: FileDeclarationsExplanation): List<String> {
    val ownDeclarations = failures.filter { failure ->
        val declaration = failure.declaration ?: return@filter false
        !declaration.startsWith("import ") && !declaration.startsWith("package ") && '.' !in declaration
    }
    val rejected = ownDeclarations.filter { it.line != null }.map { "${it.declaration} (line ${it.line})" }.toSet()
    val notAllowed = explanation.declarations.filterNot { it.verdict.isAllowed }.map { "${it.name} (line ${it.line})" }.toSet()
    val reportedMissing = ownDeclarations.filter { it.line == null }
        .mapNotNull { failure -> failure.reasons.firstOrNull()?.takeIf { it.label == "Missing" }?.text }
        .toSet()
    val explainedMissing = explanation.missing.map { it.text }.toSet()
    return buildList {
        (notAllowed - rejected).forEach { add("${explanation.file}: explained as not allowed, not rejected by the check: $it") }
        (rejected - notAllowed).forEach { add("${explanation.file}: rejected by the check, explained as allowed: $it") }
        (explainedMissing - reportedMissing).forEach { add("${explanation.file}: explained as missing, not reported missing: $it") }
        (reportedMissing - explainedMissing).forEach { add("${explanation.file}: reported missing, not explained as missing: $it") }
    }
}

/**
 * Hands one real subject to both halves of a `ktFileContent { }` constraint inside a run of
 * `FileConstraintCheck`, so the two answers are about exactly what the check saw.
 */
private class Probe(private val inner: KtFileContentConstraint) : FileConstraint {
    var failures: Result<List<FileConstraintFailure>>? = null
    var explanations: Result<List<FileDeclarationsExplanation>>? = null

    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> {
        failures = runCatching { inner.evaluate(subject) }
        explanations = runCatching { inner.explainDeclarations(subject) }
        return failures?.getOrNull().orEmpty()
    }
}

/** What one run of [explainedRun] saw: the check's failures and the explanation, or what each threw. */
internal class ExplainedRun(
    val failures: Result<List<FileConstraintFailure>>,
    val explanations: Result<List<FileDeclarationsExplanation>>,
)

/**
 * A throwaway project holding [sources], a role whose layout declares [paths] and whose one
 * `ktFileContent { }` is [block], checked with `FileConstraintCheck()` while the same constraint
 * also explains its declarations.
 */
internal fun explainedRun(
    vararg sources: Pair<String, String>,
    paths: LayoutScope.() -> Unit = { "src/main/kotlin/com/example" { "*.kt".file() } },
    block: KtFileContentScope.() -> Unit,
): ExplainedRun = fixtureProject(*sources) { root ->
    val probe = Probe(KtFileContentConstraint(block, SPEC_SITE))
    val projectArchitecture = architecture {
        files = wholeTree()
        "Screen".role {
            layout {
                "gradlew".file()
                paths()
            }
            fileConstraint("probe", check = probe)
        }
    }
    projectArchitecture.validate(RealFileSystem(root), FileConstraintCheck())
    ExplainedRun(
        failures = probe.failures ?: throw AssertionError("the check never evaluated the constraint"),
        explanations = probe.explanations ?: throw AssertionError("the check never evaluated the constraint"),
    )
}

/** Where [allowLoggerHelper] wrote its line, as `DeclarationSite.capture()` saw it on that same line. */
internal var helperLineSite: DeclarationSite? = null

/** A helper that writes one line for the caller, the way a team shares a line between roles. */
internal fun KtFileContentScope.allowLoggerHelper() {
    `val`("logger").optional().also { helperLineSite = DeclarationSite.capture() }
}
