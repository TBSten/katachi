package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.ViolationModel

/** How strongly an annotation is drawn; the IDE side maps it to a `HighlightSeverity`. */
internal enum class AnnotationSeverity { Error, Warning, WeakWarning, Information }

/**
 * What the editor shows of a violation (design 2.5): the colour from SARIF's `level`, and the hover. Pure.
 *
 * The hover's own words are in the IDE's language ([GutterStrings]); what it quotes of the report (the constraint, the message)
 * is katachi's, in English.
 *
 * ```kotlin
 * val severity = ViolationAnnotations.severityOf(violation.level, stale = state.stale)
 * val hover = ViolationAnnotations.hoverOf(violation, target, stale = state.stale, strings)
 * ```
 */
internal object ViolationAnnotations {
    /** The colour of [level]; a violation from a check that may be out of date is one step weaker (error to warning, warning to weak warning). */
    fun severityOf(level: SarifLevel, stale: Boolean): AnnotationSeverity {
        val fresh = when (level) {
            SarifLevel.Error -> AnnotationSeverity.Error
            SarifLevel.Warning -> AnnotationSeverity.Warning
            SarifLevel.Note -> AnnotationSeverity.WeakWarning
            SarifLevel.None -> AnnotationSeverity.Information
        }
        if (!stale) return fresh
        return when (fresh) {
            AnnotationSeverity.Error -> AnnotationSeverity.Warning
            AnnotationSeverity.Warning, AnnotationSeverity.WeakWarning -> AnnotationSeverity.WeakWarning
            AnnotationSeverity.Information -> AnnotationSeverity.Information
        }
    }

    /**
     * The hover of [violation]. A violation of a declaration says which constraint of which role rejected it and where
     * the constraint was written, then the report's message. A missing declaration says its kind, the message's
     * `Missing:` line and where the rule was written. One without a declaration (an unchecked file) says its kind and
     * the first line of the message. A check that may be out of date starts with [GutterStrings.stalePrefix] (`(stale) `).
     */
    fun hoverOf(violation: ViolationModel, target: AnnotationTarget, stale: Boolean, strings: GutterStrings): String {
        val head = when (target) {
            is AnnotationTarget.AtDeclaration -> declarationHead(violation, target.name, strings)
            else -> kindHead(violation)
        }
        val body = if (target is AnnotationTarget.AtDeclaration) violation.message.trim() else ""
        return (if (stale) strings.stalePrefix else "") + listOf(head, body).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun declarationHead(violation: ViolationModel, declaration: String, strings: GutterStrings): String {
        val properties = violation.properties
        val by = listOfNotNull(
            properties["constraint"]?.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
            properties["role"]?.takeIf { it.isNotBlank() }?.let { "($it)" },
        ).joinToString(" ")
        val declaredAt = properties["declaredAt"]?.takeIf { it.isNotBlank() }?.let { " " + strings.declaredAt(it) }.orEmpty()
        return "katachi: " + strings.rejected(by, declaration) + declaredAt
    }

    private fun kindHead(violation: ViolationModel): String {
        if (ViolationBinding.isMissingDeclaration(violation)) {
            val declaredAt = violation.properties["declaredAt"]?.takeIf { it.isNotBlank() }
            return "katachi: [${violation.label}] ${ViolationBinding.missingLineOf(violation)}" + (declaredAt?.let { " ($it)" } ?: "")
        }
        val firstLine = violation.message.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return "katachi: [${violation.label}]" + if (firstLine.isEmpty()) "" else " $firstLine"
    }

    /** The label of "Go to the rule": `Go to the rule (ServiceRole.kt:94)`, from the site the report names. */
    fun ruleLabelOf(site: RuleSite): String = when (site) {
        is RuleSite.At -> site.location.path.fileName?.toString().orEmpty() + (site.location.startLine?.let { ":$it" } ?: "")
        is RuleSite.Declared -> "${site.site.fileName}:${site.site.line}"
    }
}
