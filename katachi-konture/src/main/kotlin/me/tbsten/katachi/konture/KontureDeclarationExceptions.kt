package me.tbsten.katachi.konture

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/** How a message names a constraint: by its name when it has one, by where it was written otherwise. */
private fun namedFileConstraint(constraintName: String?, declaredAt: DeclarationSite): String =
    constraintName?.let { "\"$it\"" } ?: "declared at $declaredAt"

/** The first line of a throwable, the way a report quotes a cause. */
private fun Throwable.firstLine(): String = toString().lineSequence().first()

/**
 * A `konture { }` block that declared no rule.
 *
 * A block that never calls [KontureConstraintScope.files], [KontureConstraintScope.classes],
 * [KontureConstraintScope.functions] or [KontureConstraintScope.properties] has nothing to
 * evaluate, which is indistinguishable in a report from a rule everything satisfies. Since this
 * backend can count its own rules, it says so instead of returning a green answer nobody asked
 * for.
 *
 * ## Example 1: catch a block that forgot to say what it wants
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>()
 * ```
 *
 * @property role the qualified name of the role whose layout the constraint was written in.
 * @property constraintName what the constraint was called, or `null`.
 * @property declaredAt where the constraint was written.
 */
public class KatachiKontureNoExpectationException internal constructor(
    /**
     * The qualified name of the role whose layout the constraint was written in.
     *
     * ## Example 1: read back which role holds the empty block
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>().role shouldBe "domain.UseCase"
     * ```
     */
    public val role: String,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: name the rule that expects nothing
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>().constraintName shouldBe "Internal only"
     * ```
     */
    public val constraintName: String?,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the empty block
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoExpectationException>().declaredAt.fileName shouldBe
     *     "ProjectArchitecture.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Konture constraint ${namedFileConstraint(constraintName, declaredAt)} of role " +
                "\"$role\" declares no rule.",
        )
        appendLine(
            "A block that asks for nothing rejects nothing, which reads in a report exactly " +
                "like a rule every file satisfies. Katachi refuses to answer rather than let " +
                "the two look the same.",
        )
        append(
            "Write files { }, classes { }, functions { } or properties { } inside the block " +
                "at $declaredAt.",
        )
    },
)

/**
 * A `konture { }` constraint that covers files, none of which Konture can parse.
 *
 * Konture 1.0.1 parses a file only when its name ends in `.kt` — `.kts` scripts included,
 * they are not parsed. A constraint covering, say, only `build.gradle.kts` would therefore be
 * handed an empty graph and pass by empty truth, which is the one answer this backend must
 * never give silently.
 *
 * A constraint covering **no** files at all is a different thing and is not an error: that is
 * a place waiting to fill up, and the layout check already treats it as normal.
 *
 * ## Example 1: catch a rule written over files Konture cannot read
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>()
 * ```
 *
 * @property role the qualified name of the role whose layout the constraint was written in.
 * @property constraintName what the constraint was called, or `null`.
 * @property declaredAt where the constraint was written.
 * @property given how many files the constraint covered.
 */
public class KatachiKontureNoKotlinFilesException internal constructor(
    /**
     * The qualified name of the role whose layout the constraint was written in.
     *
     * ## Example 1: read back the role with nothing parseable under it
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>().role shouldBe "build.Script"
     * ```
     */
    public val role: String,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: name the rule that has nothing to parse
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>().constraintName shouldBe "Convention"
     * ```
     */
    public val constraintName: String?,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the rule to rewrite as a plain `fileConstraint { }`
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>().declaredAt.lineNumber
     * ```
     */
    public val declaredAt: DeclarationSite,
    /**
     * How many files the constraint covered, none of which Konture parses.
     *
     * ## Example 1: read back how many files were covered but unreadable
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureNoKotlinFilesException>().given shouldBe 1
     * ```
     */
    public val given: Int,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Konture constraint ${namedFileConstraint(constraintName, declaredAt)} of role " +
                "\"$role\" covers $given ${if (given == 1) "file" else "files"}, none of them " +
                "a .kt file Konture can parse.",
        )
        appendLine(
            "Konture 1.0.1 parses .kt files only, .kts scripts included in what it skips, so " +
                "this block would be asked of an empty graph and pass without looking at " +
                "anything.",
        )
        append(
            "Point the constraint at Kotlin sources, or write it as a plain `fileConstraint { }` " +
                "that reads the files itself.",
        )
    },
)

/**
 * Konture refused one of the rules in a `konture { }` block before evaluating it.
 *
 * Konture checks the shape of a rule when it runs it: a `files { }` without a `should()` is an
 * `AssertionError` rather than an empty result. katachi turns that into this exception so that
 * the report names the constraint, the role and the suite, and so that "refused before it
 * looked" cannot be read as "looked and found nothing".
 *
 * ## Example 1: catch a rule that never said what it wants
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>().suite shouldBe "files"
 * ```
 *
 * @property role the qualified name of the role whose layout the constraint was written in.
 * @property constraintName what the constraint was called, or `null`.
 * @property declaredAt where the constraint was written.
 * @property suite which kind of rule was refused: `files`, `classes`, `functions` or `properties`.
 */
