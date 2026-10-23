package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintExampleSource
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentUnknownCaptureException
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/*
 * The `## Kotlin file content` section of a role's page: the rules of `ktFileContent { }` as
 * tables, worked out for the role's example file -- the block is evaluated per file, so a table
 * can only show what it says about one.
 */

private const val HEADING: String = "## Kotlin file content"

/**
 * The section for [block], evaluated for [context]'s example file, or `null` when it writes no
 * line for that file.
 *
 * A katachi exception -- a mistake in the definition, such as a `captureValue` name the layout
 * does not capture -- is thrown, which stops the pages. Anything else the block throws is most
 * likely about the example's values, so the section says the rules could not be shown and the
 * pages go on.
 */
internal fun ktFileContentSection(
    block: KtFileContentScope.() -> Unit,
    context: FileConstraintDocumentContext,
    declaredAt: DeclarationSite,
    patterns: PatternCache,
): String? {
    val example = context.exampleFile
    val exampleLine = when (example.source) {
        FileConstraintExampleSource.Placeholder ->
            "Example: none. The tables are shown for a file named `${example.fileName}`; " +
                "add example(...) to the role to show a real one."
        else -> "Example: `${example.fileName}`"
    }
    val input = EvaluationInput(
        fileName = fileNameOf(example.fileName),
        captureValue = { name ->
            example.captureValues[name] ?: if (name in context.captureNames) {
                placeholderCaptureValue(name)
            } else {
                throw KatachiKtFileContentUnknownCaptureException(
                    name = name,
                    role = context.role.qualifiedName,
                    available = context.captureNames.toList(),
                    declaredAt = declaredAt,
                )
            }
        },
        patterns = patterns,
    )
    val rules = try {
        evaluateRules(block, input, declaredAt)
    } catch (cause: KatachiDeclarationException) {
        throw cause
    } catch (cause: InterruptedException) {
        throw cause
    } catch (cause: Exception) {
        return listOf(HEADING, exampleLine, "The rules could not be shown for this example: ${causeLineOf(cause)}")
            .joinToString("\n\n")
    }
    if (rules.isEmpty) return null
    val parts = buildList {
        add(HEADING)
        add(exampleLine)
        if (rules.packages.isNotEmpty()) add(packageTable(rules.packages))
        if (rules.imports.isNotEmpty()) add(importTable(rules.imports))
        rules.declarations?.let { declarations ->
            add(declarationTable("Declaration", declarations))
            addAll(memberSections(declarations, outer = emptyList()))
        }
    }
    return parts.joinToString("\n\n")
}

private fun packageTable(rules: List<PackageRule>): String = table(listOf("Package")) {
    rules.map { rule ->
        val target = when (val target = rule.target) {
            is PackageTarget.Pattern -> code(target.rule.written) + exceptCells(rule.except)
            PackageTarget.MatchingDirectory -> THE_FILES_DIRECTORY
        }
        listOf(if (rule.prohibited) "$target (not allowed)" else target)
    } + listOfNotNull(listOf("$ANYTHING_ELSE (not allowed)").takeIf { rules.all { it.prohibited } })
}

private fun importTable(rules: List<ImportRule>): String = table(listOf("Import", "")) {
    rules.map { rule -> listOf(code(rule.path.written) + exceptCells(rule.except), statusOf(rule.prohibited, rule.required)) } +
        listOfNotNull(listOf(ANYTHING_ELSE, NOT_ALLOWED).takeIf { rules.all { it.prohibited } })
}

private fun declarationTable(heading: String, list: DeclarationListRules): String = table(listOf(heading, "")) {
    list.entries.map { entry ->
        when (entry) {
            is DeclarationRule -> listOf(
                code(lineWithoutNotAndExcept(entry)) + exceptCells(entry.except),
                statusOf(entry.prohibited, entry.required),
            )
            is AnythingElseRule -> {
                val modifiers = renderModifierAlternatives(entry.alternatives)
                listOf(if (modifiers.isEmpty()) ANYTHING else "${code(modifiers)} $ANYTHING", OPTIONAL)
            }
        }
    } + listOfNotNull(listOf(ANYTHING_ELSE, NOT_ALLOWED).takeIf { list.allowsOnlyByNot() })
}

/**
 * The last row of a table whose lines are all `not.` lines: they still make an allow list, so
 * what they do not prohibit is not allowed either.
 */
private const val ANYTHING_ELSE: String = "(anything else)"

/** A `### Members of ...` section for every line with a `{ }`, outer classes before their nested ones. */
private fun memberSections(list: DeclarationListRules, outer: List<String>): List<String> = list.lines.flatMap { rule ->
    val members = rule.members ?: return@flatMap emptyList()
    val path = outer + rule.name.written
    val kinds = rule.shapes.map { it.kind.keyword }.distinct().joinToString(" or ")
    val heading = "### Members of ${code("$kinds ${path.joinToString(".")}")}"
    val body = if (members.entries.isEmpty()) "No members are allowed." else declarationTable("Member", members)
    listOf("$heading\n\n$body") + memberSections(members, path)
}

/** The line as written, without the `not ` a status column says and the `except` a cell lists. */
private fun lineWithoutNotAndExcept(rule: DeclarationRule): String = renderDeclaration(
    shapes = rule.shapes,
    name = rule.name.written,
    prohibited = false,
    superclasses = rule.superclasses.map { it.written },
    propertyType = rule.propertyType?.written,
    parameters = rule.parameters?.map { it.written },
    returnType = rule.returnType?.written,
)

private const val OPTIONAL: String = "optional"

private const val NOT_ALLOWED: String = "not allowed"

private fun statusOf(prohibited: Boolean, required: Boolean): String = when {
    prohibited -> NOT_ALLOWED
    required -> "required"
    else -> OPTIONAL
}

private fun exceptCells(except: List<NameRule>): String =
    if (except.isEmpty()) "" else " except " + except.joinToString(", ") { code(it.written) }

/** A Markdown table with [header], one row per element of [rows]. */
private fun table(header: List<String>, rows: () -> List<List<String>>): String = buildList {
    add(header.joinToString(" | ", "| ", " |").replace("|  |", "| |"))
    add(header.joinToString("|", "|", "|") { "---" })
    for (row in rows()) add(row.joinToString(" | ", "| ", " |") { it.replace("|", "\\|") })
}.joinToString("\n")

/** [text] as inline code, with enough backticks around it to hold the ones it contains. */
private fun code(text: String): String = if ('`' in text) "`` $text ``" else "`$text`"

/**
 * The type of [cause] and the first line of its message, on one line.
 *
 * Only the first line is kept because the result is a single line of a generated page. A report's
 * `Cause:` section prints every line of the message, so this is shorter than what a report shows.
 */
private fun causeLineOf(cause: Throwable): String {
    val type = cause::class.qualifiedName ?: cause::class.java.name
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim()
    return if (message.isNullOrEmpty()) type else "$type: $message"
}
