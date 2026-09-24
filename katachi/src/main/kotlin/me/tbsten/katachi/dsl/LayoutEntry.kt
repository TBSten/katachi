package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi

/**
 * What a flattened layout entry declares about the path it names.
 *
 * ## Example 1: tell the declared directories from the declared files
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
 * }
 * arch.process { context -> context.declaredEntries.map { it.kind } } shouldContainExactly
 *     listOf(LayoutEntryKind.Directory, LayoutEntryKind.File)
 * ```
 */
@ExperimentalKatachiApi
public enum class LayoutEntryKind {
    /**
     * A directory, `"..." { }`. Its contents are whatever the entries below it declare, so
     * an empty block means nothing may live there.
     *
     * ## Example 1: read back a directory a layout declared
     * ```kt
     * val arch = architecture { "domain".group { "UseCase" { layout { "useCase" { } } } } }
     * arch.process { context -> context.declaredEntries.single().kind } shouldBe
     *     LayoutEntryKind.Directory
     * ```
     */
    Directory,

    /**
     * A file, `"...".file()`. [LayoutEntry.path] may hold `*` or `**`.
     *
     * ## Example 1: collect every path a definition declares as a file
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { context ->
     *     context.declaredEntries.filter { it.kind == LayoutEntryKind.File }.map { it.path }
     * } shouldContainExactly listOf("useCase/GetUserUseCase.kt")
     * ```
     */
    File,

    /**
     * A directory that also allows any file directly inside it, `anyFile()`. A file one
     * level further down is not covered.
     *
     * ## Example 1: find the directories a definition left open
     * ```kt
     * val arch = architecture { "build".group { "Generated" { layout { "generated" { anyFile() } } } } }
     * arch.process { context ->
     *     context.declaredEntries.filter { it.kind == LayoutEntryKind.AnyFile }.map { it.path }
     * } shouldContainExactly listOf("generated")
     * ```
     */
    AnyFile,

    /**
     * A directory nothing below is checked in, at any depth, `ignore()`.
     *
     * ## Example 1: list what a definition stops looking at
     * ```kt
     * val arch = architecture { "build".group { "Output" { layout { "build".ignore() } } } }
     * arch.process { context ->
     *     context.declaredEntries.filter { it.kind == LayoutEntryKind.Ignore }.map { it.path }
     * } shouldContainExactly listOf("build")
     * ```
     */
    Ignore,
}

/**
 * One line of the flattened layout: a path pattern, and the role that claims it.
 *
 * Roles declare where their own files may live and nothing else, so no single place in the
 * DSL holds the whole tree. The check builds that view by flattening every role's
 * `layout { }` into these entries and walking the real tree against them, and a processor
 * reads the same view through [me.tbsten.katachi.processor.ArchitectureProcessContext.declaredEntries].
 *
 * It says what was *declared*, not what exists: an entry is here whether or not a file sits
 * at its path. What exists is
 * [me.tbsten.katachi.processor.ArchitectureProcessContext.filesOf]'s answer instead.
 *
 * ## Example 1: render the declared layout as text
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
 * }
 * arch.process { context ->
 *     context.declaredEntries.map { "${it.role.qualifiedName} ${it.path} ${it.kind}" }
 * } shouldContainExactly listOf(
 *     "domain/UseCase useCase Directory",
 *     "domain/UseCase useCase/GetUserUseCase.kt File",
 * )
 * ```
 */
