package me.tbsten.katachi.test.dsl

import kotlin.random.Random
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutFile
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcard
import me.tbsten.katachi.dsl.gradle.wildcards

/*
 * Random `layout { }` blocks for the property specs of capture().
 *
 * kotest's property testing (io.kotest.property) is not a dependency of :katachi, so these specs
 * draw from a seeded [Random] of their own and shrink a counterexample greedily ([shrink]). Every
 * trial is reproducible from its seed, which a failure prints together with the shrunk layout.
 */

/** One level of a path in a random layout. */
internal sealed interface RSeg {
    /** A literal directory name. */
    data class Lit(val name: String) : RSeg

    /**
     * A whole-level `*`. [chosen] marks the one `*` a spec renders as `capture(...)` in the named
     * spelling; the plain spelling ignores it.
     */
    data class Star(val chosen: Boolean = false) : RSeg

    /** `**`, which cannot be named. */
    data object DoubleStar : RSeg

    /** A partial wildcard such as `feat*`, which cannot be named either. */
    data class Partial(val glob: String) : RSeg

    /** `capture(name)`, written as such in both spellings. */
    data class Capture(val name: String) : RSeg
}

/** One declaration of a random layout. */
internal sealed interface RNode {
    /** `a / b / c { children }`. [segs] is never empty. */
    data class Dir(val segs: List<RSeg>, val children: List<RNode>) : RNode

    /**
     * `a / b / "name".file()`. A `{W}` in [name] is the module's first wildcard value, read with
     * `wildcards[0]` or, when the module names it, `wildcard(name)`.
     */
    data class File(val dirs: List<RSeg>, val name: String) : RNode

    /**
     * `":feature:*".module { children }`, written directly inside `layout { }`. [captureName] is
     * the name the module's `*` is given in both spellings; [chosen] gives it [NAMED_CAPTURE] in
     * the named spelling only.
     */
    data class Module(
        val children: List<RNode>,
        val captureName: String? = null,
        val chosen: Boolean = false,
    ) : RNode
}

/** The name the one `*` a property spec replaces is given. */
internal const val NAMED_CAPTURE: String = "picked"

/** The key every random module is declared with, so the fake trees know where modules live. */
internal const val RANDOM_MODULE_KEY: String = ":feature:*"

/** Directory names the generators draw from, overlapping the fake trees on purpose. */
internal val LITERAL_DIRECTORIES: List<String> = listOf("feature", "src", "main", "kotlin", "home", "docs", "ui")

/** File name patterns the generators draw from. */
internal val FILE_PATTERNS: List<String> = listOf("*.kt", "*ViewModel.kt", "README.md", "a.kt", "Home*.kt", "*")

/** Partial wildcards the generators draw from. */
internal val PARTIAL_DIRECTORIES: List<String> = listOf("feat*", "*-impl")

// region rendering

/** Declares [nodes] in this scope. [named] renders the chosen `*` as `capture(NAMED_CAPTURE)`. */
internal fun LayoutScope.declare(nodes: List<RNode>, named: Boolean) {
    nodes.forEach { declare(it, named, moduleCapture = null) }
}

private fun LayoutScope.declare(node: RNode, named: Boolean, moduleCapture: String?) {
    when (node) {
        is RNode.Dir -> open(chain(node.segs, named)) {
            node.children.forEach { declare(it, named, moduleCapture) }
        }

        is RNode.File -> {
            val file = fileName(node.name, moduleCapture).file()
            if (node.dirs.isEmpty()) file else attach(chain(node.dirs, named), file)
        }

        is RNode.Module -> {
            val name = node.captureName ?: NAMED_CAPTURE.takeIf { named && node.chosen }
            if (name == null) {
                RANDOM_MODULE_KEY.module { node.children.forEach { declare(it, named, moduleCapture = null) } }
            } else {
                RANDOM_MODULE_KEY.module(capture = name) { node.children.forEach { declare(it, named, name) } }
            }
        }
    }
}

/** [name] with `{W}` read from the module being declared, by name when it has one. */
private fun LayoutScope.fileName(name: String, moduleCapture: String?): String {
    if ("{W}" !in name) return name
    val value = if (moduleCapture != null) wildcard(moduleCapture) else wildcards[0]
    return name.replace("{W}", value)
}

