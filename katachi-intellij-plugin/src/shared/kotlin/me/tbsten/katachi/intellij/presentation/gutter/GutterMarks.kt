package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.StaleReason

/** What [GutterMarks.of] needs of one open file. */
internal data class GutterInput(
    /** The definition's answer for the file; `null` when no definition holds it. */
    val roles: FileRoles?,
    /** `fileDeclarationsOf(file)`: katachi's explanation per role that has a `ktFileContent { }` over the file. */
    val explanations: List<RoleFileDeclarations>,
    /** The top-level declarations the PSI has now (empty for anything but `.kt`). */
    val declarations: List<SourceDeclaration>,
    val fileKind: SourceFileKind,
    /** The answer is older than the file or the definition (design 2.6): the explanations get `(stale)`. */
    val stale: Boolean,
)

/**
 * Where the ◆ of one file go and what they say (design 2.1, 2.2). Pure: the line marker provider gives it what
 * the PSI and the definition say and turns each [GutterMark] into a line marker.
 *
 * - Only a file the definition [MatchOutcome.Matched] gets a ◆.
 * - A `.kt` file one of whose roles' `ktFileContent { }` restricts top-level declarations
 *   (`declarationsChecked`) gets one per declaration, saying what allowed it.
 * - Any other `.kt` file gets one, at its first declaration (or its head); `.kts` and other files one at the head.
 *
 * ```kotlin
 * val marks = GutterMarks.of(GutterInput(roles, explanations, declarations, SourceFileKind.Kotlin, stale = false))
 * ```
 */
internal object GutterMarks {
    fun of(input: GutterInput): List<GutterMark> {
        val fileRoles = input.roles ?: return emptyList()
        if (fileRoles.outcome != MatchOutcome.Matched) return emptyList()
        val reloadFailure = reloadFailureOf(fileRoles.freshness)
        val roles = fileRoles.matches.map { it.role }.distinctBy { it.qualifiedName }
        if (roles.isEmpty()) return emptyList()
        if (input.fileKind != SourceFileKind.Kotlin) return listOf(oneForFile(MarkTarget.FileHead, roles, input, missing = false, reloadFailure))

        val restricting = roles.associateWith { role -> restrictingExplanationOf(role, input.explanations) }
            .filterValues { it != null }
        val first = input.declarations.minByOrNull { it.line }
        if (restricting.isEmpty() || first == null) {
            return listOf(oneForFile(first?.let(MarkTarget::Declaration) ?: MarkTarget.FileHead, roles, input, missing = true, reloadFailure))
        }

        val bound = restricting.mapValues { (_, explanation) ->
            DeclarationBinding.bind(input.declarations, explanation?.declarations?.declarations.orEmpty())
        }
        return input.declarations.mapIndexed { index, declaration ->
            val roleMarks = roles.map { role ->
                RoleMark(role, bound[role]?.get(index)?.explanation, missingOf(role, input.explanations), restricting[role]?.constraint?.declaredAt)
            }
            GutterMark(
                target = MarkTarget.Declaration(declaration),
                placement = MarkPlacement.PerDeclaration,
                roles = roleMarks,
                stale = input.stale,
                tooltip = GutterTooltips.of(roleMarks, input.stale, reloadFailure),
            )
        }
    }

    /** Why the definition could not be read again, when the answer is the old one for that reason (design 2.6, `Stale(ReloadFailed)`). */
    private fun reloadFailureOf(freshness: Freshness): LoadFailure? =
        ((freshness as? Freshness.Stale)?.reason as? StaleReason.ReloadFailed)?.failure

    private fun oneForFile(target: MarkTarget, roles: List<RoleModel>, input: GutterInput, missing: Boolean, reloadFailure: LoadFailure?): GutterMark {
        val roleMarks = roles.map { role -> RoleMark(role, null, if (missing) missingOf(role, input.explanations) else emptyList()) }
        return GutterMark(target, MarkPlacement.OnePerFile, roleMarks, input.stale, GutterTooltips.of(roleMarks, input.stale, reloadFailure))
    }

