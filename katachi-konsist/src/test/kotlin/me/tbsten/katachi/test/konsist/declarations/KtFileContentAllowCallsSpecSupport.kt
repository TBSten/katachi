package me.tbsten.katachi.test.konsist.declarations

import me.tbsten.katachi.konsist.declarations.AnythingElse
import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationVocabulary
import me.tbsten.katachi.konsist.declarations.FunctionKind
import me.tbsten.katachi.konsist.declarations.FunctionLine
import me.tbsten.katachi.konsist.declarations.Modifiers
import me.tbsten.katachi.konsist.declarations.PropertyKind
import me.tbsten.katachi.konsist.declarations.PropertyLine
import me.tbsten.katachi.konsist.declarations.TypeAliasKind
import me.tbsten.katachi.konsist.declarations.TypeAliasLine
import me.tbsten.katachi.konsist.declarations.TypeKind
import me.tbsten.katachi.konsist.declarations.TypeLine

/*
 * `allow(...)` called with a list of modifiers, for the specs that make the modifiers at run time:
 * the generated cases of the properties, and the table of every count from none to ten. Each
 * count goes to its own overload, so every overload is called through here.
 */

/**
 * [alternatives] as the values `allow(...)` takes in front of the kind: the modifiers and
 * annotations every alternative asks for, one value each, and what tells the alternatives apart
 * -- one modifier or annotation each -- as one value joined with `or`. `fun` is left to the fun
 * interface overload.
 */
internal fun modifiersOf(vocabulary: DeclarationVocabulary, alternatives: List<GenModifiers>): List<Modifiers> {
    val tokens = alternatives.map { alternative ->
        alternative.annotations.map { "@$it" } + alternative.keywords.filter { it != "fun" }
    }
    val common = tokens.first().filter { token -> tokens.all { token in it } }
    val differing = tokens.map { it - common.toSet() }.distinct()
    val group = when {
        differing.all { it.isEmpty() } -> null
        differing.all { it.size == 1 } -> differing.map { vocabulary.token(it.single()) }.reduce(Modifiers::or)
        else -> error("$alternatives cannot be written as the values of allow(...)")
    }
    val written = common.map { vocabulary.token(it) }
    // A visibility comes first among the alternatives the generator makes; keep it in front.
    return listOfNotNull(group) + written
}

private fun DeclarationVocabulary.token(token: String): Modifiers =
    if (token.startsWith("@")) annotated(token.removePrefix("@")) else modifier(token)

/** The modifier [keyword] names. */
internal fun DeclarationVocabulary.modifier(keyword: String): Modifiers = when (keyword) {
    "public" -> public
    "internal" -> internal
    "protected" -> protected
    "private" -> private
    "data" -> data
    "sealed" -> sealed
    "enum" -> enum
    "value" -> value
    "annotation" -> annotation
    "abstract" -> abstract
    "open" -> open
    "inner" -> inner
    "companion" -> companion
    "override" -> override
    "operator" -> operator
    "infix" -> infix
    "suspend" -> suspend
    "inline" -> inline
    "tailrec" -> tailrec
    "external" -> external
    "const" -> const
    "lateinit" -> lateinit
    "expect" -> expect
    "actual" -> actual
    else -> error("no such modifier: $keyword")
}

internal fun allowType(on: DeclarationAllow, m: List<Modifiers>, kind: TypeKind, name: String, body: (ClassBodyScope.() -> Unit)? = null): TypeLine = when (m.size) {
    0 -> on.allow(kind, name, body)
    1 -> on.allow(m[0], kind, name, body)
    2 -> on.allow(m[0], m[1], kind, name, body)
    3 -> on.allow(m[0], m[1], m[2], kind, name, body)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, name, body)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], kind, name, body)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], kind, name, body)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name, body)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name, body)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name, body)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name, body)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}

