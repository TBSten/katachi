package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem

/**
 * What every value of the vocabulary keeps: the list of the block it was made in, which is the
 * only one a line may be written into with it, and its [trace].
 */
internal class VocabularyWord(val list: DeclarationListBuilder, val trace: ValueTrace) {
    /**
     * The word of the `or` of this value and [other], kept to be asked about in its turn; both
     * sides count as used. [describe] says how the joined value reads.
     *
     * @throws KatachiKtFileContentLineException when [other] belongs to another block, [line] being
     *   the `or` as written.
     */
    fun or(other: VocabularyWord, line: String, describe: () -> String): VocabularyWord {
        other.requireIn(list, line)
        trace.use()
        other.trace.use()
        return VocabularyWord(list, list.session.values.track(ValueTrace(trace.declaredAt, describe)))
    }

    /** @throws KatachiKtFileContentLineException when this value belongs to a block other than [target]'s. */
    fun requireIn(target: DeclarationListBuilder, line: () -> String) {
        if (list === target) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line(), DeclarationSite.capture())
    }

    fun requireIn(target: DeclarationListBuilder, line: String): Unit = requireIn(target) { line }

    override fun toString(): String = "VocabularyWord(${trace.text()})"

    companion object {
        /** A new value written in [list]'s block, which reads as [describe] says. */
        fun of(list: DeclarationListBuilder, describe: () -> String): VocabularyWord =
            VocabularyWord(list, list.session.values.track(ValueTrace(DeclarationSite.capture(), describe)))
    }
}

/**
 * One value of the vocabulary -- a modifier, `annotated(...)`, a kind, `anythingElse`, or an `or`
 * of them -- and whether a line was written with it.
 *
 * A value on a line of its own is not a line: Kotlin would put `@Inject` on the declaration that
 * follows, but the block only sees the expression, so it can never apply. A value no line was
 * written with by the end of the run is therefore a mistake, found with
 * [me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem.DanglingModifiers]. What writes
 * with a value -- `allow(...)`, `or` -- calls [use].
 */
internal class ValueTrace(
    /** Where the value was written. */
    val declaredAt: DeclarationSite,
    /** The value as the line reads, such as `@Inject`, `private` or `(class or interface)`. */
    private val describe: () -> String,
) {
    @Volatile
    var used: Boolean = false
        private set

    fun use() {
        used = true
    }

    /** The value as the line reads. */
    fun text(): String = describe()

    override fun toString(): String = "ValueTrace(${text()}, used=$used)"
}

/** Every [ValueTrace] of one run, in the order the values were written. */
internal class ValueTraces {
    private val traces = mutableListOf<ValueTrace>()

    /** Starts keeping [trace] to be asked about when the run ends. */
    fun track(trace: ValueTrace): ValueTrace = trace.also { synchronized(traces) { traces += it } }

    /** The first value no line was written with, or `null` when each of them became part of a line. */
    fun firstDangling(): ValueTrace? = synchronized(traces) { traces.firstOrNull { !it.used } }

    override fun toString(): String = "ValueTraces(${traces.size})"
}
