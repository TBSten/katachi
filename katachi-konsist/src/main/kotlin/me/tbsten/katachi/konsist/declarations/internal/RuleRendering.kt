package me.tbsten.katachi.konsist.declarations.internal

internal const val ANYTHING: String = "(anything)"

internal const val THE_FILES_DIRECTORY: String = "(the file's directory)"

internal fun renderRule(rule: DeclarationRule): String = renderDeclaration(
    shapes = rule.shapes,
    name = rule.name.written,
    prohibited = rule.prohibited,
    receiver = rule.receiver?.written,
    superclasses = rule.superclasses.map { it.written },
    propertyType = rule.propertyType?.written,
    parameters = rule.parameters?.map { it.written },
    returnType = rule.returnType?.written,
    except = rule.except.map { it.written },
)

// The `.optional()` modifiers are left out, as the declaration may do without them.
internal fun renderRuleAsDeclaration(rule: DeclarationRule): String = renderDeclaration(
    shapes = rule.shapes.map { it.copy(modifiers = it.modifiers.copy(optional = emptyList())) },
    name = rule.name.written,
    prohibited = rule.prohibited,
    receiver = rule.receiver?.written,
    superclasses = rule.superclasses.map { it.written },
    propertyType = rule.propertyType?.written,
    parameters = rule.parameters?.map { it.written },
    returnType = rule.returnType?.written,
    except = rule.except.map { it.written },
)

internal fun renderAnythingElse(rule: AnythingElseRule): String =
    listOf(renderModifierAlternatives(rule.alternatives), ANYTHING).filter { it.isNotEmpty() }.joinToString(" ")

internal fun renderAnythingElseLine(alternatives: List<ModifierSet>, prohibited: Boolean): String =
    notOf(prohibited) + listOf(renderModifierAlternatives(alternatives), ANYTHING).filter { it.isNotEmpty() }.joinToString(" ")

internal fun renderImport(rule: ImportRule): String =
    renderImport(rule.path.written, rule.prohibited, rule.except.map { it.written })

internal fun renderPackage(rule: PackageRule, directoryPackage: String? = null): String {
    val target = when (val target = rule.target) {
        is PackageTarget.Pattern -> target.rule.written
        PackageTarget.MatchingDirectory -> directoryPackage?.let { "$it  $THE_FILES_DIRECTORY" } ?: THE_FILES_DIRECTORY
    }
    return renderPackage(target, rule.prohibited, rule.except.map { it.written })
}

internal fun renderImport(path: String, prohibited: Boolean, except: List<String>): String =
    "${notOf(prohibited)}import $path${exceptOf(except)}"

internal fun renderPackage(target: String, prohibited: Boolean, except: List<String>): String =
    "${notOf(prohibited)}package $target${exceptOf(except)}"

internal fun renderDeclaration(
    shapes: List<Shape>,
    name: String,
    prohibited: Boolean,
    receiver: String? = null,
    superclasses: List<String> = emptyList(),
    propertyType: String? = null,
    parameters: List<String>? = null,
    returnType: String? = null,
    except: List<String> = emptyList(),
): String = buildString {
    append(notOf(prohibited))
    val prefix = renderShapes(shapes)
    if (prefix.isNotEmpty()) append(prefix).append(' ')
    receiver?.let { append(receiverText(it)).append('.') }
    append(name)
    if (shapes.isNotEmpty() && shapes.all { it.kind == DeclarationKindName.Function }) {
        append(parameters?.joinToString(", ", "(", ")") ?: "(...)")
    }
    if (superclasses.isNotEmpty()) append(" : ").append(superclasses.joinToString(", "))
    val aliases = shapes.isNotEmpty() && shapes.all { it.kind == DeclarationKindName.TypeAlias }
    propertyType?.let { append(if (aliases) " = " else ": ").append(it) }
    returnType?.let { append(": ").append(it) }
    append(exceptOf(except))
}

// Tokens every set starts or ends with are written once outside the `( ... or ... )` group, as they were written in front of `or`.
internal fun renderShapes(shapes: List<Shape>): String {
    val distinct = shapes.distinct()
    if (distinct.isEmpty()) return ""
    val sets = distinct.map { it.modifiers }.distinct()
    val kinds = distinct.map { it.kind }.distinct()
    if (distinct.size == sets.size * kinds.size) {
        val setsPart = renderAlternativesOrNull(sets)
        if (setsPart != null) {
            val kindsPart = if (kinds.size == 1) kinds.single().keyword else kinds.joinToString(" or ", "(", ")") { it.keyword }
            return listOf(setsPart, kindsPart).filter { it.isNotEmpty() }.joinToString(" ")
        }
    }
    return distinct.joinToString(" or ", "(", ")") { shape ->
        listOf(renderModifierSet(shape.modifiers), shape.kind.keyword).filter { it.isNotEmpty() }.joinToString(" ")
    }
}

internal fun renderKinds(kinds: List<DeclarationKindName>): String =
    kinds.singleOrNull()?.keyword ?: kinds.joinToString(" or ", "(", ")") { it.keyword }

internal fun renderModifierSet(set: ModifierSet): String = tokensOf(set).joinToString(" ")

internal fun renderModifierAlternatives(sets: List<ModifierSet>): String =
    renderAlternativesOrNull(sets.distinct())
        ?: sets.distinct().joinToString(" or ", "(", ")") { renderModifierSet(it).ifEmpty { NO_MODIFIER } }

private const val NO_MODIFIER: String = "no modifier"

private fun tokensOf(set: ModifierSet): List<String> {
    val required = set.modifiers.map { it.ordinal to it.keyword }
    val optional = set.optional.filter { it.isNotEmpty() }.map { group ->
        group.minOf { it.ordinal } to renderOptional(group)
    }
    return set.annotations.map { "@${it.written}" } + (required + optional).sortedBy { it.first }.map { it.second }
}

private fun renderOptional(group: List<ModifierName>): String =
    (group.singleOrNull()?.keyword ?: group.joinToString(" or ", "(", ")") { it.keyword }) + ".optional()"

// null when one of several sets is empty: `(public or )` cannot be written.
private fun renderAlternativesOrNull(sets: List<ModifierSet>): String? {
    if (sets.size == 1) return renderModifierSet(sets.single())
    val tokens = sets.map(::tokensOf)
    if (tokens.any { it.isEmpty() }) return null
    val prefix = commonPrefixOf(tokens)
    val suffix = commonPrefixOf(tokens.map { it.drop(prefix.size).asReversed() }).asReversed()
    val middles = tokens.map { it.subList(prefix.size, it.size - suffix.size) }
    if (middles.any { it.isEmpty() }) {
        return tokens.joinToString(" or ", "(", ")") { it.joinToString(" ") }
    }
    val group = middles.joinToString(" or ", "(", ")") { it.joinToString(" ") }
    return (prefix + group + suffix).joinToString(" ")
}

private fun commonPrefixOf(lists: List<List<String>>): List<String> {
    val shortest = lists.minOf { it.size }
    val length = (0 until shortest).firstOrNull { index -> lists.any { it[index] != lists[0][index] } } ?: shortest
    return lists[0].subList(0, length)
}

private fun notOf(prohibited: Boolean): String = if (prohibited) "not " else ""

private fun exceptOf(except: List<String>): String =
    if (except.isEmpty()) "" else except.joinToString(", ", prefix = " except ")
