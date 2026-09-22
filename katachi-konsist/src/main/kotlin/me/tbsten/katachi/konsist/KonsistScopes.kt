package me.tbsten.katachi.konsist

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import java.io.File
import me.tbsten.katachi.dsl.ConstraintSubject

/**
 * The only file extension Konsist 0.17.3 parses.
 *
 * `.kts` is deliberately absent, and this is measured rather than assumed: Konsist's
 * `File.isKotlinFile` is `name.endsWith(".kt")`, so a `build.gradle.kts` inside what a
 * constraint covers never reaches the parser however the layout was written. Asking for one
 * would make the count check below fail on every constraint that happens to cover a script —
 * see `KonsistAssumptionsSpec`, which pins the behaviour.
 */
private const val PARSED_EXTENSION: String = ".kt"

/** The parsed scope of one directory set, keyed so that two backends cannot collide on it. */
private data class ScopeKey(val directories: List<String>)

/**
 * A Konsist scope holding exactly the files [subject] covers, and nothing else.
 *
 * Konsist parses from disk by directory, so the scope is built by handing it the smallest set
 * of directories covering the wanted files and then narrowing that back down to the files
 * themselves. Both halves matter: parsing is what costs time, so it is memoised across the
 * constraints of one run, and narrowing is what keeps a constraint from seeing a sibling file
 * its layout block does not cover.
 *
 * Absolute paths throughout. `Konsist.scopeFromExternalDirectories` never resolves a project
 * root of its own — which is why `KoFileDeclaration.projectPath` is unusable here, as
 * [KonsistScope]'s own documentation says — so a relative path would be resolved against the
 * JVM's working directory rather than against the project katachi walked.
 *
 * @throws KatachiKonsistNoKotlinFilesException when the constraint covers files but none that
 *   Konsist parses, so the block would be asked of an empty scope and pass by empty truth.
 * @throws KatachiKonsistScopeIncompleteException when the narrowing loses a file, which can
 *   only mean katachi's own absolute paths and Konsist's have stopped matching.
 */
internal fun konsistScopeOf(subject: ConstraintSubject): KoScope {
    val wanted = subject.files
        .filter { it.endsWith(PARSED_EXTENSION) }
        .map { "${subject.projectRoot}/$it" }
        .toHashSet()
    // Covered files exist, and Konsist can read none of them. Returning an empty scope would
    // let every query inside the block come back empty and the constraint pass without having
    // looked at anything, so this is said out loud instead.
    if (wanted.isEmpty()) {
        throw KatachiKonsistNoKotlinFilesException(
            role = subject.role.qualifiedName,
            constraintName = subject.name,
            declaredAt = subject.declaredAt,
            given = subject.files.size,
        )
    }

    val directories = minimalDirectories(wanted.mapTo(mutableSetOf()) { it.substringBeforeLast('/') })
    val parsed = subject.memo(ScopeKey(directories), KoScope::class) {
        Konsist.scopeFromExternalDirectories(directories)
    }
    // Konsist reports the OS separator, so it is normalised before being looked up.
    val sliced = parsed.slice { File(it.path).invariantSeparatorsPath in wanted }
    if (sliced.files.size != wanted.size) {
        throw KatachiKonsistScopeIncompleteException(given = wanted.size, visible = sliced.files.size)
    }
    return sliced
}

/**
 * [directories] with every entry that an ancestor already covers dropped.
 *
 * `scopeFromExternalDirectories` walks recursively, so handing it both `a` and `a/b` parses
 * everything under `a/b` twice. Sorted so that one directory set always produces one
 * [ScopeKey] and the memo hits.
 */
internal fun minimalDirectories(directories: Set<String>): List<String> = directories
    .filterNot { candidate -> directories.any { it != candidate && candidate.startsWith("$it/") } }
    .sorted()
