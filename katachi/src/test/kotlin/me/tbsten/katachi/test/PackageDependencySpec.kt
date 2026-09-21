package me.tbsten.katachi.test

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The layers of `:katachi`, bottom first.
 *
 * A file may import from its own layer and from every layer **before** it in this list, and
 * from nothing after it. Sub-packages belong to the layer they sit under, so `dsl.gradle` is
 * `dsl`: the Gradle and Kotlin vocabularies are part of the DSL layer rather than layers of
 * their own, and `dsl` reaching into `dsl.kotlin` for `ktsFile()` is a move inside one layer.
 *
 * The empty name is the root package, [ROOT_PACKAGE] itself, which holds the opt-in markers
 * and the three exception bases and depends on nothing.
 */
private val LAYERS: List<String> = listOf("", "fs", "dsl", "scan", "processor", "check")

/** The package every layer name is relative to. */
private const val ROOT_PACKAGE: String = "me.tbsten.katachi"

/** One import that points at a layer after the one the file that wrote it belongs to. */
private class UnexpectedImport(
    val file: File,
    val line: Int,
    val from: String,
    val to: String,
    val statement: String,
) {
    fun describe(): String = "${file.path}:$line  ${displayNameOf(from)} -> ${displayNameOf(to)}  $statement"
}

/** A package under [ROOT_PACKAGE] that no layer of [LAYERS] claims. */
private class UnlistedPackage(val where: String, val packageName: String) {
    fun describe(): String = "$ROOT_PACKAGE.$packageName  ($where)"
}

/**
 * The layer [relativePackage] belongs to, or `null` when no layer claims it.
 *
 * `null` rather than the root layer, so that dropping a name from [LAYERS] is reported
 * instead of quietly folding that package into the bottom — which would make every import of
 * it look allowed.
 */
private fun layerOf(relativePackage: String): String? = when {
    relativePackage.isEmpty() -> ""
    else -> LAYERS.firstOrNull {
        it.isNotEmpty() && (relativePackage == it || relativePackage.startsWith("$it."))
    }
}

private fun displayNameOf(layer: String): String =
    if (layer.isEmpty()) ROOT_PACKAGE else "$ROOT_PACKAGE.$layer"

/** `dsl.gradle` for `import me.tbsten.katachi.dsl.gradle.ModulePackage`, `""` for a root import. */
private fun importedPackageOf(statement: String): String {
    val target = statement.removePrefix("import ").substringBefore(' ').trim()
    return target.removePrefix("$ROOT_PACKAGE.").substringBeforeLast('.', missingDelimiterValue = "")
}

/** The directory the layers live in, found from wherever the test happens to be run. */
private fun sourceRoot(): File {
    val fromModule = File("src/main/kotlin/me/tbsten/katachi")
    return if (fromModule.isDirectory) fromModule else File("katachi/src/main/kotlin/me/tbsten/katachi")
}

private fun sourceFiles(): List<File> =
    sourceRoot().walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.toList()

private fun packageOf(file: File): String =
    file.readLines()
        .firstOrNull { it.startsWith("package ") }
        ?.removePrefix("package ")
        ?.trim()
        ?.removePrefix("$ROOT_PACKAGE.")
        ?.removePrefix(ROOT_PACKAGE)
        .orEmpty()

/** The `import me.tbsten.katachi...` lines of [file], paired with their 1-based line number. */
private fun internalImportsOf(file: File): List<Pair<Int, String>> =
    file.readLines()
        .withIndex()
        .filter { (_, line) -> line.startsWith("import $ROOT_PACKAGE") }
        .map { (index, line) -> index + 1 to line.trim() }

private fun unlistedPackages(): List<UnlistedPackage> = sourceFiles().flatMap { file ->
    val own = packageOf(file)
    val declaring = if (layerOf(own) == null) listOf(UnlistedPackage(file.path, own)) else emptyList()
    val imported = internalImportsOf(file)
        .map { (_, statement) -> importedPackageOf(statement) }
        .filter { layerOf(it) == null }
        .map { UnlistedPackage(file.path, it) }
    declaring + imported
}.distinctBy { it.describe() }

private fun unexpectedImports(): List<UnexpectedImport> = sourceFiles().flatMap { file ->
    val from = layerOf(packageOf(file)) ?: return@flatMap emptyList()
    internalImportsOf(file).mapNotNull { (line, statement) ->
        val to = layerOf(importedPackageOf(statement)) ?: return@mapNotNull null
        if (LAYERS.indexOf(to) <= LAYERS.indexOf(from)) return@mapNotNull null
        UnexpectedImport(file = file, line = line, from = from, to = to, statement = statement)
    }
}

/** The table the failure message opens with, so that a reader sees the rule, not only the breach. */
private fun allowedDirections(): String = buildString {
    appendLine("Allowed import directions (a layer may import from itself and from every layer above it):")
    val width = LAYERS.maxOf { displayNameOf(it).length }
    LAYERS.forEachIndexed { index, layer ->
        val below = LAYERS.take(index).reversed().joinToString(", ") { displayNameOf(it) }
        appendLine("  ${displayNameOf(layer).padEnd(width)} -> ${below.ifEmpty { "(nothing)" }}")
    }
}

class PackageDependencySpec : FreeSpec({
    "依存の向き" - {
        "main のすべてのファイルが許可された向きだけを import している" {
            withClue(allowedDirections()) {
                unexpectedImports().map { it.describe() } shouldBe emptyList()
            }
        }

        "層の表が main のパッケージをひとつ残らず覆っている" {
            withClue("A package no layer claims would let every import of it read as allowed.") {
                unlistedPackages().map { it.describe() } shouldBe emptyList()
            }
        }

        "表に並べた層がどれも空になっていない" {
            val populated = sourceFiles()
                .mapNotNull { layerOf(packageOf(it)) }
                .distinct()
                .sortedBy { LAYERS.indexOf(it) }
            withClue("A layer with no file left means the table is stale, and an empty walk passes silently.") {
                populated shouldContainExactly LAYERS
            }
        }
    }
})
