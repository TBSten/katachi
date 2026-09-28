package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.TemplateScope
import me.tbsten.katachi.dsl.metadata

/**
 * A `.template { }` as it was written on one `layout { }` declaration: the block, and where it
 * was written. `id` and `title` are exactly the arguments `.template(id, title) { }` took.
 *
 * Not a `data class`: two calls to `.template { }` are two different templates even when every
 * argument happens to match, and [me.tbsten.katachi.dsl.internal.mergedWith] tells them apart by
 * identity for exactly that reason -- see [Template].
 */
internal class LayoutTemplate(
    val id: String?,
    val title: String?,
    val declaredAt: DeclarationSite,
    /**
     * [me.tbsten.katachi.dsl.internal.LayoutNode.moduleExpansionGroup] of the file this
     * `.template { }` was attached to, read back at construction time. `null` unless the
     * declaration sits inside a wildcard `":...".module { }` key's block.
     */
    val expansionGroup: Any?,
    val block: TemplateScope.() -> String,
) {
    /**
     * Equal only while both sides carry the *same* [expansionGroup] and share [declaredAt] and
     * [id] -- a wildcard module key's block replays once per module it expands to, building a
     * fresh [LayoutTemplate] instance (a fresh closure) at the same source line every time, and it
     * is [expansionGroup] that marks all of those replays as one expansion. Without it, comparing
     * by [declaredAt] alone would also merge two instances that only happen to share a source
     * line for an unrelated reason -- a plain user loop calling `.template { }` from inside
     * `forEach`, one `DataDomain` or one `id` at a time -- which is not a replay of one
     * declaration but several different ones, and must be told apart so that a repeated (or
     * missing) [id] between them is still caught by
     * [me.tbsten.katachi.dsl.internal.requireTemplatesAreWellFormed]. [id] is compared for the
     * same reason once such a loop sits *inside* a wildcard module key's block: every iteration
     * then shares the one [expansionGroup] too.
     *
     * `expansionGroup == null` on either side falls back to plain identity (`this === other`),
     * which is what makes every such loop iteration its own template by default.
     *
     * Only meaningful within one flatten: [expansionGroup] is created afresh by every flatten, so
     * the same declaration reached by two separate flattens is never equal. Match across flattens
     * by [declarationKey] instead.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayoutTemplate) return false
        val group = expansionGroup ?: return false
        return group === other.expansionGroup && declaredAt == other.declaredAt && id == other.id
    }

    override fun hashCode(): Int =
        expansionGroup?.let { 31 * System.identityHashCode(it) + declaredAt.hashCode() }
            ?: System.identityHashCode(this)

    /**
     * Which `.template { }` declaration this is, stable across separate flattens of the same
     * role -- unlike [equals], which [expansionGroup] ties to one flatten.
     *
     * [declaredAt] and [id] together are enough: two distinct templates of one role never share
     * an [id] (see [me.tbsten.katachi.dsl.internal.requireTemplatesAreWellFormed]), so the only
     * templates sharing both are a wildcard module key's replays of one declaration -- which is
     * exactly what a re-flatten against the real modules is looked up by.
     */
    val declarationKey: TemplateDeclarationKey get() = TemplateDeclarationKey(declaredAt, id)

    override fun toString(): String = "LayoutTemplate(${id ?: "<no id>"}, $declaredAt)"
}

/** See [LayoutTemplate.declarationKey]. */
internal data class TemplateDeclarationKey(val declaredAt: DeclarationSite, val id: String?)

/**
 * The metadata key `.template { }` is sugar over.
 *
 * Not public: a [LayoutTemplate] can only be built by `.template { }` itself, since its
 * constructor is internal, so nothing outside katachi could write this key even if it had the
 * key in hand. Kept in `dsl.internal` rather than exposed is what keeps `.metadata { }` from
 * being a second way to attach one.
 *
 * Compared by [LayoutTemplate.equals] when two declarations of the same path merge: a
 * `LayoutTemplate` holds a block, which has no structural equality, so `==` on two distinct
 * instances is `false` unless they are two replays of the same wildcard module key's expansion
 * -- which is exactly "attached twice", not "the same attachment reached from two `/` chains".
 */
internal val Template: MetadataKey<LayoutTemplate> = metadata()
