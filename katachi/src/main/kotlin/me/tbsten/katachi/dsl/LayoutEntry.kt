package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.MetadataValues

/**
 * What a flattened layout entry declares about the path it names.
 *
 * ## Example 1: tell the declared directories from the declared files
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase".role { layout { "useCase" / "GetUserUseCase".ktFile() } } }
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
     */
    Directory,

    /**
     * A file, `"...".file()`. [LayoutEntry.path] may hold `*` or `**`.
     */
    File,

    /**
     * A directory that also allows any file directly inside it, `anyFile()`. A file one
     * level further down is not covered.
     */
    AnyFile,

    /**
     * A directory nothing below is checked in, at any depth, `ignore()`.
     */
    Ignore,
}

/**
 * One line of the flattened layout: a path pattern, and the role that claims it.
 *
 * Every role's `layout { }` is flattened into these entries, and a processor reads them through
 * [ArchitectureProcessContext.declaredEntries][me.tbsten.katachi.processor.ArchitectureProcessContext.declaredEntries].
 *
 * It says what was *declared*, not what exists: an entry is here whether or not a file sits
 * at its path. What exists is
 * [ArchitectureProcessContext.filesOf][me.tbsten.katachi.processor.ArchitectureProcessContext.filesOf]'s answer instead.
 *
 * ## Example 1: render the declared layout as text
 * ```kt
 * val arch = architecture {
 *     "domain".group { "UseCase".role { layout { "useCase" / "GetUserUseCase".ktFile() } } }
 * }
 * arch.process { context ->
 *     context.declaredEntries.map { "${it.role.qualifiedName} ${it.path} ${it.kind}" }
 * } shouldContainExactly listOf(
 *     "domain.UseCase useCase Directory",
 *     "domain.UseCase useCase/GetUserUseCase.kt File",
 * )
 *
 * // Files the definition insists on, and where each was declared
 * arch.process { context ->
 *     context.declaredEntries.filter { it.required }.map { "${it.path} ${it.declaredAt} ${it.description}" }
 * }
 * ```
 */
@ExperimentalKatachiApi
public class LayoutEntry internal constructor(
    /**
     * Path pattern relative to the project root, `/` separated, never starting with `/`.
     */
    public val path: String,
    /**
     * [path][LayoutEntry.path] compiled into a matcher, for katachi's own tooling.
     */
    @property:InternalKatachiApi public val glob: Glob,
    /**
     * What the declaration says about this path.
     */
    public val kind: LayoutEntryKind,
    /**
     * Whether a missing file at this path is a violation.
     *
     * Only a [LayoutEntryKind.File] is ever required: git does not track an empty
     * directory, so a directory only shows up once it holds a file, and the files it holds
     * report it themselves. A file holding a wildcard, or marked `optional()`, is not
     * required either.
     */
    public val required: Boolean,
    /**
     * The role that declared this path.
     */
    public val role: Role,
    /**
     * Where inside the `layout { }` block this path was written.
     */
    public val declaredAt: DeclarationSite,
    /**
     * The `description = "..."` of this directory, when it has one.
     */
    public val description: String?,
    // Marks the two lines `module { }` injects; mergedWith keeps it true only while every declaration of this path
    // stayed synthetic.
    internal val synthetic: Boolean,
    // Only module-key directories (non-root) carry it; mergedWith ORs it.
    internal val place: Boolean,
    internal val modulePath: String?,
    // Derived while flattening: afterwards a path is a string and cannot say which levels were module package
    // marks.
    internal val pathInModule: String,
    // More than one variant is possible: the same path declared twice folds into one entry, with NONE kept beside
    // named variants.
    internal val captureVariants: List<LayoutCaptures> = emptyList(),
    // Read this, not captureVariants.firstOrNull(): variant order follows declaration order and could pick the
    // untemplated one.
    internal val templateCaptures: LayoutCaptures? = null,
    internal val metadata: MetadataValues = MetadataValues.EMPTY,
) {
    /**
     * The value written under [key] with `.metadata { }`, or `null` when this declaration does
     * not carry that key.
     *
     * The declared value, as written: a directory's metadata is not inherited by the entries
     * below it, and two declarations of the same path that wrote the same value under a key
     * keep it once, so which of them "wins" is never a question a reader has to ask.
     *
     * ## Example 1: read metadata a definition attached to one of its declared files
     * ```kt
     * val Obsolete: MetadataKey<Boolean> = metadata()
     * var MetadataScope.obsolete: Boolean? by Obsolete
     *
     * val arch = architecture {
     *     "legacy".group {
     *         "Dao".role { layout { "legacy" / "*Dao".ktFile().metadata { obsolete = true } } }
     *     }
     * }
     * arch.process { context ->
 *     context.declaredEntries.single { it.kind == LayoutEntryKind.File }[Obsolete]
 * } shouldBe true
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    /** The path, the kind and the role, plus `required` when it is. */
    override fun toString(): String =
        "LayoutEntry($path, $kind, ${role.qualifiedName}${if (required) ", required" else ""})"
}
