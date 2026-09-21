package me.tbsten.katachi.dsl

/**
 * Base of every error whose fix is in the definition. Catch this to treat "the definition
 * itself is wrong" as one case.
 *
 * Most are raised while `architecture { }` is being evaluated, long before any file is read.
 * The rest come later for a reason that has nothing to do with the project: a `layout { }`
 * block is deferred, so a key katachi cannot read is only noticed when the check flattens it,
 * and a declaration handed back to katachi later can turn out to belong to another definition
 * entirely. Both are the same mistake — a bad value written into a definition — seen at the
 * first moment katachi could look at it.
 *
 * It extends [IllegalArgumentException] because the offending value is always something
 * the caller passed to katachi.
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
 */
public abstract class KatachiDeclarationException internal constructor(
    message: String,
) : IllegalArgumentException(message)

/**
 * Base of every error raised while a check is running: the definition is fine, but the
 * environment it was asked to run in is not.
 *
 * It extends [IllegalStateException] because nothing is wrong with the values that were
 * passed in — git is missing, the project root cannot be found, and the fix is out in the
 * environment rather than in `architecture { }`.
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
 * It exists instead of the standard library's own preconditions so that the class name in
 * the stack trace says where the failure came from. A bare [IllegalStateException] leaves
 * the reader guessing whether their own code or katachi is at fault.
 *
 * ## Example 1: report a katachi bug rather than treating it as a failed check
 * ```kt
 * try {
 *     projectArchitecture.assert()
 * } catch (cause: KatachiInternalException) {
 *     println("Please report this at https://github.com/TBSten/katachi/issues: $cause")
 * }
 * ```
 */
public abstract class KatachiInternalException internal constructor(
    message: String,
) : IllegalStateException(message)
