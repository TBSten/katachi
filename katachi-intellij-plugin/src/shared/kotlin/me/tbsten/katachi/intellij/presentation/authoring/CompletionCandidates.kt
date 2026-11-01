package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.allParametersOf
import kotlin.math.abs

/** One name offered in a string of a definition: what is inserted, and the words shown around it. */
internal data class CompletionCandidate(
    /** What a choice puts between the quotes. */
    val text: String,
    /** Right of the name: `path capture`, `parameter`, or a template's title. */
    val typeText: String,
    /** After the type text, dimmed: which template a capture belongs to, ` (domain.Screen)`. */
    val tailText: String? = null,
    /** Other things typing may match: the role name and the id of a template. Never [text] itself. */
    val aliases: List<String> = emptyList(),
)

/** The template of the definition that a `.template { }` of the source stands for. */
internal data class TemplateBinding(val snapshot: DefinitionSnapshot, val template: TemplateModel)

/**
 * What the strings of a definition's source offer (ide-authoring design 3.1), worked out from the
 * definition JSON alone: the `capture` names a `.template { }` can read, the templates a
 * `templateSet` can name, and the names a member's `arg` can take. Everything here is a pure
 * function of the snapshots; finding the string in the PSI and putting the choice in the editor is
 * the platform side's business.
 *
 * ```kotlin
 * val binding = bindTemplate("ScreenRole.kt", templateLine = 7, snapshots) { snapshot, site -> resolves(snapshot, site) }
 * val names = binding?.let(::captureCandidates).orEmpty().map { it.text }     // ["feature", "name"]
 * ```
 */
internal fun bindTemplate(
    fileName: String,
    templateLine: Int,
    snapshots: List<DefinitionSnapshot>,
    /** Whether [DeclarationSiteRef] of that definition is in the open file (the resolver's answer: exactly one file of that name). */
    resolvesHere: (DefinitionSnapshot, DeclarationSiteRef) -> Boolean,
): TemplateBinding? {
    val found = buildList {
        for (snapshot in snapshots) {
            val roles = snapshot.definition?.roles ?: continue
            for (role in roles) {
                for (entry in role.layout) {
                    val ref = entry.template ?: continue
                    val site = ref.declaredAt ?: continue
                    if (site.fileName != fileName || !resolvesHere(snapshot, site)) continue
                    val template = snapshot.templates.firstOrNull { it.template == ref.specifier } ?: continue
                    add(Triple(site.line, snapshot, template))
                }
            }
        }
    }
    // The line of the call itself, else the nearest one (the definition was edited since it was read); the first declared on a tie.
    val nearest = found.minByOrNull { (line, _, _) -> abs(line - templateLine) } ?: return null
    return TemplateBinding(nearest.second, nearest.third)
}

/** The capture names of the bound template, for `captureValue("…")`. */
internal fun captureCandidates(binding: TemplateBinding, strings: AuthoringStrings): List<CompletionCandidate> {
    val template = binding.template
    // The complete specifier, so that a capture of another template of the same role (`ui.Screen.a` / `ui.Screen.b`) reads differently.
    val tail = " (${template.template})"
    val detail = template.detail
    if (detail == null) {
        return template.summary.captureNames.distinct().map { CompletionCandidate(it, strings.capture, tail) }
    }
    return detail.captures.distinctBy { it.name }.map { CompletionCandidate(it.name, captureTypeOf(it, strings), tail) }
}

/**
 * The complete specifiers of every template a member can run, for `template("…")` of a `templateSet`. A template set is not
 * one of them: a member that names a set is refused (`KatachiInvalidTemplateSetMemberException`).
 */
internal fun templateSpecCandidates(snapshots: List<DefinitionSnapshot>): List<CompletionCandidate> =
    membersOf(snapshots).map { template ->
        val spec = template.template
        CompletionCandidate(
            text = spec,
            typeText = template.title,
            aliases = aliasesOf(template).filter { it != spec },
        )
    }

/**
 * The names `arg("…")` accepts for the member that runs [spec], read the way `template("…")` reads it ([resolveMemberTemplate]):
 * its parameters in any branch, and its captures, each name once. Empty when [spec] names no member template, or not one only.
 */
internal fun argCandidates(spec: String, snapshots: List<DefinitionSnapshot>, strings: AuthoringStrings): List<CompletionCandidate> {
    val template = resolveMemberTemplate(spec, membersOf(snapshots)) ?: return emptyList()
    val detail = template.detail
    if (detail == null) {
        val names = (template.summary.parameterNames.map { it to strings.parameter } + template.summary.captureNames.map { it to strings.capture })
        return names.distinctBy { it.first }.map { (name, type) -> CompletionCandidate(name, type) }
    }
    return allParametersOf(detail).map { parameter ->
        val type = (parameter as? ParameterModel.CaptureParam)?.let { captureTypeOf(it, strings) } ?: strings.parameter
        CompletionCandidate(parameter.name, type)
    }
}

/** The templates a member can run: not the rows of a template set, each specifier once. */
private fun membersOf(snapshots: List<DefinitionSnapshot>): List<TemplateModel> =
    snapshots.flatMap { it.templates }.filter { it.set == null }.distinctBy { it.template }

/**
 * The template [spec] names among [members], by the rule of `resolveSpecifier` in `:katachi` (`template("…")` and `--arg template=`):
 * the whole of it as a role (the complete name, else the plain name of the one role it fits) with exactly one template, or its last
 * `.`-segment as the id of a template of the role before it. Only a single answer counts; none or several give `null`.
 */
internal fun resolveMemberTemplate(spec: String, members: List<TemplateModel>): TemplateModel? {
    fun rolesMatching(reference: String): List<String> {
        members.firstOrNull { it.roleName == reference }?.let { return listOf(it.roleName) }
        return members.filter { it.roleName.substringAfterLast('.') == reference }.map { it.roleName }.distinct()
    }

    val readAsRole = rolesMatching(spec).singleOrNull()
        ?.let { role -> members.filter { it.roleName == role } }
        ?.singleOrNull()
    val lastDot = spec.lastIndexOf('.')
    val readAsRoleAndId = if (lastDot >= 1) {
        val idPart = spec.substring(lastDot + 1)
        rolesMatching(spec.substring(0, lastDot)).singleOrNull()
            ?.let { role -> members.firstOrNull { it.roleName == role && it.summary.id == idPart } }
    } else {
        null
    }
    return listOfNotNull(readAsRole, readAsRoleAndId).distinctBy { it.template }.singleOrNull()
}

private fun captureTypeOf(capture: ParameterModel.CaptureParam, strings: AuthoringStrings): String =
    if (capture.places.any { it.isModule }) strings.moduleCapture else strings.pathCapture

/** What typing may match besides the specifier: the role's name, the template's id, and the specifier from each of its dots on. */
private fun aliasesOf(template: TemplateModel): List<String> {
    val spec = template.template
    val tails = spec.split('.').indices.drop(1).map { spec.split('.').drop(it).joinToString(".") }
    return (tails + listOfNotNull(template.roleName.substringAfterLast('.'), template.summary.id)).distinct()
}
