package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random

/*
 * Writing a file for the reading property, line by line, so that each declaration knows the line it
 * starts on: its first annotation, else its `context(...)`, else its first modifier or keyword.
 */

private const val INDENT: String = "    "

/** Writes [case] out and says what reading it should give. */
internal fun readFileOf(case: ReadCase): ReadFile {
    val writer = ReadingWriter()
    val random = Random(case.header)
    if (random.chance(20)) writer.emit(0, "@file:Suppress(\"unused\")\n")
    if (random.chance(20)) writer.emit(0, "/* A file header that mentions class Fake : Base. */")
    val packageLine = writer.nextLine
    writer.emit(0, "package com.example.reading\n")
    val imports = READ_IMPORTS.shuffled(random).map { (path, written) ->
        if (random.chance(15)) writer.emit(0, random.pick(listOf("// a comment", "/* a block comment */")))
        val line = writer.nextLine
        writer.emit(0, "import $written")
        path to line
    }
    writer.emit(0, "")
    val declarations = case.declarations.map { seed -> writer.declaration(Random(seed), 0, GenPlacement.File, container = null, depth = 0) }
    return ReadFile(writer.text, "com.example.reading", packageLine, imports, declarations)
}

/** The lines of a file being written. */
internal class ReadingWriter {
    private val out = mutableListOf<String>()
    private var counter = 0

    val text: String get() = out.joinToString("\n", postfix = "\n")

    /** The line the next [emit] starts on. */
    val nextLine: Int get() = out.size + 1

    /** Writes [text], each of its lines indented by [indent] levels. */
    fun emit(indent: Int, text: String) {
        for (line in text.split('\n')) out += INDENT.repeat(indent) + line
    }

    fun number(): Int = counter++
}

/** Writes one declaration in a way [random] picks, and returns what reading it should give. */
internal fun ReadingWriter.declaration(
    random: Random,
    indent: Int,
    placement: GenPlacement,
    container: GenKind?,
    depth: Int,
    companion: Boolean = false,
): ExpectedFact {
    val kind = if (companion) GenKind.Object else random.pick(kindsAt(placement, depth))
    // A type's name comes first, so that a comment or an annotation in front of it can mention its own header,
    // which the reading has to look past.
    val unnamedCompanion = companion && random.chance(50)
    val typeName = if (!kind.isType) null else if (unnamedCompanion) "Companion" else typeName(random)
    val ownHeader = typeName?.let { if (unnamedCompanion) "companion object : Fake" else "${kind.keyword} $it : Fake" }
    val trivia = LEADING_TRIVIA + listOfNotNull(ownHeader?.let { "/** Was $it. */" }, ownHeader?.let { "// $it" })
    if (random.chance(40)) emit(indent, random.pick(trivia))
    val ownAnnotation = ownHeader?.let { ReadAnnotation("@Description(\"$it (\")", "Description", "com.example.reading.Description") }
    val annotations = (READ_ANNOTATIONS.filter { !it.propertyOnly || kind == GenKind.Val || kind == GenKind.Var } + listOfNotNull(ownAnnotation))
        .shuffled(random).take(if (random.chance(45)) random.nextInt(1, 3) else 0)
    val inlineAnnotations = annotations.none { '\n' in it.text } && random.chance(30)
    val startLine = nextLine
    if (!inlineAnnotations) {
        annotations.forEach { emit(indent, it.text) }
        if (annotations.isNotEmpty() && random.chance(15)) emit(indent, "// between the annotations and the declaration")
    }
    val prefix = if (inlineAnnotations) annotations.joinToString("") { it.text + " " } else ""
    val modifiers = modifiersOf(random, kind, placement, container) + listOfNotNull("companion".takeIf { companion })
    val head = DeclarationHead(prefix, modifiers.joinToString("") { "$it " })
    val fact = when (kind) {
        GenKind.Class, GenKind.Interface, GenKind.Object -> typeDeclaration(random, kind, head, indent, depth, typeName ?: "", unnamedCompanion)
        GenKind.Function -> function(random, head, indent, container, modifiers)
        GenKind.Val, GenKind.Var -> property(random, kind, head, indent, container, modifiers)
        GenKind.TypeAlias -> {
            val name = typeName(random)
            emit(indent, head.text + "typealias $name" + random.pick(listOf(" = String", "<T> = (T) -> Unit", " = Map<String, List<Int>>")))
            plainFact(kind, name)
        }
    }
    return fact.copy(
        modifiers = modifiers.toSet(),
        annotations = annotations.map { it.simpleName to it.fullName },
        line = startLine,
    )
}

