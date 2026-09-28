package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.FileConstraintDeclaration
import me.tbsten.katachi.dsl.internal.MetadataBuilder
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.fileConstraintDeclarationOf

/**
 * Receiver of `"RoleName" { }`.
 *
 * The five properties below are metadata under the hood — sugar over [Title], [Summary],
 * [Description], [Documented] and [Examples] — and a processor adds its own words the same
 * way, with `var RoleScope.owner by Owner`. They are members rather than extensions because
 * they are what a definition is mostly made of: an import per word would be a tax on the
 * common case.
 *
 * ## Example 1: set a role's properties
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             title = "Use case"
 *             summary = "A single app-specific behavior that happens on a screen"
 *             example("GetUserUseCase", "Fetches a user")
 *             example("SignOutUseCase")
 *         }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface RoleScope : MetadataScope, FileConstraintScope {
    /**
     * Display name. Defaults to the role name.
     *
     * ## Example 1: separate the identifier from the display name
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { title = "Use case" }
     *     }
     * }
     * arch.allRoles.single()[Title] shouldBe "Use case"
     * ```
     */
    public var title: String

    /**
     * One paragraph describing what this role is for.
     *
     * ## Example 1: set a summary
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { summary = "A single app-specific behavior that happens on a screen" }
     *     }
     * }
     * arch.allRoles.single()[Summary] shouldBe "A single app-specific behavior that happens on a screen"
     * ```
     */
    public var summary: String?

    /**
     * Free-form Markdown about this role: what it is, what it may do, what it may not.
     *
     * Write [summary] for the one line that lands in a table cell, and this for everything
     * that needs more room. The text is kept exactly as written, newlines included, and
     * katachi adds no structure of its own: the headings inside are yours to choose.
     *
     * ## Example 1: give a role a body with sections of its own
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             summary = "A single app-specific behavior that happens on a screen"
     *             description = """
     *                 The UI calls use cases only; it never touches a repository directly.
     *
     *                 ### What it must not do
     *                 - Depending on an Android type
     *             """.trimIndent()
     *         }
     *     }
     * }
     * arch.allRoles.single()[Description].orEmpty().lines().size shouldBe 4
     * ```
     */
    public var description: String?

    /**
     * Set to `false` to keep this role out of the generated documentation. It still takes
     * part in the check.
     *
     * ## Example 1: keep a role out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { documented = false }
     *     }
     * }
     * arch.allRoles.single()[Documented] shouldBe false
     * ```
     */
    public var documented: Boolean

    /**
     * Adds a concrete example. Call it once per example; the examples are kept in the
     * order they were added. [description] may be left out when the name speaks for itself.
     *
     * ## Example 1: add a concrete example, with and without a description
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             example("GetUserUseCase", "Fetches a user")
     *             example("SignOutUseCase")
     *         }
     *     }
     * }
     * ```
     */
    public fun example(name: String, description: String? = null)

    /**
     * Declares where files of this role may live. Call it once per place.
     *
     * The block is stored, not evaluated: see [LayoutDeclaration].
     *
     * ## Example 1: declare where a role's files may live
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" {
     *             layout { }
     *         }
     *     }
     * }
     * ```
     *
     * @featured
     */
    public fun layout(block: LayoutScope.() -> Unit)
}

internal class RoleScopeImpl(private val roleName: String) : RoleScope {
    val metadata = MetadataBuilder()
    val layouts = mutableListOf<LayoutDeclaration>()

    /**
     * Constraints written straight on the role, which cover the union of every `layout { }`
     * it declares. See [FileConstraintScope.fileConstraint].
     */
    val fileConstraints = mutableListOf<FileConstraintDeclaration>()

    override var title: String
        // The fallback lives here and is not written into the metadata: `Title` is absent
        // unless someone wrote it, which is what lets a reader tell "called it that on
        // purpose" from "never said".
        get() = metadata[Title] ?: roleName
        set(value) {
            metadata[Title] = value
        }

    override var summary: String?
        get() = metadata[Summary]
        set(value) {
            metadata[Summary] = value
        }

    override var description: String?
        get() = metadata[Description]
        set(value) {
            metadata[Description] = value
        }

    override var documented: Boolean
        get() = metadata[Documented] ?: true
        set(value) {
            metadata[Documented] = value
        }

    // Accumulating, unlike the four properties above, so it reads the key back and appends.
    // The mechanism stays "one key, one value"; piling up is something this function does.
    override fun example(name: String, description: String?) {
        metadata[Examples] = metadata[Examples].orEmpty() + RoleExample(name, description)
    }

    override fun layout(block: LayoutScope.() -> Unit) {
        layouts += LayoutDeclaration(declaredAt = captureDeclarationSite(), block = block)
    }

    override fun fileConstraint(
        name: String?,
        declaredAt: DeclarationSite,
        scope: FileConstraintRange,
        check: FileConstraint,
    ) {
        // A role has no directory of its own: it lives wherever its `layout { }` blocks put it.
        if (scope == FileConstraintRange.DirectOnly) {
            throw KatachiFileConstraintDirectOnlyWithoutDirectoryException(name = name, declaredAt = declaredAt)
        }
        fileConstraints += fileConstraintDeclarationOf(
            name = name,
            declaredAt = declaredAt,
            scope = FileConstraintRange.Subtree,
            check = check,
        )
    }
}
