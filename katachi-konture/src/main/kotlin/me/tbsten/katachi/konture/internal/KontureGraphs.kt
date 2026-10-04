package me.tbsten.katachi.konture.internal

import io.github.baole.konture.FileDeclaration
import io.github.baole.konture.Module
import io.github.baole.konture.ProjectGraph
import io.github.baole.konture.SourceSet
import io.github.baole.konture.SourceSetId
import io.github.baole.konture.SourceSetKind
import io.github.baole.konture.SourceSetRole
import java.io.File
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konture.KatachiKontureGraphIncompleteException
import me.tbsten.katachi.konture.KatachiKontureNoKotlinFilesException
import me.tbsten.katachi.konture.KatachiKontureParseException

/**
 * The only file extension Konture 1.0.1 parses.
 *
 * `.kts` is deliberately absent: Konture's `getDeclaredClassFqNames` reads `.kt` only, so a
 * `build.gradle.kts` inside what a constraint covers would never resolve symbols for the rest
 * and would be a count mismatch waiting to happen. `KontureAssumptionsSpec` (12) pins it.
 */
private const val PARSED_EXTENSION: String = ".kt"

/** The one build and the one module of a synthesized graph. Only Konture's own messages ever print them. */
private const val BUILD_ID: String = ":"
private const val MODULE_PATH: String = ":"

/**
 * The one source set every parsed file belongs to: `main`, production, JVM.
 *
 * Konture's builders select `SourceSets.production()` by default, and `membershipsFor` would
 * invent this same membership for a file with none. It is written out rather than left to the
 * fallback so that the graph says what it means, the way Konture's own loader does.
 */
private val MAIN: SourceSetId = SourceSetId(MODULE_PATH, "main", SourceSetKind.JVM, SourceSetRole.PRODUCTION)

/** `Module.sourceSets` for the same source set, as the Gradle plugin would have written it. */
private val MAIN_SOURCE_SET: SourceSet =
    SourceSet(name = "main", kind = "KOTLIN_JVM", production = true, srcDirs = emptyList())

/**
 * The memo key of one run's graph over one set of files.
 *
 * Backend specific, as `FileConstraintSubject.memo` asks: two constraints over the same files
 * share one graph, and nothing but this module can collide on the key.
 */
internal data class KontureGraphKey(val files: List<String>)

/**
 * A Konture project graph holding exactly the `.kt` files [subject] covers, and nothing else.
 *
 * Each wanted file is parsed on its own, so a constraint costs the files it covers rather than
 * everything under their directories, and a sibling it does not cover is never read. The graph
 * is memoised for the run under [KontureGraphKey], so two constraints over the same files parse
 * once. Across runs, Konture's own content-hashed `IncrementalAstCache` answers for an
 * unchanged file.
 *
 * Konture has no public parser, so the parse comes from [KontureSymbols]; the graph itself is
 * assembled with public constructors. Paths are absolute throughout, and the graph's
 * `filePath`s are compared with what was asked for before anything is trusted.
 *
 * @throws KatachiKontureNoKotlinFilesException when the constraint covers files but none that
 *   Konture parses, so the rules would be asked of an empty graph and pass by empty truth.
 * @throws KatachiKontureGraphIncompleteException when a wanted file could not be parsed as the
 *   file katachi asked for — it vanished after the walk, or Konture reports another file.
 * @throws KatachiKontureParseException when Konture's parser threw on a file that exists.
 */
internal fun kontureGraphOf(subject: FileConstraintSubject): ProjectGraph {
    val wanted = subject.files
        .filter { it.endsWith(PARSED_EXTENSION) }
        .map { "${subject.projectRoot}/$it" }
    // Covered files exist, and Konture can read none of them. Returning an empty graph would
    // let every rule inside the block select nothing and the constraint pass without having
    // looked at anything, so this is said out loud instead.
    if (wanted.isEmpty()) {
        throw KatachiKontureNoKotlinFilesException(
            role = subject.role.qualifiedName,
            constraintName = subject.name,
            declaredAt = subject.declaredAt,
            given = subject.files.size,
        )
    }
    return subject.memo(KontureGraphKey(wanted), ProjectGraph::class) {
        val parsed = parsedFilesOf(wanted)
        if (parsed.size != wanted.size) {
            throw KatachiKontureGraphIncompleteException(given = wanted.size, visible = parsed.size)
        }
        ProjectGraph(
            builds = mapOf(
                BUILD_ID to listOf(
                    Module(
                        buildId = BUILD_ID,
                        path = MODULE_PATH,
                        projectDir = subject.projectRoot,
                        appliedPlugins = emptyList(),
                        sourceSets = listOf(MAIN_SOURCE_SET),
                        dependencies = emptyList(),
                        files = parsed,
                    ),
                ),
            ),
        )
    }
}

/**
 * Konture's parse of each of [wanted] that still exists and came back as the file asked for.
 *
 * A file that is gone, or a parse that Konture reports for another file, is left out, so the
 * caller's count check turns it into an exception rather than a graph that silently lacks it.
 * Every file is stamped with the one source set, the way Konture's own loader stamps a source
 * set's files.
 */
private fun parsedFilesOf(wanted: List<String>): List<FileDeclaration> {
    // Deleted since the walk: left out, so that it becomes the count mismatch that names the problem.
    val files = wanted.map(::File).filter { it.isFile }
    val symbols = KontureSymbols.of(files)
    return files.mapNotNull { file ->
        val declaration = try {
            symbols.parse(file)
        } catch (cause: RuntimeException) {
            throw KatachiKontureParseException(file = file.invariantSeparatorsPath, cause = cause)
        } catch (cause: java.io.IOException) {
            throw KatachiKontureParseException(file = file.invariantSeparatorsPath, cause = cause)
        }
        if (declaration == null || !declaration.isAbout(file)) return@mapNotNull null
        declaration.copy(
            sourceSets = listOf(MAIN),
            usages = declaration.usages.map { it.copy(sourceSets = listOf(MAIN)) },
        )
    }
}

/**
 * Whether this parse is of [file], whatever spelling Konture gave it.
 *
 * Konture's parse cache is keyed by canonical path but keeps the `filePath` of the first parse
 * in this JVM, so the same file asked for under another spelling (`src/../src/`, `/tmp` against
 * `/private/tmp`, a symlinked worktree) comes back under the first one. The spellings are
 * compared first, because that costs nothing; the file system is asked only when they differ.
 */
private fun FileDeclaration.isAbout(file: File): Boolean {
    val reported = File(filePath)
    return reported.invariantSeparatorsPath == file.invariantSeparatorsPath ||
        reported.canonicalPath == file.canonicalPath
}
