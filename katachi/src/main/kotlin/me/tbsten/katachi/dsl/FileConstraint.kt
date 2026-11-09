package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * What a `fileConstraint { }` block asks of the files the layout around it matched.
 *
 * It is handed the files that **exist**, already narrowed to the block the constraint was
 * written in, and answers with the ones it rejects; how it decides (parsing Kotlin, reading
 * bytes, matching names) is up to it. A constraint that rejects nothing returns an empty list;
 * one that could not check anything should throw instead.
 *
 * It may also describe itself on the role's page of the generated documentation, through
 * [documentSection]. Nothing has to: the default says nothing, and a lambda written as
 * `FileConstraint { subject -> ... }` keeps that default.
 *
 * ## Example 1: reject every file whose name does not end in `UseCase.kt`
 * ```kt
 * val namedUseCase = FileConstraint { subject ->
 *     subject.files.filterNot { it.endsWith("UseCase.kt") }.map { FileConstraintFailure(it) }
 * }
 * ```
 */
@ExperimentalKatachiApi
public fun interface FileConstraint {
    /**
     * Answers with the files of [subject] this constraint rejects, empty when it rejects none.
     *
     * Every [FileConstraintFailure.file] has to be one of [FileConstraintSubject.files]; an answer
     * about any other file makes the whole constraint unchecked.
     */
    public fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure>

    /**
     * The Markdown the role's page shows for this constraint, starting with a `## ` heading of
     * its own, or `null` -- the default -- for nothing.
     *
     * Called once per constraint each time the pages are generated, and never by the check. The
     * section goes after the page's `## Constraints` list and before its `## Examples`. When the
     * constraint has a name, its entry in the `## Constraints` list links to the first heading of
     * this section. Whatever it throws stops the generation.
     *
     * [context] says which role the page is for and, when asked, which file to show an example
     * of -- reading [FileConstraintDocumentContext.exampleFile] may list the project's files, so
     * a section that needs no example should not touch it.
     *
     * ## Example 1: describe a constraint on the role's page
     * ```kt
     * class NoTodo : FileConstraint {
     *     override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()
     *
     *     override fun documentSection(context: FileConstraintDocumentContext): String =
     *         """
     *             ## No TODO
     *
     *             The files of ${context.role.qualifiedName} leave no `TODO` comment behind.
     *         """.trimIndent()
     * }
     * ```
     */
    public fun documentSection(context: FileConstraintDocumentContext): String? = null
}

/**
 * One file a [FileConstraint] rejected, and optionally where inside it and why.
 *
 * A `data class`, so it compares with `shouldBe` in your own tests, and the same declaration
 * rejected twice in one block counts once (twice with different [FileConstraintFailure.reasons] is two).
 *
 * Only [file][FileConstraintFailure.file] is required. The rest is printed only when it is there: `Declaration:` needs
 * [declaration][FileConstraintFailure.declaration], the `<label>: <text>` lines are [reasons][FileConstraintFailure.reasons], the indented list is [allowed][FileConstraintFailure.allowed],
 * `How to fix:` appears only when [howToFix][FileConstraintFailure.howToFix] has something in it, and `Declared at:` points at
 * [ruleDeclaredAt][FileConstraintFailure.ruleDeclaredAt] when the failure comes from one line of a larger rule.
 *
 * ```text
 * [UnsatisfiedFileConstraint] file:///.../GetUserUseCase.kt
 *   Role: UseCase / FileConstraint: "ktFileContent"
 *   Declaration: GetUserUseCaseImpl (line 9)
 *   Not allowed: class GetUserUseCaseImpl
 *   Allowed here:
 *     interface GetUserUseCase
 *   Declared at: UseCaseRole.kt:16
 *
 *   How to fix:
 *     - Rename it to GetUserUseCase
 * ```
 *
 * ## Example 1: reject a whole file, without naming anything inside it
 * ```kt
 * FileConstraintFailure("core/domain/useCase/Helper.kt")
 * ```
 *
 * ## Example 2: reject one declaration inside a file
 * ```kt
 * FileConstraintFailure("core/domain/useCase/Helper.kt", declaration = "Helper", line = 12)
 * ```
 *
 * ## Example 3: say why, what would be allowed, and how to fix it
 * ```kt
 * FileConstraintFailure(
 *     file = "core/domain/GetUserUseCase.kt",
 *     declaration = "GetUserUseCaseImpl",
 *     line = 9,
 *     reasons = listOf(FileConstraintReason("Not allowed", "class GetUserUseCaseImpl")),
 *     allowed = FileConstraintAllowedList("Allowed here", listOf("interface GetUserUseCase")),
 *     howToFix = listOf(FileConstraintFix("Rename it to GetUserUseCase")),
 *     ruleDeclaredAt = DeclarationSite("UseCaseRole.kt", 16),
 * )
 * ```
 *
 * @constructor Takes the rejected file, and optionally what inside it, on which line, why, what
 *   would have been allowed, how to fix it, and which line of the rule said no.
 */
@ExperimentalKatachiApi
public data class FileConstraintFailure(
    /** The rejected file, project relative and spelled exactly as in [FileConstraintSubject.files]. */
    public val file: String,
    /**
     * What inside the file was rejected — a class or function name — or `null` for a backend
     * that works one whole file at a time.
     */
    public val declaration: String? = null,
    /** Line the rejected declaration starts at, 1-based, or `null` when the backend cannot say. */
    public val line: Int? = null,
    /** Why, one `<label>: <text>` line each, in order. Empty prints nothing. */
    public val reasons: List<FileConstraintReason> = emptyList(),
    /** What the rule would have accepted here, or `null` for no such list. */
    public val allowed: FileConstraintAllowedList? = null,
    /** The ways to fix it, in the order to try them. Empty prints no `How to fix:` at all. */
    public val howToFix: List<FileConstraintFix> = emptyList(),
    /**
     * The line of the rule this failure comes from, or `null` to keep the constraint's own site.
     *
     * It is printed as `Declared at:` in place of where the whole constraint was declared.
     */
    public val ruleDeclaredAt: DeclarationSite? = null,
)