/** A `String` or a [LayoutDirectory], whichever the DSL answers for [segs] chained with `/`. */
private fun LayoutScope.chain(segs: List<RSeg>, named: Boolean): Any =
    segs.drop(1).fold(level(segs.first(), named)) { acc, seg -> div(acc, level(seg, named)) }

private fun LayoutScope.level(seg: RSeg, named: Boolean): Any = when (seg) {
    is RSeg.Lit -> seg.name
    is RSeg.Star -> if (named && seg.chosen) capture(NAMED_CAPTURE) else "*"
    RSeg.DoubleStar -> "**"
    is RSeg.Partial -> seg.glob
    is RSeg.Capture -> capture(seg.name)
}

private fun LayoutScope.div(parent: Any, child: Any): Any = when {
    parent is String && child is String -> parent / child
    parent is String && child is LayoutDirectory -> parent / child
    parent is LayoutDirectory && child is String -> parent / child
    parent is LayoutDirectory && child is LayoutDirectory -> parent / child
    else -> error("unexpected $parent / $child")
}

private fun LayoutScope.open(directory: Any, block: me.tbsten.katachi.dsl.LayoutDirectoryScope.() -> Unit) {
    when (directory) {
        is String -> directory(block)
        is LayoutDirectory -> directory(block)
        else -> error("unexpected $directory")
    }
}

private fun LayoutScope.attach(directory: Any, file: LayoutFile) {
    when (directory) {
        is String -> directory / file
        is LayoutDirectory -> directory / file
        else -> error("unexpected $directory")
    }
}

/** [nodes] as the Kotlin a user would write, for a failure message. */
internal fun renderDsl(nodes: List<RNode>, named: Boolean): String = buildString {
    fun seg(s: RSeg): String = when (s) {
        is RSeg.Lit -> "\"${s.name}\""
        is RSeg.Star -> if (named && s.chosen) "capture(\"$NAMED_CAPTURE\")" else "\"*\""
        RSeg.DoubleStar -> "\"**\""
        is RSeg.Partial -> "\"${s.glob}\""
        is RSeg.Capture -> "capture(\"${s.name}\")"
    }

    fun line(indent: Int, text: String) = append("  ".repeat(indent)).append(text).append('\n')
    fun node(n: RNode, indent: Int, moduleCapture: String?) {
        when (n) {
            is RNode.Dir -> {
                line(indent, n.segs.joinToString(" / ", transform = ::seg) + " {")
                n.children.forEach { node(it, indent + 1, moduleCapture) }
                line(indent, "}")
            }

            is RNode.File -> {
                val read = if (moduleCapture != null) "\${wildcard(\"$moduleCapture\")}" else "\${wildcards[0]}"
                val file = "\"${n.name.replace("{W}", read)}\".file()"
                line(indent, (n.dirs.map(::seg) + file).joinToString(" / "))
            }

            is RNode.Module -> {
                val name = n.captureName ?: NAMED_CAPTURE.takeIf { named && n.chosen }
                val call = if (name == null) "module" else "module(capture = \"$name\")"
                line(indent, "\"$RANDOM_MODULE_KEY\".$call {")
                n.children.forEach { node(it, indent + 1, name) }
                line(indent, "}")
            }
        }
    }
    nodes.forEach { node(it, 0, null) }
}

// endregion

// region generation

/** A random layout whose `*`s carry no mark; see [markOneStar]. */
internal fun Random.randomLayout(withModule: Boolean = nextInt(3) == 0): List<RNode> {
    val nodes = MutableList(nextInt(1, 4)) { node(depth = 0) }
    if (withModule) nodes.add(nextInt(nodes.size + 1), RNode.Module(List(nextInt(1, 3)) { moduleChild() }))
    return nodes
}

private fun Random.node(depth: Int): RNode =
    if (depth >= 2 || nextInt(2) == 0) {
        RNode.File(List(nextInt(0, 3)) { seg() }, FILE_PATTERNS.random(this))
    } else {
        RNode.Dir(List(nextInt(1, 3)) { seg() }, List(nextInt(0, 3)) { node(depth + 1) })
    }

