package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.TemplateParameter

internal class TemplateEvaluation(
    val content: String,
) {
    override fun toString(): String = "TemplateEvaluation(${content.length} chars)"
}

internal interface TemplateParameterBinder {
    // Reads the run's value once so an unreadable value is refused even if the parameter is never read.
    fun bind(parameter: TemplateParameter<*>)

    fun <T> valueOf(parameter: TemplateParameter<T>): T
}
