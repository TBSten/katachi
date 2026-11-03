package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.flatCase
import me.tbsten.katachi.dsl.kebabCase
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.screamingSnakeCase
import me.tbsten.katachi.dsl.snakeCase

// One entry per String extension of Naming.kt; all read the same nameWords so the check and template generation
// convert alike.
internal enum class CaptureTransform(val spelling: String, val apply: (String) -> String) {
    CamelCase("camelCase", { it.camelCase }),
    PascalCase("pascalCase", { it.pascalCase }),
    KebabCase("kebabCase", { it.kebabCase }),
    SnakeCase("snakeCase", { it.snakeCase }),
    ScreamingSnakeCase("screamingSnakeCase", { it.screamingSnakeCase }),
    FlatCase("flatCase", { it.flatCase }),
    ;

    companion object {
        fun fromSpelling(spelling: String): CaptureTransform? = entries.firstOrNull { it.spelling == spelling }
    }
}
