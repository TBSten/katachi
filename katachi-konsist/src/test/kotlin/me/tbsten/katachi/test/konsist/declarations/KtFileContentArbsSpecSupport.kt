package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/*
 * Random allow lists and the sources that follow them, for KtFileContentPropertySpec: a handful
 * of declarations, each written once as a line of `ktFileContent { }` and once as Kotlin.
 *
 * A seeded `kotlin.random.Random` rather than `io.kotest.property`, which is not a dependency here
 * (the same choice as `:katachi`'s RandomLayoutSpecSupport).
 */

/** The kinds a generated declaration can be. */
internal enum class GeneratedKind { Class, Interface, Object, Function, Property }

/** The visibility a generated declaration writes, and its line asks for. */
internal enum class GeneratedVisibility(val keyword: String?) { None(null), Private("private"), Internal("internal") }

/** One declaration, written the same way in the rules and in the source. */
internal data class GeneratedDeclaration(val kind: GeneratedKind, val name: String, val visibility: GeneratedVisibility) {
    /** The declaration as Kotlin, on one line. */
    val source: String
        get() {
            val body = when (kind) {
                GeneratedKind.Class -> "class $name"
                GeneratedKind.Interface -> "interface $name"
                GeneratedKind.Object -> "object $name"
                GeneratedKind.Function -> "fun $name() {}"
                GeneratedKind.Property -> "val $name = 1"
            }
            return listOfNotNull(visibility.keyword, body).joinToString(" ")
        }
}

/** One to five declarations of distinct names, none named `Extra`. */
internal fun Random.declarations(): List<GeneratedDeclaration> = List(nextInt(1, 6)) { index ->
    val kind = GeneratedKind.entries.random(this)
    val name = when (kind) {
        GeneratedKind.Function, GeneratedKind.Property -> "member$index"
        else -> "Type$index"
    }
    GeneratedDeclaration(kind, name, GeneratedVisibility.entries.random(this))
}

/** Writes [declaration] as the line that allows exactly it (its visibility, kind and name). */
internal fun KtFileContentScope.line(declaration: GeneratedDeclaration) {
    val modifiers = when (declaration.visibility) {
        GeneratedVisibility.None -> null
        GeneratedVisibility.Private -> private
        GeneratedVisibility.Internal -> internal
    }
    val name = declaration.name
    when (declaration.kind) {
        GeneratedKind.Class -> (modifiers?.`class` ?: `class`)(name)
        GeneratedKind.Interface -> (modifiers?.`interface` ?: `interface`)(name)
        GeneratedKind.Object -> (modifiers?.`object` ?: `object`)(name)
        GeneratedKind.Function -> (modifiers?.`fun` ?: `fun`)(name)
        GeneratedKind.Property -> (modifiers?.`val` ?: `val`)(name)
    }
}

/**
 * A file of package `com.example` holding [declarations] in order -- with, when [noise] is given,
 * blank lines and comments between them.
 */
internal fun sourceOf(declarations: List<GeneratedDeclaration>, noise: Random? = null): String = buildString {
    append("package com.example\n\n")
    for (declaration in declarations) {
        if (noise != null) {
            repeat(noise.nextInt(0, 3)) { append(listOf("", "// a comment", "/* a block comment */").random(noise)).append('\n') }
        }
        append(declaration.source).append('\n')
    }
}
