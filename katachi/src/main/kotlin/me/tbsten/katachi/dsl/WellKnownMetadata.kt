package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi

/**
 * One concrete example of a role, as `example("GetUserUseCase", "Fetches a user")`.
 *
 * Name and description are separate arguments so that a description may contain anything,
 * `...` included.
 *
 * ## Example 1: attach one concrete example to a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             example("GetUserUseCase", "Fetches a user")
 *         }
 *     }
 * }
 * arch.allRoles.single()[Examples] shouldContainExactly
 *     listOf(RoleExample("GetUserUseCase", "Fetches a user"))
 * ```
 */
public data class RoleExample(
    public val name: String,
    public val description: String,
)

/**
 * Display name of a group or a role.
 *
 * Absent when it was not written. The fallback is the reader's: a documentation processor
 * shows `role[Title] ?: role.name`, because "the identifier will do" is a decision about how
 * documentation reads, not a property of the declaration.
 *
 * ## Example 1: read a declared display name, falling back to the identifier
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" { title = "Use case" }
 *         "Repository" { }
 *     }
 * }
 * arch.allRoles.map { it[Title] ?: it.name } shouldContainExactly
 *     listOf("Use case", "Repository")
 * ```
 */
@ExperimentalKatachiApi
public val Title: MetadataKey<String> = metadata()

/**
 * One paragraph describing what a role is for.
 *
 * ## Example 1: read the summary of a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" { summary = "A single app-specific behavior that happens on a screen" }
 *     }
 * }
 * arch.allRoles.single()[Summary] shouldBe "A single app-specific behavior that happens on a screen"
 * ```
 */
@ExperimentalKatachiApi
public val Summary: MetadataKey<String> = metadata()

/**
 * Free-form Markdown about a role: what it is, what it may do, what it may not.
 *
 * The pair to [Summary], and the division of labour between them is where each one is
 * rendered. [Summary] is one line in a table cell — a group's README lists its roles as
 * `| Role | Summary |` — so a paragraph break would break the table. This one is the body of the
 * role's own page, where a paragraph break is just a paragraph break.
 *
 * The value is kept exactly as written, newlines included. katachi decides where the text is
 * placed, never what is inside it: a fixed set of sections (`## What it may do` and the rest)
 * would be right for one team and wrong for the next, so the sections are the writer's.
 *
 * ## Example 1: read the free-form body of a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             summary = "A single app-specific behavior that happens on a screen"
 *             description = """
 *                 The UI calls use cases only; it never touches a repository directly.
 *
 *                 ### What it may do
 *                 - Spanning more than one repository
 *             """.trimIndent()
 *         }
 *     }
 * }
 * arch.allRoles.single()[Description].orEmpty().lines().first() shouldBe
 *     "The UI calls use cases only; it never touches a repository directly."
 * ```
 */
@ExperimentalKatachiApi
public val Description: MetadataKey<String> = metadata()

/**
 * Whether a group or a role is rendered into the generated documentation.
 *
 * Absent when it was not written, and absent means yes: a reader spells that as
 * `group[Documented] ?: true`. The value is also kept exactly as written — a role inside a
 * group that opted out has not opted out itself — so combining the two is the reader's job.
 *
 * ## Example 1: find the declarations that opted out
 * ```kt
 * val arch = architecture {
 *     "build".group {
 *         documented = false
 *         "GradleModule" { documented = false }
 *     }
 *     "domain".group { "UseCase" { } }
 * }
 * arch.allGroups.filterNot { it[Documented] ?: true }.map { it.name } shouldContainExactly
 *     listOf("build")
 * ```
 */
@ExperimentalKatachiApi
public val Documented: MetadataKey<Boolean> = metadata()

/**
 * The examples of a role, in the order `example()` added them.
 *
 * Absent rather than empty when `example()` was never called, so a reader writes
 * `role[Examples].orEmpty()`.
 *
 * ## Example 1: read the examples of a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             example("GetUserUseCase", "Fetches a user")
 *             example("SignOutUseCase", "Signs the user out")
 *         }
 *     }
 * }
 * arch.allRoles.single()[Examples].orEmpty().map { it.name } shouldContainExactly
 *     listOf("GetUserUseCase", "SignOutUseCase")
 * ```
 */
@ExperimentalKatachiApi
public val Examples: MetadataKey<List<RoleExample>> = metadata()
