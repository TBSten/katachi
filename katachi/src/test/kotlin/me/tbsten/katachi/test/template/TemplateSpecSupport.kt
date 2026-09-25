package me.tbsten.katachi.test.template

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.templateFiles
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

/**
 * What running [roleName]'s template would put in the project, against a tree that refuses to be
 * read.
 *
 * [ForbiddenFileSystem] rather than a fake one, for the reason [me.tbsten.katachi.test.docs] uses
 * it: every spec built on this also asserts, without saying so, that deciding *what* to generate
 * reads nothing. A fake tree would answer happily and the claim would quietly stop being true.
 */
internal fun Architecture.generated(
    roleName: String,
    values: Map<String, String> = emptyMap(),
): Map<String, String> = process(ForbiddenFileSystem) { context ->
    templateFiles(context, roleName, values)
}

/** Where [roleName]'s template would write, sorted, without the contents. */
internal fun Architecture.generatedPaths(
    roleName: String,
    values: Map<String, String> = emptyMap(),
): List<String> = generated(roleName, values).keys.sorted()

/** A directory of this spec's own, gone again however [block] ends. */
internal fun <R> withTempProject(block: (File) -> R): R {
    val directory = Files.createTempDirectory("katachi-template").toFile()
    return try {
        // One of the Gradle markers the project root search looks for, so a run started here
        // treats this directory as the top of the project.
        File(directory, "gradlew").writeText("")
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
