package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.RoleModel
import kotlin.math.abs

/** The role a live template is expanded for, with the definition it was read from. */
internal data class DraftRole(val snapshot: DefinitionSnapshot, val role: RoleModel)

/**
 * The role a live template written in [fileName] at [caretLine] is for (ide-live-template-from-definition): the role the
 * definition says was declared in that file, the one nearest to the caret when the file declares several. `null` when the open
 * file declares none, which is when the template falls back to its fixed text.
 *
 * ```kotlin
 * val role = draftRoleOf("ServiceRole.kt", caretLine = 12, snapshots) { snapshot, site -> resolvesHere(snapshot, site) }
 * ```
 */
internal fun draftRoleOf(
    fileName: String,
    caretLine: Int,
    snapshots: List<DefinitionSnapshot>,
    /** Whether the site is in the open file, not in another file of the same name. */
    resolvesHere: (DefinitionSnapshot, DeclarationSiteRef) -> Boolean,
): DraftRole? = snapshots.flatMap { snapshot ->
    snapshot.definition?.roles.orEmpty().mapNotNull { role ->
        val site = role.declaredAt?.takeIf { it.fileName == fileName && resolvesHere(snapshot, it) } ?: return@mapNotNull null
        Triple(abs(site.line - caretLine), snapshot, role)
    }
}.minByOrNull { it.first }?.let { DraftRole(it.second, it.third) }
