package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * What is being named by a declaration the DSL rejected.
 *
 * Read it from the caught exception to branch on what kind of name was rejected.
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
    internal val label: String,
    internal val uniquenessRule: String,
    internal val invalidNameHint: String = "Use `title` for a human readable display name.",
) {
    /** A `"...".group { }` declaration. */
    Group(
        label = "group",
        uniquenessRule = "Group names must be unique among the groups declared under the same parent.",
    ),

    /** A `"...".role { }` declaration. */
    Role(
        label = "role",
        uniquenessRule = "Role names must be unique within a group. The same name may be reused " +
            "in a different group.",
    ),

    /** A `capture("...")` in a `layout { }`. */
    Capture(
        label = "capture",
        uniquenessRule = "Capture names must be unique along one path. The same name may be reused " +
            "on a different path of the same role.",
        invalidNameHint = "The name is also the `--arg` name a template takes the value by, " +
            "so it follows the same rule as a role name.",
    ),

    /** The `id` of a `.template(id = "...") { }`. */
    TemplateId(
        label = "template id",
        uniquenessRule = "A template id must be unique within its role. See KatachiDuplicateTemplateIdException.",
        invalidNameHint = "It is also what `--arg template=role.id` selects it by, so it follows " +
            "the same rule as a role name.",
    ),

    /** A `"...".templateSet { }` declaration. */
    TemplateSet(
        label = "template set",
        uniquenessRule = "Template set names must be unique among the groups, roles and template sets " +
            "declared under the same parent.",
        invalidNameHint = "It is also what `--arg template=` selects it by, so it follows the same rule " +
            "as a role name. Use `title` for a human readable display name.",
    ),
}

/**
 * A declared name (group, role, capture, template id or template set) is not a valid identifier.
 *
 * ## Example 1: catch an invalid name
 * ```kt
 * shouldThrow<KatachiInvalidIdentifierException> {
 *     architecture { "use case".group { } }
 * }.name shouldBe "use case"
 * ```
 *
 * @property kind whether the rejected name was a group's, a role's or a capture's.
 * @property name the rejected name, as written.
 * @property declaredAt where it was written.
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
        append(kind.invalidNameHint)
    },
)

/**
 * A name was declared twice in one scope.
 *
 * That is the same group name directly under the same parent, the same role name inside the same
 * group, or a group and a role of the same name side by side: groups and roles share one
 * namespace in every container.
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
 *         "domain".role { }
 *     }
 * }
 * thrown.kind shouldBe DeclarationKind.Role
 * thrown.firstKind shouldBe DeclarationKind.Group
 * ```
 *
 * @property kind whether the rejected declaration was a group's, a role's or a template set's.
 * @property firstKind whether the declaration that took the name first was a group's, a role's or
 *   a template set's. It differs from [kind][KatachiDuplicateDeclarationException.kind] when a
 *   group and a role collided.
 * @property name the duplicated name.
 * @property scope where the two declarations collided, such as `the root of architecture { }`
 *   or `group "domain/user"`.
 * @property firstDeclaredAt where the name was declared the first time.
 * @property declaredAt where the rejected second declaration was written.
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
                "A ${firstKind.label} and a ${kind.label} in $scope would both be referred to as " +
                    "\"$name\". Rename one of them.",
            )
        }
    },
)

/**
 * A `"...".module { }` key was written somewhere other than directly inside `layout { }`.
 *
 * ## Example 1: catch a module key nested inside a directory block
 * ```kt
 * shouldThrow<KatachiModuleOutsideLayoutRootException> {
 *     layout {
 *         "app" { ":core:data".module { } }
 *     }
 * }.modulePath shouldBe ":core:data"
 * ```
 *
 * @property modulePath the module path as it was written, without its `.module { }`.
 * @property declaredAt where it was written.
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
