package me.tbsten.katachi.test.dsl

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.random.Random
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.test.check.layoutArchitecture
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.FakeFileSystem

/** How many random layouts the spec tries. `KATACHI_PBT_ITERATIONS` raises it for a longer run. */
private val ITERATIONS: Int = System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull() ?: 500

/** The first seed. `KATACHI_PBT_SEED` replays another run. */
private val FIRST_SEED: Long = System.getenv("KATACHI_PBT_SEED")?.toLongOrNull() ?: 20260928L

/** Values a whole-level `*` takes in a random tree. None holds a `.`, so no directory is ever a file's name. */
private val DIRECTORY_VALUES = listOf("home", "settings", "x")

/** One trial: a layout with one `*` marked, and the files of a tree to check it against. */
private data class Trial(val layout: List<RNode>, val files: Set<String>)

/** The trial's layout concretized into files the layout declares, plus files it does not. */
private fun Random.filesFor(nodes: List<RNode>): Set<String> {
    val files = linkedSetOf<String>()
    fun concretize(seg: RSeg): List<String> = when (seg) {
        is RSeg.Lit -> listOf(seg.name)
        is RSeg.Star -> listOf(DIRECTORY_VALUES.random(this))
        RSeg.DoubleStar -> List(nextInt(0, 3)) { DIRECTORY_VALUES.random(this) }
        is RSeg.Partial -> listOf(seg.glob.replace("*", DIRECTORY_VALUES.random(this)))
        is RSeg.Capture -> listOf(DIRECTORY_VALUES.random(this))
    }

    fun fileName(pattern: String, moduleValue: String?): String = when (pattern) {
        "*" -> listOf("Stray.txt", "Home.kt").random(this)
        else -> pattern.replace("{W}", moduleValue ?: "x").replace("*", listOf("", "Home", "Foo").random(this))
    }

    fun walk(list: List<RNode>, prefix: List<String>, moduleValue: String?) {
        for (node in list) {
            when (node) {
                is RNode.Dir -> repeat(nextInt(0, 3)) {
                    walk(node.children, prefix + node.segs.flatMap(::concretize), moduleValue)
                }

                is RNode.File -> repeat(nextInt(0, 3)) {
                    files += (prefix + node.dirs.flatMap(::concretize) + fileName(node.name, moduleValue)).joinToString("/")
                }

                is RNode.Module -> repeat(nextInt(0, 3)) {
                    val value = DIRECTORY_VALUES.random(this)
                    files += "feature/$value/build.gradle.kts"
                    walk(node.children, listOf("feature", value), value)
                }
            }
        }
    }
    walk(nodes, emptyList(), null)
    // Files no declaration asked for, at depths the layout may or may not reach.
    repeat(nextInt(0, 5)) {
        val directories = List(nextInt(0, 4)) { (LITERAL_DIRECTORIES + DIRECTORY_VALUES).random(this) }
        files += (directories + listOf("Stray.txt", "HomeViewModel.kt", "a.kt", "README.md").random(this)).joinToString("/")
    }
    return files
}

private fun treeOf(files: Set<String>): FakeFileSystem = repositoryOf {
    files.forEach { it() }
}

/** What a check reports, with nothing that depends on where the declaration was written. */
private fun List<Violation>.reported(): List<String> =
    map { "${it.kind} ${it.severity} [${it.label}] ${it.path} ${it.details}" }.sorted()

/** What a `*` a module key named reads as without the modules: `<feature>`, `<picked>` and so on. */
private val NAMED_PLACEHOLDER = Regex("<[A-Za-z][A-Za-z0-9_-]*>")

/** A result's value, or the exception's class when it failed: two spellings must fail the same way. */
private fun <T> outcome(block: () -> T): Any = runCatching(block).fold({ it as Any }, { "threw ${it::class.qualifiedName}" })

