package me.tbsten.katachi.template

import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite

/**
 * `--arg roleName=` named a role the definition does not declare.
 *
 * Almost always a typo or a role that has been renamed, so the message lists what the definition
 * does declare: the fix is one of those names, and reading them is faster than opening the
 * definition.
 *
 * ## Example 1: catch a misspelled role name
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiUnknownTemplateRoleException
 *
 * val arch = architecture { "domain".group { "UseCase" { } } }
 * val thrown = shouldThrow<KatachiUnknownTemplateRoleException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCse")).getOrThrow()
 * }
 * thrown.declaredRoles shouldBe listOf("domain/UseCase")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiUnknownTemplateRoleException internal constructor(
    /** The name `--arg roleName=` carried, as written. */
    public val roleName: String,
    /** Every role the definition declares, qualified and sorted. */
    public val declaredRoles: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Unknown role "$roleName".""")
        appendLine(
            "--arg roleName= names the role whose template is to be run, and this definition " +
                "declares no role of that name.",
        )
        if (declaredRoles.isEmpty()) {
            append("This definition declares no role at all.")
        } else {
            appendLine("Declared roles:")
            for (role in declaredRoles) appendLine("  $role")
            append("""Pass one of them, either by its name or by its qualified name.""")
        }
    },
)

/**
 * `--arg roleName=` named something two groups both declare.
 *
 * The short name is a convenience that holds only while it names one thing. Once it does not,
 * katachi says so rather than picking whichever group was written first.
 *
 * ## Example 1: catch a plain name two groups declare
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiAmbiguousTemplateRoleException
 *
 * val arch = architecture {
 *     "domain".group { "UseCase" { } }
 *     "feature".group { "UseCase" { } }
 * }
 * val thrown = shouldThrow<KatachiAmbiguousTemplateRoleException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.candidates shouldBe listOf("domain/UseCase", "feature/UseCase")
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiAmbiguousTemplateRoleException internal constructor(
    /** The name `--arg roleName=` carried, as written. */
    public val roleName: String,
    /** The qualified names of every role carrying it, sorted. */
    public val candidates: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Role name "$roleName" is declared ${candidates.size} times.""")
        for (candidate in candidates) appendLine("  $candidate")
        appendLine(
            "A plain role name reaches a role only while it names one of them, and answering " +
                "with whichever group happens to be written first would make the command line " +
                "mean two things.",
        )
        append("Pass the qualified name instead, such as `--arg roleName=${candidates.first()}`.")
    },
)

/**
 * The role `--arg roleName=` named declares no `template { }`.
 *
 * Nothing about the role is wrong -- it is simply not a role that generates anything, and running
 * it would report success having written no file.
 *
 * ## Example 1: catch a role that has no template
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 * import me.tbsten.katachi.template.KatachiNoTemplateException
 *
 * val arch = architecture { "domain".group { "UseCase" { } } }
 * val thrown = shouldThrow<KatachiNoTemplateException> {
 *     arch.process(GenerateCodeFromTemplate, GenerateCodeFromTemplate.Args(roleName = "UseCase")).getOrThrow()
 * }
 * thrown.role shouldBe "domain/UseCase"
 * ```
 *
 * @see GenerateCodeFromTemplate
 */
public class KatachiNoTemplateException internal constructor(
    /** The role that was named, qualified. */
    public val role: String,
    /** Where `"RoleName" { }` was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Role "$role" declared at $declaredAt has no template.""")
        appendLine(
            "Running a role's template is what the katachiTemplate task does, and this role says " +
                "only where its files may live -- so the run would report success having " +
                "written nothing.",
        )
        append(
            """Add a template { } to that role, with one file("...") { } per file it produces.""",
        )
    },
)
