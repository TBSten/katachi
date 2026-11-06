package me.tbsten.katachi.konsist.declarations.internal

private val CONTAINER_MODIFIERS: Set<ModifierName> = setOf(
    ModifierName.Enum,
    ModifierName.Annotation,
    ModifierName.Data,
    ModifierName.Sealed,
    ModifierName.Companion,
    ModifierName.Fun,
    ModifierName.Inner,
    ModifierName.Value,
)

private val IDENTIFIER: Regex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

internal fun renderFact(fact: DeclarationFact): String = buildString {
    val prefix = fact.annotations.map { "@${it.simpleName}" } +
        fact.modifiers.sortedBy { it.ordinal }.map { it.keyword } +
        fact.kind.keyword
    append(prefix.joinToString(" "))
    if (!fact.kind.isUnnamed) append(' ')
    fact.receiver?.let { append(receiverText(it.written)).append('.') }
    if (!fact.kind.isUnnamed) append(quotedName(fact.name))
    fact.parameters?.let { append(renderParameters(it)) }
    if (fact.supertypes.isNotEmpty()) append(" : ").append(fact.supertypes.joinToString(", ") { it.written })
    fact.propertyType?.let { append(if (fact.kind == DeclarationKindName.TypeAlias) " = " else ": ").append(it.written) }
    if (fact.returnTypeWritten) fact.returnType?.let { append(": ").append(it.written) }
}

internal fun renderContainer(fact: DeclarationFact): String {
    val modifiers = fact.modifiers.filter { it in CONTAINER_MODIFIERS }.sortedBy { it.ordinal }.map { it.keyword }
    return (modifiers + fact.kind.keyword + quotedName(fact.name)).joinToString(" ")
}

// A function type is put back in the parentheses Kotlin needs around a receiver.
internal fun receiverText(receiver: String): String =
    if ("->" in receiver && !isParenthesised(receiver)) "($receiver)" else receiver

internal fun withoutOuterParentheses(text: String): String =
    if (isParenthesised(text)) withoutOuterParentheses(text.substring(1, text.lastIndex).trim()) else text

private fun isParenthesised(text: String): Boolean {
    if (!text.startsWith("(")) return false
    var depth = 0
    for ((index, char) in text.withIndex()) {
        when (char) {
            '(' -> depth++
            ')' -> if (--depth == 0) return index == text.lastIndex
        }
    }
    return false
}

internal fun renderParameters(parameters: List<ParameterFact>): String =
    parameters.joinToString(", ", "(", ")", transform = ::renderParameter)

internal fun renderParameter(parameter: ParameterFact): String = buildString {
    parameter.modifiers.forEach { append(it).append(' ') }
    append(quotedName(parameter.name)).append(": ").append(parameter.type.written)
    parameter.defaultValue?.let { append(" = ").append(it) }
}

// TODO: a keyword such as `in` is printed without the backticks Kotlin needs around it.
private fun quotedName(name: String): String = if (IDENTIFIER.matches(name)) name else "`$name`"
