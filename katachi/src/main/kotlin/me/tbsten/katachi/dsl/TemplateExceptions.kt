package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * A role declared `template { }` more than once.
 *
 * A role has one template so that `--processor=template --arg roleName=UseCase` never has to be
 * told which of them was meant. A role that really produces several files says so with several
 * `file(...)` calls inside the one template.
 *
 * ## Example 1: catch a role that declares two templates
 * ```kt
 * val thrown = shouldThrow<KatachiDuplicateTemplateException> {
 *     architecture {
 *         "domain".group {
 *             "UseCase" {
 *                 template { file("A.kt") { "" } }
 *                 template { file("B.kt") { "" } }
 *             }
 *         }
 *     }
 * }
 * thrown.role shouldBe "UseCase"
 * ```
 *
 * @see RoleScope.template
 */
public class KatachiDuplicateTemplateException internal constructor(
    /** The role that declared both. */
    public val role: String,
    /** Where the template it already had was written. */
    public val firstDeclaredAt: DeclarationSite,
    /** Where the refused second `template { }` was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Role "$role" declares a second template at $declaredAt.""")
        appendLine(
            "It already declared one at $firstDeclaredAt, and a role has one template so that " +
                "running it never has to be told which of them was meant.",
        )
        append("Merge the two into one template { }, with one file(...) per file it produces.")
    },
)

/**
 * `file(...)` was given something other than a plain file name.
 *
 * A template names files; the role's `layout { }` names directories. Keeping it that way is
 * what stops a template from choosing a path of its own — these files are written into the
 * user's own source tree, not below `build/`.
 *
 * ## Example 1: catch a file name that is really a path
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" { template { file("useCase/GetUserUseCase.kt") { "" } } }
 *     }
 * }
 *
 * // architecture { } keeps the template's block rather than running it -- the names it
 * // declares are read when the template runs, which is here.
 * val thrown = shouldThrow<KatachiInvalidTemplateFileNameException> {
 *     arch.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     )
 * }
 * thrown.fileName shouldBe "useCase/GetUserUseCase.kt"
 * ```
 *
 * @see TemplateScope.file
 */
public class KatachiInvalidTemplateFileNameException internal constructor(
    /** The role whose template wrote it. */
    public val role: String,
    /** The rejected name, as written. */
    public val fileName: String,
    /** Where `file(...)` was called. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Invalid template file name "$fileName" declared at $declaredAt, """ +
                """in role "$role".""",
        )
        appendLine(
            """file() takes a file name with its extension, such as "GetUserUseCase.kt". The """ +
                "directory it lands in comes from that role's layout { }, so spelling it here " +
                "would be the same thing said in two places that can disagree.",
        )
        append("Give file() a plain file name, and declare where it lives in layout { }.")
    },
)

/**
 * One template declared two files of the same name.
 *
 * The second would decide what the first wrote, so what ends up on disk would depend on the
 * order the two lines happen to be in.
 *
 * ## Example 1: catch a file name declared twice in one template
 * ```kt
 * import io.kotest.assertions.throwables.shouldThrow
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.processor.process
 * import me.tbsten.katachi.template.GenerateCodeFromTemplate
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             template {
 *                 file("GetUserUseCase.kt") { "" }
 *                 file("GetUserUseCase.kt") { "" }
 *             }
 *         }
 *     }
 * }
 *
 * // The repeat is found when the template runs, not when the definition is written: the
 * // block above is kept and called then.
 * val thrown = shouldThrow<KatachiDuplicateTemplateFileException> {
 *     arch.process(
 *         GenerateCodeFromTemplate,
 *         GenerateCodeFromTemplate.Args(roleName = "UseCase"),
 *     )
 * }
 * thrown.fileName shouldBe "GetUserUseCase.kt"
 * ```
 *
 * @see TemplateScope.file
 */
public class KatachiDuplicateTemplateFileException internal constructor(
    /** The role whose template declared both. */
    public val role: String,
    /** The name declared twice. */
    public val fileName: String,
    /** Where the first `file(...)` of that name was written. */
    public val firstDeclaredAt: DeclarationSite,
    /** Where the refused second one was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Duplicate template file "$fileName" declared at $declaredAt, in role "$role".""",
        )
        appendLine(
            "A file of that name was already declared at $firstDeclaredAt, and the second one " +
                "would decide what the first wrote.",
        )
        append(
            "Rename one of them, or build the name from a parameter so that the two differ.",
        )
    },
)

/**
 * A `template { }` that declares no file.
 *
 * Running it would report success and write nothing, which is the shape of failure katachi
 * exists to remove.
 *
 * It is raised when the template is replayed rather than where it was written, because the
 * block is stored and only runs when something asks it for files.
 *
 * ## Example 1: declare a template that produces nothing
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase" { template { } } }
 * }
 * // Declaring it is fine; `--processor=template --arg roleName=UseCase` is what refuses it.
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see TemplateScope.file
 */
public class KatachiEmptyTemplateException internal constructor(
    /** The role whose template produces nothing. */
    public val role: String,
    /** Where `template { }` was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("""Template of role "$role" declared at $declaredAt produces no file.""")
        appendLine(
            "A template exists to write files and this one calls file() no times, so running " +
                "it would report success and write nothing.",
        )
        append("""Declare at least one file("...") { } in it.""")
    },
)
