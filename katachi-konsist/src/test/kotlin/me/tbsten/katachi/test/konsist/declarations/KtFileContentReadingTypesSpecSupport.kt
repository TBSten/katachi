package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random

/*
 * Writing a class, an interface or an object for the reading property: type parameters with
 * bounds, a primary constructor with properties, supertypes laid out in several ways, a `where`
 * clause, enum entries with bodies, and members -- each with what reading it should give.
 */

private const val INDENT: String = "    "

private val TYPE_PARAMETERS: List<String> = listOf("<T>", "<T : Comparable<T>>", "<T : Any, R,>", "<out T : Any>")

/** Enum entries, one of them with a body whose functions are not the enum's members. */
private val ENUM_ENTRIES: List<String> = listOf(
    "ONE,\nTWO,\n;",
    "ONE {\n    override fun toString() = \"one\"\n    fun inEntry() = 1\n},\nTWO;",
)

/** Default values of constructor parameters, some with brackets and comparisons the header holds. */
private val DEFAULT_VALUES: List<String> = listOf("", "", " = TODO()", " = \"(a) <none>\"", " = { it > 0 }", " = \"<\"", " = 1 < 2")

internal fun ReadingWriter.typeDeclaration(
    random: Random,
    kind: GenKind,
    head: DeclarationHead,
    indent: Int,
    depth: Int,
    name: String,
    unnamedCompanion: Boolean,
): ExpectedFact {
    val writtenName = when {
        unnamedCompanion -> ""
        random.chance(10) -> " `$name`"
        else -> " $name"
    }
    val typeParameters = if (kind != GenKind.Object && random.chance(25)) random.pick(TYPE_PARAMETERS) else ""
    val isEnum = "enum " in head.modifiers
    val headText = StringBuilder(head.text + kind.keyword + writtenName + typeParameters)
    val headLine = nextLine
    val constructorProperties = mutableListOf<ExpectedFact>()
    if (kind == GenKind.Class && random.chance(45)) {
        headText.append(random.pick(listOf("", "", "", " private constructor", " @Inject constructor")))
        constructor(random, headText, headLine, constructorProperties)
    }
    val supertypes = supertypesOf(random, kind, isEnum).shuffled(random)
    if (supertypes.isNotEmpty()) writeSupertypes(random, headText, supertypes)
    // After a delegation, `where` would be read as an infix call on the delegate (`handler where T`).
    if (typeParameters.isNotEmpty() && supertypes.none { " by " in it.written } && random.chance(25)) {
        headText.append(random.pick(listOf(" where T : Any", "\n    where T : Any")))
    }
    val members = mutableListOf<ExpectedFact>()
    // A companion object without a name or a body would take the next line's first word as its name.
    if (!isEnum && !unnamedCompanion && !random.chance(60)) {
        emit(indent, headText.toString())
    } else {
        emit(indent, "$headText {")
        if (isEnum) emit(indent + 1, random.pick(ENUM_ENTRIES))
        if (kind == GenKind.Class && random.chance(15)) emit(indent + 1, "init {\n    val local = 1\n}")
        if (kind == GenKind.Class && random.chance(10)) emit(indent + 1, "constructor(flag: Boolean) : this()")
        val count = if (depth >= 1) random.nextInt(0, 3) else random.nextInt(0, 4)
        val companionAt = if (kind != GenKind.Object && random.chance(25)) random.nextInt(count + 1) else -1
        for (index in 0..count) {
            if (index == companionAt || index < count) {
                val isCompanion = index == companionAt
                members += declaration(random, indent + 1, GenPlacement.ClassBody, container = kind, depth = depth + 1, companion = isCompanion)
            }
        }
        emit(indent, "}")
    }
    return plainFact(kind, name).copy(
        supertypes = supertypes.map { it.canonical to it.withoutArguments },
        members = constructorProperties + members,
    )
}

/**
 * Appends a primary constructor to [headText] (whose first line is [headLine]): properties, plain
 * parameters, annotations, defaults with brackets in strings, on one line or one per line.
 */
private fun ReadingWriter.constructor(random: Random, headText: StringBuilder, headLine: Int, properties: MutableList<ExpectedFact>) {
    val count = random.nextInt(0, 4)
    val multiLine = count > 0 && random.chance(40)
    headText.append(if (multiLine) "(\n" else "(")
    for (index in 0 until count) {
        val type = random.pick(READ_TYPES)
        val default = random.pick(DEFAULT_VALUES)
        val (annotation, annotationFact) = random.pick(
            listOf(
                "" to null,
                "" to null,
                "@Suppress(\"x\") " to ("Suppress" to "kotlin.Suppress"),
                "@param:Json(name = \"p:$index\") " to ("Json" to "com.example.reading.Json"),
            ),
        )
        val property = random.pickOrNull(listOf("val", "var")).takeIf { random.chance(60) }
        val visibility = random.pick(listOf("", "", "private ", "internal ", "override "))
        if (multiLine) headText.append(INDENT)
        val line = headLine + headText.count { it == '\n' }
        if (property == null) {
            headText.append("${annotation}p$index: ${type.written}$default")
        } else {
            val (name, written) = callableName(random)
            headText.append("$annotation$visibility$property $written: ${type.written}$default")
            properties += plainFact(if (property == "val") GenKind.Val else GenKind.Var, name).copy(
                modifiers = setOfNotNull(visibility.trim().ifEmpty { null }),
                annotations = listOfNotNull(annotationFact),
                propertyType = type.canonical,
                line = line,
            )
        }
        if (index < count - 1) headText.append(if (multiLine) ",\n" else ", ")
    }
    if (multiLine) headText.append(if (random.chance(50)) ",\n)" else "\n)") else headText.append(")")
}

private fun supertypesOf(random: Random, kind: GenKind, isEnum: Boolean): List<ReadSupertype> {
    if (!random.chance(55)) return emptyList()
    val pool = if (kind == GenKind.Interface || isEnum) INTERFACE_SUPERTYPES else CLASS_SUPERTYPES
    return pool.shuffled(random).take(random.nextInt(1, 4))
}

/** Appends ` : A, B` to [headText]: on one line, one per line, or one per line with a comment after each. */
private fun writeSupertypes(random: Random, headText: StringBuilder, supertypes: List<ReadSupertype>) {
    when (random.nextInt(3)) {
        0 -> headText.append(supertypes.joinToString(", ", " : ") { it.written })
        1 -> headText.append(supertypes.joinToString(",\n", " :\n") { INDENT + it.written })
        // The last one goes without a comment, which would take in the `{` after it.
        else -> headText.append(
            supertypes.withIndex().joinToString("\n", " :\n") { (index, it) ->
                INDENT + it.written + if (index < supertypes.lastIndex) ", // supertype $index" else ""
            },
        )
    }
}
