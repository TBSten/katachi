package me.tbsten.katachi.konsist.declarations.internal

/*
 * Rules written back the way Kotlin would write the declaration they describe, for a report, an
 * exception and the role page alike: `@HiltViewModel public class HomeViewModel : ViewModel`,
 * `public fun on*(): Unit`, `not class *Impl except DefaultImpl`, `private (anything)`.
 */

/** What an `anythingElse` line prints after its modifiers. */
internal const val ANYTHING: String = "(anything)"

/** What a `package`(matchingDirectory) line prints in place of a package. */
internal const val THE_FILES_DIRECTORY: String = "(the file's directory)"

/** One declaration line: `<prefix> <name><details>`, with `not ` and ` except ...` when prohibited. */
internal fun renderRule(rule: DeclarationRule): String = renderDeclaration(
    shapes = rule.shapes,
    name = rule.name.written,
    prohibited = rule.prohibited,
    superclasses = rule.superclasses.map { it.written },
    propertyType = rule.propertyType?.written,
    parameters = rule.parameters?.map { it.written },
    returnType = rule.returnType?.written,
    except = rule.except.map { it.written },
)

/** `(anything)`, `private (anything)`, `(internal or private) (anything)`. */
internal fun renderAnythingElse(rule: AnythingElseRule): String =
    listOf(renderModifierAlternatives(rule.alternatives), ANYTHING).filter { it.isNotEmpty() }.joinToString(" ")

/** An `anythingElse` line still being written, with `not ` in front when [prohibited]: `not private (anything)`. */
internal fun renderAnythingElseLine(alternatives: List<ModifierSet>, prohibited: Boolean): String =
    notOf(prohibited) + listOf(renderModifierAlternatives(alternatives), ANYTHING).filter { it.isNotEmpty() }.joinToString(" ")

/** `import kotlin.**`, `not import android.** except android.os.**`. */
internal fun renderImport(rule: ImportRule): String =
    renderImport(rule.path.written, rule.prohibited, rule.except.map { it.written })

/**
 * `package com.example.**`, or for `matchingDirectory` `package (the file's directory)` -- or,
 * when [directoryPackage] says what the file's directory reads as,
 * `package com.example.home  (the file's directory)`.
 */
internal fun renderPackage(rule: PackageRule, directoryPackage: String? = null): String {
    val target = when (val target = rule.target) {
        is PackageTarget.Pattern -> target.rule.written
        PackageTarget.MatchingDirectory -> directoryPackage?.let { "$it  $THE_FILES_DIRECTORY" } ?: THE_FILES_DIRECTORY
    }
    return renderPackage(target, rule.prohibited, rule.except.map { it.written })
}

/** The import line as written, also for one still being built. */
internal fun renderImport(path: String, prohibited: Boolean, except: List<String>): String =
    "${notOf(prohibited)}import $path${exceptOf(except)}"

/** The package line as written, also for one still being built. */
internal fun renderPackage(target: String, prohibited: Boolean, except: List<String>): String =
    "${notOf(prohibited)}package $target${exceptOf(except)}"

/**
 * One declaration line from its parts, also for one still being built (an exception names the line
 * it was raised for before the line is complete).
 */
internal fun renderDeclaration(
    shapes: List<Shape>,
    name: String,
    prohibited: Boolean,
    superclasses: List<String> = emptyList(),
    propertyType: String? = null,
    parameters: List<String>? = null,
    returnType: String? = null,
    except: List<String> = emptyList(),
): String = buildString {
    append(notOf(prohibited))
    val prefix = renderShapes(shapes)
    if (prefix.isNotEmpty()) append(prefix).append(' ')
    append(name)
    if (shapes.isNotEmpty() && shapes.all { it.kind == DeclarationKindName.Function }) {
        append(parameters?.joinToString(", ", "(", ")") ?: "(...)")
    }
    if (superclasses.isNotEmpty()) append(" : ").append(superclasses.joinToString(", "))
    propertyType?.let { append(": ").append(it) }
    returnType?.let { append(": ").append(it) }
    append(exceptOf(except))
}

/**
 * What comes before the name: the annotations, the modifiers and the kind of every candidate.
 *
 * - one candidate: `@A public data class`
 * - every combination of some sets and some kinds: the sets, then the kinds, each group in
 *   `( ... or ... )` when there is more than one -- `(class or interface)`,
 *   `sealed (class or interface)`, `(internal or private) data object`. What every set shares at
 *   its start or its end is written once outside the group, as it was written in front of `or`.
 * - anything else: every candidate in full, `(class or public interface)`.
 */
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

/** `class`, `(class or interface)`: the kinds of a kind value, as `toString` and an exception print it. */
internal fun renderKinds(kinds: List<DeclarationKindName>): String =
    kinds.singleOrNull()?.keyword ?: kinds.joinToString(" or ", "(", ")") { it.keyword }

/** `@A @B public data`: the annotations as written, then the modifiers in Kotlin's order. Empty for none. */
internal fun renderModifierSet(set: ModifierSet): String = tokensOf(set).joinToString(" ")

/** The alternatives of an `anythingElse` line or of a `Modifiers`: `private`, `(internal or private)`, or nothing. */
internal fun renderModifierAlternatives(sets: List<ModifierSet>): String =
    renderAlternativesOrNull(sets.distinct())
        ?: sets.distinct().joinToString(" or ", "(", ")") { renderModifierSet(it).ifEmpty { NO_MODIFIER } }

/** How a set with nothing in it reads when it has to be printed among others. */
private const val NO_MODIFIER: String = "no modifier"

private fun tokensOf(set: ModifierSet): List<String> =
    set.annotations.map { "@${it.written}" } + set.modifiers.sortedBy { it.ordinal }.map { it.keyword }

/**
 * [sets] as one group, or `null` when one of several is empty (`(public or )` cannot be written).
 * Tokens every set starts or ends with are taken out of the group.
 */
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
