package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.TemplateScopeImpl
import me.tbsten.katachi.internal.catching

/**
 * Replays [template] with [values] bound and produces the content of the one file it describes.
 *
 * The block's return value is that content, so nothing here decides *whether* to produce it the
 * way the old `file { }` calls did -- only whether the values needed to build it were all there.
 * A parameter with neither a value nor a default is collected so that the whole set can be
 * reported at once.
 *
 * The block runs first and is only then judged, so that every missing or unreadable value is
 * known before anything is said about any of them. A failure raised by the user's own code is
 * held back for the same reason: when values were missing or unreadable, that is almost always
 * why it failed, and the values are the more useful thing to say.
 *
 * A capture read with `captureValue(...)` that the run gave no value is handed to
 * [onMissingCaptures] once every parameter has a value, so that the caller reports it the same way
 * it reports a capture a file's place needs. Without one it is reported with the parameters.
 *
 * [isPreview] is what [me.tbsten.katachi.dsl.TemplateScope.isPreview] reads as for this replay --
 * `true` for `katachiTemplates` / `DescribeTemplates`, `false` for an actual run.
 */
internal fun evaluateTemplate(
    template: LayoutTemplate,
    roleName: String,
    values: Map<String, String>,
    captureNames: Set<String> = emptySet(),
    isPreview: Boolean = false,
    onMissingCaptures: ((names: List<String>, declaredAt: DeclarationSite, cause: Throwable?) -> Nothing)? = null,
): String {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = values,
        captureNames = captureNames,
        isPreview = isPreview,
    )
    var content: String? = null
    val failure = catching { content = template.block(scope) }.exceptionOrNull()
    scope.requireEveryParameterNamed(template.declaredAt)
    scope.requireEveryValueReadable(template.declaredAt, failure)
    scope.requireEveryValuePresent(template.declaredAt, failure, capturesApart = onMissingCaptures != null)
    val missingCaptures = scope.missingCaptureNames()
    if (onMissingCaptures != null && missingCaptures.isNotEmpty()) {
        onMissingCaptures(missingCaptures, template.declaredAt, failure)
    }
    failure?.let { throw it }
    val result = content.orEmpty()
    requireNoCaptureToken(result, where = "a template's returned content", declaredAt = template.declaredAt)
    return result
}

/**
 * The `--arg` names [template] declares for a run given [values], without rendering anything.
 *
 * Asked before any processor runs, to decide which arguments of that run are known. The run's
 * own [values] are bound, because a parameter declared inside `if (withImpl) { }` is declared
 * only on the runs that take that branch.
 *
 * It never throws: a template that cannot answer answers with what it managed to name, and says
 * the answer is not to be trusted. Refusing here would fail a run over a template nobody selected.
 *
 * [isPreview] is threaded to [me.tbsten.katachi.dsl.TemplateScope.isPreview] the same way
 * [evaluateTemplate]'s is.
 */
internal fun templateParameterNames(
    template: LayoutTemplate,
    roleName: String,
    values: Map<String, String>,
    captureNames: Set<String> = emptySet(),
    isPreview: Boolean = false,
): TemplateParameterNames {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = values,
        captureNames = captureNames,
        isPreview = isPreview,
    )
    val failed = catching { template.block(scope) }.isFailure
    return TemplateParameterNames(
        declared = scope.parameterNames(),
        origins = scope.parameterOrigins(),
        isUnreliable = failed || scope.branchedOnStandIn(),
    )
}

/**
 * What a names-only replay of a template found.
 *
 * [isUnreliable] is set when the replay may have taken a branch the real run will not: a value
 * that decides one could not be read, was missing, or the block failed. [declared] then belongs
 * to a different branch, and judging `--arg` names by it would report a parameter of the real
 * branch as unknown while hiding what is actually wrong. The render of the same values fails in
 * every such case, so the caller renders them to report that cause instead of judging names --
 * accepting every key would also accept a typo meant for another processor of the same run.
 */
internal class TemplateParameterNames(
    val declared: Set<String>,
    /** Where and how each of [declared] was declared. */
    val origins: Map<String, TemplateParameterOrigin>,
    val isUnreliable: Boolean,
) {
    override fun toString(): String = "TemplateParameterNames($declared, unreliable=$isUnreliable)"
}

/**
 * Where a template parameter was declared, and its full signature: which of `stringParameter()`
 * and its siblings ([declaredWith]), its type told apart even between two different enums
 * ([label], `TemplateParameterType.label` -- `declaredWith` alone reads `enumParameter()` for
 * every one of them), and its default ([default]). What
 * [me.tbsten.katachi.template.internal.requireNoConflicts] compares two declarations of the same
 * name by, since the design draft's "type or default differs" -- section 2, "複数指定のとき" --
 * means both, not [declaredWith] alone.
 */
internal class TemplateParameterOrigin(
    val declaredAt: DeclarationSite,
    val declaredWith: String,
    val label: String,
    val default: Any?,
)
