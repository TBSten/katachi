package me.tbsten.katachi.test.konsist.declarations

import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.DeclarationVocabulary
import me.tbsten.katachi.konsist.declarations.FunctionKind
import me.tbsten.katachi.konsist.declarations.FunctionLine
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.Modifiers
import me.tbsten.katachi.konsist.declarations.PropertyKind
import me.tbsten.katachi.konsist.declarations.PropertyLine
import me.tbsten.katachi.konsist.declarations.TypeAliasKind
import me.tbsten.katachi.konsist.declarations.TypeAliasLine
import me.tbsten.katachi.konsist.declarations.TypeKind
import me.tbsten.katachi.konsist.declarations.TypeLine

/*
 * The model the judging properties (plan P1) generate: an allow list of `ktFileContent { }` and a
 * Kotlin source that follows it, made together line by line, so that each declaration of the
 * source knows the line it was written for.
 *
 * Names and types may hold `{F}`, the file's name: the block fills it in with its own `fileName`
 * when it runs, the source with the name of the file it is written to.
 *
 * Names are kept apart so that the properties can say what a change does without judging it
 * themselves. Every line takes a stem of its own (`Alpha`, `bravo`), and the stems are no prefix
 * of each other; `Extra`, `Helper`, `Free` and `Legacy` (or `*Deprecated`) are left to the
 * declarations a property adds, to the ones only `allow(anythingElse)` allows, to the ones nothing looks
 * at, and to the `not.` lines.
 */

/** A kind a line names and a source declares. */
internal enum class GenKind(val keyword: String) {
    Class("class"),
    Interface("interface"),
    Object("object"),
    Function("fun"),
    Val("val"),
    Var("var"),
    TypeAlias("typealias"),
    ;

    val isType: Boolean get() = this == Class || this == Interface || this == Object

    val isProperty: Boolean get() = this == Val || this == Var
}

/** One alternative of modifiers and annotations, as a line or an `allow(..., anythingElse)` asks for it. */
internal data class GenModifiers(val keywords: List<String> = emptyList(), val annotations: List<String> = emptyList()) {
    val isEmpty: Boolean get() = keywords.isEmpty() && annotations.isEmpty()

    override fun toString(): String = (annotations.map { "@$it" } + keywords).joinToString(" ").ifEmpty { "(none)" }
}

/** Something a declaration list holds, in the order it is written. */
internal sealed interface GenEntry

/** One declaration line of the allow list. */
internal data class GenLine(
    val kinds: List<GenKind>,
    /** One or more; a single empty one is a line with no modifier written. */
    val alternatives: List<GenModifiers>,
    val name: String,
    val superclasses: List<String> = emptyList(),
    val propertyType: String? = null,
    /** `null`: not written. */
    val parameters: List<String>? = null,
    val anyArgs: Boolean = false,
    val returnType: String? = null,
    val optional: Boolean = false,
    val prohibited: Boolean = false,
    /** The names a `not.` line gives back. */
    val except: List<String> = emptyList(),
    /** The `{ }` of a class line; `null`: none, the members are not looked at. */
    val members: GenList? = null,
) : GenEntry {
    val isRequired: Boolean get() = !prohibited && !optional && '*' !in name

    /** A line named `*`, which may match a declaration of any name. */
    val isBareWildcard: Boolean get() = name == "*"
}

/** `allow(..., anythingElse)` with the alternatives written in front of it; a single empty one is `allow(anythingElse)`. */
internal data class GenAnythingElse(val alternatives: List<GenModifiers>) : GenEntry {
    /** Whether it allows a declaration that writes no modifier and no annotation. */
    val allowsPlain: Boolean get() = alternatives.any { it.annotations.isEmpty() && (it.keywords.isEmpty() || it.keywords == listOf("public")) }
}

/** The lines of the file, or of the members of a class line. */
internal data class GenList(val entries: List<GenEntry>) {
    val lines: List<GenLine> get() = entries.filterIsInstance<GenLine>()

    val anythingElse: List<GenAnythingElse> get() = entries.filterIsInstance<GenAnythingElse>()

    /** Whether a declaration no line names can still be allowed here some other way than by the line it was made for. */
    val allowsOtherwise: Boolean get() = anythingElse.isNotEmpty() || lines.any { it.isBareWildcard && !it.prohibited }
}

/** How a function's body or a property's initializer is written. */
internal enum class GenBody { Block, Expression, None }

/** One declaration as the source writes it. */
internal data class SrcDecl(
    val kind: GenKind,
    /** As Kotlin reads it, `{F}` filled in. */
    val name: String,
    /** The keywords in the order written. */
    val modifiers: List<String> = emptyList(),
    /** Each as written after its `@`: `Keep`, `com.example.annotations.Keep`. */
    val annotations: List<String> = emptyList(),
    /** As written, constructor calls included. */
    val supertypes: List<String> = emptyList(),
    val propertyType: String? = null,
    val parameters: List<String> = emptyList(),
    val returnType: String? = null,
    val body: GenBody = GenBody.Block,
    /** The members of a class, an interface or an object; `null` when it is something else. */
    val members: List<SrcDecl>? = null,
    /** A property written in the primary constructor of its class. */
    val inConstructor: Boolean = false,
    /** `false` for `companion object` written without a name. */
    val nameWritten: Boolean = true,
    /** The index in the governing list's [GenList.lines] of the line it was made for; `null` when no line was. */
    val origin: Int? = null,
)