/** Throws an [AssertionError] naming the first thing the two spellings of [trial] disagree on. */
private fun checkSame(trial: Trial) {
    val named: me.tbsten.katachi.dsl.LayoutScope.() -> Unit = { declare(trial.layout, named = true) }
    val plain: me.tbsten.katachi.dsl.LayoutScope.() -> Unit = { declare(trial.layout, named = false) }
    val tree = treeOf(trial.files)
    val index = moduleIndex(tree, FsPath.of("/repo"), ModuleResolver.Conventional)

    val comparisons = listOf(
        // Without the modules, a named `*` of a module key reads as `<its name>` rather than
        // `<name>` -- a placeholder only, the one difference naming is meant to make there.
        "flattened (unresolved index)" to { f: me.tbsten.katachi.dsl.LayoutScope.() -> Unit ->
            outcome { layoutOf(f).shape().map { it.replace(NAMED_PLACEHOLDER, "<name>") } }
        },
        "flattened (the tree's modules)" to { f: me.tbsten.katachi.dsl.LayoutScope.() -> Unit -> outcome { layoutOf(index, f).shape() } },
        "violations" to { f: me.tbsten.katachi.dsl.LayoutScope.() -> Unit ->
            outcome { layoutArchitecture(block = f).validate(tree).reported() }
        },
    )
    for ((what, run) in comparisons) {
        val a = run(named)
        val b = run(plain)
        if (a != b) throw AssertionError("$what differ\n  capture: $a\n  *      : $b")
    }
}

private fun Trial.describe(): String = buildString {
    append("named spelling:\n").append(renderDsl(layout, named = true))
    append("files:\n").append(files.sorted().joinToString("\n") { "  $it" })
}

/**
 * Replacing any one whole-level `*` of a layout with `capture("...")` changes nothing the check
 * reads: the flattened entries (as [shape] shows them) and the violations over a fake tree are
 * the same, line for line.
 *
 * Layouts are drawn at random -- nested blocks, `/` chains, `*`, `**`, partial wildcards, file
 * globs and a `":feature:*"` module whose `*` is itself one of the places that can be named --
 * and the tree holds concretized copies of the declarations plus files nothing declares. A
 * counterexample is shrunk before it is reported.
 */
class LayoutCapturePropertySpec : FreeSpec({
    "任意の1つの * を capture に置き換えても、平坦化の結果と検査の違反の列は変わらない" {
        var tried = 0
        var bothThrew = 0
        var withViolations = 0
        var moduleNamed = 0
        for (i in 0 until ITERATIONS) {
            val seed = FIRST_SEED + i
            val random = Random(seed)
            val raw = random.randomLayout()
            val stars = starCount(raw)
            if (stars == 0) continue
            val layout = markOneStar(raw, random.nextInt(stars))
            val trial = Trial(layout, random.filesFor(layout))
            tried++
            if (runCatching { layoutOf { declare(layout, named = false) } }.isFailure) bothThrew++
            if (layout.any { it is RNode.Module && it.chosen }) moduleNamed++
            val violations = runCatching { layoutArchitecture { declare(layout, named = false) }.validate(treeOf(trial.files)) }
            if (violations.getOrNull().orEmpty().isNotEmpty()) withViolations++

            val failure = runCatching { checkSame(trial) }.exceptionOrNull() ?: continue
            val shrunk = shrink(
                trial,
                smaller = { t ->
                    smallerLayouts(t.layout).filter(::hasChosenStar).map { t.copy(layout = it) } +
                        t.files.asSequence().map { f -> t.copy(files = t.files - f) }
                },
                fails = { t -> runCatching { checkSame(t) }.isFailure },
            )
            val shrunkFailure = runCatching { checkSame(shrunk) }.exceptionOrNull()
            fail(
                "seed $seed: ${failure.message}\n\nshrunk:\n${shrunk.describe()}\n${shrunkFailure?.message}",
            )
        }
        println(
            "LayoutCapturePropertySpec: $tried layouts tried ($bothThrew rejected by the DSL in both spellings, " +
                "$withViolations with violations, $moduleNamed naming the module's *)",
        )
        tried shouldBeGreaterThan ITERATIONS / 2
        // A property over trees that never violate anything would say nothing about the check.
        withViolations shouldBeGreaterThan tried / 4
    }
})
