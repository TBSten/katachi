package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.InternalKatachiApi

/**
 * Whether `fileConstraint` refuses [name]: it is blank, or holds a line break.
 *
 * A constraint name is printed on one line of a report block, so a backend that wraps `fileConstraint`
 * asks this to refuse the same names before it does any work of its own.
 *
 * ## Example 1: refuse a name before evaluating a block
 * ```kt
 * if (!isRefusedFileConstraintName(name)) constraint.validateAtDeclaration()
 * ```
 *
 * @param name the name as written.
 * @return `true` when `fileConstraint` throws [me.tbsten.katachi.dsl.KatachiFileConstraintNameException] for it.
 */
@InternalKatachiApi
public fun isRefusedFileConstraintName(name: String): Boolean =
    name.isBlank() || name.any { it == '\n' || it == '\r' }