private fun Random.moduleChild(): RNode {
    val name = if (nextBoolean()) "{W}ViewModel.kt" else FILE_PATTERNS.random(this)
    return RNode.File(listOf(RSeg.Lit("src")) + List(nextInt(0, 2)) { seg() }, name)
}

private fun Random.seg(): RSeg = when (nextInt(10)) {
    in 0..4 -> RSeg.Lit(LITERAL_DIRECTORIES.random(this))
    in 5..7 -> RSeg.Star()
    8 -> RSeg.DoubleStar
    else -> RSeg.Partial(PARTIAL_DIRECTORIES.random(this))
}

/** How many places [nodes] has that a `capture(...)` may replace: whole-level `*`s and module keys. */
internal fun starCount(nodes: List<RNode>): Int = nodes.sumOf { node ->
    when (node) {
        is RNode.Dir -> node.segs.count { it is RSeg.Star } + starCount(node.children)
        is RNode.File -> node.dirs.count { it is RSeg.Star }
        is RNode.Module -> (if (node.captureName == null) 1 else 0) + starCount(node.children)
    }
}

/** Whether [nodes] still holds the `*` [markOneStar] chose. */
internal fun hasChosenStar(nodes: List<RNode>): Boolean = nodes.any { node ->
    when (node) {
        is RNode.Dir -> node.segs.any { it is RSeg.Star && it.chosen } || hasChosenStar(node.children)
        is RNode.File -> node.dirs.any { it is RSeg.Star && it.chosen }
        is RNode.Module -> node.chosen || hasChosenStar(node.children)
    }
}

/** [nodes] with the [index]th place [starCount] counts marked as the one to name. */
internal fun markOneStar(nodes: List<RNode>, index: Int): List<RNode> {
    var remaining = index
    fun segs(list: List<RSeg>): List<RSeg> = list.map { s ->
        if (s is RSeg.Star) {
            (if (remaining == 0) RSeg.Star(chosen = true) else s).also { remaining-- }
        } else {
            s
        }
    }

    fun walk(list: List<RNode>): List<RNode> = list.map { node ->
        when (node) {
            is RNode.Dir -> RNode.Dir(segs(node.segs), walk(node.children))
            is RNode.File -> RNode.File(segs(node.dirs), node.name)
            is RNode.Module -> {
                val chosen = node.captureName == null && remaining == 0
                if (node.captureName == null) remaining--
                node.copy(chosen = chosen, children = walk(node.children))
            }
        }
    }
    return walk(nodes)
}

// endregion

// region shrinking

/** Every layout one step smaller than [nodes]: a declaration, a child or a level fewer. */
internal fun smallerLayouts(nodes: List<RNode>): Sequence<List<RNode>> = sequence {
    for (i in nodes.indices) {
        yield(nodes.filterIndexed { j, _ -> j != i })
        for (smaller in smallerNodes(nodes[i])) yield(nodes.toMutableList().also { it[i] = smaller })
    }
}

private fun smallerNodes(node: RNode): Sequence<RNode> = sequence {
    when (node) {
        is RNode.Dir -> {
            for (i in node.segs.indices) {
                if (node.segs.size > 1) yield(node.copy(segs = node.segs.filterIndexed { j, _ -> j != i }))
            }
            for (children in smallerLayouts(node.children)) yield(node.copy(children = children))
        }

        is RNode.File -> for (i in node.dirs.indices) {
            yield(node.copy(dirs = node.dirs.filterIndexed { j, _ -> j != i }))
        }

        is RNode.Module -> for (children in smallerLayouts(node.children)) {
            if (children.isNotEmpty()) yield(node.copy(children = children))
        }
    }
}

/**
 * The smallest input reachable from [failing] by steps of [smaller] that still [fails].
 *
 * Greedy: it takes the first smaller input that still fails and starts over from there, which
 * is enough for the handful of declarations a random layout holds.
 */
internal fun <T> shrink(failing: T, smaller: (T) -> Sequence<T>, fails: (T) -> Boolean): T {
    var current = failing
    var steps = 0
    while (steps < 500) {
        current = smaller(current).firstOrNull { fails(it) } ?: return current
        steps++
    }
    return current
}

// endregion
