package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * What a constraint is told when the role's page asks it for its section: see
 * [FileConstraint.documentSection].
 *
 * Built by the documentation generator, never by a caller. Everything but [exampleFile] comes
 * from the definition alone; [exampleFile] is worked out the first time it is read, and only
 * then may the project be listed.
 *
 * ## Example 1: title a section with the role and the example it shows
 * ```kt
 * override fun documentSection(context: FileConstraintDocumentContext): String {
 *     val example = context.exampleFile
 *     return """
 *         ## File content of ${context.role.qualifiedName}
 *
 *         Example: `${example.fileName}`
 *     """.trimIndent()
 * }
 * ```
 *
 * @property role the role whose page is being written.
 * @property name what the constraint was called, or `null` when it was declared without a name.
 * @property declaredAt where the constraint was written.
 * @property captureNames the names the layout the constraint covers gives its `capture("...")`
 *   levels, as [FileConstraintSubject.captureNames] lists them during a check.
 */
@ExperimentalKatachiApi
public class FileConstraintDocumentContext internal constructor(
    public val role: Role,
    public val name: String?,
    public val declaredAt: DeclarationSite,
    public val captureNames: Set<String>,
    exampleFile: () -> FileConstraintExampleFile,
) {
    /**
     * The file a section shows its rules for, decided the first time this is read.
     *
     * The role's first `example(...)` when it has one; otherwise the first file of the project
     * the constraint covers, which lists the project's files once; otherwise, when there is no
     * such file or no project to list, a stand-in named `Sample.kt`. [FileConstraintExampleFile.source]
     * says which of the three it is.
     *
     * ## Example 1: say which file the section was worked out for
     * ```kt
     * val example = context.exampleFile
     * val line = when (example.source) {
     *     FileConstraintExampleSource.Placeholder -> "No example: the rules are shown for `${example.fileName}`."
     *     else -> "Example: `${example.fileName}`"
     * }
     * ```
     */
    public val exampleFile: FileConstraintExampleFile by lazy(exampleFile)

    /** The role and the constraint (its name, or where it was declared). */
    override fun toString(): String =
        "FileConstraintDocumentContext(${role.qualifiedName}, ${name ?: declaredAt})"
}

/**
 * One file a role's page shows a constraint's rules for. See
 * [FileConstraintDocumentContext.exampleFile].
 *
 * ## Example 1: fill a rule in with the example's own values
 * ```kt
 * val example = context.exampleFile
 * val feature = example.captureValues["feature"] ?: "<feature>"
 * val pattern = "com.example.feature.$feature"
 * ```
 *
 * @property fileName the file's own name, extension included: `HomeViewModel.kt`.
 * @property path where it is, project relative, when it is a file of the project
 *   ([FileConstraintExampleSource.MatchedFile]); `null` otherwise.
 * @property captureValues what the layout's `capture("...")` levels take from this file, by name.
 *   Only the names that could be worked out are here: for a role's example, the ones its file
 *   name alone gives.
 * @property source where the file came from.
 */
@ExperimentalKatachiApi
public class FileConstraintExampleFile internal constructor(
    public val fileName: String,
    public val path: String?,
    public val captureValues: Map<String, String>,
    public val source: FileConstraintExampleSource,
) {
    /** The file name, where it came from, and the values it gave. */
    override fun toString(): String = "FileConstraintExampleFile($fileName, $source, $captureValues)"
}

/**
 * Where a [FileConstraintExampleFile] came from.
 *
 * ## Example 1: say that there was no real example to show
 * ```kt
 * if (context.exampleFile.source == FileConstraintExampleSource.Placeholder) {
 *     println("${context.role.qualifiedName} has no example(...) and no file yet")
 * }
 * ```
 */
@ExperimentalKatachiApi
public enum class FileConstraintExampleSource {
    /** The role's first `example("...")`, read as a file name: `.kt` is added when it has no `.`. */
    RoleExample,

    /** The first file of the project the constraint covers, in walk order. */
    MatchedFile,

    /** Neither was there: a stand-in named `Sample.kt`, with no captured values. */
    Placeholder,
}