/** One package line: a pattern, or `matchingDirectory` when [pattern] is `null`. */
internal data class GenPackageLine(val pattern: String?, val prohibited: Boolean = false)

/** One import line. */
internal data class GenImportLine(
    val pattern: String,
    val prohibited: Boolean = false,
    val optional: Boolean = false,
    val except: List<String> = emptyList(),
) {
    val isRequired: Boolean get() = !prohibited && !optional && '*' !in pattern
}

/** One import as written: `a.b.C`, `a.b.*`, or with an alias. */
internal data class GenImport(val path: String, val alias: String? = null)

/** An allow list of one file and a source of that file that follows it. */
internal data class GenCase(
    val fileName: String,
    /** The directories under `src/main/kotlin/`, `/` separated. */
    val directory: String,
    val packageName: String?,
    val packageLines: List<GenPackageLine> = emptyList(),
    val importLines: List<GenImportLine> = emptyList(),
    /** `null`: no declaration line is written, so the declarations are not looked at. */
    val declarationRules: GenList? = null,
    val imports: List<GenImport> = emptyList(),
    val declarations: List<SrcDecl> = emptyList(),
) {
    val path: String get() = "src/main/kotlin/" + (if (directory.isEmpty()) "" else "$directory/") + "$fileName.kt"
}

/** [this] with `{F}` made [fileName]. */
internal fun String.fillIn(fileName: String): String = replace("{F}", fileName)

/** Writes the allow list of [case]: package, imports and declarations, in the order Kotlin writes them. */
internal fun KtFileContentScope.writeCase(case: GenCase) {
    for (line in case.packageLines) {
        when {
            line.pattern == null -> `package`(matchingDirectory)
            line.prohibited -> not.`package`(line.pattern)
            else -> `package`(line.pattern)
        }
    }
    for (line in case.importLines) {
        val written = if (line.prohibited) not.`import`(line.pattern) else `import`(line.pattern)
        if (line.optional) written.optional()
        if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
    }
    case.declarationRules?.let { writeList(it) }
}

/** Writes the lines of [list] into this file or class body. */
internal fun DeclarationListScope.writeList(list: GenList) {
    for (entry in list.entries) {
        when (entry) {
            is GenLine -> writeLine(entry)
            is GenAnythingElse -> allowAnythingElse(this, modifiersOf(this, entry.alternatives))
        }
    }
}

