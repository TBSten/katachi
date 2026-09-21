package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * What is being named by a declaration the DSL rejected.
 *
 * It is carried by the exception rather than baked into its message so that a caller who
 * catches one can branch on it, and so that a group and a role are worded the same way.
 *
 * ## Example 1: tell a duplicated group from a duplicated role
 * ```kt
 * val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
 *     architecture {
 *         "domain".group { }
 *         "domain".group { }
 *     }
 * }
 * thrown.kind shouldBe DeclarationKind.Group
 * ```
 */
public enum class DeclarationKind(
    /** How this kind is written inside a message, `"group"` or `"role"`. */
    internal val label: String,
    /** The rule the last line of a duplicate declaration message states. */
    internal val uniquenessRule: String,
) {
    /**
     * A `"...".group { }` declaration.
     *
     * ## Example 1: match on a rejected group declaration
     * ```kt
     * val thrown = shouldThrow<KatachiInvalidIdentifierException> {
     *     architecture { "use case".group { } }
     * }
     * thrown.kind shouldBe DeclarationKind.Group
     * ```
     */
    Group(
        label = "group",
        uniquenessRule = "Group names must be unique among the groups declared under the same parent.",
    ),

    /**
     * A `"..." { }` role declaration inside a group.
     *
     * ## Example 1: match on a rejected role declaration
     * ```kt
     * val thrown = shouldThrow<KatachiInvalidIdentifierException> {
     *     architecture { "domain".group { "use case" { } } }
     * }
     * thrown.kind shouldBe DeclarationKind.Role
     * ```
     */
    Role(
        label = "role",
        uniquenessRule = "Role names must be unique within a group. The same name may be reused " +
            "in a different group.",
    ),
}

/**
 * A group name or a role name is not a valid identifier.
 *
 * @property kind whether the rejected name was a group's or a role's.
 * @property name the rejected name, as written.
 * @property declaredAt where it was written.
 *
 * ## Example 1: catch an invalid name
 * ```kt
 * shouldThrow<KatachiInvalidIdentifierException> {
 *     architecture { "use case".group { } }
 * }.name shouldBe "use case"
 * ```
 */
public class KatachiInvalidIdentifierException internal constructor(
    public val kind: DeclarationKind,
    public val name: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Invalid ${kind.label} name \"$name\" declared at $declaredAt.")
        appendLine(
            "Names must match [A-Za-z][A-Za-z0-9_-]*: start with an ASCII letter, " +
                "then ASCII letters, digits, '_' or '-'.",
        )
        append("Use `title` for a human readable display name.")
    },
)

/**
 * A name was taken twice in one scope: the same group name directly under the same parent,
 * the same role name inside the same group, or a group and a role of the same name declared
 * side by side — groups and roles share one namespace in every container.
 *
 * @property kind whether the rejected declaration was a group's or a role's.
 * @property firstKind whether the declaration that took the name first was a group's or a
 *   role's. It differs from [kind] when a group and a role collided, and telling the two
 *   cases apart is what the last line of the message does.
 * @property name the duplicated name.
 * @property scope where the two declarations collided, such as `the root of architecture { }`
 *   or `group "domain/user"`. It is a value the message is built from, not the message.
 * @property firstDeclaredAt where the name was declared the first time.
 * @property declaredAt where the rejected second declaration was written.
 *
 * ## Example 1: catch a group declared twice under the same parent
 * ```kt
 * shouldThrow<KatachiDuplicateDeclarationException> {
 *     architecture {
 *         "domain".group { }
 *         "domain".group { }
 *     }
 * }.name shouldBe "domain"
 * ```
 *
 * ## Example 2: tell a group/role collision at the root from a plain duplicate
 * ```kt
 * val thrown = shouldThrow<KatachiDuplicateDeclarationException> {
 *     architecture {
 *         "domain".group { }
 *         "domain" { }
 *     }
 * }
 * thrown.kind shouldBe DeclarationKind.Role
 * thrown.firstKind shouldBe DeclarationKind.Group
 * ```
 */
public class KatachiDuplicateDeclarationException internal constructor(
    public val kind: DeclarationKind,
    public val firstKind: DeclarationKind,
    public val name: String,
    public val scope: String,
    public val firstDeclaredAt: DeclarationSite,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Duplicate ${kind.label} \"$name\" declared at $declaredAt.")
        if (kind == firstKind) {
            appendLine("It was already declared at $firstDeclaredAt, in $scope.")
            append(kind.uniquenessRule)
        } else {
            appendLine(
                "A ${firstKind.label} of that name was already declared at $firstDeclaredAt, " +
                    "in $scope.",
            )
            append(
                "A group and a role in $scope would both be referred to as \"$name\". " +
                    "Rename one of them, or move the role into a group.",
            )
        }
    },
)

/**
 * A `"...".module { }` key was written somewhere other than directly inside `layout { }`.
 *
 * @property modulePath the module path as it was written, without its `.module { }`.
 * @property declaredAt where it was written.
 *
 * ## Example 1: catch a module key nested inside a directory block
 * ```kt
 * shouldThrow<KatachiModuleOutsideLayoutRootException> {
 *     layout {
 *         "app" { ":core:data".module { } }
 *     }
 * }.modulePath shouldBe ":core:data"
 * ```
 */
public class KatachiModuleOutsideLayoutRootException internal constructor(
    public val modulePath: String,
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = "`$modulePath`.module { } at $declaredAt is not directly inside `layout { }`. A module " +
        "path is resolved to a directory below the project root, so it cannot be " +
        "nested in another directory. Move it up, or write the directory it lives in " +
        "as a plain key.",
)