public class KatachiKontureRuleRefusedException internal constructor(
    /**
     * The qualified name of the role whose layout the constraint was written in.
     *
     * ## Example 1: read back which role holds the refused rule
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>().role shouldBe "domain.UseCase"
     * ```
     */
    public val role: String,
    /**
     * What the constraint was called, or `null` when it was declared without a name.
     *
     * ## Example 1: name the constraint holding the refused rule
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>().constraintName shouldBe "Naming"
     * ```
     */
    public val constraintName: String?,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the block to add the missing `should()`
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>().declaredAt.lineNumber
     * ```
     */
    public val declaredAt: DeclarationSite,
    /**
     * Which kind of rule was refused: `files`, `classes`, `functions` or `properties`.
     *
     * ## Example 1: read back which suite Konture refused
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureRuleRefusedException>().suite shouldBe "classes"
     * ```
     */
    public val suite: String,
    cause: AssertionError,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Konture refused the $suite rule of constraint " +
                "${namedFileConstraint(constraintName, declaredAt)} of role \"$role\" " +
                "declared at $declaredAt: ${cause.firstLine()}",
        )
        appendLine("The rule was refused before it looked at a file, so nothing has been checked.")
        append("End the rule with should() followed by at least one assertion.")
    },
    cause = cause,
)

/**
 * An `AssertionError` came out of a `konture { }` block, at one of two moments.
 *
 * While the block was declaring its rules ([suite] is `null`): [KontureDefaultGraphShadows]
 * makes Konture's entry points to its default graph compile errors, so this is a shadow that
 * was suppressed and whose body threw, a `check()` called by hand on a builder, or Konture
 * asserting from somewhere katachi does not shadow.
 *
 * While Konture was evaluating one of the rules ([suite] names which): a predicate such as
 * `satisfy { }` asserted instead of answering `true` or `false`. Konture runs the predicate on
 * each declaration and nothing catches what it throws, so the first declaration it dislikes
 * ends the rule. The one `AssertionError` Konture itself throws at that moment, for a rule with
 * no `should()`, is told apart and reported as [KatachiKontureRuleRefusedException] instead.
 *
 * Either way the run has no idea which declarations were rejected, and a report block here
 * opens with a real path, so the constraint is reported as unanswered rather than as a failure
 * it cannot describe.
 *
 * ## Example 1: catch an assertion that bypassed the shadows
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureDirectAssertionException>()
 * ```
 *
 * @property spelling the exception type that came out of the block.
 * @property suite which kind of rule was being evaluated when the assertion fired (`files`,
 *   `classes`, `functions` or `properties`), or `null` when it fired while the block was still
 *   declaring its rules.
 */
public class KatachiKontureDirectAssertionException internal constructor(
    /**
     * The exception type that came out of the block.
     *
     * ## Example 1: read back what was thrown
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureDirectAssertionException>().spelling shouldBe "AssertionError"
     * ```
     */
    public val spelling: String,
    /**
     * Which kind of rule was being evaluated when the assertion fired, or `null` when it fired
     * while the block was still declaring its rules.
     *
     * ## Example 1: tell a predicate that asserted from a block that asserted
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureDirectAssertionException>().suite shouldBe "classes"
     * ```
     */
    public val suite: String?,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        if (suite == null) {
            appendLine(
                "A konture { } block called Konture's own $spelling" +
                    (cause?.let { ": ${it.firstLine()}" } ?: "."),
            )
            appendLine(
                "Konture's assertions throw on the first violation and describe it in prose, so " +
                    "katachi cannot say which declarations were rejected or which files to open, " +
                    "and the first line of a report block has to be a path a reader can open.",
            )
            append("Use files { }, classes { }, functions { } or properties { } with that() ... should() instead.")
        } else {
            appendLine(
                "A $spelling came out of the $suite rule of a konture { } block while Konture was " +
                    "evaluating it" + (cause?.let { ": ${it.firstLine()}" } ?: "."),
            )
            appendLine(
                "A predicate such as satisfy { } has to answer true or false for each declaration; " +
                    "one that asserts instead throws at the first declaration it dislikes, so " +
                    "katachi can say neither which declarations were rejected nor which files to open.",
            )
            append("Return a Boolean from the predicate instead of asserting inside it.")
        }
    },
    cause = cause,
)

/**
 * A `konture { }` block reached for Konture's default project graph.
 *
 * katachi builds a graph from the covered files alone and never produces the `layout_v2.json`
 * Konture's Gradle plugin writes, so anything that reads `Konture.projectGraph` — `Konture.files()`,
 * `Konture.scope`, `architecture { }` — finds no graph and throws. [KontureDefaultGraphShadows]
 * makes the common spellings compile errors; this is what the rest of them, and a suppressed
 * shadow, turn into. Only a failure that actually went through `Konture.projectGraph` is
 * classified so: a `require` or `check` of the reader's own, or Konture refusing an argument,
 * keeps its own exception.
 *
 * ## Example 1: catch a block that used Konture's own entry point
 * ```kt
 * val failure = shouldThrow<KatachiArchitectureAssertionError> {
 *     projectArchitecture.assert(FileConstraintCheck())
 * }
 * failure.violations.filterIsInstance<UncheckedFileConstraint>()
 *     .single().cause.shouldBeInstanceOf<KatachiKontureDefaultGraphException>()
 * ```
 *
 * @property spelling the entry point that was written, or the exception type Konture threw.
 */
public class KatachiKontureDefaultGraphException internal constructor(
    /**
     * The entry point that was written, or the exception type Konture threw when the call is not
     * one katachi shadows.
     *
     * ## Example 1: read back which entry point was used
     * ```kt
     * cause.shouldBeInstanceOf<KatachiKontureDefaultGraphException>().spelling shouldBe "architecture"
     * ```
     */
    public val spelling: String,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "A konture { } block reached Konture's default project graph through $spelling" +
                (cause?.let { ": ${it.firstLine()}" } ?: "."),
        )
        appendLine(
            "katachi does not write layout_v2.json and builds its graph from the covered files " +
                "alone, so Konture's default graph does not exist here and would not know " +
                "which files the constraint covers.",
        )
        append(
            "Inside konture { } write files { }, classes { }, functions { } or properties { }; " +
                "module rules need Konture's own Gradle plugin in a test module of their own.",
        )
    },
    cause = cause,
)
