package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationAt
import me.tbsten.katachi.intellij.model.DeclarationKind
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import kotlin.math.abs

/** The kinds of line of a definition file the definition side draws on (ide-authoring design 4.1). */
internal enum class AnchorKind(val declares: DeclarationKind) {
    /** `"Service".role { }`. */
    Role(DeclarationKind.Role),

    /** `"*Service".ktFile()`. */
    Entry(DeclarationKind.Entry),

    /** `….template { }` on an entry. */
    Template(DeclarationKind.Template),
}

/**
 * A place in a definition file's source that says something the definition model also says, as the PSI shows it.
 * [name] is what [AnchorBinding] matches against the model (`null` when the source does not say it, an interpolated role
 * name), [line] is 1-based, the line the call was written on.
 */
internal data class Anchor(val kind: AnchorKind, val name: String?, val line: Int)

/** An [Anchor] and the declarations of the model it stands for. [declarations] is empty for an anchor nothing in the model matches. */
internal data class BoundAnchor(val anchor: Anchor, val declarations: List<DeclarationAt>)

/**
 * Ties the anchors of a definition file to the declarations of the model (ide-authoring design 4.2), without any IDE type.
 * Binding by name and the nearest line, never by line alone, is what keeps it right after the file was edited and the
 * lines moved.
 *
 * For each anchor, among the declarations of its kind:
 * 1. those whose name is the anchor's (every one when the anchor has no name);
 * 2. one left: that one. Several: the ones whose `declaredAt` line is nearest the anchor's, all of them on a tie, which is how
 *    a wildcard module key (one declaration, one entry per module) stays one sum;
 * 3. none: the anchor binds nothing.
 *
 * ```kotlin
 * val bound = AnchorBinding.bind(anchors, service.declarationsIn(file))
 * ```
 */
internal object AnchorBinding {
    /** One [BoundAnchor] per anchor, in the order of [anchors]. */
    fun bind(anchors: List<Anchor>, declarations: List<DeclarationAt>): List<BoundAnchor> = anchors.map { anchor ->
        val ofKind = declarations.filter { it.kind == anchor.kind.declares }
        val named = if (anchor.name == null) ofKind else ofKind.filter { nameOf(it) == anchor.name }
        BoundAnchor(anchor, nearest(named, anchor.line))
    }

    /** The name the source writes for [declaration]: a role's short name, the last segment of a file entry's glob, `null` for any other declaration. */
    fun nameOf(declaration: DeclarationAt): String? = when (declaration.kind) {
        DeclarationKind.Role -> declaration.role?.name
        // Only a file entry is written as a file declaration; a directory or `anyFile()` that ends alike is another line.
        DeclarationKind.Entry, DeclarationKind.Template ->
            declaration.entry?.takeIf { it.kind == LayoutEntryKind.File }?.pattern?.substringAfterLast('/')
        else -> null
    }

    private fun nearest(candidates: List<DeclarationAt>, line: Int): List<DeclarationAt> {
        if (candidates.size <= 1) return candidates
        val nearest = candidates.minOf { abs(it.site.line - line) }
        return candidates.filter { abs(it.site.line - line) == nearest }
    }
}
