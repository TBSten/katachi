package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.NamePattern

/*
 * The rules one evaluation of a `ktFileContent { }` block wrote for one file: what the matching
 * reads, and nothing else. Immutable once built.
 */

/** The kinds of declaration a line can name, with the keyword Kotlin spells each with. */
internal enum class DeclarationKindName(val keyword: String) {
    Class("class"),
    Interface("interface"),
    Object("object"),
    Function("fun"),
    Val("val"),
    Var("var"),
    TypeAlias("typealias"),
}

/**
 * The modifiers a line can ask for, declared in the order Kotlin's coding conventions write them,
 * which is the order every rendering prints them in.
 */
internal enum class ModifierName(val keyword: String) {
    Public("public"),
    Protected("protected"),
    Private("private"),
    Internal("internal"),
    Expect("expect"),
    Actual("actual"),
    Abstract("abstract"),
    Open("open"),
    Sealed("sealed"),
    Const("const"),
    External("external"),
    Override("override"),
    Lateinit("lateinit"),
    Tailrec("tailrec"),
    Suspend("suspend"),
    Inner("inner"),
    Enum("enum"),
    Annotation("annotation"),
    Fun("fun"),
    Companion("companion"),
    Inline("inline"),
    Value("value"),
    Infix("infix"),
    Operator("operator"),
    Data("data"),
    ;

    // Named, because the default name `Companion` is taken by the entry above.
    companion object Groups {
        val VISIBILITIES: Set<ModifierName> = setOf(Public, Protected, Private, Internal)
    }
}

/** One name or path pattern: [written] as it was written (after `fileName` and the like were filled in). */
internal data class NameRule(val written: String, val pattern: NamePattern) {
    val hasWildcard: Boolean get() = pattern.hasWildcard

    /** Whether [written] holds a `*`: a line that matches names it was never written for. */
    val hasStar: Boolean get() = '*' in written

    fun matches(text: String): Boolean = pattern.matches(text)
}

/** A type as written, and the pattern its canonical form (see `canonicalType`) is matched with. */
internal data class TypeRule(val written: String, val canonical: NamePattern)

/**
 * One `.superclass("...")`. [byName] when it was written without type arguments, so that it
 * matches a supertype whatever its arguments are (`Repository` matches `Repository<User>`).
 */
internal data class SuperclassRule(val written: String, val byName: Boolean, val pattern: NamePattern)

/** One name of `annotated(...)`. [qualified] when it holds a `.` and is matched as a full name. */
internal data class AnnotationRule(val written: String, val qualified: Boolean, val pattern: NamePattern)

/** One set of modifiers and annotations that all have to be on a declaration. */
internal data class ModifierSet(val modifiers: Set<ModifierName>, val annotations: List<AnnotationRule>) {
    val isEmpty: Boolean get() = modifiers.isEmpty() && annotations.isEmpty()

    operator fun plus(modifier: ModifierName): ModifierSet = copy(modifiers = modifiers + modifier)

    /** This set with [more] added, each annotation once, in the order first written. */
    fun withAnnotations(more: List<AnnotationRule>): ModifierSet =
        copy(annotations = (annotations + more).distinctBy { it.written })

    /** Everything this set and [other] ask for together. */
    fun union(other: ModifierSet): ModifierSet =
        ModifierSet(modifiers + other.modifiers, (annotations + other.annotations).distinctBy { it.written })

    companion object {
        val EMPTY: ModifierSet = ModifierSet(emptySet(), emptyList())
    }
}

/** One candidate of a line: a set of modifiers on one kind. Combinations no declaration can have are already gone. */
internal data class Shape(val modifiers: ModifierSet, val kind: DeclarationKindName)

/** Where a list of declarations sits: directly in the file, or inside a class's `{ }`. */
internal enum class Placement { File, ClassBody }

/** A line of a declaration list: a declaration line or an `anythingElse()`, in the order they were written. */
internal sealed interface DeclarationListEntry {
    val declaredAt: DeclarationSite
}

/** One declaration line, allowed or (with `not.`) prohibited. */
internal class DeclarationRule(
    /** One or more. */
    val shapes: List<Shape>,
    val name: NameRule,
    /** Empty means not asked about. Every one of them has to be among the declaration's supertypes. */
    val superclasses: List<SuperclassRule>,
    /** `null` means not asked about. */
    val propertyType: TypeRule?,
    /** `null` means not asked about (not written, or `anyArgs()`); empty means no parameter. */
    val parameters: List<TypeRule>?,
    /** `null` means not asked about. */
    val returnType: TypeRule?,
    /** `null` when the line has no `{ }`, so its members are not looked at. */
    val members: DeclarationListRules?,
    /** `!prohibited && !optional() && !name.hasWildcard`. */
    val required: Boolean,
    val prohibited: Boolean,
    val except: List<NameRule>,
    override val declaredAt: DeclarationSite,
) : DeclarationListEntry {
    override fun toString(): String = "DeclarationRule(${renderRule(this)})"
}

/** One `anythingElse()`: any declaration matching one of [alternatives] is allowed. */
internal class AnythingElseRule(
    /** A single empty set means anything at all. */
    val alternatives: List<ModifierSet>,
    override val declaredAt: DeclarationSite,
) : DeclarationListEntry {
    override fun toString(): String = "AnythingElseRule(${renderAnythingElse(this)})"
}

/** The allow list of the declarations directly in a file, or of the members of one class. */
internal class DeclarationListRules(
    val placement: Placement,
    /** Every line, in the order it was written. */
    val entries: List<DeclarationListEntry>,
    /** The `ktFileContent { }` call for a file; the class line for a class's members. */
    val declaredAt: DeclarationSite,
) {
    /** The declaration lines, in the order they were written. */
    val lines: List<DeclarationRule> = entries.filterIsInstance<DeclarationRule>()

    /** The `anythingElse()` lines, in the order they were written. */
    val anythingElse: List<AnythingElseRule> = entries.filterIsInstance<AnythingElseRule>()
}

/** One import line. Its path is matched with `.` as the separator. */
internal class ImportRule(
    val path: NameRule,
    val required: Boolean,
    val prohibited: Boolean,
    val except: List<NameRule>,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "ImportRule(${renderImport(this)})"
}

/** What a package line asks the file's package to be. */
internal sealed interface PackageTarget {
    /** A path pattern, separated by `.`. */
    class Pattern(val rule: NameRule) : PackageTarget

    /** The directories of the file below its source set's `kotlin/`, joined with `.`. */
    data object MatchingDirectory : PackageTarget
}

/** One package line. */
internal class PackageRule(
    val target: PackageTarget,
    val prohibited: Boolean,
    val except: List<NameRule>,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "PackageRule(${renderPackage(this)})"
}

/** Everything one evaluation of the block wrote, for one file. */
internal class FileRules(
    /** Empty means the package is not looked at. */
    val packages: List<PackageRule>,
    /** Empty means the imports are not looked at. */
    val imports: List<ImportRule>,
    /** `null` means the declarations directly in the file are not looked at. */
    val declarations: DeclarationListRules?,
    /** Where `ktFileContent { }` was called. */
    val declaredAt: DeclarationSite,
) {
    val isEmpty: Boolean get() = packages.isEmpty() && imports.isEmpty() && declarations == null
}
