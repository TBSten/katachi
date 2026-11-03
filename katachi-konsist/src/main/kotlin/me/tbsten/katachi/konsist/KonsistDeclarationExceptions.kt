package me.tbsten.katachi.konsist

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

private fun namedFileConstraint(constraintName: String?, declaredAt: DeclarationSite): String =
    constraintName?.let { "\"$it\"" } ?: "declared at $declaredAt"

/**
 * A `konsist { }` block that asked for nothing.
 *
 * A block that queries Konsist and never ends the query with [KonsistScope.must],
 * [KonsistScope.mustNot] or [KonsistScope.mustBeEmpty] rejects nothing, which is
 * indistinguishable from a rule everything satisfies. Since this backend can count its own
 * expectations, it says so instead of returning a green answer nobody asked for.
 *
 * ## Example 1: catch a block that forgot to say what it wants
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKonsistNoExpectationException>()
 * cause.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 *
 * @property role the qualified name of the role whose layout the constraint was written in.
 * @property constraintName what the constraint was called, or `null`.
 * @property declaredAt where the constraint was written.
 */
public class KatachiKonsistNoExpectationException internal constructor(
    /** The qualified name of the role whose layout the constraint was written in. */
    public val role: String,
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val constraintName: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Konsist constraint ${namedFileConstraint(constraintName, declaredAt)} of role " +
                "\"$role\" never called must, mustNot or mustBeEmpty.",
        )
        appendLine(
            "A block that asks for nothing rejects nothing, which reads in a report exactly " +
                "like a rule every file satisfies. Katachi refuses to answer rather than let " +
                "the two look the same.",
        )
        append("End the query at $declaredAt with must { }, mustNot { } or mustBeEmpty().")
    },
)

/**
 * A `konsist { }` constraint that covers files, none of which Konsist can parse.
 *
 * Konsist parses only files whose name ends in `.kt`; `.kts` scripts are skipped. A constraint
 * covering, say, only `build.gradle.kts` would see nothing and pass, so it is reported instead.
 *
 * A constraint covering **no** files at all is a different thing and is not an error: that is
 * a place waiting to fill up, and the layout check already treats it as normal.
 *
 * ## Example 1: catch a rule written over files Konsist cannot read
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKonsistNoKotlinFilesException>()
 * cause.given shouldBe 1
 * ```
 *
 * @property role the qualified name of the role whose layout the constraint was written in.
 * @property constraintName what the constraint was called, or `null`.
 * @property declaredAt where the constraint was written.
 * @property given how many files the constraint covered.
 */
public class KatachiKonsistNoKotlinFilesException internal constructor(
    /** The qualified name of the role whose layout the constraint was written in. */
    public val role: String,
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val constraintName: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
    /** How many files the constraint covered, none of which Konsist parses. */
    public val given: Int,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Konsist constraint ${namedFileConstraint(constraintName, declaredAt)} of role " +
                "\"$role\" covers $given ${if (given == 1) "file" else "files"}, none of them " +
                "a .kt file Konsist can parse.",
        )
        appendLine(
            "Konsist 0.18.1 parses .kt files only, .kts scripts included in what it skips, so " +
                "this block would be asked of an empty scope and pass without looking at " +
                "anything.",
        )
        append(
            "Point the constraint at Kotlin sources, or write it as a plain `fileConstraint { }` " +
                "that reads the files itself.",
        )
    },
)

/**
 * Konsist's own assertion ran inside a `konsist { }` block.
 *
 * [KonsistScope] shadows all twelve of them as compile errors, so reaching this means the
 * shadow was suppressed, or Konsist threw from somewhere katachi does not shadow. Either way
 * the run has no idea which declarations were rejected — Konsist reports them in prose, and a
 * report block here opens with a real path — so the constraint is reported as unanswered
 * rather than as a failure it cannot describe.
 *
 * ## Example 1: catch a suppressed `assertTrue`
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKonsistDirectAssertionException>()
 * cause.spelling shouldBe "assertTrue"
 * ```
 *
 * @property spelling the assertion that ran, or the exception type when Konsist threw it.
 */
public class KatachiKonsistDirectAssertionException internal constructor(
    /**
     * The assertion that ran, or the Konsist exception type when the call is not one katachi
     * shadows.
     */
    public val spelling: String,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("A konsist { } block called Konsist's own $spelling.")
        appendLine(
            "Konsist's assertions throw on the first rejection and describe it in prose, so " +
                "katachi cannot say which declarations were rejected or which files to open — " +
                "and the first line of a report block has to be a path a reader can open.",
        )
        append("Use must { }, mustNot { } or mustBeEmpty() instead.")
    },
    cause = cause,
)

/**
 * A rejected element that cannot say where it is.
 *
 * [KonsistScope.must] and its siblings accept any `KoBaseProvider`, because that is the bound
 * `declarations()` comes back at. Almost everything Konsist hands back also carries
 * `KoPathProvider`; one that does not could only be reported as a violation with no path,
 * which the report format does not have a shape for.
 *
 * ## Example 1: catch a rejection katachi could not point at a file
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * val cause = failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKonsistUnlocatableDeclarationException>()
 * cause.declaration
 * ```
 *
 * @property declaration the simple type name of the element that could not be located.
 */
public class KatachiKonsistUnlocatableDeclarationException internal constructor(
    /** The simple type name of the element that could not be located. */
    public val declaration: String,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("A konsist { } block rejected a $declaration, which carries no file path.")
        appendLine(
            "Every violation katachi reports opens with a path, so an element Konsist cannot " +
                "locate has no block to appear in and reporting it would mean inventing one.",
        )
        append(
            "Narrow the query to declarations that live in a file — classes(), functions(), " +
                "properties() or files — before ending it with must { }.",
        )
    },
)
