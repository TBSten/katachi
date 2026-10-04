package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * One line of why a [FileConstraintFailure] was raised, printed as `<label>: <text>`.
 *
 * A label and a value rather than a sentence, so that a report block keeps the shape an agent
 * greps against: `Not allowed: class GetUserUseCaseImpl`, `Reason: use cases are interfaces`.
 * Both are printed on one line; anything after a line break is dropped.
 *
 * ## Example 1: say what was found and why it is not allowed
 * ```kt
 * FileConstraintFailure(
 *     file = "core/domain/GetUserUseCase.kt",
 *     declaration = "GetUserUseCaseImpl",
 *     line = 9,
 *     reasons = listOf(FileConstraintReason("Not allowed", "class GetUserUseCaseImpl")),
 * )
 * ```
 *
 * @property label what the line is about, such as `Not allowed` or `Reason`. Printed before
 *   the colon.
 * @property text the value, such as `class GetUserUseCaseImpl`.
 * @constructor Takes the label and the text of one line.
 */
@ExperimentalKatachiApi
public data class FileConstraintReason(
    public val label: String,
    public val text: String,
)

/**
 * What a constraint would have accepted instead, printed as a heading and one entry per line.
 *
 * ```text
 *   Allowed here:
 *     interface GetUserUseCase
 *     class DefaultGetUserUseCase : GetUserUseCase  (missing)
 * ```
 *
 * An empty [entries] prints `(nothing)`, which is an answer of its own: the rule allows nothing
 * here at all.
 *
 * ## Example 1: list the declarations a file may hold
 * ```kt
 * FileConstraintFailure(
 *     file = "core/domain/GetUserUseCase.kt",
 *     declaration = "GetUserUseCaseImpl",
 *     allowed = FileConstraintAllowedList(
 *         heading = "Allowed here",
 *         entries = listOf("interface GetUserUseCase", "class DefaultGetUserUseCase : GetUserUseCase  (missing)"),
 *     ),
 * )
 * ```
 *
 * @property heading what the list is, printed with a colon after it.
 * @property entries one line each, in the order given.
 * @constructor Takes the heading and the entries.
 */
@ExperimentalKatachiApi
public data class FileConstraintAllowedList(
    public val heading: String,
    public val entries: List<String>,
)

/**
 * One way to fix a [FileConstraintFailure], printed as an item of the block's `How to fix:` list,
 * optionally followed by code to paste.
 *
 * ```text
 *   How to fix:
 *     - Allow it in ktFileContent { } at UseCaseRole.kt:16:
 *         `class`("GetUserUseCaseImpl").optional()
 * ```
 *
 * [text] is printed on one line. Each element of [code] is printed indented under it as written,
 * leading spaces kept, so a fragment of several lines keeps its own indentation; an element
 * holding line breaks is printed as that many lines.
 *
 * ## Example 1: offer a rename and a line to paste
 * ```kt
 * FileConstraintFailure(
 *     file = "core/domain/GetUserUseCase.kt",
 *     declaration = "GetUserUseCaseImpl",
 *     howToFix = listOf(
 *         FileConstraintFix("Rename it to DefaultGetUserUseCase"),
 *         FileConstraintFix("Allow it at UseCaseRole.kt:16:", code = listOf("`class`(\"GetUserUseCaseImpl\").optional()")),
 *     ),
 * )
 * ```
 *
 * @property text the fix, as one sentence.
 * @property code lines to paste under it, or empty for none.
 * @constructor Takes the sentence and, optionally, the code under it.
 */
@ExperimentalKatachiApi
public data class FileConstraintFix(
    public val text: String,
    public val code: List<String> = emptyList(),
)
