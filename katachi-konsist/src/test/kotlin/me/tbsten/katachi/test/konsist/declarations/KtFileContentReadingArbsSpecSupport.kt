package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random
import me.tbsten.katachi.konsist.declarations.internal.DeclarationFact
import me.tbsten.katachi.konsist.declarations.internal.DeclarationKindName
import me.tbsten.katachi.konsist.declarations.internal.KtFileFacts
import me.tbsten.katachi.konsist.declarations.internal.ModifierName

/*
 * The reading property (plan P2): files that write the same kinds of declaration in many ways, and
 * what reading each declaration should give. The expected values are written down with each way of
 * writing (the canonical form of a type, the supertypes after the header's `:`), not worked out by
 * katachi's own reading.
 *
 * A file is a header seed and one seed per declaration written directly in it, so that the shrinker
 * can drop declarations; everything about a declaration, its members included, comes from its seed.
 */

/** One file for the reading property. */
internal data class ReadCase(val header: Long, val declarations: List<Long>) {
    /** The same file with one of its declarations left out. */
    fun shrinks(): List<ReadCase> =
        declarations.indices.map { index -> copy(declarations = declarations.filterIndexed { i, _ -> i != index }) }
}

internal fun Random.readCase(): ReadCase = ReadCase(nextLong(), List(nextInt(1, 6)) { nextLong() })

/** What reading one declaration should give. */
internal data class ExpectedFact(
    val kind: GenKind,
    val name: String,
    val modifiers: Set<String>,
    /** Each annotation's simple name and a full name it may stand for. */
    val annotations: List<Pair<String, String>>,
    /** Each supertype, canonical, and canonical without its type arguments. */
    val supertypes: List<Pair<String, String>>,
    val propertyType: String?,
    val parameters: List<String>?,
    val returnType: String?,
    val returnTypeWritten: Boolean,
    val line: Int,
    val members: List<ExpectedFact>?,
)

/** The file written out, with what reading it should give. */
internal class ReadFile(
    val text: String,
    val packageName: String,
    val packageLine: Int,
    /** Each import as the facts hold it, with its line. */
    val imports: List<Pair<String, Int>>,
    val declarations: List<ExpectedFact>,
)

/** One line per declaration (members indented under their class), holding every fact the property compares. */
internal fun describeExpected(declarations: List<ExpectedFact>, indent: String = ""): List<String> = declarations.flatMap { fact ->
    val head = describe(
        indent,
        fact.kind.keyword,
        fact.name,
        fact.modifiers.sortedBy { keyword -> ModifierName.entries.first { it.keyword == keyword }.ordinal },
        fact.annotations.map { (simple, full) -> "$simple->$full" },
        fact.supertypes.map { (canonical, byName) -> "$canonical/$byName" },
        fact.propertyType,
        fact.parameters,
        fact.returnType?.let { it + if (fact.returnTypeWritten) "" else " (implied)" },
        fact.line,
    )
    listOf(head) + describeExpected(fact.members.orEmpty(), "$indent  ")
}

/**
 * [facts] as [describeExpected] writes the expected ones. An annotation shows the full name [expected] gives it
 * when that name is among the ones the facts say it may stand for.
 */
// Enum entries, `init` blocks and secondary constructors are read too, but this reading writes them as noise only
// (KtFileContentClassMemberSpec asks about them), so they are left out here.
internal fun describeActual(facts: List<DeclarationFact>, expected: List<ExpectedFact>, indent: String = ""): List<String> =
    facts.filterNot { it.kind in CLASS_BODY_ONLY_KINDS }.mapIndexed { index, fact -> fact to expected.getOrNull(index) }.flatMap { (fact, expectedFact) ->
        val head = describe(
            indent,
            fact.kind.keyword,
            fact.name,
            fact.modifiers.sortedBy { it.ordinal }.map { it.keyword },
            fact.annotations.mapIndexed { i, annotation ->
                val full = expectedFact?.annotations?.getOrNull(i)?.second
                val reached = if (full != null && full in annotation.qualifiedCandidates) full else "(not among ${annotation.qualifiedCandidates})"
                annotation.simpleName + "->" + reached
            },
            fact.supertypes.map { "${it.canonical}/${it.withoutArguments}" },
            fact.propertyType?.canonical,
            fact.parameters?.map { "${it.name}: ${it.type.canonical}" },
            fact.returnType?.let { it.canonical + if (fact.returnTypeWritten) "" else " (implied)" },
            fact.line ?: -1,
        )
        listOf(head) + describeActual(fact.members.orEmpty(), expectedFact?.members.orEmpty(), "$indent  ")
    }

private fun describe(
    indent: String,
    kind: String,
    name: String,
    modifiers: List<String>,
    annotations: List<String>,
    supertypes: List<String>,
    propertyType: String?,
    parameters: List<String>?,
    returnType: String?,
    line: Int,
): String = buildString {
    append(indent).append(kind).append(" [").append(name).append("] line ").append(line)
    if (modifiers.isNotEmpty()) append(" modifiers=").append(modifiers)
    if (annotations.isNotEmpty()) append(" annotations=").append(annotations)
    if (supertypes.isNotEmpty()) append(" supertypes=").append(supertypes)
    propertyType?.let { append(" type=").append(it) }
    parameters?.let { append(" parameters=").append(it) }
    returnType?.let { append(" returns=").append(it) }
}

/** The package and the imports of [facts], written the way [describeHeader] writes the expected ones. */
internal fun describeActualHeader(facts: KtFileFacts): List<String> =
    listOf("package ${facts.packageName} line ${facts.packageLine}") + facts.imports.map { "import ${it.path} line ${it.line}" }

