package me.tbsten.katachi.test.template

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.GenerateCodeFromTemplate
import me.tbsten.katachi.template.KatachiInvalidTemplateCaptureValueException
import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
import me.tbsten.katachi.template.internal.templateFiles
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem
import me.tbsten.katachi.test.dsl.moduleIndexOf

private val ITERATIONS: Int = System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull() ?: 2000
private val FIRST_SEED: Long = System.getenv("KATACHI_PBT_SEED")?.toLongOrNull() ?: 20260928L

/** How many of the values are also run through the whole processor against a real directory. */
private const val DISK_RUNS = 150

/**
 * Characters a value is drawn from, weighted towards the ones the check is about: separators,
 * dots, katachi's glob metacharacters, what Windows refuses, control characters -- and ordinary
 * letters, so that some values are fine.
 */
private val ALPHABET: List<Char> = buildList {
    repeat(6) { addAll("abhomeXZ09-_".toList()) }
    addAll("/\\..:*?[]{}\"<>| ~#%é日".toList())
    addAll((0 until 0x20).map(Int::toChar))
    add('\u007f')
    add(' ')
}

private fun Random.value(): String = when (nextInt(20)) {
    0 -> ""
    1 -> "."
    2 -> ".."
    3 -> listOf("CON", "nul", "Com1", "lpt9.txt", "aux.kt", "console").random(this)
    else -> String(CharArray(nextInt(1, 7)) { ALPHABET.random(this) })
}

/**
 * Whether a capture value may be refused: what the design says one directory level cannot be.
 * The spec asserts the implication one way -- such a value is always refused -- and reports the
 * values it accepted, so a value the design would refuse cannot slip through unnoticed.
 */
private fun mustBeRefused(value: String): Boolean =
    value.isBlank() || value == "." || value == ".." ||
        value.any { it == '/' || it == '\\' || it < ' ' || it in '\u007f'..'\u009f' || it in "*?[]{}:\"<>|\u2028\u2029" } ||
        value.first().isWhitespace() || value.last().isWhitespace() || value.last() == '.' ||
        value.substringBefore('.').uppercase() in WINDOWS_RESERVED

/** The device names Windows reserves, which no directory may be named whatever its extension. */
private val WINDOWS_RESERVED: Set<String> = setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") }

private fun pathArchitecture(): Architecture = architecture {
    "ViewModel" {
        layout { "feature" / capture("feature") / "*ViewModel.kt".file() }
        template { file("HomeViewModel.kt") { "" } }
    }
}

private fun moduleArchitecture(): Architecture = architecture {
    "ViewModel" {
        layout { ":feature:*".module(capture = "feature") { "src" / "*ViewModel.kt".file() } }
        template { file("HomeViewModel.kt") { "" } }
    }
}

/** Fails the spec if placement asks which modules exist: a value is checked before that. */
private val forbiddenModules: () -> ModuleIndex = { throw AssertionError("the modules were read") }

private fun Architecture.generated(value: String, modules: () -> ModuleIndex): Result<Map<String, String>> =
    runCatching { process(ForbiddenFileSystem) { context -> templateFiles(context, "ViewModel", mapOf("feature" to value), modules) } }

/** The whole run, `GenerateCodeFromTemplate`, into a project inside a directory of its own. */
private fun Architecture.runOnDisk(value: String): Pair<Result<Unit>, List<String>> {
    val outer = Files.createTempDirectory("katachi-capture-value").toFile()
    try {
        val root = File(outer, "project").apply { mkdirs() }
        File(root, "gradlew").writeText("")
        val context = FakeArchitectureProcessContext(
            architecture = this,
            args = GenerateCodeFromTemplate.Args(roleName = "ViewModel"),
            fileSystem = RealFileSystem(root),
            rawArgs = mapOf("roleName" to "ViewModel", "feature" to value),
        )
        val result = runCatching { GenerateCodeFromTemplate.process(context).getOrThrow() }.map { }
        // Everything below the outer directory, so a file written outside the project shows too.
        val written = outer.walkTopDown().filter { it.isFile }
            .map { it.toRelativeString(outer).replace(File.separatorChar, '/') }.sorted().toList()
        return result to written
    } finally {
        outer.deleteRecursively()
    }
}

