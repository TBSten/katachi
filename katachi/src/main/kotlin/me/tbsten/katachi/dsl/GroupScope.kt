package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.DeclaredNames
import me.tbsten.katachi.dsl.internal.MetadataBuilder
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.declareGroup
import me.tbsten.katachi.dsl.internal.declareRole

/**
 * Receiver of `"name".group { }`. Holds nested groups and roles.
 *
 * [title], [summary], [description] and [documented] are sugar over the [Title], [Summary],
 * [Description] and [Documented] metadata keys, the same way the role block's properties are.
 * They are spelled and mean exactly what they do on a role: a group is a thing a reader arrives
 * at and asks "what is this" about, and answering that in two different vocabularies would be
 * two things to learn for one question.
 *
 * ## Example 1: declare nested groups and roles
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         title = "Domain"
 *         summary = "The rules of the product, with nothing about how they are delivered"
 *         "UseCase" { }
 *         "model".group { }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface GroupScope : DeclarationContainerScope {
    /**
     * Display name of this group. Defaults to the group name.
     *
     * ## Example 1: separate the identifier from the display name
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         title = "Domain"
     *     }
     * }
     * arch.groups.single()[Title] shouldBe "Domain"
     * ```
     */
    public var title: String

    /**
     * One paragraph describing what this group is for.
     *
     * ## Example 1: set a summary
     * ```kt
     * val arch = architecture {
     *     "api".group { summary = "Everything that faces HTTP" }
     * }
     * arch.groups.single()[Summary] shouldBe "Everything that faces HTTP"
     * ```
     */
    public var summary: String?

    /**
     * Free-form Markdown about this group: what it is, what belongs in it, what does not.
     *
     * Write [summary] for the one line, and this for everything that needs more room. The text is
     * kept exactly as written, newlines included, and katachi adds no structure of its own.
     *
     * ## Example 1: give a group a body with sections of its own
     * ```kt
     * val arch = architecture {
     *     "api".group {
     *         description = """
     *             Everything that faces HTTP is here.
     *
     *             ### What does not belong here
     *             - A decision the domain should be making
     *         """.trimIndent()
     *     }
     * }
     * arch.groups.single()[Description].orEmpty().lines().size shouldBe 4
     * ```
     */
    public var description: String?

    /**
     * Set to `false` to keep this group out of the generated documentation. It still takes
     * part in the check.
     *
     * The value is not inherited: a role inside a group that opted out has to say so for
     * itself, and so does a nested group.
     *
     * ## Example 1: keep a group out of the generated documentation
     * ```kt
     * val arch = architecture {
     *     "build".group {
     *         documented = false
     *         "VersionCatalog" { documented = false }
     *     }
     * }
     * arch.groups.single()[Documented] shouldBe false
     * ```
     */
    public var documented: Boolean
}

internal class GroupScopeImpl(private val path: List<String>) : GroupScope {
    val metadata = MetadataBuilder()

    val groups = mutableListOf<Group>()
    val roles = mutableListOf<Role>()

    // One namespace for groups and roles alike, the same as the root of `architecture { }`.
    // `"x".group { "domain".group { }; "domain" { } }` gives both the qualified name
    // "x/domain", so a reference to it could not say which one it means.
    private val declaredNames = DeclaredNames()

    override var title: String
        get() = metadata[Title] ?: path.last()
        set(value) {
            metadata[Title] = value
        }

    override var summary: String?
        get() = metadata[Summary]
        set(value) {
            metadata[Summary] = value
        }

    override var description: String?
        get() = metadata[Description]
        set(value) {
            metadata[Description] = value
        }

    override var documented: Boolean
        get() = metadata[Documented] ?: true
        set(value) {
            metadata[Documented] = value
        }

    override fun String.group(block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = path,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    override operator fun String.invoke(block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = this,
            groupPath = path,
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }
}
