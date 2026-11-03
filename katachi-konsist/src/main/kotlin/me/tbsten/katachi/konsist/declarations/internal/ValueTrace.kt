package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem

internal class VocabularyWord(val list: DeclarationListBuilder, val trace: ValueTrace) {
    fun or(other: VocabularyWord, line: String, describe: () -> String): VocabularyWord {
        other.requireIn(list, line)
        trace.use()
        other.trace.use()
        return VocabularyWord(list, list.session.values.track(ValueTrace(trace.declaredAt, describe)))
    }

    fun derive(text: String): VocabularyWord {
        trace.use()
        return VocabularyWord(list, list.session.values.track(ValueTrace(DeclarationSite.capture()) { text }))
    }

    fun requireIn(target: DeclarationListBuilder, line: () -> String) {
        if (list === target) return
        throw KatachiKtFileContentLineException(KtFileContentLineProblem.OutsideItsBlock, line(), DeclarationSite.capture())
    }

    fun requireIn(target: DeclarationListBuilder, line: String): Unit = requireIn(target) { line }

    override fun toString(): String = "VocabularyWord(${trace.text()})"

    companion object {
        fun of(list: DeclarationListBuilder, describe: () -> String): VocabularyWord =
            VocabularyWord(list, list.session.values.track(ValueTrace(DeclarationSite.capture(), describe)))
    }
}

// A value on a line of its own is not a line (Kotlin would put `@Inject` on the next declaration, but the block only sees the expression),
// so a value no line was written with by the end of the run is a mistake.
internal class ValueTrace(
    val declaredAt: DeclarationSite,
    private val describe: () -> String,
) {
    @Volatile
    var used: Boolean = false
        private set

    fun use() {
        used = true
    }

    fun text(): String = describe()

    override fun toString(): String = "ValueTrace(${text()}, used=$used)"
}

internal class ValueTraces {
    private val traces = mutableListOf<ValueTrace>()

    fun track(trace: ValueTrace): ValueTrace = trace.also { synchronized(traces) { traces += it } }

    fun firstDangling(): ValueTrace? = synchronized(traces) { traces.firstOrNull { !it.used } }

    override fun toString(): String = "ValueTraces(${traces.size})"
}
