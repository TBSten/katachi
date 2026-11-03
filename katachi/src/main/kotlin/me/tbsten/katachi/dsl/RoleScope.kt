package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.FileConstraintDeclaration
import me.tbsten.katachi.dsl.internal.MetadataBuilder
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.fileConstraintDeclarationOf
import org.intellij.lang.annotations.Language

/**
 * Receiver of `"RoleName".role { }`.
 *
 * The five properties below are metadata under the hood — sugar over [Title], [Summary],
 * [Description], [Documented] and [Examples] — and a processor adds its own words the same
 * way, with `var RoleScope.owner by Owner`.
 *
 * ## Example 1: set a role's properties
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase".role {
 *             title = "Use case"
 *             summary = "A single app-specific behavior that happens on a screen"
 *             description = """
 *                 The UI calls use cases only; it never touches a repository directly.
 *             """.trimIndent()
 *             example("GetUserUseCase", "Fetches a user")
 *             example("SignOutUseCase")
 *             layout { "useCase" / "*UseCase".ktFile() }
 *         }
 *         "Generated".role {
 *             documented = false // left out of the generated documentation, still checked
 *             layout { "generated" { anyFile() } }
 *         }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface RoleScope : MetadataScope, FileConstraintScope {
    /**
     * Display name. Defaults to the role name.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var title: String

    /**
     * One paragraph describing what this role is for.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var summary: String?

    /**
     * Free-form Markdown about this role: what it is, what it may do, what it may not.
     *
     * Write [summary] for the one line that lands in a table cell, and this for everything
     * that needs more room. The text is kept exactly as written, newlines included, and
     * katachi adds no structure of its own: the headings inside are yours to choose.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var description: String?

    /**
     * Set to `false` to keep this role out of the generated documentation. It still takes
     * part in the check.
     */
    public var documented: Boolean

    /**
     * Adds a concrete example.
     *
     * Call it once per example; the examples are kept in the
     * order they were added. [description] may be left out when the name speaks for itself.
     */
    public fun example(name: String, description: String? = null)

    /**
     * Declares where files of this role may live. Call it once per place.
     *
     * The block is stored, not evaluated: see [LayoutDeclaration].
     *
     * @featured
     */
    public fun layout(block: LayoutScope.() -> Unit)
}

internal class RoleScopeImpl(private val roleName: String) : RoleScope {
    val metadata = MetadataBuilder()
    val layouts = mutableListOf<LayoutDeclaration>()

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
