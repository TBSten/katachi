package me.tbsten.katachi.dsl

/**
 * Receiver of `layout { }`.
 *
 * TODO(step 2): this is where the path DSL goes — `"...".module { }`, `"...".file()`,
 * `/` chaining, `description`, source set and package keys. In step 1 the interface is
 * empty on purpose and the block is only stored, never evaluated.
 */
@KatachiDsl
public sealed interface LayoutScope

/**
 * A `layout { }` block that has been declared but not evaluated.
 *
 * Blocks are deferred on purpose: the same `architecture { }` value is read by tests and
 * (from v0.3) by documentation generation, so building it must not touch the file system
 * or run any check.
 */
public class LayoutDeclaration internal constructor(
    /** Where `layout { }` was written. */
    public val declaredAt: DeclarationSite,
    /** The block itself. Evaluated by the checker, not by the DSL. */
    @property:InternalKatachiApi
    public val block: LayoutScope.() -> Unit,
) {
    override fun toString(): String = "LayoutDeclaration($declaredAt)"
}