@ExperimentalKatachiApi
public class LayoutEntry internal constructor(
    /**
     * Path pattern relative to the project root, `/` separated, never starting with `/`.
     *
     * ## Example 1: read the declared paths
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { context -> context.declaredEntries.map { it.path } } shouldContainExactly
     *     listOf("useCase", "useCase/GetUserUseCase.kt")
     * ```
     */
    public val path: String,
    /** [path] compiled. `Glob.hasWildcard` is what made a declaration optional by itself. */
    @property:InternalKatachiApi public val glob: Glob,
    /**
     * What the declaration says about this path.
     *
     * ## Example 1: keep only the files a definition declared
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { context ->
     *     context.declaredEntries.filter { it.kind == LayoutEntryKind.File }.map { it.path }
     * } shouldContainExactly listOf("useCase/GetUserUseCase.kt")
     * ```
     */
    public val kind: LayoutEntryKind,
    /**
     * Whether a missing file at this path is a violation.
     *
     * Only a [LayoutEntryKind.File] is ever required: git does not track an empty
     * directory, so a directory only shows up once it holds a file, and the files it holds
     * report it themselves. A file holding a wildcard, or marked `optional()`, is not
     * required either.
     *
     * ## Example 1: list the files a definition insists on
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile(); "useCase" / "*.kt".file() } }
     *     }
     * }
     * arch.process { context -> context.declaredEntries.filter { it.required }.map { it.path } } shouldContainExactly
     *     listOf("useCase/GetUserUseCase.kt")
     * ```
     */
    public val required: Boolean,
    /**
     * The role that declared this path.
     *
     * ## Example 1: group the declared paths by the role that claims them
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { context ->
     *     context.declaredEntries.groupBy({ it.role.qualifiedName }, { it.path })
     * } shouldBe mapOf("domain/UseCase" to listOf("useCase", "useCase/GetUserUseCase.kt"))
     * ```
     */
    public val role: Role,
    /**
     * Where inside the `layout { }` block this path was written.
     *
     * ## Example 1: point a report back at the line that declared a path
     * ```kt
     * val arch = architecture {
     *     "domain".group { "UseCase" { layout { "useCase" / "GetUserUseCase".ktFile() } } }
     * }
     * arch.process { context -> context.declaredEntries.first().declaredAt.fileName } shouldBe
     *     "ProjectArchitecture.kt"
     * ```
     */
    public val declaredAt: DeclarationSite,
    /**
     * The `description = "..."` of this directory, when it has one.
     *
     * ## Example 1: read the descriptions a definition wrote on its directories
     * ```kt
     * val arch = architecture {
     *     "domain".group {
     *         "UseCase" { layout { "useCase" { description = "Behavior for each screen" } } }
     *     }
     * }
     * arch.process { context -> context.declaredEntries.single().description } shouldBe "Behavior for each screen"
     * ```
     */
    public val description: String?,
    /**
     * Whether this entry is one of the two lines a `module { }` block injects (`build`, and
     * `build.gradle.kts`) rather than something the user wrote.
     *
     * Internal: it exists for `ambiguousLayoutsOf` to drop what the sugar duplicates on
     * every role that shares a module, not for a processor to read. See
     * [me.tbsten.katachi.dsl.LayoutNode.synthetic], which this carries forward, and
     * `mergedWith`, which keeps it `true` only while every declaration of this path stayed
     * synthetic: a user who wrote `"build.gradle.kts".file()` themselves takes that path back.
     */
    internal val synthetic: Boolean,
    /**
     * Whether this entry is one of the places its role may live in — the directory a
     * `"...".module { }` key opened, for a module other than the root project.
     *
     * Internal: it exists for `missingDescriptionsOf` to find the blocks a `description = "..."`
     * could be written in, not for a processor to read. See [me.tbsten.katachi.dsl.LayoutNode.place],
     * which this carries forward and whose KDoc says why only module keys carry it, and
     * `mergedWith`, which ORs it: one declaration opening a block at a path is enough to make it
     * somewhere a description belongs.
     */
    internal val place: Boolean,
    /**
     * The Gradle module path this entry sits in, as written in the `module { }` key, or `null`
     * when no module key opened the block it was declared in.
     *
     * Internal: it exists so that documentation can show `:core:domain` next to a path that has
     * that module's directory taken off the front, not for a processor to read. A wildcard key
     * stays as it was written, because [me.tbsten.katachi.dsl.ModuleIndex] leaves it that way
     * when nothing has listed the project.
     */
    internal val modulePath: String?,
    /**
     * [path] as the declaration reads inside its module: the module's own directory taken off
     * the front, and every level a module package produced folded into a single `**`.
     *
     * Equal to [path] when [modulePath] is `null` or names the root project, neither of which
     * has a directory of its own to take off.
     *
     * Internal for the same reason [modulePath] is. It is derived while the tree is flattened
     * because that is the last moment the marks survive -- a flattened path is a string, and a
     * string cannot say which of its levels the user wrote.
     */
    internal val pathInModule: String,
) {
    override fun toString(): String =
        "LayoutEntry($path, $kind, ${role.qualifiedName}${if (required) ", required" else ""})"
}