private fun String.escaped(): String = buildString {
    this@escaped.forEach { c -> if (c < ' ' || c == '\u007f' || c == ' ') append("\\u%04x".format(c.code)) else append(c) }
}

/**
 * A `capture` value is checked before anything touches the disk: a value that cannot be one
 * directory level -- empty, `.`, `..`, a separator, a control character, a glob metacharacter or a
 * character Windows refuses -- is refused with [KatachiInvalidTemplateCaptureValueException]
 * without reading the tree, without asking which modules exist, and without writing a file.
 */
class TemplateCaptureValuePropertySpec : FreeSpec({
    "壊れた capture の値はディスクに触る前に必ず落ちる" - {
        for ((kind, arch) in listOf("パスの capture" to pathArchitecture(), "モジュールの capture" to moduleArchitecture())) {
            "$kind（ForbiddenFileSystem・モジュール一覧も読ませない）" {
                var refused = 0
                val acceptedOdd = sortedSetOf<String>()
                for (i in 0 until ITERATIONS) {
                    val value = Random(FIRST_SEED + i).value()
                    val result = arch.generated(value, forbiddenModules)
                    val thrown = result.exceptionOrNull()
                    if (mustBeRefused(value)) {
                        if (thrown !is KatachiInvalidTemplateCaptureValueException || thrown.value != value) {
                            fail("seed ${FIRST_SEED + i}: \"${value.escaped()}\" was not refused as a capture value: ${result.fold({ "generated ${it.keys}" }, { "$it" })}")
                        }
                        refused++
                    } else {
                        // A value that is fine gets as far as placing the file: for a path capture that
                        // is done, for a module capture it is the moment the modules are asked for.
                        val reachedPlacement = when (kind) {
                            "パスの capture" -> result.getOrNull()?.keys == setOf("feature/$value/HomeViewModel.kt")
                            else -> thrown is AssertionError && thrown.message == "the modules were read"
                        }
                        if (!reachedPlacement) fail("seed ${FIRST_SEED + i}: \"${value.escaped()}\" was refused: $thrown")
                        if (value.any { !it.isLetterOrDigit() && it !in "-_" }) acceptedOdd += value.escaped()
                    }
                }
                println("TemplateCaptureValuePropertySpec[$kind]: $ITERATIONS values, $refused refused; accepted non-identifier values: ${acceptedOdd.take(40)}")
                refused shouldBeGreaterThan ITERATIONS / 2
            }
        }

        "GenerateCodeFromTemplate を実ディレクトリで回しても、壊れた値では1ファイルも書かない" {
            var refused = 0
            for (i in 0 until DISK_RUNS) {
                val value = Random(FIRST_SEED + i).value()
                val (result, written) = pathArchitecture().runOnDisk(value)
                if (mustBeRefused(value)) {
                    if (result.exceptionOrNull() !is KatachiInvalidTemplateCaptureValueException || written != listOf("project/gradlew")) {
                        fail("seed ${FIRST_SEED + i}: \"${value.escaped()}\" -> $result, files: $written")
                    }
                    refused++
                } else if (result.isFailure || written != listOf("project/feature/$value/HomeViewModel.kt", "project/gradlew").sorted()) {
                    fail("seed ${FIRST_SEED + i}: \"${value.escaped()}\" is a fine value but -> $result, files: $written")
                }
            }
            refused shouldBeGreaterThan DISK_RUNS / 2
        }

        "モジュールの capture に存在しないが正しい形の値を渡すと、モジュールが無いとして落ちる（比較用）" {
            val thrown = moduleArchitecture().generated("ghost", { moduleIndexOf("feature/home") }).exceptionOrNull()
            if (thrown !is KatachiTemplateModuleNotFoundException) fail("expected KatachiTemplateModuleNotFoundException, got $thrown")
        }
    }
})
