package me.tbsten.katachi.intellij.presentation.gutter

/**
 * The texts of the source file's gutter: the ◆'s tooltip ([GutterTooltips]), its menu ([MenuModels]) and the hover of a
 * violation ([ViolationAnnotations]). English by default and Japanese under the Japanese language pack, like the generate
 * dialog. Implemented over `KatachiBundle` in the IDE (`BundleGutterStrings`) and over the properties files for the tests
 * ([PropertiesGutterStrings]); both only supply [MessageGutterStrings.message], so a key is written once.
 *
 * What katachi itself wrote (a rule's text, the report's message, the difference of a mismatch) is passed in as it is.
 *
 * ```kotlin
 * val tooltip = GutterTooltips.of(roleMarks, stale, reloadFailure, strings)
 * ```
 */
internal interface GutterStrings {
    // ---- the ◆'s tooltip ----

    /** Put after what allowed a declaration when the answer may be older than the file or the definition. */
    val staleSuffix: String

    /** The second line of the tooltip when the definition could not be read again; [summary] is one of the `failure*` texts. */
    fun reloadFailed(summary: String): String

    val failureCompilation: String
    val failureTaskNotFound: String
    val failureArchitectureNotSet: String
    val failureProcessorRejected: String
    val failureDefinitionRejected: String

    /** A Gradle failure with no line of its own to show. */
    val failureGradle: String
    val failureJsonMissing: String
    val failureJsonMalformed: String
    val failureJsonIncompatible: String
    val failureCancelled: String

    /** What allowed a declaration is not computed yet (the definition JSON is older than the explanations). */
    val notComputed: String

    /** [rule] is the first line that decided, with where it was written and how many more did ([GutterTooltips.rules]). */
    fun allowedBy(rule: String): String

    fun prohibitedBy(rule: String): String

    fun mismatched(rule: String): String

    val unexpected: String

    fun judgedByNewer(verdict: String): String

    /** In place of a rule katachi did not record. */
    val unrecordedRule: String

    // ---- the ◆'s menu ----

    /** The row in place of what allowed a declaration that is not computed yet: it reloads the definition. */
    val menuReloadToSee: String

    fun menuAllowedBy(rule: String): String

    fun menuProhibitedBy(rule: String): String

    fun menuMismatched(rule: String): String

    /** No line of `ktFileContent { }` allows the declaration; the row opens the `ktFileContent {`. */
    val menuUnexpected: String

    fun menuJudgedByNewer(verdict: String): String

    /** The heading over the required lines no declaration of the file matched. */
    fun missingSection(count: Int): String

    /** The right side of a row whose definition site was not found; [reason] is the resolver's. */
    fun declarationNotFound(reason: String): String

    /** The role's row; [summary] (its title or summary) is added after it by the caller. */
    fun roleRow(name: String): String

    /** The heading over the other files of [role]; [count] is `null` when there is none. */
    fun siblingsSection(role: String, count: Int?, stale: Boolean): String

    val noSibling: String

    fun moreSiblings(count: Int): String

    fun newFromTemplate(role: String): String

    /** The run row while the module's check is running. */
    val checking: String

    fun runCheck(taskPath: String): String

    val reload: String

    // ---- the hover of a violation ----

    /** What a hover starts with when the check may be out of date. */
    val stalePrefix: String

    /** [by] is the constraint and the role that rejected [declaration] (`"class *Service" (domain.Service)`), empty when the report names neither. */
    fun rejected(by: String, declaration: String): String

    fun declaredAt(site: String): String
}
