package me.tbsten.katachi.intellij.data.placement

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

private val CONVERSIONS: Map<String, (String) -> String> = mapOf(
    "camelCase" to { value -> nameWords(value).mapIndexed { index, word -> if (index == 0) word else word.capitalized() }.joinToString("") },
    "pascalCase" to { value -> nameWords(value).joinToString("") { it.capitalized() } },
    "kebabCase" to { value -> nameWords(value).joinToString("-") },
    "snakeCase" to { value -> nameWords(value).joinToString("_") },
    "screamingSnakeCase" to { value -> nameWords(value).joinToString("_") { it.uppercase() } },
    "flatCase" to { value -> nameWords(value).joinToString("") },
)

internal fun conversionOf(spelling: String): ((String) -> String)? = CONVERSIONS[spelling]
