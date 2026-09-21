package me.tbsten.katachi.dsl

/**
 * One concrete example of a role, as `example("GetUserUseCase", "ユーザーを取得する")`.
 *
 * Name and description are separate arguments so that a description may contain anything,
 * `...` included.
 *
 * ## Example 1: attach one concrete example to a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             example("GetUserUseCase", "ユーザーを取得する")
 *         }
 *     }
 * }
 * arch.allRoles.single()[Examples] shouldContainExactly
 *     listOf(RoleExample("GetUserUseCase", "ユーザーを取得する"))
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
 *         "UseCase" { title = "ユースケース" }
 *         "Repository" { }
 *     }
 * }
 * arch.allRoles.map { it[Title] ?: it.name } shouldContainExactly
 *     listOf("ユースケース", "Repository")
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
 *         "UseCase" { summary = "各画面で発生するアプリ固有の1つの振る舞い" }
 *     }
 * }
 * arch.allRoles.single()[Summary] shouldBe "各画面で発生するアプリ固有の1つの振る舞い"
 * ```
 */
@ExperimentalKatachiApi
public val Summary: MetadataKey<String> = metadata()

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
 *             example("GetUserUseCase", "ユーザーを取得する")
 *             example("SignOutUseCase", "サインアウトする")
 *         }
 *     }
 * }
 * arch.allRoles.single()[Examples].orEmpty().map { it.name } shouldContainExactly
 *     listOf("GetUserUseCase", "SignOutUseCase")
 * ```
 */
@ExperimentalKatachiApi
public val Examples: MetadataKey<List<RoleExample>> = metadata()
