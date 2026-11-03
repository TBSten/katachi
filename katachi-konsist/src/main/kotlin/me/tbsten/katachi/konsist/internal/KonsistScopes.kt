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

// `.kts` is deliberately absent: Konsist only parses names ending in `.kt` and its parser rejects a script outright (pinned by `KonsistAssumptionsSpec`).
private const val PARSED_EXTENSION: String = ".kt"

private data class ParsedFileKey(val absolutePath: String)

// Absolute paths throughout: Konsist resolves no project root here, so a relative path would resolve against the JVM's working directory.
// The parse and the scope come from `konsist.core`, which Konsist's compatibility promise does not cover; `KonsistAssumptionsSpec` pins them.
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

// Take each stamp before the parse it guards, and never store a parse the run memo already held: it may predate the stamp.
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
