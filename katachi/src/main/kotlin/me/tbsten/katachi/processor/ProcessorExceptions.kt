package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiCheckException
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.Role

/**
 * [ArchitectureProcessContext.filesOf] was handed a role that belongs to a different
 * definition.
 *
 * A run answers for the one [me.tbsten.katachi.dsl.Architecture] it was started from, and a
 * [Role] carries no identity beyond the object itself, so a role taken from elsewhere cannot be
 * matched against anything the run walked. Without this it would simply come back with no
 * files, which reads exactly like "this role owns nothing" — a wrong answer that no test can
 * tell from a right one.
 *
 * @property role the role that was passed in.
 *
 * ## Example 1: catch a role read off the wrong definition
 * ```kt
 * val one = architecture { "domain".group { "UseCase" { } } }
 * val other = architecture { "domain".group { "UseCase" { } } }
 *
 * shouldThrow<KatachiUnknownRoleException> {
 *     one.process { context -> context.filesOf(other.allRoles.single()) }
 * }.role.qualifiedName shouldBe "domain/UseCase"
 * ```
 */
@ExperimentalKatachiApi
public class KatachiUnknownRoleException internal constructor(
    public val role: Role,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Role \"${role.qualifiedName}\", declared at ${role.declaredAt}, " +
                "is not one of this run's roles.",
        )
        appendLine(
            "A run answers for the single architecture { } it was started from, and roles are " +
                "matched by identity, so a role from another definition -- or from a second " +
                "call of a function that builds one -- matches nothing the run walked.",
        )
        append(
            "Read the role from `context.roles`, or process the definition this role was " +
                "declared in. Hold that definition in one `val` rather than rebuilding it.",
        )
    },
)

/**
 * A check was handed an [ArchitectureProcessContext] katachi did not build.
 *
 * [me.tbsten.katachi.check.LayoutCheck] and [me.tbsten.katachi.check.KonsistCheck] do not read
 * violations off the context -- they read the walk behind it, which only katachi's own context
 * carries. A context written elsewhere has declarations and nothing to check them against, and
 * answering with an empty list would say "this project is clean", which is the one wrong answer
 * a green test cannot be told from a right one.
 *
 * @property context the class name of the context that was passed in.
 *
 * ## Example 1: run katachi's checks through a `process` entry point rather than by hand
 * ```kt
 * // Instead of LayoutCheck().process(myOwnContext):
 * val violations = projectArchitecture.process(LayoutCheck())
 * violations.map { it.path } shouldBe emptyList()
 * ```
 */
@ExperimentalKatachiApi
public class KatachiForeignProcessContextException internal constructor(
    public val context: String,
) : KatachiCheckException(
    message = buildString {
        appendLine("Cannot run a katachi check against a $context.")
        appendLine(
            "katachi's own checks read the single walk of the project that the context " +
                "katachi builds carries, and a context implemented elsewhere has no walk " +
                "behind it.",
        )
        append(
            "Run the check through Architecture.process(...), assert(...) or validate(...), " +
                "which build that context themselves.",
        )
    },
)
