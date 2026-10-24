package me.tbsten.katachi.konsist.declarations.internal

/*
 * What a file holds, written back the way it is written in the file, for a report:
 * `class GetUserUseCaseImpl : GetUserUseCase`, `var isLoading`, `private const val TAG`,
 * `fun onRefresh(): Unit`. Only what is written is shown: no implicit `public`, no inferred type.
 */

/** The modifiers that say what kind of class or object a container is, as `renderContainer` writes them. */
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

/** A name Kotlin can read without backticks. */
private val IDENTIFIER: Regex = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

/** `<@A @B> <modifiers> <kind> <receiver.><name><details>`, as written in the file. */
internal fun renderFact(fact: DeclarationFact): String = buildString {
    val prefix = fact.annotations.map { "@${it.simpleName}" } +
        fact.modifiers.sortedBy { it.ordinal }.map { it.keyword } +
        fact.kind.keyword
    append(prefix.joinToString(" ")).append(' ')
    fact.receiver?.let { append(receiverText(it)).append('.') }
    append(quotedName(fact.name))
    fact.parameters?.let { append(renderParameters(it)) }
    if (fact.supertypes.isNotEmpty()) append(" : ").append(fact.supertypes.joinToString(", ") { it.written })
    fact.propertyType?.let { append(": ").append(it.written) }
    if (fact.returnTypeWritten) fact.returnType?.let { append(": ").append(it.written) }
}

/**
 * A class, an interface or an object as the place its members are in: its kind, with what makes
 * it a kind of its own (`enum`, `data`, `companion` and the like), and its name --
 * `class HomeViewModel`, `enum class Mode`, `companion object Companion`.
 */
internal fun renderContainer(fact: DeclarationFact): String {
    val modifiers = fact.modifiers.filter { it in CONTAINER_MODIFIERS }.sortedBy { it.ordinal }.map { it.keyword }
    return (modifiers + fact.kind.keyword + quotedName(fact.name)).joinToString(" ")
}

/** [receiver] as it is written before `.`: a function type in parentheses, as Kotlin needs it. */
private fun receiverText(receiver: String): String =
    if ("->" in receiver && !isParenthesised(receiver)) "($receiver)" else receiver

/** Whether [text] is one `(...)` as a whole, its first `(` closed by its last `)`. */
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

/** The parameters of a function as Kotlin writes them: `(id: String, count: Int)`. */
internal fun renderParameters(parameters: List<ParameterFact>): String =
    parameters.joinToString(", ", "(", ")") { "${quotedName(it.name)}: ${it.type.written}" }

// TODO: a keyword such as `in` is printed without the backticks Kotlin needs around it.
/** [name], in backticks when Kotlin could not read it without them. */
private fun quotedName(name: String): String = if (IDENTIFIER.matches(name)) name else "`$name`"
