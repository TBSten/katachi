package me.tbsten.katachi.test.template

import java.io.File
import java.nio.file.Files
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.internal.templateFilesFor
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

/**
 * What running the templates [specifiers] name would put in the project, against a tree that
 * refuses to be read.
 *
 * [ForbiddenFileSystem] rather than a fake one, for the reason [me.tbsten.katachi.test.docs] uses
 * it: every spec built on this also asserts, without saying so, that deciding *what* to generate
 * reads nothing but a module capture's value, which only reads the modules that exist. A fake
 * tree would answer happily and the claim would quietly stop being true.
 */
internal fun Architecture.generated(
    specifiers: List<String>,
    values: Map<String, String> = emptyMap(),
): Map<String, String> = process(ForbiddenFileSystem) { context ->
    templateFilesFor(context, specifiers, values)
}

/** [generated], for one specifier. */
internal fun Architecture.generated(
    specifier: String,
    values: Map<String, String> = emptyMap(),
): Map<String, String> = generated(listOf(specifier), values)

/**
 * [generated], against [modules] instead of an unresolved index -- for a spec that needs a module
 * capture's value to pick a module that really exists. [ForbiddenFileSystem] still backs the
 * context itself: [modules] is a fixed answer built once by [me.tbsten.katachi.test.dsl.moduleIndexOf],
 * not a second tree this run might read.
 */
internal fun Architecture.generated(
    specifiers: List<String>,
    values: Map<String, String> = emptyMap(),
    modules: () -> ModuleIndex,
): Map<String, String> = process(ForbiddenFileSystem) { context ->
    templateFilesFor(context, specifiers, values, modules)
}

/** [generated] with [modules], for one specifier. */
internal fun Architecture.generated(
    specifier: String,
    values: Map<String, String> = emptyMap(),
    modules: () -> ModuleIndex,
): Map<String, String> = generated(listOf(specifier), values, modules)

/** Where the templates [specifiers] name would write, sorted, without the contents. */
internal fun Architecture.generatedPaths(
    specifiers: List<String>,
    values: Map<String, String> = emptyMap(),
): List<String> = generated(specifiers, values).keys.sorted()

/** [generatedPaths], for one specifier. */
internal fun Architecture.generatedPaths(
    specifier: String,
    values: Map<String, String> = emptyMap(),
): List<String> = generatedPaths(listOf(specifier), values)

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
