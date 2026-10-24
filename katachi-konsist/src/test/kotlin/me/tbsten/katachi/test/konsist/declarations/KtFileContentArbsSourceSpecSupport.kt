package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random

/*
 * A generated case written out: the Kotlin source the judging reads, and the allow list as the
 * `ktFileContent { }` it stands for, so that a failure shows both the way a user would write them.
 */

/** Comments and blank lines put between declarations: none of them changes what the file declares. */
private val NOISE: List<String> = listOf(
    "",
    "// a comment",
    "// class Fake : Base, in a comment",
    "/* a block comment */",
    "/** A KDoc that mentions class Fake : Base. */",
)

/** The comments that may sit between imports. */
private val IMPORT_NOISE: List<String> = listOf("", "// a comment", "/* a block comment */")

private const val INDENT: String = "    "

/** The Kotlin source of [case]; with blank lines and comments between its declarations when [noise] is given. */
internal fun sourceOf(case: GenCase, noise: Random? = null): String {
    val writer = SourceWriter(noise)
    case.packageName?.let { packageName ->
        writer.line(0, "package $packageName")
        writer.line(0, "")
    }
    if (case.imports.isNotEmpty()) {
        for (import in case.imports) {
            writer.noise(0, IMPORT_NOISE)
            writer.line(0, "import ${import.path}" + import.alias?.let { " as $it" }.orEmpty())
        }
        writer.line(0, "")
    }
    for (declaration in case.declarations) writer.declaration(declaration, 0)
    return writer.text
}

private class SourceWriter(private val noise: Random?) {
    private val out = StringBuilder()

    val text: String get() = out.toString()

    fun line(indent: Int, text: String) {
        out.append(INDENT.repeat(indent)).append(text).append('\n')
    }

    fun noise(indent: Int, comments: List<String> = NOISE) {
        val random = noise ?: return
        repeat(random.nextInt(0, 3)) { line(indent, random.pick(comments)) }
    }

    fun declaration(declaration: SrcDecl, indent: Int) {
        noise(indent)
        for (annotation in declaration.annotations) line(indent, "@$annotation")
        // Only a companion object goes without a name; a change that takes `companion` away puts it back.
        val nameWritten = declaration.nameWritten || "companion" !in declaration.modifiers
        val head = (declaration.modifiers + declaration.kind.keyword).joinToString(" ") +
            if (nameWritten) " " + declaration.name else ""
        when (declaration.kind) {
            GenKind.Class, GenKind.Interface, GenKind.Object -> typeDeclaration(declaration, head, indent)
            GenKind.Function -> line(indent, head + parametersOf(declaration) + returnTypeOf(declaration) + functionBodyOf(declaration))
            GenKind.Val, GenKind.Var -> line(indent, head + propertyTypeOf(declaration) + initializerOf(declaration))
            GenKind.TypeAlias -> line(indent, "$head = String")
        }
    }

    private fun typeDeclaration(declaration: SrcDecl, head: String, indent: Int) {
        val (constructorProperties, bodyMembers) = declaration.members.orEmpty()
            .partition { declaration.kind == GenKind.Class && it.inConstructor }
        val constructor = if (constructorProperties.isEmpty()) {
            ""
        } else {
            constructorProperties.joinToString(", ", "(", ")") { propertyInConstructor(it) }
        }
        val supertypes = if (declaration.supertypes.isEmpty()) "" else " : " + declaration.supertypes.joinToString(", ")
        val isEnum = "enum" in declaration.modifiers
        // A companion object with neither a name nor a body would take the next line's first word as its name.
        val unnamed = !declaration.nameWritten && "companion" in declaration.modifiers
        if (bodyMembers.isEmpty() && !isEnum) {
            line(indent, head + constructor + supertypes + if (unnamed) " {}" else "")
            return
        }
        line(indent, "$head$constructor$supertypes {")
        if (isEnum) line(indent + 1, "ONE, TWO;")
        for (member in bodyMembers) declaration(member, indent + 1)
        line(indent, "}")
    }
}

private fun propertyInConstructor(property: SrcDecl): String =
    (property.annotations.map { "@$it" } + property.modifiers + property.kind.keyword).joinToString(" ") +
        " ${property.name}" + propertyTypeOf(property)

