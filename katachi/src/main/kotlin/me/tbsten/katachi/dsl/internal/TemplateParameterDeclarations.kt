package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.TemplateScopeImpl
import me.tbsten.katachi.internal.catching

internal class DeclaredTemplateParameter(
    val name: String,
    val type: TemplateParameterType<*>,
    val default: Any?,
) {
    override fun toString(): String = "DeclaredTemplateParameter($name: ${type.label})"
}

// Never throws: a block failing part way answers with the parameters declared before it failed.
internal fun declaredTemplateParameters(
    template: LayoutTemplate,
    roleName: String,
    values: Map<String, String>,
    captureNames: Set<String> = emptySet(),
    isPreview: Boolean = false,
): List<DeclaredTemplateParameter> {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = values,
        captureNames = captureNames,
        isPreview = isPreview,
    )
    catching { template.block(scope) }
    return scope.parameters().mapNotNull { parameter ->
        val name = parameter.name ?: return@mapNotNull null
        DeclaredTemplateParameter(name = name, type = parameter.type, default = parameter.default)
    }
}