/** What a declaration writes before its keyword: annotations written on the same line, and its modifiers. */
internal class DeclarationHead(val annotations: String, val modifiers: String) {
    val text: String get() = annotations + modifiers
}

private fun kindsAt(placement: GenPlacement, depth: Int): List<GenKind> {
    val types = if (depth < 2) listOf(GenKind.Class, GenKind.Interface, GenKind.Object) else emptyList()
    val callables = listOf(GenKind.Function, GenKind.Val, GenKind.Var)
    return types + callables + if (placement == GenPlacement.File) listOf(GenKind.TypeAlias) else emptyList()
}

private fun modifiersOf(random: Random, kind: GenKind, placement: GenPlacement, container: GenKind?): List<String> {
    val member = placement == GenPlacement.ClassBody
    val written = mutableListOf<String>()
    val visibilities = if (member) listOf("public", "internal", "protected", "private") else listOf("public", "internal", "private")
    if (random.chance(40)) written += random.pick(visibilities)
    if (!member && random.chance(6)) written += random.pick(listOf("expect", "actual"))
    val inheritance = if (member && container != GenKind.Object && random.chance(30)) random.pick(listOf("override", "open", "abstract")) else null
    when (kind) {
        GenKind.Class -> {
            val classModifiers = listOf("data", "sealed", "enum", "value", "annotation", "abstract", "open") +
                if (member) listOf("inner") else emptyList()
            if (random.chance(40)) written += random.pick(classModifiers)
        }
        GenKind.Interface -> if (random.chance(30)) written += random.pick(listOf("sealed", "fun"))
        GenKind.Object -> if (random.chance(20)) written += "data"
        GenKind.Function -> {
            inheritance?.let { written += it }
            listOf("suspend" to 20, "inline" to 10, "operator" to 8, "infix" to 5, "tailrec" to 5).forEach { (keyword, percent) ->
                if (random.chance(percent)) written += keyword
            }
        }
        GenKind.Val -> if (inheritance != null) written += inheritance else if (random.chance(15)) written += "const"
        GenKind.Var -> if (inheritance != null) written += inheritance else if (random.chance(20)) written += "lateinit"
        GenKind.TypeAlias -> Unit
    }
    // Kotlin reads modifiers in any order -- but `fun interface`, whose `fun` read anywhere else starts a function.
    return if (random.chance(20)) written.shuffled(random).sortedBy { it == "fun" } else written
}

/** A name for a class, an interface, an object or a typealias: now and then in backticks, which the facts leave out. */
internal fun ReadingWriter.typeName(random: Random): String = random.pick(listOf("Widget", "Gadget", "Store", "Panel")) + number()

/** The name a function or a property is read as, and how it is written. */
internal fun ReadingWriter.callableName(random: Random): Pair<String, String> {
    val name = random.pick(listOf("load", "count", "items", "render")) + number()
    return when (random.nextInt(10)) {
        0 -> "$name data".let { it to "`$it`" }
        1 -> name to "`$name`"
        else -> name to name
    }
}

/** A fact with nothing but its kind and name: the rest filled in by whoever made it. */
internal fun plainFact(kind: GenKind, name: String): ExpectedFact = ExpectedFact(
    kind = kind,
    name = name,
    modifiers = emptySet(),
    annotations = emptyList(),
    supertypes = emptyList(),
    propertyType = null,
    parameters = null,
    returnType = null,
    returnTypeWritten = false,
    line = 0,
    members = null,
)

