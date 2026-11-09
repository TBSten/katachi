package me.tbsten.katachi.test.script

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import me.tbsten.katachi.script.internal.Messages
import me.tbsten.katachi.script.internal.Msg
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Every message the CLI prints lives in [Messages], so that the docs' dictionary (docs/prh.yml) can
 * be held against it (KATACHI-328).
 */
class MessagesSpec : FreeSpec({
    val dictionary = PrhDictionary.load(File("../docs/prh.yml"))

    "prh.yml の specs を同じ解釈で再現できる（docs の textlint と結果がずれない）" {
        val mismatches = dictionary.rules.flatMap { rule ->
            rule.specs.mapNotNull { spec ->
                val actual = rule.apply(spec.from)
                if (actual == spec.to) null else "${rule.expected}: \"${spec.from}\" → \"$actual\" (prh.yml says \"${spec.to}\")"
            }
        }
        dictionary.rules.flatMap { it.specs }.shouldNotBeEmpty()
        mismatches.shouldBeNone("The dictionary is read differently from prh. Fix PrhDictionary:")
    }

    "Messages の日本語は言い換え辞書に引っかからない" {
        val messages = allMessages()
        messages.shouldNotBeEmpty()
        val found = messages.flatMap { (name, msg) ->
            msg.ja.lines().flatMap { line ->
                dictionary.violations(line).map { v ->
                    "$name: 「${v.found}」→「${v.replaced}」" + (v.rule.prh?.let { " ($it)" } ?: "") + "\n    ${line.trim()}"
                }
            }
        }.distinct()
        found.shouldBeNone("Rewrite these in Messages.kt (the dictionary is docs/prh.yml):")
    }

    "Messages の英語に日本語が混ざっていない" {
        val japanese = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]")
        allMessages().filter { (_, msg) -> japanese.containsMatchIn(msg.en) }.map { it.first }.distinct()
            .shouldBeNone("The en side of these messages has Japanese in it:")
    }

    "CLI の文言は Messages.kt の外に書かない（ASCII でない文字列リテラルが無い）" {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Messages.kt" }.toList()
        sources.shouldNotBeEmpty()
        val found = sources.flatMap { file ->
            stringLiterals(file.readText())
                .filter { (_, literal) -> literal.any { it.code > 0x7f } }
                .map { (line, literal) -> "${file.path}:$line ${literal.take(60)}" }
        }
        found.shouldBeNone("Move these to Messages.kt as a Msg with ja and en:")
    }
})

private fun List<String>.shouldBeNone(what: String) {
    if (isNotEmpty()) fail("$what\n" + joinToString("\n"))
}

/** Each message with a stand-in for every argument, so that the text around the arguments can be read. */
private fun allMessages(): List<Pair<String, Msg>> = Messages::class.java.declaredClasses.flatMap { group ->
    val instance = group.getField("INSTANCE").get(null)
    group.declaredMethods
        .filter { it.returnType == Msg::class.java && Modifier.isPublic(it.modifiers) }
        .flatMap { method ->
            val name = if (method.parameterCount == 0) method.name.removePrefix("get").replaceFirstChar { it.lowercase() } else method.name
            argumentSets(method).map { args -> "Messages.${group.simpleName}.$name" to method.invoke(instance, *args) as Msg }
        }
}

private fun argumentSets(method: Method): List<Array<Any?>> =
    method.parameterTypes.fold(listOf(emptyList<Any?>())) { sets, type ->
        // A digit, not a letter: "x 版" reads as a language edition to prh, which would hide a real hit.
        val choices: List<Any?> = when (type) {
            Boolean::class.javaPrimitiveType -> listOf(true, false)
            Msg::class.java -> listOf(Msg("0", "0"))
            List::class.java -> listOf(listOf("0", "1"), listOf("0", "1", "2"))
            else -> listOf("0")
        }
        sets.flatMap { set -> choices.map { set + it } }
    }.map { it.toTypedArray() }

/** The string literals of a Kotlin source, with their line, comments skipped. Template expressions count as text. */
private fun stringLiterals(source: String): List<Pair<Int, String>> {
    val found = mutableListOf<Pair<Int, String>>()
    var i = 0
    fun line(at: Int) = source.substring(0, at).count { it == '\n' } + 1
    while (i < source.length) {
        when {
            source.startsWith("//", i) -> i = source.indexOf('\n', i).let { if (it < 0) source.length else it }
            source.startsWith("/*", i) -> i = source.indexOf("*/", i + 2) + 2
            source.startsWith("\"\"\"", i) -> {
                val end = source.indexOf("\"\"\"", i + 3)
                found += line(i) to source.substring(i + 3, end)
                i = end + 3
                while (i < source.length && source[i] == '"') i++
            }
            source[i] == '\'' && i + 2 < source.length -> {
                // A char literal such as '"' must not open a string.
                val end = if (source[i + 1] == '\\') source.indexOf('\'', i + 3) else i + 2
                i = if (end > 0 && source[end] == '\'') end + 1 else i + 1
            }
            source[i] == '"' -> {
                val start = i
                i++
                while (i < source.length && source[i] != '"') i += if (source[i] == '\\') 2 else 1
                found += line(start) to source.substring(start + 1, i)
                i++
            }
            else -> i++
        }
    }
    return found
}
