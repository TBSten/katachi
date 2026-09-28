package me.tbsten.katachi.test.template

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import kotlin.random.Random
import me.tbsten.katachi.check.ViolationKind
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.KatachiAmbiguousTemplatePlacementException
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.template.KatachiNoTemplatePlacementException
import me.tbsten.katachi.template.KatachiTemplateModuleNotFoundException
import me.tbsten.katachi.template.KatachiWildcardTemplatePlacementException
import me.tbsten.katachi.template.internal.templateFiles
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.RNode
import me.tbsten.katachi.test.dsl.RSeg
import me.tbsten.katachi.test.dsl.declare
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem
import me.tbsten.katachi.test.dsl.randomLayout
import me.tbsten.katachi.test.dsl.layoutOf
import me.tbsten.katachi.test.dsl.moduleIndexOf
import me.tbsten.katachi.test.dsl.renderDsl
import me.tbsten.katachi.test.dsl.shrink
import me.tbsten.katachi.test.dsl.smallerLayouts

private val ITERATIONS: Int = System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull() ?: 500
private val FIRST_SEED: Long = System.getenv("KATACHI_PBT_SEED")?.toLongOrNull() ?: 20260928L

private const val ROLE = "Role"

/** Path capture names. A module's `*` is always [MODULE_CAPTURE], which none of these is. */
private val CAPTURE_NAMES = listOf("feature", "layer", "part")
private const val MODULE_CAPTURE = "mod"

/** The modules the project has: `:feature:home` and `:feature:settings`. */
private val EXISTING_MODULES = listOf("home", "settings")

private val CAPTURE_VALUES = listOf("home", "settings", "ui")
private val MODULE_VALUES = EXISTING_MODULES + "ghost"
private val TEMPLATE_FILE_NAMES =
    listOf("HomeViewModel.kt", "a.kt", "README.md", "homeViewModel.kt", "settingsViewModel.kt", "Home.kt", "Stray.txt")

/** Every failure placement is allowed to end in: each names why there is not exactly one place. */
private val PLACEMENT_FAILURES = setOf(
    KatachiAmbiguousTemplatePlacementException::class,
    KatachiNoTemplatePlacementException::class,
    KatachiWildcardTemplatePlacementException::class,
    KatachiMissingTemplateCaptureException::class,
    KatachiTemplateModuleNotFoundException::class,
)

private data class Trial(val layout: List<RNode>, val fileNames: List<String>, val values: Map<String, String>)

/** A random layout whose `*`s are named at random, never twice with one name along one path. */
private fun Random.capturingLayout(): List<RNode> {
    fun segs(list: List<RSeg>, used: MutableSet<String>): List<RSeg> = list.map { seg ->
        val free = CAPTURE_NAMES - used
        if (seg is RSeg.Star && free.isNotEmpty() && nextInt(10) < 6) {
            RSeg.Capture(free.random(this)).also { used += it.name }
        } else {
            seg
        }
    }

    fun walk(list: List<RNode>, used: Set<String>): List<RNode> = list.map { node ->
        when (node) {
            is RNode.Dir -> {
                val here = used.toMutableSet()
                val named = segs(node.segs, here)
                RNode.Dir(named, walk(node.children, here))
            }

            is RNode.File -> RNode.File(segs(node.dirs, used.toMutableSet()), node.name)
            is RNode.Module -> node.copy(
                captureName = MODULE_CAPTURE.takeIf { nextBoolean() },
                children = walk(node.children, used),
            )
        }
    }
    return walk(randomLayout(withModule = nextInt(3) == 0), emptySet())
}

private fun captureNamesIn(nodes: List<RNode>): Set<String> = nodes.flatMapTo(linkedSetOf()) { node ->
    when (node) {
        is RNode.Dir -> node.segs.filterIsInstance<RSeg.Capture>().map { it.name } + captureNamesIn(node.children)
        is RNode.File -> node.dirs.filterIsInstance<RSeg.Capture>().map { it.name }
        is RNode.Module -> listOfNotNull(node.captureName) + captureNamesIn(node.children)
    }
}

