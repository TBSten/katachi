package me.tbsten.katachi.test.docs

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.docs.GenerateDocumentation
import me.tbsten.katachi.docs.roleReferenceDocuments
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

/**
 * The pages this definition produces, built against a tree that refuses to be read.
 *
 * [ForbiddenFileSystem] rather than a fake one on purpose: every spec below then also asserts,
 * without saying so, that building documentation reads nothing — a fake tree would answer
 * happily and the specs would stay green if that stopped being true.
 */
internal fun Architecture.documents(): Map<String, String> =
    process(ForbiddenFileSystem) { context -> roleReferenceDocuments(context) }

/** One page of [documents], which fails the spec rather than returning null when it is absent. */
internal fun Architecture.page(path: String): String = documents().getValue(path)

/**
 * A directory of this spec's own, gone again however [block] ends.
 *
 * The shell of [GenerateDocumentation] is the one part of documentation generation a fake tree
 * cannot stand in for: what it does *is* write files, so the only spec that says anything about
 * it is one that looks at a real directory afterwards.
 */
internal fun <R> withTempDirectory(block: (File) -> R): R {
    val directory = Files.createTempDirectory("katachi-docs").toFile()
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

/** Every file below [this], as a relative path with `/` separators, sorted. */
internal fun File.relativeFilePaths(): List<String> = walkTopDown()
    .filter { it.isFile }
    .map { it.toRelativeString(this).replace(File.separatorChar, '/') }
    .sorted()
    .toList()
