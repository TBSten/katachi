package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.KatachiInternalException
import kotlin.reflect.KClass

/** A line break rendered so that a message stays one line, whatever was written. */
private fun oneLine(value: String): String = value.replace("\r", "\\r").replace("\n", "\\n")

/** A class as a message names it, qualified when it can be. */
private fun nameOf(type: KClass<*>): String = type.qualifiedName ?: type.java.name

/**
 * A constraint was given a name katachi cannot print on one line.
 *
 * @property name the rejected name, as written, with its line breaks escaped.
 * @property declaredAt where the constraint was written.
 *
 * ## Example 1: catch a constraint name holding a line break
 * ```kt
 * shouldThrow<KatachiConstraintNameException> {
 *     architecture {
 *         "UseCase" {
 *             constraint("has an invoke\nfunction") { emptyList() }
 *             layout { "useCase" { } }
 *         }
 *     }
 * }.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 */
public class KatachiConstraintNameException internal constructor(
    /**
     * The rejected name, as written.
     *
     * ## Example 1: read back the name that was rejected
     * ```kt
     * shouldThrow<KatachiConstraintNameException> {
     *     architecture { "UseCase" { constraint("   ") { emptyList() }; layout { } } }
     * }.name shouldBe "   "
     * ```
     */
    public val name: String,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the line that declared the rejected constraint
     * ```kt
     * shouldThrow<KatachiConstraintNameException> {
     *     architecture { "UseCase" { constraint("   ") { emptyList() }; layout { } } }
     * }.declaredAt.lineNumber shouldBeGreaterThan 0
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Invalid constraint name \"${oneLine(name)}\" declared at $declaredAt.")
        appendLine(
            "A constraint name is printed on one line of a report block, so it must hold at " +
                "least one non-whitespace character and no line break.",
        )
        append(
            "Shorten it, or leave the name out and let the declaration site name the " +
                "constraint instead.",
        )
    },
)

/**
 * A role declared a constraint but no `layout { }` to evaluate it over.
 *
 * Nothing would ever run it: a constraint is about the files the layout around it matched, and
 * a role with no layout matches none. Refusing it here, while `architecture { }` is still
 * being evaluated, is what keeps such a definition from looking green.
 *
 * A role whose only layout is a wildcard module key that currently matches nothing is **not**
 * rejected: that is a place waiting to fill up, not a definition that cannot work.
 *
 * @property role the name of the role the constraint was written on.
 * @property declaredAt where the first constraint of that role was written.
 *
 * ## Example 1: catch a constraint that has nothing to be about
 * ```kt
 * shouldThrow<KatachiConstraintWithoutLayoutException> {
 *     architecture {
 *         "domain".group {
 *             "UseCase" { constraint("has an invoke function") { emptyList() } }
 *         }
 *     }
 * }.role shouldBe "UseCase"
 * ```
 */
public class KatachiConstraintWithoutLayoutException internal constructor(
    /**
     * The name of the role the constraint was written on.
     *
     * ## Example 1: read back which role could not hold a constraint
     * ```kt
     * shouldThrow<KatachiConstraintWithoutLayoutException> {
     *     architecture { "UseCase" { constraint { emptyList() } } }
     * }.role shouldBe "UseCase"
     * ```
     */
    public val role: String,
    /**
     * Where the first constraint of that role was written.
     *
     * ## Example 1: jump to the constraint that has nothing to be about
     * ```kt
     * shouldThrow<KatachiConstraintWithoutLayoutException> {
     *     architecture { "UseCase" { constraint { emptyList() } } }
     * }.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Role \"$role\" declares a constraint at $declaredAt but no `layout { }`.")
        appendLine(
            "A constraint is evaluated over the files the layout around it matched, so a role " +
                "that declares no layout has no files for it to be about and it could never run.",
        )
        append(
            "Add a `layout { }` to this role, or move the constraint to the role that owns " +
                "those files.",
        )
    },
)

/**
 * Two checks used the same scratch key for different types.
 *
 * @property key the key, as its `toString()` prints it.
 * @property expected the type the reader asked for.
 * @property actual the type the value already stored under that key has.
 *
 * ## Example 1: report it rather than treating it as a failed check
 * ```kt
 * try {
 *     projectArchitecture.assert(KonsistCheck())
 * } catch (cause: KatachiConstraintMemoTypeException) {
 *     println("scratch key ${cause.key} is used by two checks")
 * }
 * ```
 */
public class KatachiConstraintMemoTypeException internal constructor(
    /**
     * The key, as its `toString()` prints it.
     *
     * ## Example 1: read back which key two checks fought over
     * ```kt
     * shouldThrow<KatachiConstraintMemoTypeException> {
     *     subject.memo("scope", Int::class) { 1 }
     * }.key shouldBe "scope"
     * ```
     */
    public val key: String,
    /**
     * The type the reader asked for.
     *
     * ## Example 1: read back the type that was asked for
     * ```kt
     * shouldThrow<KatachiConstraintMemoTypeException> {
     *     subject.memo("scope", Int::class) { 1 }
     * }.expected shouldBe Int::class
     * ```
     */
    public val expected: KClass<*>,
    /**
     * The type the value already stored under that key has.
     *
     * ## Example 1: read back the type that was already there
     * ```kt
     * shouldThrow<KatachiConstraintMemoTypeException> {
     *     subject.memo("scope", Int::class) { 1 }
     * }.actual shouldBe String::class
     * ```
     */
    public val actual: KClass<*>,
) : KatachiInternalException(
    message = buildString {
        appendLine(
            "Two checks used the scratch key \"${oneLine(key)}\" for different types: " +
                "${nameOf(expected)} was asked for, ${nameOf(actual)} was already stored.",
        )
        appendLine(
            "A scratch key has to be unique to the check that holds it, so that one check " +
                "cannot read back another check's value under the same name.",
        )
        append(
            "If both checks are katachi's, please report it at " +
                "https://github.com/TBSten/katachi/issues.",
        )
    },
)
