package me.tbsten.katachi.dsl

/**
 * A `template { }` as it was written: the block, and where it was written.
 *
 * Internal for the reason [ConstraintDeclaration] is: a template says nothing until it has been
 * replayed with the values of one run. What a caller works with is [TemplateEvaluation].
 */
internal class TemplateDeclaration(
    val declaredAt: DeclarationSite,
    val block: TemplateScope.() -> Unit,
) {
    override fun toString(): String = "TemplateDeclaration($declaredAt)"
}

/**
 * What one replay of a template produced.
 *
 * [parameters] is the whole vocabulary the template declared, in declaration order, whether or
 * not anything read it. [files] is what it wrote, already rendered.
 */
internal class TemplateEvaluation(
    val parameters: List<TemplateParameterInfo>,
    val files: List<RenderedTemplateFile>,
) {
    override fun toString(): String =
        "TemplateEvaluation(${parameters.size} parameters, ${files.size} files)"
}

/** One parameter a template declared, as it was declared. */
internal class TemplateParameterInfo(
    val name: String,
    val default: String?,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "TemplateParameterInfo($name)"
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

/**
 * Why a template is being replayed.
 *
 * The block runs the same way either way; what differs is how far the replay goes and what an
 * unbound parameter costs.
 */
internal enum class TemplateEvaluationMode {
    /**
     * To learn which `--arg` names this template accepts.
     *
     * Runs before any processor does, so it renders nothing: every parameter is named by its
     * `val`, which the block body reaches on its own. Reading one that has no value yields a
     * stand-in rather than a complaint, because there are no values yet to be missing.
     */
    Names,

    /**
     * To produce the files.
     *
     * Every `file { }` body is invoked, and a parameter with neither a value nor a default is
     * collected so that the whole set can be reported at once.
     */
    Render,
}