private fun DeclarationListScope.writeLine(line: GenLine) {
    val target: DeclarationAllow = if (line.prohibited) not else this
    val modifiers = modifiersOf(this, line.alternatives)
    val name = line.name.fillIn(fileName)
    val body: (ClassBodyScope.() -> Unit)? = line.members?.let { members -> { writeList(members) } }
    val kind = line.kinds.first()
    when {
        kind.isType -> {
            val funInterface = line.kinds == listOf(GenKind.Interface) && line.alternatives.any { "fun" in it.keywords }
            val types: TypeKind = line.kinds.map { typeKind(it) }.reduce(TypeKind::or)
            val superclasses = line.superclasses.map { it.fillIn(fileName) }
            val lineBody = body.takeIf { superclasses.isEmpty() }
            val written = if (funInterface) {
                allowFunInterface(target, modifiers, `fun`, types, name, lineBody)
            } else {
                allowType(target, modifiers, types, name, lineBody)
            }
            if (superclasses.isNotEmpty()) written.superclass(*superclasses.toTypedArray(), body = body)
            if (line.optional) written.optional()
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
        kind == GenKind.Function -> {
            var written = allowFunction(target, modifiers, `fun`, name)
            line.parameters?.let { types -> written = written(*types.map { it.fillIn(fileName) }.toTypedArray()) }
            if (line.anyArgs) written = written(anyArgs())
            line.returnType?.let { written = written.returns(it.fillIn(fileName)) }
            if (line.optional) written.optional()
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
        kind == GenKind.TypeAlias -> {
            val written = allowTypeAlias(target, modifiers, `typealias`, name)
            if (line.optional) written.optional()
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
        else -> {
            val properties: PropertyKind = line.kinds.map { if (it == GenKind.Val) `val` else `var` }.reduce(PropertyKind::or)
            val written = allowProperty(target, modifiers, properties, name, line.propertyType?.fillIn(fileName))
            if (line.optional) written.optional()
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
    }
}

private fun DeclarationVocabulary.typeKind(kind: GenKind): TypeKind = when (kind) {
    GenKind.Class -> `class`
    GenKind.Interface -> `interface`
    GenKind.Object -> `object`
    else -> error("not a type kind: $kind")
}

/**
 * [alternatives] as the values `allow(...)` takes in front of the kind: the modifiers and
 * annotations every alternative asks for, one value each, and what tells the alternatives apart
 * -- one modifier or annotation each -- as one value joined with `or`. `fun` is left to the fun
 * interface overload.
 */
private fun modifiersOf(vocabulary: DeclarationVocabulary, alternatives: List<GenModifiers>): List<Modifiers> {
    val tokens = alternatives.map { alternative ->
        alternative.annotations.map { "@$it" } + alternative.keywords.filter { it != "fun" }
    }
    val common = tokens.first().filter { token -> tokens.all { token in it } }
    val differing = tokens.map { it - common.toSet() }.distinct()
    val group = when {
        differing.all { it.isEmpty() } -> null
        differing.all { it.size == 1 } -> differing.map { vocabulary.token(it.single()) }.reduce(Modifiers::or)
        else -> error("$alternatives cannot be written as the values of allow(...)")
    }
    val written = common.map { vocabulary.token(it) }
    // A visibility comes first among the alternatives the generator makes; keep it in front.
    return listOfNotNull(group) + written
}

private fun DeclarationVocabulary.token(token: String): Modifiers =
    if (token.startsWith("@")) annotated(token.removePrefix("@")) else modifier(token)

private fun allowType(on: DeclarationAllow, m: List<Modifiers>, kind: TypeKind, name: String, body: (ClassBodyScope.() -> Unit)?): TypeLine =
    when (m.size) {
        0 -> on.allow(kind, name, body)
        1 -> on.allow(m[0], kind, name, body)
        2 -> on.allow(m[0], m[1], kind, name, body)
        3 -> on.allow(m[0], m[1], m[2], kind, name, body)
        4 -> on.allow(m[0], m[1], m[2], m[3], kind, name, body)
        else -> error("more modifiers than the generator makes: $m")
    }

private fun allowFunInterface(
    on: DeclarationAllow,
    m: List<Modifiers>,
    fn: FunctionKind,
    kind: TypeKind,
    name: String,
    body: (ClassBodyScope.() -> Unit)?,
): TypeLine = when (m.size) {
    0 -> on.allow(fn, kind, name, body)
    1 -> on.allow(m[0], fn, kind, name, body)
    2 -> on.allow(m[0], m[1], fn, kind, name, body)
    3 -> on.allow(m[0], m[1], m[2], fn, kind, name, body)
    4 -> on.allow(m[0], m[1], m[2], m[3], fn, kind, name, body)
    else -> error("more modifiers than the generator makes: $m")
}

private fun allowFunction(on: DeclarationAllow, m: List<Modifiers>, kind: FunctionKind, name: String): FunctionLine = when (m.size) {
    0 -> on.allow(kind, name)
    1 -> on.allow(m[0], kind, name)
    2 -> on.allow(m[0], m[1], kind, name)
    3 -> on.allow(m[0], m[1], m[2], kind, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, name)
    else -> error("more modifiers than the generator makes: $m")
}

private fun allowTypeAlias(on: DeclarationAllow, m: List<Modifiers>, kind: TypeAliasKind, name: String): TypeAliasLine = when (m.size) {
    0 -> on.allow(kind, name)
    1 -> on.allow(m[0], kind, name)
    2 -> on.allow(m[0], m[1], kind, name)
    3 -> on.allow(m[0], m[1], m[2], kind, name)
    4 -> on.allow(m[0], m[1], m[2], m[3], kind, name)
    else -> error("more modifiers than the generator makes: $m")
}

private fun allowProperty(on: DeclarationAllow, m: List<Modifiers>, kind: PropertyKind, name: String, type: String?): PropertyLine =
    when (m.size) {
        0 -> on.allow(kind, name, type)
        1 -> on.allow(m[0], kind, name, type)
        2 -> on.allow(m[0], m[1], kind, name, type)
        3 -> on.allow(m[0], m[1], m[2], kind, name, type)
        4 -> on.allow(m[0], m[1], m[2], m[3], kind, name, type)
        else -> error("more modifiers than the generator makes: $m")
    }

private fun DeclarationListScope.allowAnythingElse(on: DeclarationAllow, m: List<Modifiers>) {
    when (m.size) {
        0 -> on.allow(anythingElse)
        1 -> on.allow(m[0], anythingElse)
        2 -> on.allow(m[0], m[1], anythingElse)
        3 -> on.allow(m[0], m[1], m[2], anythingElse)
        4 -> on.allow(m[0], m[1], m[2], m[3], anythingElse)
        else -> error("more modifiers than the generator makes: $m")
    }
}

/** The modifier [keyword] names. */
internal fun DeclarationVocabulary.modifier(keyword: String): Modifiers = when (keyword) {
    "public" -> public
    "internal" -> internal
    "protected" -> protected
    "private" -> private
    "data" -> data
    "sealed" -> sealed
    "enum" -> enum
    "value" -> value
    "annotation" -> annotation
    "abstract" -> abstract
    "open" -> open
    "inner" -> inner
    "companion" -> companion
    "override" -> override
    "operator" -> operator
    "infix" -> infix
    "suspend" -> suspend
    "inline" -> inline
    "tailrec" -> tailrec
    "external" -> external
    "const" -> const
    "lateinit" -> lateinit
    "expect" -> expect
    "actual" -> actual
    else -> error("no such modifier: $keyword")
}
