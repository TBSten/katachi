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
 * ## Example 1: catch a constraint name holding a line break
 * ```kt
 * shouldThrow<KatachiFileConstraintNameException> {
 *     architecture {
 *         "UseCase" {
 *             fileConstraint("has an invoke\nfunction") { emptyList() }
 *             layout { "useCase" { } }
 *         }
 *     }
 * }.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 *
 * @property name the rejected name, as written, with its line breaks escaped.
 * @property declaredAt where the constraint was written.
 */
public class KatachiFileConstraintNameException internal constructor(
    /**
     * The rejected name, as written.
     *
     * ## Example 1: read back the name that was rejected
     * ```kt
     * shouldThrow<KatachiFileConstraintNameException> {
     *     architecture { "UseCase" { fileConstraint("   ") { emptyList() }; layout { } } }
     * }.name shouldBe "   "
     * ```
     */
    public val name: String,
    /**
     * Where the constraint was written.
     *
     * ## Example 1: jump to the line that declared the rejected constraint
     * ```kt
     * shouldThrow<KatachiFileConstraintNameException> {
     *     architecture { "UseCase" { fileConstraint("   ") { emptyList() }; layout { } } }
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
 * ## Example 1: catch a constraint that has nothing to be about
 * ```kt
 * shouldThrow<KatachiFileConstraintWithoutLayoutException> {
 *     architecture {
 *         "domain".group {
 *             "UseCase" { fileConstraint("has an invoke function") { emptyList() } }
 *         }
 *     }
 * }.role shouldBe "UseCase"
 * ```
 *
 * @property role the name of the role the constraint was written on.
 * @property declaredAt where the first constraint of that role was written.
 */
public class KatachiFileConstraintWithoutLayoutException internal constructor(
    /**
     * The name of the role the constraint was written on.
     *
     * ## Example 1: read back which role could not hold a constraint
     * ```kt
     * shouldThrow<KatachiFileConstraintWithoutLayoutException> {
     *     architecture { "UseCase" { fileConstraint { emptyList() } } }
     * }.role shouldBe "UseCase"
     * ```
     */
    public val role: String,
    /**
     * Where the first constraint of that role was written.
     *
     * ## Example 1: jump to the constraint that has nothing to be about
     * ```kt
     * shouldThrow<KatachiFileConstraintWithoutLayoutException> {
     *     architecture { "UseCase" { fileConstraint { emptyList() } } }
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
 * A constraint asked for `directOnly = true` somewhere that has no directory of its own.
 *
 * "Directly in" needs one directory to be directly in. A role lives wherever its `layout { }`
 * blocks put it, and directly under `layout { }` or inside `":".module { }` the block writes
 * into the project root, which every other block of that `layout { }` shares. Ignoring the
 * flag there would leave a constraint that checks something other than what it says.
 *
 * Written on a role, this is thrown while `architecture { }` is evaluated. Written inside a
 * `layout { }`, it is thrown when the layout is evaluated, because `layout { }` is deferred.
 *
 * ## Example 1: catch a direct-only constraint written on the role itself
 * ```kt
 * shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
 *     architecture {
 *         "util".group {
 *             "Util" {
 *                 fileConstraint("uses the stdlib only", directOnly = true) { emptyList() }
 *                 layout { "util" { "*.kt".file() } }
 *             }
 *         }
 *     }
 * }.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 *
 * @property name what the constraint was called, or `null` when it was declared without a name.
 * @property declaredAt where the constraint was written.
 */
public class KatachiFileConstraintDirectOnlyWithoutDirectoryException internal constructor(
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val name: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Constraint ${labelOf(name)} declared at $declaredAt asks for directOnly = true, " +
                "but it is not written in a directory block.",
        )
        appendLine(
            "directOnly covers the files directly in the surrounding directory. Directly on a " +
                "role, directly under `layout { }`, or in `\":\".module { }` there is no such " +
                "directory: the role spans all its layouts, and the project root is shared.",
        )
        append(
            "Move the constraint into the directory block whose own files it is about " +
                "(e.g. `\"util\" { ... }` or `\":core\".module { ... }`), or drop directOnly " +
                "to cover everything below.",
        )
    },
)

/**
 * A constraint asked for `directOnly = true` in a block that declares no file directly.
 *
 * Such a constraint could never see a file: everything the block declares sits in a nested
 * directory. The file `.module { }` adds by itself, `build.gradle.kts`, does not count, since
 * a constraint written in a module block never covers it. This is a mistake in the definition,
 * not an empty directory, so it is refused rather than left to pass as green.
 *
 * Thrown when the layout is evaluated, because `layout { }` is deferred.
 *
 * ## Example 1: catch a direct-only constraint over a directory that only has subdirectories
 * ```kt
 * val arch = architecture {
 *     "util".group {
 *         "Util" {
 *             layout {
 *                 "util" {
 *                     fileConstraint("uses the stdlib only", directOnly = true) { emptyList() }
 *                     "ksp" { "*.kt".file() }
 *                 }
 *             }
 *         }
 *     }
 * }
 * shouldThrow<KatachiFileConstraintDirectOnlyCoversNothingException> { arch.assert() }
 *     .layoutPath shouldBe "util"
 * ```
 *
 * @property name what the constraint was called, or `null` when it was declared without a name.
 * @property layoutPath the directory the constraint was written in, resolved.
 * @property declaredAt where the constraint was written.
 */
public class KatachiFileConstraintDirectOnlyCoversNothingException internal constructor(
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val name: String?,
    /** The directory the constraint was written in, resolved (`core/domain` for `":core:domain"`). */
    public val layoutPath: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Constraint ${labelOf(name)} declared at $declaredAt asks for directOnly = true, " +
                "but its block declares no file directly in ${layoutPath ?: "its directory"}.",
        )
        appendLine(
            "directOnly covers only the files the block itself declares, not those of its " +
                "nested directories, so this constraint could never see a file.",
        )
        append(
            "Declare the files that sit directly in that directory (e.g. `\"*\".ktFile()` or " +
                "`anyFile()`), move the constraint into the nested block it is about, or drop " +
                "directOnly to cover everything below.",
        )
    },
)

/** A constraint as a message names it: its name in quotes, or a placeholder when it has none. */
private fun labelOf(name: String?): String = if (name == null) "(unnamed)" else "\"${oneLine(name)}\""

/**
 * Two checks used the same scratch key for different types.
 *
 * ## Example 1: report it rather than treating it as a failed check
 * ```kt
 * try {
 *     projectArchitecture.assert(FileConstraintCheck())
 * } catch (cause: KatachiFileConstraintMemoTypeException) {
 *     println("scratch key ${cause.key} is used by two checks")
 * }
 * ```
 *
 * @property key the key, as its `toString()` prints it.
 * @property expected the type the reader asked for.
 * @property actual the type the value already stored under that key has.
 */
public class KatachiFileConstraintMemoTypeException internal constructor(
    /**
     * The key, as its `toString()` prints it.
     *
     * ## Example 1: read back which key two checks fought over
     * ```kt
     * shouldThrow<KatachiFileConstraintMemoTypeException> {
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
     * shouldThrow<KatachiFileConstraintMemoTypeException> {
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
     * shouldThrow<KatachiFileConstraintMemoTypeException> {
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