private fun Random.trial(): Trial {
    val layout = capturingLayout()
    val values = captureNamesIn(layout).filter { nextInt(4) != 0 }.associateWith { name ->
        if (name == MODULE_CAPTURE) MODULE_VALUES.random(this) else CAPTURE_VALUES.random(this)
    }
    return Trial(layout, TEMPLATE_FILE_NAMES.shuffled(this).take(nextInt(1, 3)), values)
}

private fun architectureOf(trial: Trial): Architecture = architecture {
    files = wholeTree()
    "g".group {
        ROLE {
            layout { declare(trial.layout, named = false) }
            template { trial.fileNames.forEach { name -> file(name) { "" } } }
        }
    }
}

/** katachi's glob as the model reads it: `*` is one character or more within a level. */
private fun globMatches(pattern: String, name: String): Boolean =
    Regex(pattern.split('*').joinToString("[^/]+") { Regex.escape(it) }).matches(name)

/** One level of a declared path as the model reads it. */
private sealed interface Level {
    /** A literal level, or a capture the run gave [name] for. */
    data class Named(val name: String, val byCapture: Boolean) : Level

    /** A capture the run gave no value. */
    data object Missing : Level

    /** A `*` without a name, or anything else that names no single directory. */
    data object Unnamed : Level
}

/**
 * Where the model says [fileName] goes, or `null` when the run has to fail.
 *
 * Every declared file pattern that accepts the name is a place, and the places are narrowed the
 * way the design says: places reached through a capture the run gave a value win over the rest;
 * without one, a place that needs a capture the run did not give fails the run; otherwise every
 * place whose directories are all literal is a candidate. A module value that picks no module
 * fails the run whatever else takes the file.
 */
private fun expectedPlaces(trial: Trial, fileName: String): Set<String>? {
    val captured = linkedSetOf<String>()
    val plain = linkedSetOf<String>()
    var missing = false
    var missesModule = false
    fun walk(list: List<RNode>, prefix: List<Level>, moduleValue: String?) {
        for (node in list) {
            when (node) {
                is RNode.Dir -> walk(node.children, prefix + node.segs.map { level(it, trial.values) }, moduleValue)
                is RNode.File -> {
                    val dirs = prefix + node.dirs.map { level(it, trial.values) }
                    val pattern = if ("{W}" in node.name) moduleValue?.let { node.name.replace("{W}", it) } else node.name
                    if (pattern == null || !globMatches(pattern, fileName)) continue
                    when {
                        dirs.any { it == Level.Missing } -> missing = true
                        dirs.any { it == Level.Unnamed } -> Unit
                        else -> {
                            val named = dirs.filterIsInstance<Level.Named>()
                            val path = (named.map { it.name } + fileName).joinToString("/")
                            if (named.any { it.byCapture }) captured += path else plain += path
                        }
                    }
                }

                is RNode.Module -> {
                    val value = node.captureName?.let { trial.values[it] }
                    if (value != null && value !in EXISTING_MODULES) missesModule = true
                    val bound = value?.takeIf { it in EXISTING_MODULES }
                    val moduleLevel = when {
                        bound != null -> Level.Named(bound, byCapture = true)
                        node.captureName != null && value == null -> Level.Missing
                        else -> Level.Unnamed
                    }
                    walk(node.children, listOf(Level.Named("feature", byCapture = false), moduleLevel), bound)
                }
            }
        }
    }
    walk(trial.layout, emptyList(), null)
    return when {
        missesModule -> null
        captured.isNotEmpty() -> captured
        missing -> null
        else -> plain
    }
}

/** A level as the model reads it. */
private fun level(seg: RSeg, values: Map<String, String>): Level = when (seg) {
    is RSeg.Lit -> Level.Named(seg.name, byCapture = false)
    is RSeg.Capture -> values[seg.name]?.let { Level.Named(it, byCapture = true) } ?: Level.Missing
    else -> Level.Unnamed
}

private fun modules(): ModuleIndex = moduleIndexOf(*EXISTING_MODULES.map { "feature/$it" }.toTypedArray())

private fun generate(trial: Trial): Map<String, String> = architectureOf(trial).process(ForbiddenFileSystem) { context ->
    templateFiles(context, ROLE, trial.values, ::modules)
}

