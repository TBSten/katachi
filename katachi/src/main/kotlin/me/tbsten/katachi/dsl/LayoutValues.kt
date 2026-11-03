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
 * What `"...".module { }` declared: one subtree per module the key stood for.
 *
 * A module path with a wildcard may have stood for several modules, or for none, so this is
 * not one directory and cannot be continued with `/`. It exists for [optional].
 *
 * Leaves the public API together with `.module { }` in katachi 0.5.
 *
 * ## Example 1: Declaring a module, without opting into any of its own layout
 * ```kt
 * layout {
 *   ":core:data".module { }
 * }
 * ```
 */
public class LayoutModule internal constructor(
    internal val declared: List<LayoutNode>,
    // Empty for `":".module { }`: the root project has no directory of its own, so nothing to attach metadata to.
    internal val moduleDirectories: List<LayoutNode> = emptyList(),
) {
    /**
     * Attaches metadata to every module this declaration expanded to, the same way `owner =
     * "..."` attaches it to a role. See [LayoutDeclarationScope].
     *
     * Written once, read many: a wildcard key such as `":feature:*".module { }` may stand for
     * several modules, and every one of them gets the same value, the same way [optional] marks
     * every one of them.
     *
     * ## Example 1: attach the same metadata to every module a wildcard key expanded to
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var MetadataScope.owner: String? by Owner
     *
     * layout {
     *   ":feature:*".module { }.metadata { owner = "mobile" }
     * }
     * ```
     *
     * @throws KatachiMetadataWithoutEntryException when called on `":".module { }`: the root
     *   project has no directory of its own for a value to attach to.
     */
    @ExperimentalKatachiApi
    public fun metadata(block: LayoutDeclarationScope.() -> Unit): LayoutModule {
        if (moduleDirectories.isEmpty()) {
            throw KatachiMetadataWithoutEntryException(modulePath = ":")
        }
        moduleDirectories.forEach { it.attachMetadata(block) }
        return this
    }

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

    /** `LayoutModule(<paths>)`, the paths as far as this declaration wrote them. */
    override fun toString(): String = "LayoutModule(${declared.map { it.pathFromDeclaration() }})"
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
