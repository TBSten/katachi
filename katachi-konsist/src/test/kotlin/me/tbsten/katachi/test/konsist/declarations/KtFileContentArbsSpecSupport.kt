package me.tbsten.katachi.test.konsist.declarations

import me.tbsten.katachi.konsist.declarations.ClassBodyScope
import me.tbsten.katachi.konsist.declarations.DeclarationAllow
import me.tbsten.katachi.konsist.declarations.DeclarationListScope
import me.tbsten.katachi.konsist.declarations.DeclarationVocabulary
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.PropertyKind
import me.tbsten.katachi.konsist.declarations.TypeKind

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
    /** Written with `required(...)`: a declaration has to match it. */
    val required: Boolean = false,
    val prohibited: Boolean = false,
    /** The names a `not.` line gives back. */
    val except: List<String> = emptyList(),
    /** The `{ }` of a class line; `null`: none, the members are not looked at. */
    val members: GenList? = null,
) : GenEntry {
    val isRequired: Boolean get() = !prohibited && required

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
    /** The parameter written `q<index>` rather than `p<index>`: a name no line's pattern but `*` matches. */
    val renamedParameter: Int? = null,
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
    val except: List<String> = emptyList(),
)

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
        if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
    }
    case.declarationRules?.let { writeList(it) }
}

/** Writes the lines of [list] into this file or class body. */
internal fun DeclarationListScope.writeList(list: GenList) {
    for (entry in list.entries) {
        when (entry) {
            is GenLine -> writeLine(entry)
            is GenAnythingElse -> allowAnythingElse(this, modifiersOf(this, entry.alternatives), anythingElse)
        }
    }
}

private fun DeclarationListScope.writeLine(line: GenLine) {
    val target: DeclarationAllow = if (line.prohibited) not else this
    // A required line is written with required(...), which only the scope itself has.
    val required = line.isRequired
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
                if (required) requiredFunInterface(this, modifiers, `fun`, types, name, lineBody)
                else allowFunInterface(target, modifiers, `fun`, types, name, lineBody)
            } else {
                if (required) requiredType(this, modifiers, types, name, lineBody) else allowType(target, modifiers, types, name, lineBody)
            }
            if (superclasses.isNotEmpty()) written.superclass(*superclasses.toTypedArray(), body = body)
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
        kind == GenKind.Function -> {
            var written = if (required) requiredFunction(this, modifiers, `fun`, name) else allowFunction(target, modifiers, `fun`, name)
            line.parameters?.let { types ->
                written = written(*types.mapIndexed { index, type -> parameterPatternOf(index, type.fillIn(fileName)) }.toTypedArray())
            }
            if (line.anyArgs) written = written(anyArgs())
            line.returnType?.let { written = written.returns(it.fillIn(fileName)) }
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
        kind == GenKind.TypeAlias -> {
            val written = if (required) requiredTypeAlias(this, modifiers, `typealias`, name) else allowTypeAlias(target, modifiers, `typealias`, name)
            if (line.except.isNotEmpty()) written.except(*line.except.toTypedArray())
        }
        else -> {
            val properties: PropertyKind = line.kinds.map { if (it == GenKind.Val) `val` else `var` }.reduce(PropertyKind::or)
            val type = line.propertyType?.fillIn(fileName)
            val written = if (required) requiredProperty(this, modifiers, properties, name, type) else allowProperty(target, modifiers, properties, name, type)
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
