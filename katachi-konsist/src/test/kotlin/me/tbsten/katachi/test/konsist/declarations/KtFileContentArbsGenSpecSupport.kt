package me.tbsten.katachi.test.konsist.declarations

import kotlin.random.Random

/*
 * Random cases for the judging properties: an allow list and a source that follows it, made line
 * by line (`KtFileContentArbsSpecSupport.kt` has the model, `KtFileContentArbsTablesSpecSupport.kt`
 * what the imports, packages and types match).
 */

/** The stems of the lines' names: no stem is a prefix of another, nor of `Extra`, `Helper`, `Legacy` or a file name. */
private val STEMS: List<String> = listOf(
    "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India", "Juliet", "Kilo", "Lima", "Mike",
    "November", "Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "Xray", "Yankee", "Zulu",
)

/** The names of the files the cases are written to. */
internal val FILE_NAMES: List<String> = listOf("HomeViewModel", "SettingsScreen", "ProfileRepository", "SearchUseCase", "DetailState")

/** Where a list of declarations is. */
internal enum class GenPlacement { File, ClassBody }

/** What a generated case must hold, for a property that needs it. */
internal data class GenOptions(
    val declarations: Boolean = false,
    /** A `not.` line among the file's declaration lines. */
    val prohibitedDeclaration: Boolean = false,
    val prohibitedImport: Boolean = false,
    /** A `not.` import line with an `.except(...)`. */
    val exceptImport: Boolean = false,
)

/** One case: an allow list and a source that follows it. */
internal fun Random.genCase(options: GenOptions = GenOptions()): GenCase {
    val context = GenContext(this, pick(FILE_NAMES))
    val packageName = pick(CONFORMING_PACKAGES)
    val checkPackage = chance(40)
    val checkImports = options.prohibitedImport || options.exceptImport || chance(50)
    val checkDeclarations = options.declarations || options.prohibitedDeclaration || chance(90) || (!checkPackage && !checkImports)
    val declarationRules = if (checkDeclarations) context.genList(GenPlacement.File, depth = 0, prohibited = options.prohibitedDeclaration) else null
    val declarations = if (declarationRules == null) {
        context.uncheckedDeclarations(GenPlacement.File)
    } else {
        context.sourceOf(declarationRules, GenPlacement.File, depth = 0, container = null)
    }
    val (importLines, imports) = context.importsOf(checkImports, options)
    return GenCase(
        fileName = context.fileName,
        directory = packageName.replace('.', '/'),
        packageName = packageName,
        packageLines = if (checkPackage) packageLinesOf(packageName) else emptyList(),
        importLines = importLines,
        declarationRules = declarationRules,
        imports = imports,
        declarations = declarations,
    )
}

private fun Random.packageLinesOf(packageName: String): List<GenPackageLine> {
    val matching = ALLOWED_PACKAGE_PATTERNS.filter { it in PACKAGE_TABLE.getValue(packageName) }.map { GenPackageLine(it) } +
        GenPackageLine(pattern = null)
    val notMatching = ALLOWED_PACKAGE_PATTERNS.filterNot { it in PACKAGE_TABLE.getValue(packageName) }.map { GenPackageLine(it) }
    val lines = matching.shuffled(this).take(nextInt(1, 3)) + listOfNotNull(pickOrNull(notMatching)?.takeIf { chance(40) })
    val prohibited = if (chance(30)) listOf(GenPackageLine(PROHIBITED_PACKAGE, prohibited = true)) else emptyList()
    return (lines + prohibited).shuffled(this)
}

/** What one case is being made with: the file's name, the stems not taken yet, and the imports its annotations need. */
internal class GenContext(val random: Random, val fileName: String) {
    private var nextStem = 0
    private var counter = 0
    private val fileNameNames = mutableSetOf<String>()
    val annotationImports: MutableSet<String> = linkedSetOf()

    val stemsLeft: Int get() = STEMS.size - nextStem

    fun stem(): String = STEMS[nextStem++]

    fun number(): Int = counter++

    /** A name made from the file's name that this case has not used yet, or `null`. */
    fun fileNameName(candidates: List<String>): String? =
        candidates.filterNot { it in fileNameNames }.shuffled(random).firstOrNull()?.also { fileNameNames += it }

