package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintExampleSource
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentUnknownCaptureException
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

private const val HEADING: String = "## Kotlin file content"

// A katachi exception (a mistake in the definition) is thrown and stops the pages; anything else the block throws is likely about the example's values, so the pages go on.
internal fun ktFileContentSection(
    block: KtFileContentScope.() -> Unit,
    context: FileConstraintDocumentContext,
    declaredAt: DeclarationSite,
    patterns: PatternCache,
): String {
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
    val declarations = rules.declarations
    val parts = buildList {
        add(HEADING)
        add(exampleLine)
        add(if (rules.packages.isEmpty()) NO_PACKAGE_LINE else packageTable(rules.packages))
        add(if (rules.imports.isEmpty()) NO_IMPORT_LINE else importTable(rules.imports))
        add(if (declarations.entries.isEmpty()) NO_DECLARATION_LINE else declarationTable("Declaration", declarations))
        addAll(memberSections(declarations, outer = emptyList()))
    }
    return parts.joinToString("\n\n")
}

private const val NO_PACKAGE_LINE: String = "No package line is written, so no package may be declared."

private const val NO_IMPORT_LINE: String = "No import line is written, so no import is allowed."

private const val NO_DECLARATION_LINE: String = "No declaration line is written, so nothing may be declared in the file."

private const val NO_MEMBERS: String = "No members are allowed."

private val KINDS_WITH_MEMBERS: Set<DeclarationKindName> =
    setOf(DeclarationKindName.Class, DeclarationKindName.Interface, DeclarationKindName.Object)

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
    // An import line only allows: it is never required.
    rules.map { rule -> listOf(code(rule.path.written) + exceptCells(rule.except), statusOf(rule.prohibited, required = false)) } +
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

private const val ANYTHING_ELSE: String = "(anything else)"

private fun memberSections(list: DeclarationListRules, outer: List<String>): List<String> = list.lines.flatMap { rule ->
    if (rule.prohibited || rule.shapes.none { it.kind in KINDS_WITH_MEMBERS }) return@flatMap emptyList()
    val members = rule.members
    val path = outer + rule.name.written
    // The line's modifiers and kinds as written (`public enum class`, `(data or value) class`), the name with its outer classes.
    val heading = "### Members of ${code(renderDeclaration(rule.shapes, path.joinToString("."), prohibited = false))}"
    val body = if (members == null || members.entries.isEmpty()) NO_MEMBERS else declarationTable("Member", members)
    listOf("$heading\n\n$body") + members?.let { memberSections(it, path) }.orEmpty()
}

private fun lineWithoutNotAndExcept(rule: DeclarationRule): String = renderDeclaration(
    shapes = rule.shapes,
    name = rule.name.written,
    prohibited = false,
    receiver = rule.receiver?.written,
    superclasses = rule.superclasses.map { it.written },
    propertyType = rule.propertyType?.written,
    parameters = rule.parameters?.map { it.written },
    returnType = rule.returnType?.written,
    details = rule.details.map { it.rendered },
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

private fun table(header: List<String>, rows: () -> List<List<String>>): String = buildList {
    add(header.joinToString(" | ", "| ", " |").replace("|  |", "| |"))
    add(header.joinToString("|", "|", "|") { "---" })
    for (row in rows()) add(row.joinToString(" | ", "| ", " |") { it.replace("|", "\\|") })
}.joinToString("\n")

private fun code(text: String): String = if ('`' in text) "`` $text ``" else "`$text`"

private fun causeLineOf(cause: Throwable): String {
    val type = cause::class.qualifiedName ?: cause::class.java.name
    val message = cause.message?.lineSequence()?.firstOrNull()?.trim()
    return if (message.isNullOrEmpty()) type else "$type: $message"
}
