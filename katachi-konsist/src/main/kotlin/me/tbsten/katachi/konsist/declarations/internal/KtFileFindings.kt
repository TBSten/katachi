package me.tbsten.katachi.konsist.declarations.internal

/*
 * What the matching found wrong with one file, before it is worded: one finding per report block.
 * The judging (`DeclarationJudge.kt`, `ImportPackageJudge.kt`) produces these; `FailureText.kt`
 * words them. Kept apart so that each side can be read without the other.
 */

/** One thing wrong with a file. */
internal sealed interface KtFileFinding

/**
 * A declaration list being judged: the rules of the file's own declarations, or of the members of
 * the classes in [containers] (outermost first; empty for the file).
 */
internal class DeclarationListContext(
    val rules: DeclarationListRules,
    val containers: List<DeclarationFact>,
    /** The lines of [rules] that some declaration of the list matches in full. */
    val satisfied: Set<DeclarationRule>,
) {
    /** The class whose members these are, or `null` for the file. */
    val container: DeclarationFact? get() = containers.lastOrNull()

    /** [name] as the report and the baseline name it: `HomeViewModel.isLoading` for a member. */
    fun qualified(name: String): String = (containers.map { it.name } + name).joinToString(".")

    /** Whether [rule] is required and no declaration of the list matches it. */
    fun isMissing(rule: DeclarationRule): Boolean = rule.required && rule !in satisfied

    override fun toString(): String = "DeclarationListContext(${containers.map { it.name }})"
}

/** A declaration that matches no line, nor any line without a `*` in its name by name alone, nor `allow(..., anythingElse)`. */
internal class UnexpectedDeclaration(
    val fact: DeclarationFact,
    val list: DeclarationListContext,
    /** The required lines no declaration matches and no mismatched declaration claims. */
    val unclaimed: List<DeclarationRule>,
) : KtFileFinding

/** A declaration a `not.` line matches. */
internal class ProhibitedDeclaration(
    val fact: DeclarationFact,
    val rule: DeclarationRule,
    val list: DeclarationListContext,
) : KtFileFinding

/**
 * A declaration whose name an allowed line (one without a `*` in its name) matches, but whose kind, modifiers, annotations,
 * supertypes, type, parameters or return type do not: reported once rather than as one too many
 * and one missing, since there is one thing to fix.
 */
internal class MismatchedDeclaration(
    val fact: DeclarationFact,
    val rule: DeclarationRule,
    val list: DeclarationListContext,
    /** What differs, worded for `Differs in:`. */
    val differences: List<String>,
) : KtFileFinding

/** A `required(...)` line nothing in the list matches. */
internal class MissingDeclaration(
    val rule: DeclarationRule,
    val list: DeclarationListContext,
    /** A declaration of the same name that is allowed for another reason, when there is one. */
    val found: DeclarationFact?,
) : KtFileFinding

/** An import no allowed import line matches. */
internal class UnexpectedImport(
    val import: ImportFact,
    /** The allowed import lines, in the order written. */
    val allowed: List<ImportRule>,
) : KtFileFinding

/** An import a `not.` import line matches. */
internal class ProhibitedImport(val import: ImportFact, val rule: ImportRule) : KtFileFinding

/** A package (or the lack of one) no allowed package line matches. */
internal class UnexpectedPackage(
    /** `null` when the file declares no package. */
    val packageName: String?,
    val line: Int?,
    /** The allowed package lines, in the order written. */
    val allowed: List<PackageRule>,
    /** The first package line, which `Declared at:` points at. */
    val firstRule: PackageRule,
    /** What the file's directory reads as, for `matchingDirectory`; `null` when it cannot be told. */
    val directoryPackage: String?,
) : KtFileFinding

/** A package a `not.` package line matches. */
internal class ProhibitedPackage(val packageName: String, val line: Int?, val rule: PackageRule) : KtFileFinding