    /**
     * The explanation of [role] that restricts top-level declarations. One whose block could not be judged
     * (`unexplainedReason`) has no declarations to bind and is left out.
     * TODO: a role with two `ktFileContent { }` over one file shows the first one's explanation only.
     */
    private fun restrictingExplanationOf(role: RoleModel, explanations: List<RoleFileDeclarations>): RoleFileDeclarations? =
        explanations.firstOrNull {
            it.role.qualifiedName == role.qualifiedName && it.declarations.declarationsChecked && it.declarations.unexplainedReason == null
        }

    private fun missingOf(role: RoleModel, explanations: List<RoleFileDeclarations>): List<RuleRef> =
        explanations.filter { it.role.qualifiedName == role.qualifiedName }.flatMap { it.declarations.missing }
}

/**
 * The ◆'s hover text: `katachi: <roles>[ — <what allowed it>][ (stale)]`, and when the definition could not be read again
 * a second line saying why (design 2.2, 2.6). English only (U-11).
 */
internal object GutterTooltips {
    fun of(roles: List<RoleMark>, stale: Boolean, reloadFailure: LoadFailure? = null): String {
        val names = roles.joinToString(", ") { it.role.qualifiedName }
        val explanation = roles.firstNotNullOfOrNull { it.explanation }?.let { " — ${describe(it)}" }.orEmpty()
        val staleMark = if (stale && explanation.isNotEmpty()) " (stale)" else ""
        val failure = reloadFailure?.let { "\nThe definition could not be read again: ${summary(it)}" }.orEmpty()
        return "katachi: $names$explanation$staleMark$failure"
    }

    /** One line (no line break, at most [SUMMARY_LIMIT] characters) on why a read of the definition failed. */
    fun summary(failure: LoadFailure): String = when (failure) {
        is LoadFailure.Gradle -> when (val gradle = failure.failure) {
            is GradleFailure.CompilationFailed -> "the definition does not compile"
            is GradleFailure.TaskNotFound -> "Gradle does not know the task, so the project needs a sync"
            is GradleFailure.ArchitectureNotSet -> "katachi { architecture } is not set"
            is GradleFailure.ProcessorRejected -> "katachi refused the command line"
            is GradleFailure.Other -> oneLine(gradle.details.firstOrNull { it.isNotBlank() }) ?: "Gradle failed"
        }
        is LoadFailure.JsonMissing -> "the task wrote no definition JSON"
        is LoadFailure.MalformedJson -> "the definition JSON is cut off"
        is LoadFailure.IncompatibleJson -> "the definition JSON does not fit this plugin, so katachi and the plugin differ in version"
        LoadFailure.Cancelled -> "the read was cancelled"
    }

    private fun oneLine(text: String?): String? =
        text?.trim()?.replace(Regex("\\s+"), " ")?.take(SUMMARY_LIMIT)?.takeIf { it.isNotEmpty() }

    private const val SUMMARY_LIMIT = 120

    fun describe(explanation: BoundExplanation): String = when (explanation) {
        BoundExplanation.NotComputed -> "not computed yet. Reload Definition."
        is BoundExplanation.Explained -> {
            val declaration = explanation.declaration
            when (val verdict = declaration.verdict) {
                DeclarationVerdict.Allowed, DeclarationVerdict.AllowedByAnythingElse -> "allowed by ${rules(declaration.rules)}"
                DeclarationVerdict.Prohibited -> "prohibited by ${rules(declaration.rules)}"
                DeclarationVerdict.Mismatched ->
                    "does not match ${rules(declaration.rules)}" + declaration.difference?.let { ": $it" }.orEmpty()
                DeclarationVerdict.Unexpected -> "no line in ktFileContent allows this (UnsatisfiedFileConstraint in the check)"
                is DeclarationVerdict.Unknown -> "judged ${verdict.jsonName} by a newer katachi"
            }
        }
    }

    /** The first line that decided, where it was written, and how many more did: `class HealthService (ServiceRole.kt:80) +1`. */
    fun rules(rules: List<RuleRef>): String {
        val first = rules.firstOrNull() ?: return "a line katachi did not record"
        val site = first.declaredAt?.let { " (${it.fileName}:${it.line})" }.orEmpty()
        val more = if (rules.size > 1) " +${rules.size - 1}" else ""
        return "${first.text}$site$more"
    }
}
