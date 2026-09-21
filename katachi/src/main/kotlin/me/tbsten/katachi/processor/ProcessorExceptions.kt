package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.Role

/**
 * [ProjectModel.filesOf] was handed a role that belongs to a different definition.
 *
 * A model answers for the one [me.tbsten.katachi.dsl.Architecture] it was built from, and a
 * [Role] carries no identity beyond the object itself, so a role taken from elsewhere cannot be
 * matched against anything the model walked. Without this it would simply come back with no
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
 *     one.process { model -> model.filesOf(other.allRoles.single()) }
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
                "is not one of this model's roles.",
        )
        appendLine(
            "A model answers for the single architecture { } it was built from, and roles are " +
                "matched by identity, so a role from another definition -- or from a second " +
                "call of a function that builds one -- matches nothing the model walked.",
        )
        append(
            "Read the role from `model.roles`, or process the definition this role was " +
                "declared in. Hold that definition in one `val` rather than rebuilding it.",
        )
    },
)
