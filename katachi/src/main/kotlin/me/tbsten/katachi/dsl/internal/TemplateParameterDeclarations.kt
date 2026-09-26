package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.TemplateScopeImpl
import me.tbsten.katachi.internal.catching

/**
 * One parameter a template declared, as a describer reads it: its name, what it reads its `--arg`
 * as, and its default.
 *
 * [default] is the typed value (`true`, `20`, an enum entry), or `null` when the parameter has
 * none and so has to be given.
 */
internal class DeclaredTemplateParameter(
    val name: String,
    val type: TemplateParameterType<*>,
    val default: Any?,
) {
    override fun toString(): String = "DeclaredTemplateParameter($name: ${type.label})"
}

/**
 * The parameters [declaration] declares for a run given [values], in declaration order.
 *
 * The same names-only replay as [templateParameterNames] -- no `file { }` body runs -- with each
 * parameter's type and default kept. Like it, this never throws: a block that fails part way
 * answers with the parameters it declared before failing.
 */
internal fun declaredTemplateParameters(
    declaration: TemplateDeclaration,
    roleName: String,
    values: Map<String, String>,
): List<DeclaredTemplateParameter> {
    val scope = TemplateScopeImpl(roleName = roleName, values = values)
    catching { declaration.block(scope) }
    return scope.parameters().mapNotNull { parameter ->
        val name = parameter.name ?: return@mapNotNull null
        DeclaredTemplateParameter(name = name, type = parameter.type, default = parameter.default)
    }
}
