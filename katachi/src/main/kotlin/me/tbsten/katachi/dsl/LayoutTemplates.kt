package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.LayoutTemplate
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.requireValidIdentifier

/**
 * Attaches a template to this file declaration: `.template { }`'s block is stored, replayed
 * once per run with that run's `--arg` values bound, and its return value becomes the file's
 * content. It is sugar over `.metadata { }`, over a key katachi does not expose -- see
 * [LayoutDeclarationScope].
 *
 * The file's directory and name are never repeated inside the block: they already come from
 * this declaration's own path, `capture("...")` levels included, which is what
 * [TemplateScope.captureValue] reads them back through. There is no `content { }` either --
 * the block's own return value is the whole of what a template writes.
 *
 * [id] tells this template apart from another one on the same role, in `--arg
 * template=role.id`. It may be left out only while the role has one template in total; the
 * moment a second one joins it, both need an id -- see [KatachiMissingTemplateIdException].
 *
 * ## Example 1: attach a template to a declared file
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout {
 *                 "useCase" / "${capture("name")}UseCase.kt".file()
 *                     .template {
 *                         val name = captureValue("name")
 *                         "interface ${name}UseCase"
 *                     }
 *             }
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @param id unique within the role, following the same character rule as a role name
 *   (`[A-Za-z][A-Za-z0-9_-]*`). `null` while the role has only this one template.
 * @param title shown instead of [id] (or the role's own title) wherever a template is listed by
 *   name, such as `katachiTemplates`. `null` falls back to [id], then to the role's title.
 * @throws KatachiInvalidIdentifierException when [id] is not `null` and not an identifier.
 * @throws KatachiDuplicateTemplateException when this declaration already carries a template.
 * @throws KatachiDuplicateTemplateIdException when [id] is already used by another template of
 *   the same role, noticed when the layout is flattened.
 * @throws KatachiMissingTemplateIdException when [id] is `null` and the role ends up with more
 *   than one template, noticed when the layout is flattened.
 * @throws KatachiTemplateOnWildcardException when this declaration's path still holds an
 *   unnamed `*` or `**`, noticed when the layout is flattened.
 * @featured
 */
@ExperimentalKatachiApi
public fun LayoutFile.template(
    id: String? = null,
    title: String? = null,
    block: TemplateScope.() -> String,
): LayoutFile {
    val declaredAt = captureDeclarationSite()
    id?.let { requireValidIdentifier(it, DeclarationKind.TemplateId, declaredAt) }
    val template = LayoutTemplate(
        id = id,
        title = title,
        declaredAt = declaredAt,
        expansionGroup = leaf.moduleExpansionGroup,
        block = block,
    )
    if (leaf.metadata[Template] != null) {
        // The role that declared this is not known yet -- a `layout { }` block is evaluated on
        // its own, ahead of anything that would say which role it belongs to -- so the
        // KatachiDuplicateTemplateException this is meant to become is raised later, once
        // flattening reaches this node and does know it. See `LayoutNode.toEntry`.
        leaf.duplicateTemplateAttempt = leaf.duplicateTemplateAttempt ?: template
    } else {
        leaf.metadata[Template] = template
    }
    return this
}
