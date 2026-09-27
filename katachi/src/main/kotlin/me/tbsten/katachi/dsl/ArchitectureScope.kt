package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.DeclaredNames
import me.tbsten.katachi.dsl.internal.MetadataBuilder
import me.tbsten.katachi.dsl.internal.captureDeclarationSite
import me.tbsten.katachi.dsl.internal.declareGroup
import me.tbsten.katachi.dsl.internal.declareRole

/**
 * Receiver of `architecture { }`.
 *
 * It holds groups and roles, exactly as a group block does — see [DeclarationContainerScope].
 * The root is a container like any other, so a small definition, or the first example anyone
 * reads, does not have to invent a group before it can name a role.
 *
 * ## Example 1: declare groups and roles inside architecture { }
 * ```kt
 * val arch = architecture {
 *     "Readme" { layout { "README.md".file() } }
 *     "domain".group {
 *         "UseCase" { }
 *     }
 * }
 * ```
 */
@KatachiDsl
public sealed interface ArchitectureScope : DeclarationContainerScope {
    /**
     * What the whole definition is called: the heading of the generated root page.
     *
     * Written here rather than passed in by whoever runs the generator, because it is a fact
     * about the project and not about one run of one tool. `null` or unwritten leaves the root
     * page its own default heading.
     *
     * ## Example 1: name the definition
     * ```kt
     * val arch = architecture {
     *     title = "myapp"
     *     "domain".group { "UseCase" { } }
     * }
     * arch[Title] shouldBe "myapp"
     * ```
     */
    public var title: String?

    /**
     * One paragraph under the root page's heading, as Markdown, kept exactly as written.
     *
     * The place to say what the repository is and how to read what follows. Unwritten, the root
     * page goes straight from its heading to the document map.
     *
     * ## Example 1: describe the repository on its root page
     * ```kt
     * val arch = architecture {
     *     title = "myapp"
     *     description = "このリポジトリの構成。役割ごとに1ページある。"
     *     "domain".group { "UseCase" { } }
     * }
     * arch[Description] shouldBe "このリポジトリの構成。役割ごとに1ページある。"
     * ```
     */
    public var description: String?

    /**
     * Which files of the project the check looks at. Defaults to [gitTracked].
     *
     * ## Example 1: walk the whole tree instead of only git-tracked files
     * ```kt
     * val arch = architecture {
     *   files = wholeTree()
     *   "domain".group { "UseCase" { } }
     * }
     * arch.files shouldBe FileSelection.WholeTree
     * ```
     */
    public var files: FileSelection

    /**
     * How a module path written in a `layout { }` becomes a directory. Defaults to
     * [ModuleResolver.Conventional].
     *
     * ## Example 1: replace the resolution rule for module paths
     * ```kt
     * val architecture = architecture {
     *   moduleResolver = ModuleResolver { module ->
     *     if (module.value == ":app") "apps/android" else module.segments.joinToString("/")
     *   }
     * }
     * ```
     */
    public var moduleResolver: ModuleResolver

    /**
     * The ledger of violations to hold back, or `null` -- the default -- to hold back nothing.
     * See [Baseline].
     *
     * ## Example 1: hold back the violations recorded in katachi-baseline.json
     * ```kt
     * val arch = architecture {
     *     baseline = baselineFile()
     *     "Baseline" { layout { "katachi-baseline.json".file() } }
     * }
     * arch.baseline?.path shouldBe "katachi-baseline.json"
     * ```
     */
    @ExperimentalKatachiApi
    public var baseline: Baseline?
}

internal class ArchitectureScopeImpl : ArchitectureScope {
    val metadata = MetadataBuilder()

    private val groups = mutableListOf<Group>()
    private val roles = mutableListOf<Role>()

    /**
     * One namespace for groups and roles alike, exactly as a group block keeps one.
     *
     * A top level group's `qualifiedName` is its bare name, and so is a root role's, so
     * `"domain".group { }` and `"domain" { }` would both answer to `"domain"` and nothing
     * could say which one a reference means. Sharing the reservations is how that is said.
     */
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

    override var moduleResolver: ModuleResolver = ModuleResolver.Conventional

    override var baseline: Baseline? = null

    override fun String.group(block: GroupScope.() -> Unit) {
        groups += declareGroup(
            name = this,
            parentPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    override operator fun String.invoke(block: RoleScope.() -> Unit) {
        roles += declareRole(
            name = this,
            groupPath = emptyList(),
            declaredAt = captureDeclarationSite(),
            declaredNames = declaredNames,
            block = block,
        )
    }

    fun build(): Architecture = Architecture(
        groups = groups.toList(),
        roles = roles.toList(),
        files = files,
        moduleResolver = moduleResolver,
        metadata = metadata.build(),
        baseline = baseline,
    )
}
