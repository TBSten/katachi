package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.KatachiInternalException
import kotlin.reflect.KClass

private fun oneLine(value: String): String = value.replace("\r", "\\r").replace("\n", "\\n")

private fun nameOf(type: KClass<*>): String = type.qualifiedName ?: type.java.name

/**
 * A constraint was given a name katachi cannot print on one line.
 *
 * ## Example 1: catch a constraint name holding a line break
 * ```kt
 * shouldThrow<KatachiFileConstraintNameException> {
 *     architecture {
 *         "UseCase".role {
 *             fileConstraint("has an invoke\nfunction") { emptyList() }
 *             layout { "useCase" { } }
 *         }
 *     }
 * }.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 */
public class KatachiFileConstraintNameException internal constructor(
    /** The rejected name, as written. */
    public val name: String,
    /** Where the constraint was written. */
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
 * A role with no layout matches no files, so the constraint could never run. A role whose layout
 * currently matches nothing is **not** rejected: that is a place waiting to fill up.
 *
 * ## Example 1: catch a constraint that has nothing to be about
 * ```kt
 * shouldThrow<KatachiFileConstraintWithoutLayoutException> {
 *     architecture {
 *         "domain".group {
 *             "UseCase".role { fileConstraint("has an invoke function") { emptyList() } }
 *         }
 *     }
 * }.role shouldBe "UseCase"
 * ```
 */
public class KatachiFileConstraintWithoutLayoutException internal constructor(
    /** The name of the role the constraint was written on. */
    public val role: String,
    /** Where the first constraint of that role was written. */
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
 * A constraint asked for `scope = FileConstraintRange.DirectOnly` somewhere that has no directory
 * of its own.
 *
 * "Directly in" needs one directory. A constraint written on the role spans all its layouts, and
 * one written directly under `layout { }` sits at the project root, which every other block
 * shares.
 *
 * Written on a role, this is thrown while `architecture { }` is evaluated. Written inside a
 * `layout { }`, it is thrown when the layout is evaluated, because `layout { }` is deferred.
 *
 * ## Example 1: catch a direct-only constraint written on the role itself
 * ```kt
 * shouldThrow<KatachiFileConstraintDirectOnlyWithoutDirectoryException> {
 *     architecture {
 *         "util".group {
 *             "Util".role {
 *                 fileConstraint("uses the stdlib only", scope = FileConstraintRange.DirectOnly) { emptyList() }
 *                 layout { "util" { "*.kt".file() } }
 *             }
 *         }
 *     }
 * }.declaredAt.fileName shouldBe "ProjectArchitecture.kt"
 * ```
 */
public class KatachiFileConstraintDirectOnlyWithoutDirectoryException internal constructor(
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val name: String?,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Constraint ${labelOf(name)} declared at $declaredAt asks for scope = DirectOnly, " +
                "but it is not written in a directory block.",
        )
        appendLine(
            "DirectOnly covers the files directly in the surrounding directory. Directly on a " +
                "role or directly under `layout { }` there is no such directory: the role spans " +
                "all its layouts, and the project root is shared.",
        )
        append(
            "Move the constraint into the directory block whose own files it is about " +
                "(e.g. `\"util\" { ... }` or `\"core\" { ... }`), or use the default " +
                "scope, Subtree, to cover everything below.",
        )
    },
)

/**
 * A constraint asked for `scope = FileConstraintRange.DirectOnly` in a block that declares no file
 * directly.
 *
 * Such a constraint could never see a file: everything the block declares sits in a nested
 * directory.
 *
 * Thrown when the layout is evaluated, because `layout { }` is deferred.
 *
 * ## Example 1: catch a direct-only constraint over a directory that only has subdirectories
 * ```kt
 * val arch = architecture {
 *     "util".group {
 *         "Util".role {
 *             layout {
 *                 "util" {
 *                     fileConstraint("uses the stdlib only", scope = FileConstraintRange.DirectOnly) { emptyList() }
 *                     "ksp" { "*.kt".file() }
 *                 }
 *             }
 *         }
 *     }
 * }
 * shouldThrow<KatachiFileConstraintDirectOnlyCoversNothingException> { arch.assert() }
 *     .layoutPath shouldBe "util"
 * ```
 */
public class KatachiFileConstraintDirectOnlyCoversNothingException internal constructor(
    /** What the constraint was called, or `null` when it was declared without a name. */
    public val name: String?,
    /** The directory the constraint was written in, relative to the project root. */
    public val layoutPath: String,
    /** Where the constraint was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Constraint ${labelOf(name)} declared at $declaredAt asks for scope = DirectOnly, " +
                "but its block declares no file directly in $layoutPath.",
        )
        appendLine(
            "DirectOnly covers only the files the block itself declares, not those of its " +
                "nested directories, so this constraint could never see a file.",
        )
        append(
            "Declare the files that sit directly in that directory (e.g. `\"*\".ktFile()` or " +
                "`anyFile()`), move the constraint into the nested block it is about, or use the " +
                "default scope, Subtree, to cover everything below.",
        )
    },
)

private fun labelOf(name: String?): String = if (name == null) "(unnamed)" else "\"${oneLine(name)}\""

/**
 * Two checks used the same scratch key for different types.
 *
 * ## Example 1: report it rather than treating it as a failed check
 * ```kt
 * try {
 *     projectArchitecture.assert(FileConstraintCheck())
 * } catch (cause: KatachiFileConstraintMemoTypeException) {
 *     println("scratch key ${cause.key}: ${cause.expected} asked for, ${cause.actual} stored")
 * }
 * ```
 */
public class KatachiFileConstraintMemoTypeException internal constructor(
    /** The key, as its `toString()` prints it. */
    public val key: String,
    /** The type the reader asked for. */
    public val expected: KClass<*>,
    /** The type the value already stored under that key has. */
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
