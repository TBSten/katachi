package me.tbsten.katachi

/**
 * Base of every error whose fix is in the definition. Catch this to treat "the definition
 * itself is wrong" as one case.
 *
 * Most are raised while `architecture { }` is being evaluated, before any file is read. Some,
 * such as an unreadable key inside a `layout { }` block, only surface once the check runs. It
 * extends [IllegalArgumentException]: the offending value is always one you passed to katachi.
 *
 * **A third-party architecture-checking backend may name this as its base** when it rejects a
 * definition written for it (a misspelled rule, a block that expects nothing). Name the
 * subclass `<Prefix><What>Exception`, build the message inside the class, and include the
 * declaration site.
 *
 * ## Example 1: catch every declaration-time error the same way
 * ```kt
 * shouldThrow<KatachiDeclarationException> {
 *     architecture {
 *         "domain".group { }
 *         "domain".group { }
 *     }
 * }
 * ```
 *
 * ## Example 2: a backend of your own naming this as its base
 * ```kt
 * class MyBackendNoExpectationException(
 *     val declaredAt: DeclarationSite,
 * ) : KatachiDeclarationException(
 *     message = "This block expects nothing, declared at $declaredAt.",
 * )
 * ```
 *
 * @constructor Takes the finished message and, optionally, the cause. Open to a third-party
 *   backend's own subclass, which opts in to [ExperimentalKatachiApi].
 */
public abstract class KatachiDeclarationException @ExperimentalKatachiApi public constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * Base of every error raised while a check is running: the definition is fine, but the
 * environment it was asked to run in is not.
 *
 * Examples are a missing git executable or a project root that cannot be found; the fix is in
 * the environment rather than in `architecture { }`.
 *
 * ## Example 1: tell an environment problem apart from a failed check
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiCheckException) {
 *     println("katachi could not run here: ${cause.message}")
 * }
 * ```
 */
public abstract class KatachiCheckException internal constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Base of every error that means one of katachi's own assumptions broke.
 *
 * There is nothing for the caller to fix: a definition cannot cause one of these, so every
 * message says so and asks for a report at https://github.com/TBSten/katachi/issues.
 *
 * **A third-party architecture-checking backend may name this as its base** when one of its
 * own assumptions breaks. Its message should say that this is a bug in that library and give
 * the address to report it at.
 *
 * ## Example 1: report a katachi bug rather than treating it as a failed check
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiInternalException) {
 *     println("Please report this at https://github.com/TBSten/katachi/issues: $cause")
 * }
 * ```
 *
 * ## Example 2: a backend of your own naming this as its base
 * ```kt
 * class MyBackendScopeIncompleteException(
 *     val expected: Int,
 *     val actual: Int,
 * ) : KatachiInternalException(
 *     message = """
 *         Built a scope over $expected directories but got $actual back.
 *         This is a bug in my-backend. Please report it at https://example.com/issues.
 *     """.trimIndent(),
 * )
 * ```
 *
 * @constructor Takes the finished message and, optionally, the cause. Open to a third-party
 *   backend's own subclass, which opts in to [ExperimentalKatachiApi].
 */
public abstract class KatachiInternalException @ExperimentalKatachiApi public constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