internal fun allowFunInterface(
    on: DeclarationAllow,
    m: List<Modifiers>,
    fn: FunctionKind,
    kind: TypeKind,
    name: String,
    body: (ClassBodyScope.() -> Unit)? = null,
): TypeLine = when (m.size) {
    0 -> on.allow(fn, kind, name, body)
    1 -> on.allow(m[0], fn, kind, name, body)
    2 -> on.allow(m[0], m[1], fn, kind, name, body)
    3 -> on.allow(m[0], m[1], m[2], fn, kind, name, body)
    4 -> on.allow(m[0], m[1], m[2], m[3], fn, kind, name, body)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], fn, kind, name, body)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], fn, kind, name, body)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], fn, kind, name, body)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], fn, kind, name, body)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], fn, kind, name, body)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], fn, kind, name, body)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}

internal fun allowFunction(on: DeclarationAllow, m: List<Modifiers>, kind: FunctionKind, name: String): FunctionLine = when (m.size) {
    0 -> on.allow(kind, name)
    1 -> on.allow(m[0], kind, name)
    2 -> on.allow(m[0], m[1], kind, name)
    3 -> on.allow(m[0], m[1], m[2], kind, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, name)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], kind, name)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], kind, name)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}

internal fun allowTypeAlias(on: DeclarationAllow, m: List<Modifiers>, kind: TypeAliasKind, name: String): TypeAliasLine = when (m.size) {
    0 -> on.allow(kind, name)
    1 -> on.allow(m[0], kind, name)
    2 -> on.allow(m[0], m[1], kind, name)
    3 -> on.allow(m[0], m[1], m[2], kind, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, name)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], kind, name)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], kind, name)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}

internal fun allowProperty(on: DeclarationAllow, m: List<Modifiers>, kind: PropertyKind, name: String, type: String? = null): PropertyLine = when (m.size) {
    0 -> on.allow(kind, name)
    1 -> on.allow(m[0], kind, name)
    2 -> on.allow(m[0], m[1], kind, name)
    3 -> on.allow(m[0], m[1], m[2], kind, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, name)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], kind, name)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], kind, name)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}.withType(type)

internal fun allowAnythingElse(on: DeclarationAllow, m: List<Modifiers>, anything: AnythingElse) {
    when (m.size) {
        0 -> on.allow(anything)
        1 -> on.allow(m[0], anything)
        2 -> on.allow(m[0], m[1], anything)
        3 -> on.allow(m[0], m[1], m[2], anything)
        4 -> on.allow(m[0], m[1], m[2], m[3], anything)
        5 -> on.allow(m[0], m[1], m[2], m[3], m[4], anything)
        6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], anything)
        7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], anything)
        8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], anything)
        9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], anything)
        10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], anything)
        else -> error("allow(...) takes at most 10 modifiers: $m")
    }
}

internal fun allowExtensionFunction(on: DeclarationAllow, m: List<Modifiers>, kind: FunctionKind, receiver: String, name: String): FunctionLine = when (m.size) {
    0 -> on.allow(kind, receiver, name)
    1 -> on.allow(m[0], kind, receiver, name)
    2 -> on.allow(m[0], m[1], kind, receiver, name)
    3 -> on.allow(m[0], m[1], m[2], kind, receiver, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, receiver, name)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], kind, receiver, name)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], kind, receiver, name)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, receiver, name)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, receiver, name)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, receiver, name)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, receiver, name)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}

internal fun allowExtensionProperty(on: DeclarationAllow, m: List<Modifiers>, kind: PropertyKind, receiver: String, name: String, type: String): PropertyLine = when (m.size) {
    0 -> on.allow(kind, receiver, name)
    1 -> on.allow(m[0], kind, receiver, name)
    2 -> on.allow(m[0], m[1], kind, receiver, name)
    3 -> on.allow(m[0], m[1], m[2], kind, receiver, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, receiver, name)
    5 -> on.allow(m[0], m[1], m[2], m[3], m[4], kind, receiver, name)
    6 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], kind, receiver, name)
    7 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, receiver, name)
    8 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, receiver, name)
    9 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, receiver, name)
    10 -> on.allow(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, receiver, name)
    else -> error("allow(...) takes at most 10 modifiers: $m")
}.withType(type)

/** The line with `.type(...)` chained after it when [type] is given. */
internal fun PropertyLine.withType(type: String?): PropertyLine = if (type == null) this else type(type)