private fun ReadingWriter.function(random: Random, head: DeclarationHead, indent: Int, container: GenKind?, modifiers: List<String>): ExpectedFact {
    val (name, written) = callableName(random)
    val context = "context(logger: Logger) ".takeIf { random.chance(15) }.orEmpty()
    val typeParameters = random.pick(listOf("", "", "", "<T : Any> ", "<T, R : Comparable<R>,> "))
    val receiver = random.pick(listOf("", "", "", "String.", "List<Int>."))
    val parameters = List(random.nextInt(0, 4)) { parameterOf(random, it) }
    val multiLine = parameters.isNotEmpty() && random.chance(30)
    val parameterList = if (multiLine) {
        parameters.joinToString(",\n", "(\n", (if (random.chance(50)) "," else "") + "\n)") { INDENT + it.first }
    } else {
        parameters.joinToString(", ", "(", if (parameters.isNotEmpty() && random.chance(15)) ",)" else ")") { it.first }
    }
    val returnType = random.pickOrNull(READ_TYPES + SPREAD_TYPES).takeIf { random.chance(50) }
    val noBody = modifiers.any { it == "abstract" || it == "expect" } || (container == GenKind.Interface && random.chance(40))
    val expression = !noBody && random.chance(35)
    val body = when {
        noBody -> ""
        expression -> " = TODO()"
        else -> random.pick(listOf(" {}", " {\n    val local = 1\n    fun localFunction() {}\n    class LocalClass\n}", " {\n    println(\"(\")\n}"))
    }
    // context(...) goes before the modifiers, on the declaration's first line or on a line of its own.
    val contextOnItsOwnLine = context.isNotEmpty() && random.chance(50)
    if (contextOnItsOwnLine) emit(indent, head.annotations + context.trim())
    val start = if (contextOnItsOwnLine) head.modifiers else head.annotations + context + head.modifiers
    emit(indent, "${start}fun $typeParameters$receiver$written$parameterList" + returnType?.let { ": ${it.written}" }.orEmpty() + body)
    return plainFact(GenKind.Function, name).copy(
        parameters = parameters.map { it.second },
        returnType = returnType?.canonical ?: if (expression) null else "Unit",
        returnTypeWritten = returnType != null,
    )
}

/** One parameter as written, and its name and the canonical form of its type as the facts hold them. */
private fun parameterOf(random: Random, index: Int): Pair<String, String> {
    val type = random.pick(READ_TYPES + SPREAD_TYPES.takeIf { random.chance(20) }.orEmpty())
    val annotation = "@Suppress(\"x\") ".takeIf { random.chance(15) }.orEmpty()
    val vararg = "vararg ".takeIf { random.chance(10) }.orEmpty()
    val default = random.pick(listOf("", "", " = TODO()", " = error(\"(\")"))
    // A name in backticks is read without them, as a declaration's name is.
    val written = if (random.chance(15)) "`p$index`" else "p$index"
    return "$annotation$vararg$written: ${type.written}$default" to "p$index: ${type.canonical}"
}

private fun ReadingWriter.property(
    random: Random,
    kind: GenKind,
    head: DeclarationHead,
    indent: Int,
    container: GenKind?,
    modifiers: List<String>,
): ExpectedFact {
    val (name, written) = callableName(random)
    val noInitializer = modifiers.any { it == "lateinit" || it == "abstract" || it == "expect" } ||
        (container == GenKind.Interface && random.chance(50))
    val receiver = if (!noInitializer && "const" !in modifiers && random.chance(10)) "String." else ""
    val type = random.pickOrNull(READ_TYPES + SPREAD_TYPES).takeIf { noInitializer || random.chance(60) }
    val context = "context(logger: Logger) ".takeIf { receiver.isNotEmpty() && random.chance(50) }.orEmpty()
    val initializer = when {
        noInitializer -> ""
        receiver.isNotEmpty() -> "\n    get() = TODO()"
        "const" in modifiers -> " = 1"
        else -> random.pick(
            listOf(
                " = 1",
                " = \"class Fake : Base { (\"",
                " = \"\"\"\n    class Fake : Base {\n    (unbalanced\n\"\"\".trimIndent()",
                " = $$\"\"\"{\"name\": \"$\$name\", \"cost\": \"$9\"}\"\"\"",
                " = object : Runnable {\n    override fun run() {}\n}",
                " by lazy { TODO() }",
                "\n    get() = TODO()",
            ),
        )
    }
    val setter = "\n    private set".takeIf { kind == GenKind.Var && initializer.startsWith(" =") && random.chance(20) }.orEmpty()
    val typed = type?.let { ": ${it.written}" }.orEmpty()
    emit(indent, head.annotations + context + head.modifiers + "${kind.keyword} $receiver$written$typed$initializer$setter")
    return plainFact(kind, name).copy(propertyType = type?.canonical)
}
