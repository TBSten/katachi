package me.tbsten.katachi.test.konsist.declarations

import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationRequired
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
 * `required(...)` called with a list of modifiers, as `KtFileContentAllowCallsSpecSupport.kt` calls
 * `allow(...)`: every count from none to ten goes to its own overload.
 */

internal fun requiredType(on: DeclarationRequired, m: List<Modifiers>, kind: TypeKind, name: String, body: (ClassBodyScope.() -> Unit)? = null): TypeLine = when (m.size) {
    0 -> on.required(kind, name, body)
    1 -> on.required(m[0], kind, name, body)
    2 -> on.required(m[0], m[1], kind, name, body)
    3 -> on.required(m[0], m[1], m[2], kind, name, body)
    4 -> on.required(m[0], m[1], m[2], m[3], kind, name, body)
    5 -> on.required(m[0], m[1], m[2], m[3], m[4], kind, name, body)
    6 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], kind, name, body)
    7 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name, body)
    8 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name, body)
    9 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name, body)
    10 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name, body)
    else -> error("required(...) takes at most 10 modifiers: $m")
}

internal fun requiredFunInterface(
    on: DeclarationRequired,
    m: List<Modifiers>,
    fn: FunctionKind,
    kind: TypeKind,
    name: String,
    body: (ClassBodyScope.() -> Unit)? = null,
): TypeLine = when (m.size) {
    0 -> on.required(fn, kind, name, body)
    1 -> on.required(m[0], fn, kind, name, body)
    2 -> on.required(m[0], m[1], fn, kind, name, body)
    3 -> on.required(m[0], m[1], m[2], fn, kind, name, body)
    4 -> on.required(m[0], m[1], m[2], m[3], fn, kind, name, body)
    5 -> on.required(m[0], m[1], m[2], m[3], m[4], fn, kind, name, body)
    6 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], fn, kind, name, body)
    7 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], fn, kind, name, body)
    8 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], fn, kind, name, body)
    9 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], fn, kind, name, body)
    10 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], fn, kind, name, body)
    else -> error("required(...) takes at most 10 modifiers: $m")
}

internal fun requiredFunction(on: DeclarationRequired, m: List<Modifiers>, kind: FunctionKind, name: String): FunctionLine = when (m.size) {
    0 -> on.required(kind, name)
    1 -> on.required(m[0], kind, name)
    2 -> on.required(m[0], m[1], kind, name)
    3 -> on.required(m[0], m[1], m[2], kind, name)
    4 -> on.required(m[0], m[1], m[2], m[3], kind, name)
    5 -> on.required(m[0], m[1], m[2], m[3], m[4], kind, name)
    6 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], kind, name)
    7 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name)
    8 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name)
    9 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name)
    10 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name)
    else -> error("required(...) takes at most 10 modifiers: $m")
}

internal fun requiredTypeAlias(on: DeclarationRequired, m: List<Modifiers>, kind: TypeAliasKind, name: String): TypeAliasLine = when (m.size) {
    0 -> on.required(kind, name)
    1 -> on.required(m[0], kind, name)
    2 -> on.required(m[0], m[1], kind, name)
    3 -> on.required(m[0], m[1], m[2], kind, name)
    4 -> on.required(m[0], m[1], m[2], m[3], kind, name)
    5 -> on.required(m[0], m[1], m[2], m[3], m[4], kind, name)
    6 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], kind, name)
    7 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name)
    8 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name)
    9 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name)
    10 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name)
    else -> error("required(...) takes at most 10 modifiers: $m")
}

internal fun requiredProperty(on: DeclarationRequired, m: List<Modifiers>, kind: PropertyKind, name: String, type: String? = null): PropertyLine = when (m.size) {
    0 -> on.required(kind, name, type)
    1 -> on.required(m[0], kind, name, type)
    2 -> on.required(m[0], m[1], kind, name, type)
    3 -> on.required(m[0], m[1], m[2], kind, name, type)
    4 -> on.required(m[0], m[1], m[2], m[3], kind, name, type)
    5 -> on.required(m[0], m[1], m[2], m[3], m[4], kind, name, type)
    6 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], kind, name, type)
    7 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], kind, name, type)
    8 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], kind, name, type)
    9 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], kind, name, type)
    10 -> on.required(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], kind, name, type)
    else -> error("required(...) takes at most 10 modifiers: $m")
}
