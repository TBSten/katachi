package me.tbsten.katachi.konsist.internal

import com.lemonappdev.konsist.api.container.KoScope
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.core.container.KoScopeCore
import com.lemonappdev.konsist.core.util.KotlinFileParser
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
 * constraint covers never reaches the parser however the layout was written, and
 * `KotlinFileParser.getKoFile` rejects one outright. Asking for one would fail every constraint
 * that happens to cover a script — see `KonsistAssumptionsSpec`, which pins both.
 */
private const val PARSED_EXTENSION: String = ".kt"

/** One file's parse within a run, keyed so that two backends cannot collide on it. */
private data class ParsedFileKey(val absolutePath: String)

/**
 * A Konsist scope holding exactly the files [subject] covers, and nothing else.
 *
 * Each wanted file is parsed on its own, so a constraint costs the files it covers rather than
 * everything under their directories, and a sibling it does not cover is never read. Parsing is
 * what costs time, so each file is memoised across the constraints of one run and, across runs,
 * in [ParsedFileCache].
 *
 * Konsist's public API only builds a scope from directories (or from paths relative to a project
 * root it finds for itself), so the parse and the scope come from `konsist.core`:
 * `KotlinFileParser.getKoFile` is what every directory scope calls per file, and `KoScopeCore`
 * is the class every one of them returns. Neither is covered by Konsist's compatibility
 * promise; `KonsistAssumptionsSpec` (9) pins both against the version katachi depends on.
 *
 * Absolute paths throughout. Konsist never resolves a project root of its own here — which is
 * why `KoFileDeclaration.projectPath` is unusable, as [KonsistScope]'s own documentation says —
 * so a relative path would be resolved against the JVM's working directory rather than against
 * the project katachi walked.
 *
 * @throws KatachiKonsistNoKotlinFilesException when the constraint covers files but none that
 *   Konsist parses, so the block would be asked of an empty scope and pass by empty truth.
 * @throws KatachiKonsistScopeIncompleteException when a wanted file could not be parsed as the
 *   path katachi asked for — it vanished after the walk, or Konsist reports a different path.
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
    return KoScopeCore(parsedFiles)
}

/**
 * Konsist's parse of each of [wanted], at most once per file per run, and not at all for a
 * file [ParsedFileCache] already holds under its current stamp.
 *
 * Every stamp is taken before the parse it guards, and a parse the run memo already held is
 * never stored: it may predate the stamp. A file that is gone, or that Konsist reports under
 * another path, is left out, so the caller's count check turns it into an exception rather than
 * a scope that silently lacks it.
 */
private fun parsedFilesOf(subject: FileConstraintSubject, wanted: Set<String>): List<KoFileDeclaration> {
    val found = HashMap<String, KoFileDeclaration>(wanted.size)
    for (path in wanted) {
        val stamp = ParsedFileCache.stampOf(path)
        val cached = stamp?.let { ParsedFileCache.get(path, it) }
        if (cached != null) {
            found[path] = cached
            continue
        }
        val file = File(path)
        // Deleted since the walk. `getKoFile` would reject it with a bare `require`; left out,
        // it becomes the count mismatch that names the problem.
        if (!file.isFile) continue
        var parsedHere = false
        val parsed = subject.memo(ParsedFileKey(path), KoFileDeclaration::class) {
            parsedHere = true
            KotlinFileParser.getKoFile(file)
        }
        // Konsist reports the OS separator, so it is normalised before being compared.
        if (File(parsed.path).invariantSeparatorsPath != path) continue
        found[path] = parsed
        if (parsedHere && stamp != null) ParsedFileCache.put(path, stamp, parsed)
    }
    // Sorted by path, the order a Konsist scope lists its files in.
    return found.entries.sortedBy { it.key }.map { it.value }
}
