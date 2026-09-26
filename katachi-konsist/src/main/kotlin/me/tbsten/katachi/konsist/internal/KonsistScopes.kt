package me.tbsten.katachi.konsist.internal

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.core.container.KoScopeCore
import java.io.File
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konsist.KatachiKonsistNoKotlinFilesException
import me.tbsten.katachi.konsist.KatachiKonsistScopeIncompleteException
import me.tbsten.katachi.konsist.KonsistScope

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
 * of directories covering the wanted files and then keeping only the files themselves. Both
 * halves matter: parsing is what costs time, so it is memoised across the constraints of one
 * run and, per file, across runs in [ParsedFileCache]; and keeping only the wanted files is what
 * keeps a constraint from seeing a sibling file its layout block does not cover.
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
internal fun konsistScopeOf(subject: FileConstraintSubject): KoScope {
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

    val parsedFiles = parsedFilesOf(subject, wanted)
    if (parsedFiles.size != wanted.size) {
        throw KatachiKonsistScopeIncompleteException(given = wanted.size, visible = parsedFiles.size)
    }
    // Konsist's public API only builds a scope from a directory; this is the class every one of
    // those calls returns, and `slice` of one is exactly this constructor over a filtered list.
    return KoScopeCore(parsedFiles)
}

/**
 * Konsist's parse of each of [wanted], at most once per file per run, and not at all for a
 * file [ParsedFileCache] already holds under its current stamp.
 *
 * Only the files the cache missed are handed to Konsist, by directory, so a second run over an
 * unchanged tree parses nothing. Every stamp is taken before the parse it guards, and a parse
 * the run memo already held is never stored: it may predate the stamp.
 */
private fun parsedFilesOf(subject: FileConstraintSubject, wanted: Set<String>): List<KoFileDeclaration> {
    val stamps = wanted.associateWith { ParsedFileCache.stampOf(it) }
    val found = HashMap<String, KoFileDeclaration>(wanted.size)
    for ((path, stamp) in stamps) {
        val cached = stamp?.let { ParsedFileCache.get(path, it) } ?: continue
        found[path] = cached
    }
    val missed = wanted - found.keys
    if (missed.isNotEmpty()) {
        val directories = minimalDirectories(missed.mapTo(mutableSetOf()) { it.substringBeforeLast('/') })
        var parsedHere = false
        val parsed = subject.memo(ScopeKey(directories), KoScope::class) {
            parsedHere = true
            Konsist.scopeFromExternalDirectories(directories)
        }
        for (file in parsed.files) {
            // Konsist reports the OS separator, so it is normalised before being looked up.
            val path = File(file.path).invariantSeparatorsPath
            if (path !in missed) continue
            found[path] = file
            val stamp = stamps[path]
            if (parsedHere && stamp != null) ParsedFileCache.put(path, stamp, file)
        }
    }
    // Sorted by path, the order a Konsist scope lists its files in.
    return found.entries.sortedBy { it.key }.map { it.value }
}

/**
 * [directories] with every entry that an ancestor already covers dropped.
 *
 * `scopeFromExternalDirectories` walks recursively, so handing it both `a` and `a/b` parses
 * everything under `a/b` twice. Sorted so that one directory set always produces one
 * [ScopeKey] and the memo hits.
 */
private fun minimalDirectories(directories: Set<String>): List<String> = directories
    .filterNot { candidate -> directories.any { it != candidate && candidate.startsWith("$it/") } }
    .sorted()
