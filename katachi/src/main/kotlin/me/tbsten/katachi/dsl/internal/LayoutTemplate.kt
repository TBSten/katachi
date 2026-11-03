package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.TemplateScope
import me.tbsten.katachi.dsl.metadata

// Not a data class: two `.template { }` calls are different templates even with equal arguments; mergedWith
// compares by identity.
internal class LayoutTemplate(
    val id: String?,
    val title: String?,
    val declaredAt: DeclarationSite,
    val expansionGroup: Any?,
    val block: TemplateScope.() -> String,
) {
    // Equal only with the same expansionGroup: a wildcard module key replays the block per module at one line, and
    // declaredAt alone would merge unrelated instances.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayoutTemplate) return false
        val group = expansionGroup ?: return false
        return group === other.expansionGroup && declaredAt == other.declaredAt && id == other.id
    }

    override fun hashCode(): Int =
        expansionGroup?.let { 31 * System.identityHashCode(it) + declaredAt.hashCode() }
            ?: System.identityHashCode(this)

    // Stable across flattens (unlike equals): declaredAt and id suffice because distinct templates of a role never
    // share an id.
    val declarationKey: TemplateDeclarationKey get() = TemplateDeclarationKey(declaredAt, id)

    override fun toString(): String = "LayoutTemplate(${id ?: "<no id>"}, $declaredAt)"
}

internal data class TemplateDeclarationKey(val declaredAt: DeclarationSite, val id: String?)

// Internal so `.metadata { }` cannot become a second way to attach a template; compared by LayoutTemplate.equals
// when declarations merge.
internal val Template: MetadataKey<LayoutTemplate> = metadata()
