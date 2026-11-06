package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.LayoutNode
import me.tbsten.katachi.dsl.internal.attachMetadata

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
    internal val top: LayoutNode,
    internal val leaf: LayoutNode,
) {
    /**
     * Attaches metadata to this directory, the same way `owner = "..."` attaches it to a role.
     * See [LayoutDeclarationScope].
     *
     * Written on the value a key returned: `"..." { }`'s block does not see this directory as a
     * receiver, so metadata cannot be attached from inside it -- only from outside, where the
     * value is in hand.
     *
     * ## Example 1: attach metadata to a declared directory
     * ```kt
     * val Obsolete: MetadataKey<Boolean> = metadata()
     * var MetadataScope.obsolete: Boolean? by Obsolete
     *
     * layout {
     *   "legacy" { }.metadata { obsolete = true } / "*.kt".file()
     * }
     * ```
     */
    @ExperimentalKatachiApi
    public fun metadata(block: LayoutDeclarationScope.() -> Unit): LayoutDirectory {
        leaf.attachMetadata(block)
        return this
    }

    /** `LayoutDirectory(<path>)`, the path as far as this declaration wrote it. */
    override fun toString(): String = "LayoutDirectory(${leaf.pathFromDeclaration()})"
}

/**
 * A file declared in a `layout { }` block.
 *
 * ## Example 1: Declaring a file at the project root, and one that may be absent
 * ```kt
 * layout {
 *   "libs.versions.toml".file()
 *   "gradle" / "gradle-daemon-jvm.properties".file().optional()
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
     */
    public fun optional(): LayoutFile {
        leaf.optional = true
        return this
    }

    /**
     * Attaches metadata to this file, the same way `owner = "..."` attaches it to a role. See
     * [LayoutDeclarationScope].
     *
     * ## Example 1: attach metadata to a declared file
     * ```kt
     * val Obsolete: MetadataKey<Boolean> = metadata()
     * var MetadataScope.obsolete: Boolean? by Obsolete
     *
     * layout {
     *   "legacy" / "*Dao".ktFile().metadata { obsolete = true }
     * }
     * ```
     */
    @ExperimentalKatachiApi
    public fun metadata(block: LayoutDeclarationScope.() -> Unit): LayoutFile {
        leaf.attachMetadata(block)
        return this
    }

    /** `LayoutFile(<path>)`, the path as far as this declaration wrote it. */
    override fun toString(): String = "LayoutFile(${leaf.pathFromDeclaration()})"
}

/**
 * A `layout { }` block that has been declared but not evaluated.
 *
 * Blocks are deferred on purpose: building an `architecture { }` value never touches the file
 * system or runs a check. The blocks run when the architecture is checked or processed.
 *
 * ## Example 1: Reading a role's declared `layout { }` blocks back
 * ```kt
 * val arch = architecture {
 *   "domain".group {
 *     "UseCase".role {
 *       layout { }
 *     }
 *   }
 * }
 *
 * arch.allRoles.single().layouts.single().declaredAt // where `layout { }` was written
 * ```
 */
public class LayoutDeclaration internal constructor(
    /**
     * Where `layout { }` was written.
     */
    public val declaredAt: DeclarationSite,
    internal val block: LayoutScope.() -> Unit,
) {
    /** `LayoutDeclaration(<declaredAt>)`. */
    override fun toString(): String = "LayoutDeclaration($declaredAt)"
}
