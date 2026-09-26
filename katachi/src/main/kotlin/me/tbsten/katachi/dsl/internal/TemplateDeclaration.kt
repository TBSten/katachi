package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.TemplateParameter
import me.tbsten.katachi.dsl.TemplateScope

/**
 * A `template { }` as it was written: the block, and where it was written.
 *
 * Internal for the reason [FileConstraintDeclaration] is: a template says nothing until it has been
 * replayed with the values of one run. What a caller works with is [TemplateEvaluation].
 */
internal class TemplateDeclaration(
    val declaredAt: DeclarationSite,
    val block: TemplateScope.() -> Unit,
) {
    override fun toString(): String = "TemplateDeclaration($declaredAt)"
}

/** What one replay of a template produced: the files it wrote, already rendered. */
internal class TemplateEvaluation(
    val files: List<RenderedTemplateFile>,
) {
    override fun toString(): String = "TemplateEvaluation(${files.size} files)"
}

/**
 * One file a template produced.
 *
 * [fileName] is a plain file name with its extension and no separator in it — the directory is
 * the next stage's job, and comes from the role's `layout { }`.
 */
internal class RenderedTemplateFile(
    val fileName: String,
    val content: String,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "RenderedTemplateFile($fileName)"
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
