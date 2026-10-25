package me.tbsten.katachi.konsist.declarations.internal

/*
 * What one Kotlin file holds, as the matching reads it: written down from Konsist once, so that
 * the judging never touches Konsist and can be read (and tested) as plain data.
 *
 * Only what is written in the file is here. An inferred type, an inherited member and a member
 * the compiler generates are not -- `ktFileContent { }` looks at what is written.
 */

/** One `.kt` file: its package, its imports and what is declared directly in it. */
internal class KtFileFacts(
    /** Project relative, `/` separated. */
    val path: String,
    /** `null` when the file declares no package. */
    val packageName: String?,
    val packageLine: Int?,
    val imports: List<ImportFact>,
    /** In the order they are written. */
    val declarations: List<DeclarationFact>,
) {
    override fun toString(): String = "KtFileFacts($path)"
}

/** One import as written: `a.b.C`, or `a.b.*` for a star import. An alias is not part of it. */
internal class ImportFact(val path: String, val line: Int?) {
    override fun toString(): String = "ImportFact($path)"
}

/** A type as written: [written] with its spaces made single, [canonical] as `canonicalType` reads it. */
internal class TypeText(val written: String, val canonical: String) {
    override fun toString(): String = written
}

/** One parameter of a function: its name without backticks, and its type. */
internal class ParameterFact(val name: String, val type: TypeText) {
    override fun toString(): String = "$name: ${type.written}"
}

/**
 * One supertype after the header's `:`: as written (`ViewModel`, `Repository<User>`), without its
 * type arguments for a by-name match, and canonical.
 */
internal class SupertypeFact(val written: String, val withoutArguments: String, val canonical: String) {
    override fun toString(): String = written
}

/** One annotation: its simple name, and every full name it may stand for in this file. */
internal class AnnotationFact(val simpleName: String, val qualifiedCandidates: Set<String>) {
    override fun toString(): String = "@$simpleName"
}

/** One declaration of a file or of a class body. */
internal class DeclarationFact(
    val kind: DeclarationKindName,
    /** Without backticks; without the receiver of an extension. */
    val name: String,
    /**
     * The receiver of an extension function or property as written (`String`, `List<T>`), or
     * `null`. Shown in a report only: a line's name is matched without it.
     */
    val receiver: String? = null,
    /** The modifiers written on it, and no others: no implicit `public`. */
    val modifiers: Set<ModifierName>,
    val annotations: List<AnnotationFact>,
    /** Empty for anything but a class, an interface or an object. */
    val supertypes: List<SupertypeFact>,
    /** The type written on a property, or `null` when it is inferred (or this is no property). */
    val propertyType: TypeText?,
    /** The parameters of a function, in order; `null` for anything but a function. */
    val parameters: List<ParameterFact>?,
    /**
     * The return type of a function as the matching reads it: the one written, or `Unit` for a
     * block body (or no body) that writes none. `null` for an expression body that writes none.
     */
    val returnType: TypeText?,
    /** Whether [returnType] was written rather than filled in with `Unit`. */
    val returnTypeWritten: Boolean,
    val line: Int?,
    /** The members of a class, an interface or an object, in order; `null` for anything else. */
    val members: List<DeclarationFact>?,
    /** Whether this is a property written in the primary constructor of its class. */
    val inConstructor: Boolean = false,
) {
    override fun toString(): String = "DeclarationFact(${renderFact(this)})"
}
