package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.TemplateParameter

/**
 * What one replay of a [LayoutTemplate] produced: the block's return value, the finished
 * content of the one file it describes.
 */
internal class TemplateEvaluation(
    val content: String,
) {
    override fun toString(): String = "TemplateEvaluation(${content.length} chars)"
}

/** What a [TemplateParameter] talks back to: the scope that is collecting this replay. */
internal interface TemplateParameterBinder {
    /**
     * Takes the name the parameter has just been given, and reads the run's value for it once so
     * that a value it cannot read is refused even when nothing ever reads the parameter.
     */
    fun bind(parameter: TemplateParameter<*>)

    /** The value bound for this run, the declared default, or a stand-in noted as missing. */
    fun <T> valueOf(parameter: TemplateParameter<T>): T
}
