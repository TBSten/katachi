package me.tbsten.katachi.check.internal

import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.LayoutCheck
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.internal.projectWalk

internal fun Architecture.validateWith(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<Unit, List<Violation>>>,
): List<Violation> = validateWithRoot(fileSystem, checks).violations

internal data class ValidationResult(
    val violations: List<Violation>,
    val projectRoot: FsPath,
    val ran: List<CheckedViolations> = emptyList(),
    val unattributed: List<Violation> = emptyList(),
    val baselineDeclared: Boolean = false,
)

internal fun Architecture.validateWithRoot(
    fileSystem: KatachiFileSystem,
    checks: List<ArchitectureProcessor<Unit, List<Violation>>>,
): ValidationResult {
    // `LayoutCheck` runs below whatever was passed. Letting a passed one through as well would
    // put the same `Violation` instance in the list twice: the count, the blocks and the
    // truncation budget would all double for a caller who only spelled out what already
    // happens.
    val extra = checks.filterNot { it is LayoutCheck }
    return process(fileSystem) { context ->
        // Started here rather than inside `LayoutCheck`, which keeps whatever it threw as its
        // answer: an exception the walk hands up -- the caller's own assertion from inside the
        // file system included -- would otherwise come back looking like what the check found.
        context.projectWalk.layoutViolations
        // `LayoutCheck` does not appear in any signature above. Deny by default is the whole
        // of what katachi is, so it cannot depend on the caller remembering to ask for it.
        // A failure other than what it found is thrown on: a layout check that cannot run --
        // no project root, no git, a definition it cannot read -- leaves nothing to report, and
        // `validate()` documents those exceptions as its own.
        val layout = LayoutCheck().process(context).getOrElse { cause ->
            (cause as? KatachiArchitectureAssertionError)?.violations ?: throw cause
        }
        // One check at a time, each caught on its own: errors.md's "if this fails, can the
        // neighbour still answer" applies here exactly as it applies per file inside the walk.
        // A third-party check throwing once must not take the layout violations with it —
        // that is the failure this library can least afford.
        val found = extra.map { check ->
            val answer = catching { check.process(context) }
                .getOrElse { cause -> return@map CheckedViolations(checkNameOf(check), listOf(uncheckedCheckOf(check, cause))) }
                .violationsOf(check)
            CheckedViolations(checkNameOf(check), answer)
        }
        // One context, therefore one walk. The last term is the guard against the quietest way
        // this library could break: a definition full of constraints, code breaking them, and
        // a green test because nothing was handed a check that evaluates them.
        val unattributed = context.projectWalk.unevaluatedFileConstraintViolations()
        val ran = listOf(CheckedViolations(LAYOUT_CHECK_NAME, layout)) + found
        val violations = (ran.flatMap { it.violations } + unattributed).sortedBy { it.kind.ordinal }
        val declared = baseline?.let { isAllowedByLayout(LayoutIndex(context.projectWalk.declaredEntries), it.path) } ?: false
        ValidationResult(violations, context.projectWalk.projectRoot, ran, unattributed, declared)
    }
}

internal val LAYOUT_CHECK_NAME: String = LayoutCheck::class.qualifiedName ?: LayoutCheck::class.java.name

private fun isAllowedByLayout(index: LayoutIndex, path: String): Boolean {
    if (index.rolesOf(path).isNotEmpty()) return true
    val segments = path.split('/')
    return (1 until segments.size).any { index.isIgnored(segments.take(it).joinToString("/")) }
}
