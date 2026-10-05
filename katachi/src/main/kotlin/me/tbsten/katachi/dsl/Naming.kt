package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.CaptureTransform
import me.tbsten.katachi.dsl.internal.asTransformedCaptureToken

/**
 * The words [this] name is made of, lower case and in order.
 *
 * One rule, used by every conversion below and by the `modulePackage` strategies, so that a
 * module name folds the same way wherever katachi folds it:
 *
 * | in the name | read as |
 * |---|---|
 * | `-`, `_` or a space | the end of a word, and nothing else |
 * | a capital after a lower case letter or a digit | the start of a word (`debugMenu`, `v2Api`) |
 * | a capital before a lower case letter, after other capitals | the start of a word (`APIClient`) |
 * | a digit | part of the word it sits in, never a boundary (`2fa`, `v2`) |
 *
 * A digit is not a boundary because a module called `2fa` is one word, not a number followed
 * by a name, and splitting it would turn `2fa` into `2Fa`.
 *
 * Applied to a `capture("x")` of a `layout { }` -- `"${capture("x").pascalCase}Screen".ktFile()` --
 * each conversion below makes a *derived* capture instead of converting characters: the check
 * then holds that level to the conversion of what `x` reads elsewhere on the same path, and a
 * template fills it from the one `--arg x=` value. Only these six do that; a `String` function
 * from the standard library such as `lowercase()` rewrites the token itself and names nothing.
 *
 * ## Example 1: split a name into words
 * ```kt
 * "debug-menu".nameWords    // [debug, menu]
 * "remoteAPI".nameWords     // [remote, api]
 * "APIClient".nameWords     // [api, client]
 * ```
 */
public val String.nameWords: List<String>
    get() {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        fun endWord() {
            if (word.isNotEmpty()) {
                words += word.toString().lowercase()
                word.clear()
            }
        }
        forEachIndexed { index, character ->
            when {
                character == '-' || character == '_' || character == ' ' -> endWord()

                character.isUpperCase() -> {
                    val previous = getOrNull(index - 1)
                    val next = getOrNull(index + 1)
                    val afterLowerOrDigit = previous != null && (previous.isLowerCase() || previous.isDigit())
                    val lastOfARunOfCapitals =
                        previous != null && previous.isUpperCase() && next != null && next.isLowerCase()
                    if (afterLowerOrDigit || lastOfARunOfCapitals) endWord()
                    word.append(character)
                }

                else -> word.append(character)
            }
        }
        endWord()
        return words
    }

/**
 * `debug-menu` as `DebugMenu`.
 *
 * Written on a captured wildcard, this is what ties a file name to the module it sits in.
 * A name that holds no word at all (`""`, `"--"`) converts to the empty string, and a name
 * starting with a digit keeps it (`2fa` stays `2fa`), because there is no capital form of a
 * digit to move to.
 *
 * ## Example 1: convert a name to PascalCase
 * ```kt
 * "debug-menu".pascalCase shouldBe "DebugMenu"
 * "remoteAPI".pascalCase shouldBe "RemoteApi"
 * ```
 *
 * ## Example 2: build a file name from a name
 * ```kt
 * val feature = "debug-menu"
 * mainSourceSet / kotlin / "com/example/debug" / "${feature.pascalCase}Screen".ktFile()
 * ```
 */
public val String.pascalCase: String
    get() = asTransformedCaptureToken(CaptureTransform.PascalCase) ?: nameWords.joinToString("") { it.capitalized() }

/**
 * `debug-menu` as `debugMenu`. The same words as [pascalCase], with the first one left alone.
 *
 * ## Example 1: convert a name to camelCase
 * ```kt
 * "debug-menu".camelCase shouldBe "debugMenu"
 * "DebugMenu".camelCase shouldBe "debugMenu"
 * ```
 */
public val String.camelCase: String
    get() = asTransformedCaptureToken(CaptureTransform.CamelCase)
        ?: nameWords.mapIndexed { index, word -> if (index == 0) word else word.capitalized() }.joinToString("")

/**
 * `debugMenu` as `debug-menu`.
 *
 * ## Example 1: convert a name to kebab-case
 * ```kt
 * "debugMenu".kebabCase shouldBe "debug-menu"
 * ```
 */
public val String.kebabCase: String
    get() = asTransformedCaptureToken(CaptureTransform.KebabCase) ?: nameWords.joinToString("-")

/**
 * `debugMenu` as `debug_menu`.
 *
 * ## Example 1: convert a name to snake_case
 * ```kt
 * "debugMenu".snakeCase shouldBe "debug_menu"
 * ```
 */
public val String.snakeCase: String
    get() = asTransformedCaptureToken(CaptureTransform.SnakeCase) ?: nameWords.joinToString("_")

/**
 * `debugMenu` as `DEBUG_MENU`.
 *
 * ## Example 1: convert a name to SCREAMING_SNAKE_CASE
 * ```kt
 * "debugMenu".screamingSnakeCase shouldBe "DEBUG_MENU"
 * ```
 */
public val String.screamingSnakeCase: String
    get() = asTransformedCaptureToken(CaptureTransform.ScreamingSnakeCase) ?: nameWords.joinToString("_") { it.uppercase() }

/**
 * `debug-menu` as `debugmenu` — the words run together with nothing between them.
 *
 * This is the spelling for a place that takes neither a separator nor a capital, such as a
 * package segment in a project that writes them all in lower case.
 *
 * ## Example 1: convert a name to flatcase
 * ```kt
 * "debug-menu".flatCase shouldBe "debugmenu"
 * ```
 */
public val String.flatCase: String
    get() = asTransformedCaptureToken(CaptureTransform.FlatCase) ?: nameWords.joinToString("")

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
