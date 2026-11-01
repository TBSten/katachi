package me.tbsten.katachi.intellij.presentation.authoring

/**
 * The texts of the definition file: the ◆'s tooltip and menu ([DefinitionGutterMarks], [DefinitionMenus]), the Code Vision and
 * its popup ([CountText], [EntryFilesPopups]), the hover of a mistake ([DefinitionErrorTargets]) and the kind a completion
 * shows. English by default and Japanese under the Japanese language pack. Implemented over `KatachiBundle` in the IDE
 * (`BundleAuthoringStrings`) and over the properties files for the tests ([PropertiesAuthoringStrings]); both only supply
 * [MessageAuthoringStrings.message], so a key is written once.
 *
 * ```kotlin
 * val text = CountText.of(entries, stale = false, strings)   // "3 files"
 * ```
 */
internal interface AuthoringStrings {
    /** Put after a count or a tooltip that may be older than the source. */
    val staleSuffix: String

    /** What a hover of a mistake starts with once the definition changed after it was read. */
    val stalePrefix: String

    /** `no files`, `1 file`, `3 files`, and `? files` for a count nobody knows (`null`). */
    fun files(count: Int?): String

    /** A count the JSON cut: [listed] is the files listed, a lower bound. */
    fun filesAtLeast(listed: Int): String

    /** A line that holds no file and is `required`. */
    val missingRequired: String

    /** Put after the count of a wildcard module key, an entry per module. */
    fun modules(count: Int): String

    /** The tooltip of a `.template { }` ◆; [roles] are the roles the line is tied to. */
    fun generateFrom(roles: String): String

    /** A role or a line whose files are not known, because the check has not run. */
    val countUnknown: String

    val noFileYet: String

    /** The last row of a list that shows only the first files: the tool window has them all. */
    fun moreFiles(count: Int): String

    fun runCheck(taskPath: String): String

    val reload: String

    val generateFromThis: String

    val showInToolWindow: String

    // ---- the popup of a Code Vision whose line holds no file ----

    fun noFileMatches(pattern: String): String

    /** [where] is a directory, or [projectRoot]. */
    fun noFileIn(where: String): String

    fun filesIn(where: String): String

    val projectRoot: String

    /** After the files of a directory when there are more than the popup lists. */
    val andMore: String

    // ---- the kind on the right of a completion ----

    val pathCapture: String
    val moduleCapture: String
    val capture: String
    val parameter: String
}