    override fun toString(): String = "GenContext($fileName)"
}

/** The name of a declaration of [kind] made from [stem]: `Alpha` for a type, `alpha` for anything else. */
internal fun nameOf(kind: GenKind, stem: String): String =
    if (kind.isType || kind == GenKind.TypeAlias) stem else stem.replaceFirstChar { it.lowercase() }

internal fun GenContext.genList(placement: GenPlacement, depth: Int, prohibited: Boolean = false): GenList {
    val entries = mutableListOf<GenEntry>()
    val allowed = if (placement == GenPlacement.File) random.nextInt(1, 5) else random.nextInt(0, 4)
    repeat(allowed) { if (stemsLeft > 2) entries += genAllowedLine(placement, depth) }
    if (prohibited || random.chance(25)) entries.add(random.nextInt(entries.size + 1), genProhibitedLine(placement))
    genAnythingElse(placement)?.let { entries.add(random.nextInt(entries.size + 1), it) }
    return GenList(withCompanionNamedCompanion(entries))
}

/** [entries] with the first `companion object` line renamed `Companion` now and then: the name of one written without a name. */
private fun GenContext.withCompanionNamedCompanion(entries: List<GenEntry>): List<GenEntry> {
    val companion = entries.firstOrNull { entry ->
        entry is GenLine && !entry.prohibited && '*' !in entry.name && entry.alternatives.any { "companion" in it.keywords }
    }
    if (companion !is GenLine || !random.chance(50)) return entries
    return entries.map { if (it === companion) companion.copy(name = "Companion") else it }
}

private fun GenContext.genAllowedLine(placement: GenPlacement, depth: Int): GenLine {
    val style = random.nextInt(100)
    if (placement == GenPlacement.File && style < 15) fileNameLine()?.let { return it }
    val bare = style >= 85
    val (kinds, alternatives) = lineShape(placement, bare)
    val stem = stem()
    val name = when {
        bare -> "*"
        style < 60 -> nameOf(kinds.first(), stem)
        else -> nameOf(kinds.first(), stem) + "*"
    }
    val kind = kinds.singleOrNull()
    return GenLine(
        kinds = kinds,
        alternatives = alternatives,
        optional = optionalGroupsOf(kinds, alternatives, placement),
        name = name,
        superclasses = if (!bare && kinds.first().isType && random.chance(30)) superclassPatterns() else emptyList(),
        propertyType = random.pick(TYPE_SAMPLES).pattern.takeIf { !bare && kinds.first().isProperty && random.chance(40) },
        parameters = List(random.nextInt(0, 3)) { random.pick(TYPE_SAMPLES).pattern }
            .takeIf { !bare && kind == GenKind.Function && random.chance(40) },
        anyArgs = !bare && kind == GenKind.Function && random.chance(15),
        returnType = if (!bare && kind == GenKind.Function && random.chance(40)) random.pick(RETURN_SAMPLES).pattern else null,
        // Most lines named in full are required, as before; now and then a line with `*` is too.
        required = !bare && if (style < 60) !random.chance(20) else random.chance(25),
        members = if (!bare && kinds.first().isType && depth < 2 && stemsLeft > 8 && random.chance(35)) {
            genList(GenPlacement.ClassBody, depth + 1)
        } else {
            null
        },
    ).let { line -> if (line.parameters != null) line.copy(anyArgs = false) else line }
}

/**
 * Modifiers a line of [kinds] may make `.optional()` without changing how its declarations are
 * written: no `abstract`, `lateinit`, `const` or `companion`, which take a body, an initializer or a name away.
 */
private fun optionalCandidatesOf(kind: GenKind, placement: GenPlacement): List<String> {
    val member = placement == GenPlacement.ClassBody
    return when (kind) {
        GenKind.Class -> listOf("open", "data") + if (member) listOf("inner") else emptyList()
        GenKind.Interface -> listOf("sealed")
        GenKind.Object -> listOf("data")
        GenKind.Function -> listOf("suspend", "inline", "operator", "infix", "tailrec") + if (member) listOf("override", "open") else emptyList()
        GenKind.Val, GenKind.Var -> if (member) listOf("override", "open") else emptyList()
        GenKind.TypeAlias -> emptyList()
    }
}

