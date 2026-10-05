package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.ViolationModel

/** How strongly an annotation is drawn; the IDE side maps it to a `HighlightSeverity`. */
internal enum class AnnotationSeverity { Error, Warning, WeakWarning, Information }

/**
 * What the editor shows of a violation (design 2.5): the colour from SARIF's `level`, and the hover. Pure.
 *
 * The hover is English only (design U-11): it quotes the report, which is English.
 *
 * ```kotlin
 * val severity = ViolationAnnotations.severityOf(violation.level, stale = state.stale)
 * val hover = ViolationAnnotations.hoverOf(violation, target, stale = state.stale)
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
     * the first line of the message. A check that may be out of date starts with `(stale) `.
     */
    fun hoverOf(violation: ViolationModel, target: AnnotationTarget, stale: Boolean): String {
        val head = when (target) {
            is AnnotationTarget.AtDeclaration -> declarationHead(violation, target.name)
            else -> kindHead(violation)
        }
        val body = if (target is AnnotationTarget.AtDeclaration) violation.message.trim() else ""
        return (if (stale) STALE else "") + listOf(head, body).filter { it.isNotEmpty() }.joinToString("\n")
    }

    /** The text of a hover that starts the way [hoverOf] does with `stale`; the tests and the debug bridge read the prefix from here. */
    const val STALE: String = "(stale) "

    private fun declarationHead(violation: ViolationModel, declaration: String): String {
        val properties = violation.properties
        val parts = buildList {
            properties["constraint"]?.takeIf { it.isNotBlank() }?.let { add("\"$it\"") }
            properties["role"]?.takeIf { it.isNotBlank() }?.let { add("($it)") }
            add("rejected $declaration")
            properties["declaredAt"]?.takeIf { it.isNotBlank() }?.let { add("— declared at $it") }
        }
        return "katachi: " + parts.joinToString(" ")
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
