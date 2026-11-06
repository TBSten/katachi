package me.tbsten.katachi.konsist.declarations.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.NamePattern

internal enum class DeclarationKindName(val keyword: String) {
    Class("class"),
    Interface("interface"),
    Object("object"),
    Function("fun"),
    Val("val"),
    Var("var"),
    TypeAlias("typealias"),
}

// Declared in the order Kotlin's coding conventions write modifiers, which is the order every rendering prints them in.
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

internal data class NameRule(val written: String, val pattern: NamePattern) {
    val hasWildcard: Boolean get() = pattern.hasWildcard

    val hasStar: Boolean get() = '*' in written

    fun matches(text: String): Boolean = pattern.matches(text)
}

internal data class TypeRule(val written: String, val canonical: NamePattern)

internal data class ParameterRule(val written: String, val name: NameRule, val type: TypeRule)

// byName: written without type arguments, so it matches a supertype whatever its arguments are (`Repository` matches `Repository<User>`).
internal data class SuperclassRule(val written: String, val byName: Boolean, val pattern: NamePattern)

internal data class AnnotationRule(val written: String, val qualified: Boolean, val pattern: NamePattern)

// On a declaration line the declaration may carry no modifier beyond these, `optional` and `bundled`; `public` is never counted as another.
internal data class ModifierSet(
    val modifiers: Set<ModifierName>,
    val annotations: List<AnnotationRule>,
    val optional: List<List<ModifierName>> = emptyList(),
    // The other alternatives of the `or`s `modifiers` were chosen from: `(suspend or inline)` needs one, so a declaration carrying both still matches. Never printed.
    val bundled: Set<ModifierName> = emptySet(),
) {
    val isEmpty: Boolean get() = modifiers.isEmpty() && annotations.isEmpty()

    val alsoAllowed: Set<ModifierName> get() = optional.flatten().toSet() + bundled

    operator fun plus(modifier: ModifierName): ModifierSet = copy(modifiers = modifiers + modifier)

    fun withAnnotations(more: List<AnnotationRule>): ModifierSet =
        copy(annotations = (annotations + more).distinctBy { it.written })

    fun union(other: ModifierSet): ModifierSet = ModifierSet(
        modifiers = modifiers + other.modifiers,
        annotations = (annotations + other.annotations).distinctBy { it.written },
        optional = (optional + other.optional).distinct(),
        bundled = bundled + other.bundled,
    )

    companion object {
        val EMPTY: ModifierSet = ModifierSet(emptySet(), emptyList())
    }
}

internal data class Shape(val modifiers: ModifierSet, val kind: DeclarationKindName)

internal enum class Placement { File, ClassBody }

internal sealed interface DeclarationListEntry {
    val declaredAt: DeclarationSite
}

internal class DeclarationRule(
    val shapes: List<Shape>,
    val name: NameRule,
    // null when the line writes none: the declaration may then have any receiver, or none.
    val receiver: TypeRule?,
    val superclasses: List<SuperclassRule>,
    val propertyType: TypeRule?,
    val parameters: List<ParameterRule>?,
    val returnType: TypeRule?,
    // null when the line has no `{ }`: its declaration may then have no members, as with an empty `{ }`.
    val members: DeclarationListRules?,
    val required: Boolean,
    val prohibited: Boolean,
    val except: List<NameRule>,
    override val declaredAt: DeclarationSite,
) : DeclarationListEntry {
    override fun toString(): String = "DeclarationRule(${renderRule(this)})"
}

internal class AnythingElseRule(
    val alternatives: List<ModifierSet>,
    override val declaredAt: DeclarationSite,
) : DeclarationListEntry {
    override fun toString(): String = "AnythingElseRule(${renderAnythingElse(this)})"
}

// With no entries it allows nothing: a kind no line is written for is denied, not left unchecked.
internal class DeclarationListRules(
    val placement: Placement,
    val entries: List<DeclarationListEntry>,
    val declaredAt: DeclarationSite,
    val withoutBody: Boolean = false,
) {
    val lines: List<DeclarationRule> = entries.filterIsInstance<DeclarationRule>()

    val anythingElse: List<AnythingElseRule> = entries.filterIsInstance<AnythingElseRule>()

    companion object {
        fun membersOfBodilessLine(line: DeclarationRule): DeclarationListRules =
            DeclarationListRules(Placement.ClassBody, emptyList(), line.declaredAt, withoutBody = true)
    }
}

internal class ImportRule(
    val path: NameRule,
    val prohibited: Boolean,
    val except: List<NameRule>,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "ImportRule(${renderImport(this)})"
}

internal sealed interface PackageTarget {
    class Pattern(val rule: NameRule) : PackageTarget

    data object MatchingDirectory : PackageTarget
}

internal class PackageRule(
    val target: PackageTarget,
    val prohibited: Boolean,
    val except: List<NameRule>,
    val declaredAt: DeclarationSite,
) {
    override fun toString(): String = "PackageRule(${renderPackage(this)})"
}

internal class FileRules(
    val packages: List<PackageRule>,
    val imports: List<ImportRule>,
    val declarations: DeclarationListRules,
    val declaredAt: DeclarationSite,
) {
    val isEmpty: Boolean get() = packages.isEmpty() && imports.isEmpty() && declarations.entries.isEmpty()
}