internal fun describeHeader(file: ReadFile): List<String> =
    listOf("package ${file.packageName} line ${file.packageLine}") + file.imports.map { (path, line) -> "import $path line $line" }

/** A type as some file writes it, and its canonical form: no white space, no package. */
internal class ReadType(val written: String, val canonical: String)

internal val READ_TYPES: List<ReadType> = listOf(
    ReadType("Int", "Int"),
    ReadType("kotlin.Int", "Int"),
    ReadType("String?", "String?"),
    ReadType("List<String>", "List<String>"),
    ReadType("kotlin.collections.List< kotlin.String >", "List<String>"),
    ReadType("Map<String, List<Int>>", "Map<String,List<Int>>"),
    ReadType("(String) -> Unit", "(String)->Unit"),
    ReadType("suspend (Int) -> String?", "suspend(Int)->String?"),
    ReadType("kotlinx.coroutines.flow.StateFlow<com.example.HomeUiState>", "StateFlow<HomeUiState>"),
    ReadType("Map.Entry<String, Int>", "Map.Entry<String,Int>"),
    ReadType("Array<out String>", "Array<outString>"),
)

/** Types written over several lines, or with a trailing comma: the same types as the canonical form says. */
internal val SPREAD_TYPES: List<ReadType> = listOf(
    ReadType("Map<\n    String,\n    Int\n>", "Map<String,Int>"),
    ReadType("Map<String, Int,>", "Map<String,Int>"),
    ReadType("(\n    String,\n    Int,\n) -> Unit", "(String,Int)->Unit"),
)

/**
 * A supertype as a class or an object writes it, the canonical form reading should give, and the
 * same without type arguments. The constructor call and a delegation (`by`) are not part of it.
 */
internal class ReadSupertype(val written: String, val canonical: String, val withoutArguments: String)

internal val CLASS_SUPERTYPES: List<ReadSupertype> = listOf(
    ReadSupertype("Base()", "Base", "Base"),
    ReadSupertype("com.example.Base()", "Base", "Base"),
    ReadSupertype("Base<Int>(1, \"a\")", "Base<Int>", "Base"),
    ReadSupertype("Repository<User>", "Repository<User>", "Repository"),
    ReadSupertype("com.example.Repository<com.example.User>", "Repository<User>", "Repository"),
    ReadSupertype("Handler by handler", "Handler", "Handler"),
    ReadSupertype("Callback by Callback.Noop", "Callback", "Callback"),
    ReadSupertype("Comparable<Item>", "Comparable<Item>", "Comparable"),
    ReadSupertype("Exception(\"Not found (404): {id}\")", "Exception", "Exception"),
    ReadSupertype("Base({ it > 0 })", "Base", "Base"),
    ReadSupertype("Base(count >= 0)", "Base", "Base"),
    ReadSupertype("Exception(\"Failed :( <\")", "Exception", "Exception"),
)

internal val INTERFACE_SUPERTYPES: List<ReadSupertype> = listOf(
    ReadSupertype("Base", "Base", "Base"),
    ReadSupertype("Repository<User>", "Repository<User>", "Repository"),
    ReadSupertype("com.example.Repository<com.example.User>", "Repository<User>", "Repository"),
    ReadSupertype("(String) -> Unit", "(String)->Unit", "(String)->Unit"),
    ReadSupertype("Comparable<Item>", "Comparable<Item>", "Comparable"),
)

/** An annotation as written (one line or several), its simple name, a full name it stands for, and whether only a property takes it. */
internal class ReadAnnotation(val text: String, val simpleName: String, val fullName: String, val propertyOnly: Boolean = false)

internal val READ_ANNOTATIONS: List<ReadAnnotation> = listOf(
    ReadAnnotation("@Keep", "Keep", "com.example.annotations.Keep"),
    ReadAnnotation("@Marked", "Marked", "com.example.annotations.Mark"),
    ReadAnnotation("@Composable", "Composable", "androidx.compose.runtime.Composable"),
    ReadAnnotation("@Suppress(\"unused\")", "Suppress", "kotlin.Suppress"),
    ReadAnnotation("@Suppress(\"a\", \"b\",)", "Suppress", "kotlin.Suppress"),
    ReadAnnotation("@com.example.annotations.Keep", "Keep", "com.example.annotations.Keep"),
    ReadAnnotation("@Description(\"class Fake : Base { (\")", "Description", "com.example.reading.Description"),
    ReadAnnotation("@Deprecated(\n    \"class Fake : Base (x)\",\n    level = DeprecationLevel.WARNING,\n)", "Deprecated", "kotlin.Deprecated"),
    ReadAnnotation("@get:JvmName(\"named\")", "JvmName", "kotlin.jvm.JvmName", propertyOnly = true),
)

/** The imports every file writes, in some order: the path the facts hold, and how the line writes it. */
internal val READ_IMPORTS: List<Pair<String, String>> = listOf(
    "com.example.annotations.Keep" to "com.example.annotations.Keep",
    "com.example.annotations.Mark" to "com.example.annotations.Mark as Marked",
    "androidx.compose.runtime.*" to "androidx.compose.runtime.*",
    "kotlinx.coroutines.flow.StateFlow" to "kotlinx.coroutines.flow.StateFlow",
    "com.example.Repository" to "com.example.Repository",
)

/** What may come before a declaration without being part of it. */
internal val LEADING_TRIVIA: List<String> = listOf(
    "/** A declaration. */",
    "/**\n * Holds class Fake : Base (and more).\n *\n * @param x the x\n */",
    "// note: class Fake : Base",
    "/* class Fake : Base { ( */",
    "",
)

private val CLASS_BODY_ONLY_KINDS: Set<DeclarationKindName> =
    setOf(DeclarationKindName.EnumEntry, DeclarationKindName.Init, DeclarationKindName.Constructor)