/**
 * The `.optional()` values of a line, now and then: a visibility (or two joined with `or`) when
 * the line asks for none, and a modifier of its kind that fits with every alternative.
 */
private fun GenContext.optionalGroupsOf(kinds: List<GenKind>, alternatives: List<GenModifiers>, placement: GenPlacement): List<List<String>> {
    val asked = alternatives.flatMap { it.keywords }.toSet()
    val visibility = if (asked.none { it in VISIBILITIES } && random.chance(30)) {
        random.pick(
            listOf(listOf("internal"), listOf("private"), listOf("internal", "private")) +
                if (placement == GenPlacement.ClassBody) listOf(listOf("protected"), listOf("internal", "protected")) else emptyList(),
        )
    } else {
        null
    }
    val kind = kinds.singleOrNull()
    val specific = kind?.let { optionalCandidatesOf(it, placement) }.orEmpty()
        // A fun interface cannot be sealed. Konsist 0.17.3 reads no supertype of a `companion data object`.
        .filter { candidate -> alternatives.all { fitsWith(candidate, it.keywords) } && !(candidate == "sealed" && "fun" in asked) }
        .filter { candidate -> !(candidate == "data" && "companion" in asked) }
    val modifier = random.pickOrNull(specific)?.takeIf { random.chance(25) }
    return listOfNotNull(visibility, modifier?.let { listOf(it) })
}

/** A line named after the file: `{F}`, `Default{F} : {F}`, `{F}Keys`, `create{F}`. */
private fun GenContext.fileNameLine(): GenLine? {
    val name = fileNameName(listOf("{F}", "Default{F}", "{F}Keys", "create{F}")) ?: return null
    val kind = when (name) {
        "{F}" -> random.pick(listOf(GenKind.Class, GenKind.Interface))
        "Default{F}" -> GenKind.Class
        "{F}Keys" -> GenKind.Object
        else -> GenKind.Function
    }
    return GenLine(
        kinds = listOf(kind),
        alternatives = listOf(GenModifiers(listOfNotNull(random.pickOrNull(listOf("public", "internal")).takeIf { random.chance(40) }))),
        name = name,
        superclasses = if (name == "Default{F}") listOf(FILE_SUPERTYPE.pattern) else emptyList(),
        returnType = if (kind == GenKind.Function && random.chance(50)) UNIT_SAMPLE.pattern else null,
        required = true,
    )
}

private fun GenContext.superclassPatterns(): List<String> {
    val picked = SUPERTYPE_SAMPLES.shuffled(random).take(random.nextInt(1, 3)).map { it.pattern }
    // Repository<User> and Repository<*> both match Repository<User>: changing one would go unnoticed.
    return if (picked.size > 1 && picked.all { it.startsWith("Repository") }) picked.take(1) else picked
}

private val FILE_KINDS: List<GenKind> = GenKind.entries
private val MEMBER_KINDS: List<GenKind> = GenKind.entries - GenKind.TypeAlias
private val KIND_PAIRS: List<List<GenKind>> = listOf(
    listOf(GenKind.Class, GenKind.Interface),
    listOf(GenKind.Class, GenKind.Object),
    listOf(GenKind.Interface, GenKind.Object),
    listOf(GenKind.Val, GenKind.Var),
)

/**
 * The kinds and the modifier alternatives of a line. A [bare] line (named `*`) always asks for
 * something a declaration that writes no modifier lacks, so that it never takes in one a
 * property adds.
 */
