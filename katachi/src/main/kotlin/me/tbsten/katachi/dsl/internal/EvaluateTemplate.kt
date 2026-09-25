package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.TemplateScopeImpl
import me.tbsten.katachi.internal.catching

/**
 * Replays [declaration] with [values] bound and produces its files.
 *
 * The block runs first and is only then judged, so that every missing value is known before
 * anything is said about any of them. A failure raised by the user's own code is held back for
 * the same reason: when values were missing, that is almost always why it failed, and the
 * missing names are the more useful thing to say.
 */
internal fun evaluateTemplate(
    declaration: TemplateDeclaration,
    roleName: String,
    values: Map<String, String>,
): TemplateEvaluation {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = values,
        mode = TemplateEvaluationMode.Render,
    )
    var failure = catching { declaration.block(scope) }.exceptionOrNull()
    if (failure == null) failure = catching { scope.render() }.exceptionOrNull()
    scope.requireEveryParameterNamed(declaration.declaredAt)
    scope.requireEveryValuePresent(declaration.declaredAt, failure)
    failure?.let { throw it }
    scope.requireAtLeastOneFile(declaration.declaredAt)
    return scope.evaluation()
}

/**
 * The `--arg` names [declaration] accepts, without rendering anything.
 *
 * Asked before any processor runs, to decide which arguments of that run are known. It never
 * throws: a template that cannot answer answers with what it managed to name, because refusing
 * here would fail a run over a template nobody selected.
 */
internal fun templateParameterNames(
    declaration: TemplateDeclaration,
    roleName: String,
): Set<String> {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = emptyMap(),
        mode = TemplateEvaluationMode.Names,
    )
    catching { declaration.block(scope) }
    return scope.parameterNames()
}
