package me.tbsten.katachi.intellij.data.placement

/**
 * katachi's `String.nameWords` (`dsl/Naming.kt`): `-`, `_` and a space end a word; a capital after a lower case letter
 * or a digit starts one, and so does the last capital of a run followed by a lower case letter; a digit is part of its
 * word. Words are kept in lower case. The six conversions of [conversionOf] all read these words, so the plugin
 * derives a value (`<x.pascalCase>` of a template pattern, the derived level of a bound layout entry) the way katachi does.
 */
internal fun nameWords(name: String): List<String> {
    val words = ArrayList<String>()
    val word = StringBuilder()
    fun endWord() {
        if (word.isNotEmpty()) {
            words += word.toString().lowercase()
            word.clear()
        }
    }
    name.forEachIndexed { index, character ->
        when {
            character == '-' || character == '_' || character == ' ' -> endWord()

            character.isUpperCase() -> {
                val previous = name.getOrNull(index - 1)
                val next = name.getOrNull(index + 1)
                val afterLowerOrDigit = previous != null && (previous.isLowerCase() || previous.isDigit())
                val lastOfCapitals = previous != null && previous.isUpperCase() && next != null && next.isLowerCase()
                if (afterLowerOrDigit || lastOfCapitals) endWord()
                word.append(character)
            }

            else -> word.append(character)
        }
    }
    endWord()
    return words
}

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

/**
 * The conversions a derived capture can name, by the spelling katachi writes in the JSON (`transform` of a capture;
 * `<x.pascalCase>` of a pattern): the String extensions of katachi's `Naming.kt`, one each. Nothing else is one.
 */
private val CONVERSIONS: Map<String, (String) -> String> = mapOf(
    "camelCase" to { value -> nameWords(value).mapIndexed { index, word -> if (index == 0) word else word.capitalized() }.joinToString("") },
    "pascalCase" to { value -> nameWords(value).joinToString("") { it.capitalized() } },
    "kebabCase" to { value -> nameWords(value).joinToString("-") },
    "snakeCase" to { value -> nameWords(value).joinToString("_") },
    "screamingSnakeCase" to { value -> nameWords(value).joinToString("_") { it.uppercase() } },
    "flatCase" to { value -> nameWords(value).joinToString("") },
)

/** The conversion [spelling] names (`pascalCase`), or `null` for a word this plugin does not know: a newer katachi's. */
internal fun conversionOf(spelling: String): ((String) -> String)? = CONVERSIONS[spelling]