private fun GenContext.lineShape(placement: GenPlacement, bare: Boolean): Pair<List<GenKind>, List<GenModifiers>> {
    if (random.chance(15)) return random.pick(KIND_PAIRS) to visibilityAlternatives(placement, bare, mustAsk = bare)
    val kind = random.pick(if (placement == GenPlacement.File) FILE_KINDS else MEMBER_KINDS)
    val specific = random.pickOrNull(specificModifiersOf(kind, placement))?.takeIf { random.chance(35) }
    val annotation = random.pickOrNull(ANNOTATION_SPELLINGS.keys.toList())?.takeIf { random.chance(20) }
    val expectOrActual = random.pick(listOf("expect", "actual")).takeIf { placement == GenPlacement.File && random.chance(4) }
    val visibilities = visibilityAlternatives(placement, bare, mustAsk = bare && specific == null && annotation == null)
    val alternatives = visibilities.map { visibility ->
        GenModifiers(
            keywords = visibility.keywords + listOfNotNull(expectOrActual, specific),
            annotations = listOfNotNull(annotation),
        )
    }
    return listOf(kind) to alternatives
}

private fun GenContext.visibilityAlternatives(placement: GenPlacement, bare: Boolean, mustAsk: Boolean): List<GenModifiers> {
    val roll = random.nextInt(100)
    val asked = when {
        roll < 15 -> listOf("internal")
        roll < 35 -> listOf("private")
        roll < 45 -> listOf("internal", "private")
        roll < 50 && placement == GenPlacement.ClassBody -> listOf("protected")
        roll < 60 && !bare -> listOf("public")
        else -> emptyList()
    }
    if (asked.isEmpty() && mustAsk) return listOf(GenModifiers(listOf("private")))
    if (asked.isEmpty()) return listOf(GenModifiers())
    return asked.map { GenModifiers(listOf(it)) }
}

/** The modifiers other than a visibility that a declaration of [kind] can take at [placement]. */
private fun specificModifiersOf(kind: GenKind, placement: GenPlacement): List<String> {
    val member = placement == GenPlacement.ClassBody
    return when (kind) {
        GenKind.Class -> listOf("data", "sealed", "enum", "value", "annotation", "abstract", "open") + if (member) listOf("inner") else emptyList()
        GenKind.Interface -> listOf("sealed", "fun")
        GenKind.Object -> listOf("data") + if (member) listOf("companion") else emptyList()
        GenKind.Function -> listOf("suspend", "inline", "operator", "infix", "tailrec") +
            if (member) listOf("override", "open", "abstract") else emptyList()
        GenKind.Val -> listOf("const") + if (member) listOf("override", "open", "abstract") else emptyList()
        GenKind.Var -> listOf("lateinit") + if (member) listOf("override", "open") else emptyList()
        GenKind.TypeAlias -> emptyList()
    }
}

/** A `not.` line, named only with a name no other declaration of the cases takes: `Legacy*`, `legacy*`, `*Deprecated`. */
private fun GenContext.genProhibitedLine(placement: GenPlacement): GenLine {
    val kind = random.pick(if (placement == GenPlacement.File) FILE_KINDS else MEMBER_KINDS)
    val name = if (random.chance(30)) "*Deprecated" else nameOf(kind, "Legacy") + "*"
    val visibility = random.pickOrNull(listOf("internal", "private")).takeIf { random.chance(30) }
    // Now and then one name is given back: `Legacy*` but `LegacyKept`.
    val except = listOf(name.replace("*", if (kind.isType || kind == GenKind.TypeAlias) "Kept" else "kept")).takeIf { random.chance(40) }.orEmpty()
    return GenLine(kinds = listOf(kind), alternatives = listOf(GenModifiers(listOfNotNull(visibility))), name = name, prohibited = true, except = except)
}

private fun GenContext.genAnythingElse(placement: GenPlacement): GenAnythingElse? {
    val alternatives = when (random.nextInt(100)) {
        in 0..54 -> return null
        in 55..69 -> listOf(GenModifiers(listOf("private")))
        in 70..76 -> listOf(GenModifiers(listOf("internal")))
        in 77..83 -> listOf(GenModifiers(listOf("internal")), GenModifiers(listOf("private")))
        in 84..89 -> listOf(GenModifiers(annotations = listOf("Keep")))
        in 90..92 -> if (placement == GenPlacement.ClassBody) listOf(GenModifiers(listOf("protected"))) else listOf(GenModifiers(listOf("private")))
        in 93..96 -> listOf(GenModifiers())
        else -> listOf(GenModifiers(listOf("public")))
    }
    return GenAnythingElse(alternatives)
}
