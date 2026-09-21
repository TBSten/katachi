package me.tbsten.katachi.dsl

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
 * ```kotlin
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
 * Written on a captured wildcard, this is what ties a file name to the module it sits in:
 *
 * ```kotlin
 * ":feature:*".module {
 *   mainSourceSet / kotlin / modulePackage / "${wildcards[0].pascalCase}Screen".ktFile()
 * }
 * ```
 *
 * A name that holds no word at all (`""`, `"--"`) converts to the empty string, and a name
 * starting with a digit keeps it (`2fa` stays `2fa`), because there is no capital form of a
 * digit to move to.
 */
public val String.pascalCase: String
    get() = nameWords.joinToString("") { it.capitalized() }

/** `debug-menu` as `debugMenu`. The same words as [pascalCase], with the first one left alone. */
public val String.camelCase: String
    get() = nameWords.mapIndexed { index, word -> if (index == 0) word else word.capitalized() }.joinToString("")

/** `debugMenu` as `debug-menu`. */
public val String.kebabCase: String
    get() = nameWords.joinToString("-")

/** `debugMenu` as `debug_menu`. */
public val String.snakeCase: String
    get() = nameWords.joinToString("_")

/** `debugMenu` as `DEBUG_MENU`. */
public val String.screamingSnakeCase: String
    get() = nameWords.joinToString("_") { it.uppercase() }

/**
 * `debug-menu` as `debugmenu` — the words run together with nothing between them.
 *
 * This is the spelling for a place that takes neither a separator nor a capital, such as a
 * package segment in a project that writes them all in lower case.
 */
public val String.flatCase: String
    get() = nameWords.joinToString("")

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }
