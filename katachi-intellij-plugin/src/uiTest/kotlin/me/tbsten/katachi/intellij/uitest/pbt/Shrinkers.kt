package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.Shrinker
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.list

/*
 * Shrinkers that drop one piece at a time, so that a failure ends on the few operations and
 * templates that matter: kotest's own list shrinking mostly cuts from the ends.
 */

/**
 * Sequences of [stepArb] (single operations and the runs of Scenarios.kt), at most [range]`.last`
 * long, shrinking by halves and then one step at a time.
 */
internal fun opsArb(range: IntRange): Arb<List<Op>> = arbitrary(OpsShrinker) { Arb.list(stepArb, range).bind().flatten().take(range.last) }

private object OpsShrinker : Shrinker<List<Op>> {
    override fun shrink(value: List<Op>): List<List<Op>> {
        if (value.size <= 1) return emptyList()
        val halves = if (value.size >= 4) listOf(value.take(value.size / 2), value.drop(value.size / 2)) else emptyList()
        return (halves + value.indices.map { index -> value.withoutIndex(index) }).distinct()
    }
}

/**
 * Catalogs shrinking to the first definition alone, then one module or one template fewer (from
 * every definition at once, so that the later ones stay changes of the first).
 */
internal object CatalogShrinker : Shrinker<Catalog> {
    override fun shrink(value: Catalog): List<Catalog> = buildList {
        if (value.worlds.size > 1) add(Catalog(listOf(value.initial)))
        val first = value.initial
        if (first.modules.size > 1) {
            val kept = first.modules.dropLast(1).map { it.module }.toSet()
            add(Catalog(value.worlds.map { world -> World(world.modules.filter { it.module in kept }) }.filter { it.modules.isNotEmpty() }))
        }
        for ((moduleIndex, module) in first.modules.withIndex()) {
            for (templateIndex in module.specs.indices) {
                add(Catalog(value.worlds.map { world -> world.withoutTemplate(moduleIndex, templateIndex) }))
            }
        }
    }.filter { it.worlds.isNotEmpty() && it != value }.distinct()
}

private fun World.withoutTemplate(moduleIndex: Int, templateIndex: Int): World = World(
    modules.mapIndexed { index, module ->
        if (index == moduleIndex && templateIndex < module.specs.size) module.copy(specs = module.specs.withoutIndex(templateIndex)) else module
    },
)

private fun <T> List<T>.withoutIndex(index: Int): List<T> = filterIndexed { i, _ -> i != index }
