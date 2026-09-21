package me.tbsten.katachi.dsl

/**
 * A directory declared in a `layout { }` block.
 *
 * The value exists so that `/` can nest what was just declared, and so that a block can be
 * opened below it. It is not a path: the declaration is already recorded when the value is
 * handed back.
 *
 * ## Example 1: Continuing a `/` chain with the value a previous directory declaration returned
 * ```kt
 * layout {
 *   "app" / "ios/support" / "Info.plist".file()
 * }
 * ```
 */
public class LayoutDirectory internal constructor(
    /** Outermost node of the chain this expression created; the one `/` re-parents. */
    internal val top: LayoutNode,
    /** Innermost node; where the next level attaches. */
    internal val leaf: LayoutNode,
) {
    override fun toString(): String = "LayoutDirectory(${leaf.pathFromDeclaration()})"
}

/**
 * A file declared in a `layout { }` block.
 *
 * ## Example 1: Declaring a file at the project root
 * ```kt
 * layout {
 *   "libs.versions.toml".file()
 * }
 * ```
 */
public class LayoutFile internal constructor(
    internal val top: LayoutNode,
    internal val leaf: LayoutNode,
) {
    /**
     * Stops this file from being reported as missing when it does not exist.
     *
     * A declaration holding a `*` or a `**` is already optional on its own: such a place is
     * one that fills up over time, and zero matches is a normal state for it.
     *
     * ## Example 1: Making a declared file optional
     * ```kt
     * layout {
     *   "gradle" / "libs.versions.toml".file().optional()
     * }
     * ```
     */
    public fun optional(): LayoutFile {
        leaf.optional = true
        return this
    }

    override fun toString(): String = "LayoutFile(${leaf.pathFromDeclaration()})"
}

/**
 * What `"...".module { }` declared: one subtree per module the key stood for.
 *
 * A module path with a wildcard may have stood for several modules, or for none, so this is
 * not one directory and cannot be continued with `/`. It exists for [optional].
 *
 * ## Example 1: Declaring a module, without opting into any of its own layout
 * ```kt
 * layout {
 *   ":core:data".module { }
 * }
 * ```
 */
public class LayoutModule internal constructor(
    /** The nodes the declaration added, one group per module it expanded to. */
    internal val declared: List<LayoutNode>,
) {
    /**
     * Stops everything this module declared from being reported as missing.
     *
     * Written on a module that may or may not be there yet. The module's files are still
     * checked the usual way once they exist — `optional()` is about existence, not about
     * letting anything through.
     *
     * ## Example 1: Marking a not-yet-created module optional
     * ```kt
     * layout {
     *   ":experimental".module { "README.md".file() }.optional()
     * }
     * ```
     */
    public fun optional(): LayoutModule {
        declared.forEach { it.markFilesOptional() }
        return this
    }

    override fun toString(): String = "LayoutModule(${declared.map { it.pathFromDeclaration() }})"
}

/**
 * A `layout { }` block that has been declared but not evaluated.
 *
 * Blocks are deferred on purpose: the same `architecture { }` value is read by tests and
 * (from v0.3) by documentation generation, so building it must not touch the file system
 * or run any check. Evaluating one is what `flattenLayout()` does.
 *
 * ## Example 1: Reading a role's declared `layout { }` blocks back
 * ```kt
 * val arch = architecture {
 *   "domain".group {
 *     "UseCase" {
 *       layout { }
 *     }
 *   }
 * }
 *
 * arch.allRoles.single().layouts.single()
 * ```
 */
public class LayoutDeclaration internal constructor(
    /**
     * Where `layout { }` was written.
     *
     * ## Example 1: Reading where a role's `layout { }` block was written
     * ```kt
     * val arch = architecture {
     *   "domain".group {
     *     "UseCase" { layout { } }
     *   }
     * }
     *
     * arch.allRoles.single().layouts.single().declaredAt
     * ```
     */
    public val declaredAt: DeclarationSite,
    /** The block itself. Evaluated by the checker, not by the DSL. */
    @property:InternalKatachiApi
    public val block: LayoutScope.() -> Unit,
) {
    override fun toString(): String = "LayoutDeclaration($declaredAt)"
}
