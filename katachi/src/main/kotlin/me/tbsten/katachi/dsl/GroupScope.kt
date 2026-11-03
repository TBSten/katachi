package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.DeclaredNames
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.MetadataBuilder
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.declareGroup
import me.tbsten.katachi.dsl.internal.declareRole
import me.tbsten.katachi.dsl.internal.declareTemplateSet
import org.intellij.lang.annotations.Language

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
 *         description = """
 *             ### What does not belong here
 *             - Anything about how the product is delivered
 *         """.trimIndent()
 *         "UseCase".role { }
 *         "model".group { }
 *     }
 *     "build".group {
 *         documented = false // left out of the generated documentation, still checked
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface GroupScope : DeclarationContainerScope {
    /**
     * Display name of this group. Defaults to the group name.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var title: String

    /**
     * One paragraph describing what this group is for.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var summary: String?

    /**
     * Free-form Markdown about this group: what it is, what belongs in it, what does not.
     *
     * Write [summary] for the one line, and this for everything that needs more room. The text is
     * kept exactly as written, newlines included, and katachi adds no structure of its own.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var description: String?

    /**
     * Set to `false` to keep this group out of the generated documentation. It still takes
     * part in the check.
     *
     * The generated documentation leaves out everything inside the group with it: its roles and
     * nested groups, whatever they wrote themselves. The value itself is not copied onto them
     * (each keeps what it wrote, as all metadata does), so a processor of your own that reads it
     * walks up [Role.groupPath].
     */
    public var documented: Boolean
}

internal class GroupScopeImpl(private val path: List<String>) : GroupScope {
    val metadata = MetadataBuilder()

    val groups = mutableListOf<Group>()
    val roles = mutableListOf<Role>()
    val templateSets = mutableListOf<TemplateSetDeclaration>()

    // One namespace for groups and roles alike, the same as the root of `architecture { }`.
    // `"x".group { "domain".group { }; "domain".role { } }` gives both the qualified name
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
        declareGroupAt(name = this, declaredAt = captureDeclarationSite(), block = block)
    }

    override fun String.role(block: RoleScope.() -> Unit) {
        declareRoleAt(name = this, declaredAt = captureDeclarationSite(), block = block)
    }

    override fun String.templateSet(block: TemplateSetScope.() -> Unit) {
        declareTemplateSetAt(name = this, declaredAt = captureDeclarationSite(), block = block)
    }

    fun declareGroupAt(name: String, declaredAt: DeclarationSite, block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = name,
            parentPath = path,
            declaredAt = declaredAt,
            declaredNames = declaredNames,
            block = block,
        )
    }

    fun declareRoleAt(name: String, declaredAt: DeclarationSite, block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = name,
            groupPath = path,
            declaredAt = declaredAt,
            declaredNames = declaredNames,
            block = block,
        )
    }

    fun declareTemplateSetAt(name: String, declaredAt: DeclarationSite, block: TemplateSetScope.() -> Unit) {
        templateSets += declareTemplateSet(
            name = name,
            groupPath = path,
            declaredAt = declaredAt,
            declaredNames = declaredNames,
            block = block,
        )
    }
}
