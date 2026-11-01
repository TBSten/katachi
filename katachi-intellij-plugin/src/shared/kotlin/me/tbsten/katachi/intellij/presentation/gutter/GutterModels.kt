package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind

/**
 * A top-level declaration of a `.kt` file as the PSI has it, without IntelliJ types: what the gutter binds to
 * katachi's explanation (design 2.4) and puts a ◆ next to.
 *
 * ```kotlin
 * val declaration = SourceDeclaration(SourceDeclarationKind.Class, "HealthService", line = 7)
 * val bound = DeclarationBinding.bind(listOf(declaration), explanation.declarations)
 * ```
 */
internal data class SourceDeclaration(
    val kind: SourceDeclarationKind,
    /** Without backquotes; a function's name without its receiver. */
    val name: String,
    /** 1-based; the first annotation's line when there is one, as katachi counts it. */
    val line: Int,
)

/** Whether a file gets a ◆ per top-level declaration or one for the file (design 2.1). */
internal enum class MarkPlacement { PerDeclaration, OnePerFile }

/** What kind of file the ◆ is for: `.kt` binds declarations, `.kts` and the rest get one ◆ at the top. */
internal enum class SourceFileKind { Kotlin, KotlinScript, Other }

/** Where a ◆ goes. The IDE side turns it into a PSI leaf. */
internal sealed interface MarkTarget {
    /** The name of [declaration]. */
    data class Declaration(val declaration: SourceDeclaration) : MarkTarget

    /** The `package` line of a `.kt` file with no top-level declaration, else the first leaf that is not white space. */
    data object FileHead : MarkTarget
}

/** What katachi said about a declaration under one role's `ktFileContent { }` (design 2.2). */
internal sealed interface BoundExplanation {
    /** katachi judged the declaration: [declaration] holds the verdict and the lines that decided it. */
    data class Explained(val declaration: DeclarationModel) : BoundExplanation

    /** The definition was written before the declaration (or before it got this kind or name). */
    data object NotComputed : BoundExplanation
}

/** One role's part of a ◆: the role, and for a ◆ per declaration what its `ktFileContent { }` said. */
internal data class RoleMark(
    val role: RoleModel,
    /** `null` for a ◆ of the file, and for a role that does not restrict the declarations. */
    val explanation: BoundExplanation?,
    /** The required lines no declaration of the file matched (the menu's "Missing in this file"). */
    val missing: List<RuleRef>,
    /** Where this role's `ktFileContent {` was written: what the menu opens for a declaration nothing allows. */
    val constraintSite: DeclarationSiteRef? = null,
)

/**
 * One ◆: where it goes, the roles that hold the file, and for a declaration what allowed it. [tooltip] is what
 * the gutter shows on hover, in the IDE's language (design 2.7, [GutterStrings]).
 *
 * ```kotlin
 * val marks = GutterMarks.of(GutterInput(roles, explanations, declarations, SourceFileKind.Kotlin, stale = false), strings)
 * marks.single().tooltip // katachi: domain.Service — allowed by class HealthService (ServiceRole.kt:80)
 * ```
 */
internal data class GutterMark(
    val target: MarkTarget,
    val placement: MarkPlacement,
    val roles: List<RoleMark>,
    /** The definition is stale, or the file changed after it was written: the explanation may be out of date. */
    val stale: Boolean,
    val tooltip: String,
)