private fun parametersOf(function: SrcDecl): String =
    function.parameters.withIndex().joinToString(", ", "(", ")") { (index, type) -> "p$index: $type" }

private fun returnTypeOf(function: SrcDecl): String = function.returnType?.let { ": $it" }.orEmpty()

private fun functionBodyOf(function: SrcDecl): String = when (function.body) {
    GenBody.Block -> " {}"
    GenBody.Expression -> if (function.returnType == null) " = 1" else " = TODO()"
    GenBody.None -> ""
}

private fun propertyTypeOf(property: SrcDecl): String = property.propertyType?.let { ": $it" }.orEmpty()

private fun initializerOf(property: SrcDecl): String = when (property.body) {
    GenBody.Expression -> if (property.propertyType == null) " = 1" else " = TODO()"
    else -> ""
}

/** [case] as a user would write its `ktFileContent { }`, and the source after it: for a failure to show. */
internal fun describeCase(case: GenCase): String = buildString {
    appendLine("file: ${case.path}")
    appendLine("ktFileContent {")
    for (line in case.packageLines) {
        append(INDENT).append(if (line.prohibited) "not." else "")
        appendLine("`package`(" + (line.pattern?.let(::quoted) ?: "matchingDirectory") + ")")
    }
    for (line in case.importLines) {
        append(INDENT).append(if (line.prohibited) "not." else "").append("`import`(${quoted(line.pattern)})")
        if (line.optional) append(".optional()")
        if (line.except.isNotEmpty()) append(".except(${line.except.joinToString(", ", transform = ::quoted)})")
        appendLine()
    }
    case.declarationRules?.let { describeList(it, 1) }
    appendLine("}")
    appendLine("source:")
    append(sourceOf(case))
}

private fun StringBuilder.describeList(list: GenList, indent: Int) {
    for (entry in list.entries) {
        append(INDENT.repeat(indent))
        when (entry) {
            is GenAnythingElse -> appendLine("allow(" + (describeAlternatives(entry.alternatives) + "anythingElse") + ")")
            is GenLine -> {
                append(if (entry.prohibited) "not." else "").append("allow(").append(describeAlternatives(entry.alternatives))
                if (entry.kinds == listOf(GenKind.Interface) && entry.alternatives.any { "fun" in it.keywords }) append("`fun`, ")
                append(entry.kinds.joinToString(" or ") { "`${it.keyword}`" })
                append(", ${quoted(entry.name)}")
                entry.propertyType?.let { append(", ${quoted(it)}") }
                append(")")
                entry.parameters?.let { append(it.joinToString(", ", "(", ")", transform = ::quoted)) }
                if (entry.anyArgs) append("(anyArgs())")
                entry.returnType?.let { append(".returns(${quoted(it)})") }
                if (entry.superclasses.isNotEmpty()) append(".superclass(${entry.superclasses.joinToString(", ", transform = ::quoted)})")
                if (entry.optional) append(".optional()")
                if (entry.except.isNotEmpty()) append(".except(${entry.except.joinToString(", ", transform = ::quoted)})")
                val members = entry.members
                if (members == null) {
                    appendLine()
                } else {
                    appendLine(" {")
                    describeList(members, indent + 1)
                    append(INDENT.repeat(indent)).appendLine("}")
                }
            }
        }
    }
}

/** The values in front of the kind, each followed by `, `: what every alternative shares, and what tells them apart joined with `or`. */
private fun describeAlternatives(alternatives: List<GenModifiers>): String {
    val tokens = alternatives.map { alternative ->
        alternative.annotations.map { "annotated(${quoted(it)})" } + alternative.keywords.filter { it != "fun" }
    }
    val common = tokens.first().filter { token -> tokens.all { token in it } }
    val differing = tokens.map { it - common.toSet() }.filter { it.isNotEmpty() }.distinct()
    val group = if (differing.isEmpty()) emptyList() else listOf(differing.joinToString(" or ") { it.joinToString(" ") })
    return (group + common).joinToString("") { "$it, " }
}

private fun quoted(text: String): String = "\"$text\""
