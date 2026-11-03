package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.TemplateScopeImpl
import me.tbsten.katachi.internal.catching

// The block runs first and is judged afterwards so every missing or unreadable parameter is collected and reported
// at once.
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

// Never throws; an unreliable answer is flagged instead. Run values are bound because a parameter inside `if
// (withImpl)` exists only on that branch.
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

// isUnreliable: the replay may have taken a branch the real run won't, so judging `--arg` names by `declared`
// could blame the wrong thing.
internal class TemplateParameterNames(
    val declared: Set<String>,
    val origins: Map<String, TemplateParameterOrigin>,
    val isUnreliable: Boolean,
) {
    override fun toString(): String = "TemplateParameterNames($declared, unreliable=$isUnreliable)"
}

// `label` tells two different enums apart; `declaredWith` alone reads enumParameter() for every one of them.
internal class TemplateParameterOrigin(
    val declaredAt: DeclarationSite,
    val declaredWith: String,
    val label: String,
    val default: Any?,
)
