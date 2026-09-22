package me.tbsten.katachi

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
 * **A third-party architecture-checking backend may name this as its base.** The constructor
 * is open for that: a check plugged into katachi rejects definitions written for it — a
 * misspelled rule, a block that expects nothing — and those are the same mistake as a bad
 * role name, so a caller who catches this should catch them too. Follow the same rules
 * katachi's own exceptions follow: name it `<Prefix><What>Exception` so the stack trace says
 * whose it is, build the message inside the class rather than taking one as a parameter, and
 * point at the declaration site.
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
 */
public abstract class KatachiDeclarationException @ExperimentalKatachiApi public constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

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
 * **A third-party architecture-checking backend may name this as its base**, for the same
 * reason katachi has it: an assumption of its own broke, and the caller cannot fix it. The
 * rule about the message generalises with it — say that this is a bug in **that** library,
 * and give the address to report it at, so the reader knows the failure is not theirs and
 * where to send it.
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
 */
public abstract class KatachiInternalException @ExperimentalKatachiApi public constructor(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