/** Throws an [AssertionError] when the run disagrees with the model or leaves a file the check rejects. */
private fun checkPlacement(trial: Trial) {
    val expected = trial.fileNames.associateWith { expectedPlaces(trial, it) }
    val unique = expected.values.all { it != null && it.size == 1 }
    val result = runCatching { generate(trial) }
    if (!unique) {
        val thrown = result.exceptionOrNull()
            ?: throw AssertionError("expected a failure (places: $expected), but it generated ${result.getOrNull()?.keys}")
        if (thrown::class !in PLACEMENT_FAILURES) throw AssertionError("expected a placement failure, got $thrown", thrown)
        return
    }
    val generated = result.getOrElse { throw AssertionError("expected ${expected.values} but it threw $it", it) }
    val paths = expected.values.map { it!!.single() }.toSet()
    if (generated.keys != paths) throw AssertionError("generated ${generated.keys}, the model says $paths")

    val hasModule = trial.layout.any { it is RNode.Module }
    val tree = repositoryOf {
        generated.keys.forEach { it() }
        if (hasModule) EXISTING_MODULES.forEach { "feature/$it/build.gradle.kts"() }
    }
    val unexpected = architectureOf(trial).validate(tree).filter { violation ->
        violation.kind == ViolationKind.Unexpected &&
            generated.keys.any { it == violation.path || it.startsWith(violation.path + "/") }
    }
    if (unexpected.isNotEmpty()) {
        throw AssertionError("generated ${generated.keys}, but the check reports ${unexpected.map { "[${it.label}] ${it.path}" }}")
    }
}

private fun Trial.describe(): String =
    "layout:\n${renderDsl(layout, named = false)}template files: $fileNames\nvalues: $values"

/**
 * Generating a file for a role whose layout names directories with `capture("...")` places each
 * file exactly where a small model of the layout says: when the model finds one directory, the
 * file goes there and the check accepts it (no `Unexpected` on it or its directories); when it
 * finds none or several, or a module value picks no module, the run fails with a placement
 * exception rather than writing anywhere.
 *
 * The model reads the random layout directly: a declared file pattern is a place for a name when
 * its glob accepts the name and every directory above it is a literal or a capture given a value.
 */
class TemplateCapturePlacementPropertySpec : FreeSpec({
    "生成先がちょうど1つなら生成したファイルは検査を通り、0 / 2つ以上なら生成せずに落ちる" {
        var tried = 0
        var succeeded = 0
        var succeededInModule = 0
        for (i in 0 until ITERATIONS) {
            val seed = FIRST_SEED + i
            val trial = Random(seed).trial()
            if (runCatching { layoutOf { declare(trial.layout, named = false) } }.isFailure) continue
            tried++
            if (trial.fileNames.all { expectedPlaces(trial, it)?.size == 1 }) {
                succeeded++
                if (trial.fileNames.any { name -> expectedPlaces(trial, name)!!.single().let { p -> EXISTING_MODULES.any { p.startsWith("feature/$it/src/") } } }) {
                    succeededInModule++
                }
            }

            val failure = runCatching { checkPlacement(trial) }.exceptionOrNull() ?: continue
            val shrunk = shrink(
                trial,
                smaller = { t ->
                    smallerLayouts(t.layout).map { t.copy(layout = it) } +
                        t.fileNames.indices.asSequence().filter { t.fileNames.size > 1 }
                            .map { j -> t.copy(fileNames = t.fileNames.filterIndexed { k, _ -> k != j }) } +
                        t.values.keys.asSequence().map { k -> t.copy(values = t.values - k) }
                },
                fails = { t -> runCatching { checkPlacement(t) }.isFailure },
            )
            val shrunkFailure = runCatching { checkPlacement(shrunk) }.exceptionOrNull()
            fail("seed $seed: ${failure.message}\n\nshrunk:\n${shrunk.describe()}\n${shrunkFailure?.message}")
        }
        println(
            "TemplateCapturePlacementPropertySpec: $tried trials ($succeeded expected to generate, " +
                "$succeededInModule of them into feature/<module>/src/)",
        )
        tried shouldBeGreaterThan ITERATIONS / 2
        // Both halves of the property have to be exercised.
        succeeded shouldBeGreaterThan tried / 10
        (tried - succeeded) shouldBeGreaterThan tried / 10
    }
})
