package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite

/**
 * One value of modifiers or annotations (`private`, `annotated("Inject")`, `public.data`) and whether
 * anything was built from it.
 *
 * A value on a line of its own is not a line: Kotlin would put `@Inject` on the declaration that
 * follows, but the block only sees the expression, so it can never apply. A value that nothing
 * was built from by the end of the run is therefore a mistake, found with [me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem.DanglingModifiers].
 * What builds on a value -- a kind, `anythingElse()`, `or`, the next word of a chain -- calls [use].
 */
internal class ModifierTrace(
    private val alternatives: List<ModifierSet>,
    private val prohibited: Boolean,
    /** Where the first word of the chain was written. */
    val declaredAt: DeclarationSite,
) {
    @Volatile
    var used: Boolean = false
        private set

    fun use() {
        used = true
    }

    /** The value as the line reads, such as `@Inject`, `private` or `not private`. */
    fun line(): String = (if (prohibited) "not " else "") + renderModifierAlternatives(alternatives)

    override fun toString(): String = "ModifierTrace(${line()}, used=$used)"
}

/** Every [ModifierTrace] of one run, in the order the values were written. */
internal class ModifierTraces {
    private val traces = mutableListOf<ModifierTrace>()

    /** Starts keeping [trace] to be asked about when the run ends. */
    fun track(trace: ModifierTrace): ModifierTrace = trace.also { synchronized(traces) { traces += it } }

    /** The first value nothing was built from, or `null` when each of them became part of a line. */
    fun firstDangling(): ModifierTrace? = synchronized(traces) { traces.firstOrNull { !it.used } }

    override fun toString(): String = "ModifierTraces(${traces.size})"
}
