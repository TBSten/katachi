package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.flatCase
import me.tbsten.katachi.dsl.kebabCase
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.screamingSnakeCase
import me.tbsten.katachi.dsl.snakeCase

/**
 * The conversion a derived capture applies to its plain capture's value: `capture("x").pascalCase`
 * is a token carrying [PascalCase], and the check compares the level it sits on with
 * `pascalCase` of what `x` read elsewhere on the same path.
 *
 * One entry per String extension of `Naming.kt`, and nothing else: every entry reads the same
 * `nameWords`, so the check and template generation convert a value the same way. [spelling] is
 * what the extension is called, which is how the token, a report, `katachiTemplates` and the IDE
 * plugin's JSON all name it.
 */
internal enum class CaptureTransform(val spelling: String, val apply: (String) -> String) {
    CamelCase("camelCase", { it.camelCase }),
    PascalCase("pascalCase", { it.pascalCase }),
    KebabCase("kebabCase", { it.kebabCase }),
    SnakeCase("snakeCase", { it.snakeCase }),
    ScreamingSnakeCase("screamingSnakeCase", { it.screamingSnakeCase }),
    FlatCase("flatCase", { it.flatCase }),
    ;

    companion object {
        /** The entry [spelling] names, or `null` for a word no extension is called. */
        fun fromSpelling(spelling: String): CaptureTransform? = entries.firstOrNull { it.spelling == spelling }
    }
}
