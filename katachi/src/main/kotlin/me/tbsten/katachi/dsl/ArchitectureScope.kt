package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.DeclaredNames
import me.tbsten.katachi.dsl.internal.MetadataBuilder
import me.tbsten.katachi.dsl.internal.TemplateSetDeclaration
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.declareGroup
import me.tbsten.katachi.dsl.internal.declareRole
import me.tbsten.katachi.dsl.internal.declareTemplateSet
import me.tbsten.katachi.dsl.internal.isValidBaselinePath
import org.intellij.lang.annotations.Language

/**
 * Receiver of `architecture { }`.
 *
 * It holds groups and roles exactly as a group block does (see [DeclarationContainerScope]),
 * so a small definition does not have to invent a group before it can name a role.
 *
 * ## Example 1: declare groups and roles inside architecture { }
 * ```kt
 * val arch = architecture {
 *     title = "myapp"
 *     description = "How this repository is laid out. One page per role."
 *     files = wholeTree()
 *     baseline()
 *
 *     "BaselineFile".role { layout { "katachi-baseline.json".file() } }
 *     "Readme".role { layout { "README.md".file() } }
 *     "domain".group {
 *         "UseCase".role { }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface ArchitectureScope : DeclarationContainerScope {
    /**
     * What the whole definition is called: the heading of the generated root page.
     *
     * `null` or unwritten leaves the root page its own default heading.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var title: String?

    /**
     * One paragraph under the root page's heading, as Markdown, kept exactly as written.
     *
     * Say what the repository is and how to read what follows. Unwritten, the root page goes
     * straight from its heading to the document map.
     */
    @get:Language("markdown")
    @set:Language("markdown")
    public var description: String?

    /** Which files of the project the check looks at. Defaults to [gitTracked]. */
    public var files: FileSelection

    /**
     * Holds back the violations recorded in the baseline file at [path], relative to the project
     * root. Without a call, nothing is held back. See [Baseline].
     *
     * Only the file is named here; it is written when a run is given
     * `-Dkatachi.baseline.update=true`, and a `layout { }` has to allow it like any other file.
     * Called again, the later path replaces the earlier one.
     *
     * @param path relative to the project root, `/` separated, written the one way it can be: no
     *   `.` or `..` segment, no empty segment and no trailing `/`.
     * @throws KatachiInvalidBaselinePathException when [path] is empty, absolute, contains a `\`,
     *   or is not written the one way it can be.
     */
    @ExperimentalKatachiApi
    public fun baseline(path: String = "katachi-baseline.json")
}

internal class ArchitectureScopeImpl : ArchitectureScope {
    val metadata = MetadataBuilder()

    private val groups = mutableListOf<Group>()
    private val roles = mutableListOf<Role>()
    private val templateSets = mutableListOf<TemplateSetDeclaration>()

    // Groups and roles share one namespace: a top-level group and a root role have the same qualifiedName.
    private val declaredNames = DeclaredNames()

    override var title: String?
        get() = metadata[Title]
        set(value) {
            metadata[Title] = value
        }

    override var description: String?
        get() = metadata[Description]
        set(value) {
            metadata[Description] = value
        }

    override var files: FileSelection = FileSelection.GitTracked

    private var baseline: Baseline? = null

    override fun baseline(path: String) {
        val declaredAt = captureDeclarationSite()
        if (!isValidBaselinePath(path)) throw KatachiInvalidBaselinePathException(path, declaredAt)
        baseline = Baseline(path, declaredAt)
    }

    override fun String.group(block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    override fun String.role(block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = this,
            groupPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    override fun String.templateSet(block: TemplateSetScope.() -> Unit) {
        templateSets += declareTemplateSet(
            name = this,
            groupPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    fun build(declaredAt: DeclarationSite = DeclarationSite.Unknown): Architecture = Architecture(
        groups = groups.toList(),
        roles = roles.toList(),
        files = files,
        moduleResolver = ModuleResolver.Conventional,
        metadata = metadata.build(),
        baseline = baseline,
        declaredAt = declaredAt,
        templateSets = templateSets.toList(),
    )
}
