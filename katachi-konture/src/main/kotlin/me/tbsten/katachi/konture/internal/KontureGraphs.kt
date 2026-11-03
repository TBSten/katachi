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

// `.kts` is deliberately absent: Konture reads `.kt` only, so a script would never resolve symbols and would be a count mismatch waiting to happen.
private const val PARSED_EXTENSION: String = ".kt"

private const val BUILD_ID: String = ":"
private const val MODULE_PATH: String = ":"

private val MAIN: SourceSetId = SourceSetId(MODULE_PATH, "main", SourceSetKind.JVM, SourceSetRole.PRODUCTION)

private val MAIN_SOURCE_SET: SourceSet =
    SourceSet(name = "main", kind = "KOTLIN_JVM", production = true, srcDirs = emptyList())

// Backend specific, as `FileConstraintSubject.memo` asks: nothing but this module can collide on the key.
internal data class KontureGraphKey(val files: List<String>)

// Konture has no public parser, so the parse comes from `KontureSymbols`; the graph's `filePath`s are compared with what was asked for before anything is trusted.
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

// A gone file or a parse for another file is left out, so the caller's count check throws rather than building a graph that silently lacks it.
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

// Konture's parse cache keeps the `filePath` of the first parse in this JVM, so compare spellings first (free) and ask the file system only when they differ.
private fun FileDeclaration.isAbout(file: File): Boolean {
    val reported = File(filePath)
    return reported.invariantSeparatorsPath == file.invariantSeparatorsPath ||
        reported.canonicalPath == file.canonicalPath
}
