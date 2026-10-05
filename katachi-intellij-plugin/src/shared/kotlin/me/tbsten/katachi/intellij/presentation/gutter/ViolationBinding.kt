package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.model.ViolationModel

/** Where a violation is drawn in the source file it points at (design 2.5). */
internal sealed interface AnnotationTarget {
    /**
     * The name of a declaration of the file ([name] is `Outer.member` for a member). [line] is where the check saw it
     * (1-based, `null` without a `region`): it only chooses between declarations of one name, and is the fallback line
     * when the declaration is not found.
     */
    data class AtDeclaration(val name: String, val line: Int?) : AnnotationTarget

    /** A line of the file (1-based). */
    data class AtLine(val line: Int) : AnnotationTarget

    /** The whole file: a banner over the editor. A violation without a line or a declaration in the file (a missing declaration, an unchecked file). */
    data object FileLevel : AnnotationTarget

    /** Not drawn here: the notification of a file no role holds (#13), or a violation of the definition side. */
    data object Skipped : AnnotationTarget
}

/** A violation of the source file and where it goes. */
internal data class BoundViolation(val violation: ViolationModel, val target: AnnotationTarget)

/** Where "Go to the rule" goes: the definition's line the check reported. */
internal sealed interface RuleSite {
    /** A `relatedLocations` entry ("Declared here"): an absolute path. */
    data class At(val location: SarifLocation) : RuleSite

    /** The `FileName.kt:NN` of `properties.declaredAt`, which the definition model finds among the test sources. */
    data class Declared(val site: DeclarationSiteRef) : RuleSite
}

/**
 * Which violations the source file draws and where (design 2.5, the source side). Pure: [KatachiViolationAnnotator] gives
 * it the violations whose first location is the file and puts the targets on the PSI.
 *
 * Only `UnsatisfiedFileConstraint` and `UncheckedFile` are drawn here. The violations of the definition side
 * (`MissingFile`, `MissingDescription` … point at the definition file) and `UnexpectedFile` are not.
 *
 * ```kotlin
 * val drawn = ViolationBinding.bindSourceSide(violations.forFile(file)).filter { it.target != AnnotationTarget.Skipped }
 * ```
 */
internal object ViolationBinding {
    // TODO(ide-gutter): the violations of the definition side (MissingFile, MissingDescription, UncheckedFileConstraint …) are drawn in the definition file when that side is built (deferred-definition-side.md).
    const val UNSATISFIED_FILE_CONSTRAINT: String = "UnsatisfiedFileConstraint"
    const val UNCHECKED_FILE: String = "UncheckedFile"
    private const val MISSING_PREFIX: String = "Missing:"

    fun bindSourceSide(violations: List<ViolationModel>): List<BoundViolation> =
        violations.map { BoundViolation(it, targetOf(it)) }

    fun targetOf(violation: ViolationModel): AnnotationTarget {
        val line = violation.location?.startLine?.takeIf { it > 0 }
        return when (violation.label) {
            UNSATISFIED_FILE_CONSTRAINT -> {
                val declaration = violation.properties["declaration"]?.takeIf { it.isNotBlank() }
                when {
                    // A missing declaration also has a `declaration` (the rule's name), but the file has nothing to underline for it.
                    isMissingDeclaration(violation) -> AnnotationTarget.FileLevel
                    declaration != null -> AnnotationTarget.AtDeclaration(declaration, line)
                    line != null -> AnnotationTarget.AtLine(line)
                    else -> AnnotationTarget.FileLevel
                }
            }
            UNCHECKED_FILE -> AnnotationTarget.FileLevel
            else -> AnnotationTarget.Skipped
        }
    }

    /**
     * Whether [violation] says the file lacks a declaration a rule requires: no `region` (katachi has no line for a
     * declaration that is not there) and a `Missing:` line in the message. katachi writes the rule's name as
     * `properties.declaration` for it too, so that property alone does not tell it from a rejected declaration.
     */
    fun isMissingDeclaration(violation: ViolationModel): Boolean =
        violation.label == UNSATISFIED_FILE_CONSTRAINT && violation.location?.startLine == null && missingLineOf(violation) != null

    /** The `Missing: class DefaultGetUserUseCase : GetUserUseCase` line of a report's message, or `null` when it has none. */
    fun missingLineOf(violation: ViolationModel): String? =
        violation.message.lineSequence().map { it.trim() }.firstOrNull { it.startsWith(MISSING_PREFIX) && it.length > MISSING_PREFIX.length }

    /** The definition's line the violation names, for "Go to the rule"; `null` when the report names none. */
    fun ruleSiteOf(violation: ViolationModel): RuleSite? {
        violation.related.firstOrNull()?.let { return RuleSite.At(it) }
        val declaredAt = violation.properties["declaredAt"] ?: return null
        val separator = declaredAt.lastIndexOf(':')
        if (separator <= 0) return null
        val line = declaredAt.substring(separator + 1).toIntOrNull()?.takeIf { it > 0 } ?: return null
        return RuleSite.Declared(DeclarationSiteRef(declaredAt.substring(0, separator), line))
    }
}
