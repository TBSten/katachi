package me.tbsten.katachi.dsl

import me.tbsten.katachi.KatachiDeclarationException

/**
 * `.template { }` was attached to the same file declaration twice: once directly, by calling it
 * twice on the same value, or once each on two declarations of the same path.
 *
 * A file has one template so that `--arg template=` never has to be told which of them was
 * meant. Two file names built to differ -- Repository.kt and RepositoryImpl.kt -- are two
 * declarations, each with its own `.template { }`; a role that really wants two ways to fill
 * in the *same* path has nowhere for the second to go.
 *
 * ## Example 1: catch a file declaration that attached two templates
 * ```kt
 * val thrown = shouldThrow<KatachiDuplicateTemplateException> {
 *     architecture {
 *         "domain".group {
 *             "UseCase" {
 *                 layout {
 *                     "useCase" / "${capture("name")}UseCase.kt".file()
 *                         .template { "a" }
 *                         .template { "b" }
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }
 * thrown.role shouldBe "domain.UseCase"
 * ```
 *
 * @see LayoutFile.template
 */
public class KatachiDuplicateTemplateException internal constructor(
    /** The role that declared both, qualified. */
    public val role: String,
    /** The path the two attachments claim. */
    public val path: String,
    /** Where the template it already had was written. */
    public val firstDeclaredAt: DeclarationSite,
    /** Where the refused second `.template { }` was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """"$path" of role "$role" is given a second `.template { }` at $declaredAt.""",
        )
        appendLine(
            "It already has one, written at $firstDeclaredAt, and a declared file has one " +
                "template so that `--arg template=` never has to be told which of them was meant.",
        )
        append(
            "Merge the two blocks into one `.template { }`, or, if they are meant to produce " +
                "different files, declare a second path for the other one.",
        )
    },
)

/**
 * `.template(id = "...")` was given an id already used by another template of the same role.
 *
 * `--arg template=` selects by `role.id`, so two templates answering to the same id could never
 * be told apart. The same [LayoutTemplate] instance repeated by a wildcard module key's
 * expansion is not a duplicate: every module gets one template with the same id, by design.
 *
 * ## Example 1: catch two templates of one role sharing an id
 * ```kt
 * val thrown = shouldThrow<KatachiDuplicateTemplateIdException> {
 *     architecture {
 *         "data".group {
 *             "Repository" {
 *                 layout {
 *                     "repository" / "${capture("name")}Repository.kt".file()
 *                         .template(id = "repository") { "" }
 *                     "repository" / "${capture("name")}RepositoryImpl.kt".file()
 *                         .template(id = "repository") { "" }
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }
 * thrown.id shouldBe "repository"
 * ```
 *
 * @see LayoutFile.template
 */
public class KatachiDuplicateTemplateIdException internal constructor(
    /** The role both templates belong to, qualified. */
    public val role: String,
    /** The id claimed twice. */
    public val id: String,
    /** Where the template that took the id first was written. */
    public val firstDeclaredAt: DeclarationSite,
    /** Where the refused second one was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Duplicate template id "$id" declared at $declaredAt, in role "$role".""",
        )
        appendLine(
            "It was already given to another template at $firstDeclaredAt, and --arg " +
                "template=$role.$id could never say which of them was meant.",
        )
        append("Give one of them a different id.")
    },
)

/**
 * A role's second `.template { }` left out `id`, when its first one did too.
 *
 * An id may be left out only while a role has one template, where `--arg template=$role` needs
 * no further choosing. The moment a second template joins it, every template of that role has
 * to be reachable by id, so the one that omitted it is refused -- pointing at the declaration
 * that made the omission ambiguous, not at the role's first template, which was fine on its own.
 *
 * ## Example 1: catch a role whose second template left out `id`
 * ```kt
 * val thrown = shouldThrow<KatachiMissingTemplateIdException> {
 *     architecture {
 *         "data".group {
 *             "Repository" {
 *                 layout {
 *                     "repository" / "${capture("name")}Repository.kt".file()
 *                         .template { "" }
 *                     "repository" / "${capture("name")}RepositoryImpl.kt".file()
 *                         .template(id = "impl") { "" }
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }
 * thrown.role shouldBe "data.Repository"
 * ```
 *
 * @see LayoutFile.template
 */
public class KatachiMissingTemplateIdException internal constructor(
    /** The role with more than one template, qualified. */
    public val role: String,
    /** Where the template that left out `id` was written. */
    public val declaredAt: DeclarationSite,
    /** The ids the role's other templates were given. */
    public val otherIds: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """Template of role "$role" declared at $declaredAt has no id, but the role has """ +
                "more than one template.",
        )
        appendLine(
            "An id may be left out only while a role has one template. Once a second one joins " +
                "it, --arg template=$role alone could not say which was meant.",
        )
        append(
            "Give it an id, such as .template(id = \"...\") { }. The role's other id(s): " +
                otherIds.joinToString(", ") { "\"$it\"" } + ".",
        )
    },
)

/**
 * `.template { }` was attached to a declaration whose path still holds a `*` or a `**` that no
 * `capture("...")` names.
 *
 * Generating a file needs a value for every level its path leaves open, and an unnamed wildcard
 * has nowhere for that value to come from: no `--arg` name reaches it.
 *
 * ## Example 1: catch a template on a path with an unnamed wildcard
 * ```kt
 * val thrown = shouldThrow<KatachiTemplateOnWildcardException> {
 *     architecture {
 *         "data".group {
 *             "Repository" {
 *                 layout {
 *                     "repository" / "*Repository.kt".file()
 *                         .template { "" }
 *                 }
 *             }
 *         }
 *     }.flattenLayout()
 * }
 * thrown.role shouldBe "data.Repository"
 * ```
 *
 * @see LayoutFile.template
 */
public class KatachiTemplateOnWildcardException internal constructor(
    /** The role whose declaration this is, qualified. */
    public val role: String,
    /** The declared path, with every named level shown as `${capture("name")}`. */
    public val path: String,
    /** Where `.template { }` was written. */
    public val declaredAt: DeclarationSite,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            """`.template { }` at $declaredAt is attached to "$path" of role "$role", whose """ +
                "path still holds an unnamed wildcard.",
        )
        appendLine(
            "Generating a file needs a value for every level the path leaves open, and an " +
                "unnamed * or ** has no --arg name for that value to arrive as.",
        )
        append(
            "Name every remaining wildcard with capture(\"...\"), such as " +
                "\"\${capture(\"name\")}Repository.kt\".file(), or drop the ones that stay a `*` " +
                "into a level of their own so the file this template describes has a single path.",
        )
    },
)
