package me.tbsten.katachi.intellij.model

import java.nio.file.Path
import java.time.Instant

/**
 * What `definition.json` says (design 1.x), without IntelliJ types: the architecture, its groups and
 * roles with their layout entries and file constraints. The templates part of the JSON is read into
 * [TemplateModel]s next to it, not in here.
 *
 * ```kotlin
 * val parsed = parseDefinitionJson(text, file)
 * val roleNames = parsed.definition.roles.map { it.qualifiedName }
 * ```
 */
internal data class DefinitionModel(
    /** The walk's project root, absolute with `/`. `null` when the walk could not run. */
    val projectRoot: Path?,
    val architecture: ArchitectureModel,
    val groups: List<GroupModel>,
    val roles: List<RoleModel>,
    val problems: List<DefinitionProblem>,
    val katachiVersion: String,
    /** When the task wrote the file. The gutter compares it with a file's modification time to say the file is newer than the definition. */
    val generatedAt: Instant,
)

/** `architecture { }`. [files] is `"gitTracked"`, `"wholeTree"` or a `FileSelection` class name, as written. */
internal data class ArchitectureModel(
    val declaredAt: DeclarationSiteRef?,
    val files: String,
    /** The baseline file relative to the project root, or `null`. */
    val baseline: String?,
) {
    /** Whether katachi sees only the files git tracks (`gitTracked()`). */
    val isGitTracked: Boolean get() = files == FILES_GIT_TRACKED

    companion object {
        const val FILES_GIT_TRACKED: String = "gitTracked"
        const val FILES_WHOLE_TREE: String = "wholeTree"
    }
}

/** Where in the definition a thing was written: the file's name and a 1-based line. The directory is not known. */
internal data class DeclarationSiteRef(val fileName: String, val line: Int)

internal data class GroupModel(
    val qualifiedName: String,
    val name: String,
    val path: List<String>,
    val title: String?,
    val summary: String?,
    /** `Documented`; `null` when not written (read as `true`). */
    val documented: Boolean?,
    val declaredAt: DeclarationSiteRef?,
)

internal data class RoleModel(
    /** `domain.UseCase`, the same spelling as a template's `roleName`. */
    val qualifiedName: String,
    val name: String,
    val groupPath: List<String>,
    val title: String?,
    val summary: String?,
    val documented: Boolean?,
    val declaredAt: DeclarationSiteRef?,
    val layout: List<LayoutEntryModel>,
    val constraints: List<ConstraintModel>,
)

/** One `layout { }` entry of a role, with its glob already resolved (wildcard module keys expanded). */
internal data class LayoutEntryModel(
    /** The position in the role's `layout`; [ConstraintModel.entryIndices] point at it. */
    val index: Int,
    /** The resolved glob, relative to the project root, `/` separated. */
    val pattern: String,
    val kind: LayoutEntryKind,
    val required: Boolean,
    val description: String?,
    val declaredAt: DeclarationSiteRef?,
    val modulePath: String?,
    val pathInModule: String,
    val captures: List<CaptureRef>,
    val moduleCapture: ModuleCaptureRef?,
    val template: TemplateRef?,
    /** The files the entry held when the task ran, relative to the project root (capped; see [truncated]). */
    val matchedFiles: List<String>,
    /** The count before the cap. `null` for a directory / `ignore()` entry, or when the walk could not run. */
    val matchedCount: Int?,
    val truncated: Boolean,
    /**
     * Injected by a `module { }` block (its `build` / `build.gradle.kts`) and not written by the user. It holds a file and
     * stops an `ignore()` like any other entry, so matching treats it as one; only the ambiguity check, which the IDE
     * does not run, leaves it out. A JSON from before the flag reads `false`.
     */
    val synthetic: Boolean = false,
    /**
     * The directory a `"...".module { }` key opened, for a module other than the root project: one of the places the role may
     * live in. It is a `Directory` entry like any other to matching (it makes the module's directory known); only the check
     * for a missing `description` reads the flag. A JSON from before the flag reads `false`.
     */
    val place: Boolean = false,
)

/**
 * One occurrence of a `capture("name")` on an entry's pattern: which `/` level it sits in ([segmentIndex], 0-based), which
 * `*` of that level it is ([wildcardIndex], 0-based, the unnamed `*` counted too), and for a derived occurrence
 * (`capture("x").pascalCase`) the conversion's name ([transform]; `null` for the plain capture that gives the value).
 * A JSON from before the two reads `0` and `null`. Two occurrences of one name, or a derived one, bind the entry: it
 * holds only a file whose levels agree (design capture-check 3.8).
 */
internal data class CaptureRef(val name: String, val segmentIndex: Int, val wildcardIndex: Int = 0, val transform: String? = null)

/**
 * The names given to the `*` of a module key; [pattern] is such as `":feature:*"`. Only katachi 0.3 and earlier write it
 * (0.4 removed `.module { }`); it is still read, since the plugin may open a project on an older katachi.
 */
internal data class ModuleCaptureRef(val pattern: String, val names: List<String>)

/** A `.template { }` attached to an entry. [specifier] is the complete one, `domain.UseCase.useCase`. */
internal data class TemplateRef(
    val specifier: String,
    val id: String?,
    val title: String?,
    val declaredAt: DeclarationSiteRef?,
)

/** A file constraint (`fileConstraint`, `konsist { }`, `ktFileContent { }`, ...) of a role. */
internal data class ConstraintModel(
    val name: String?,
    val backend: ConstraintBackend,
    /** `check::class.java.name`; what the IDE shows for a [ConstraintBackend.Custom] one. */
    val checkClass: String,
    val range: ConstraintRange,
    val declaredAt: DeclarationSiteRef?,
    val layoutPath: String?,
    val anchors: List<String>,
    /** The [LayoutEntryModel.index] values this constraint applies to. */
    val entryIndices: List<Int>,
    val coverage: ConstraintCoverage,
    /** The explanation of each covered `.kt` file; `null` when the constraint cannot explain (or was told not to). */
    val fileDeclarations: List<FileDeclarationsModel>?,
)

/** What a constraint covers: a file is covered when [files] has a glob matching it, or its directory matches an [anyFileDirectories] glob. */
internal data class ConstraintCoverage(val files: List<String>, val anyFileDirectories: List<String>)

internal enum class DefinitionProblemKind { WalkFailed, TemplatePreviewFailed, TemplateSetPreviewFailed, ModulesNotListed, ExplanationTruncated, ExplanationFailed, Unknown }

/** Something the JSON was written without, so the IDE can say the answer is partial. */
internal data class DefinitionProblem(
    val kind: DefinitionProblemKind,
    /** The kind as written, kept for the one that is [DefinitionProblemKind.Unknown]. */
    val kindName: String,
    val message: String,
    val detail: String?,
    /** Where the thing that failed was written; set by katachi on `TemplatePreviewFailed`, `TemplateSetPreviewFailed` and `ExplanationFailed`, and only when the exception says. */
    val declaredAt: DeclarationSiteRef? = null,
    /** The class name of the exception behind the problem, on the same three kinds. */
    val exception: String? = null,
)

/** The `kind` of a layout entry. A kind this plugin does not know is [Unknown] and takes no part in matching. */
internal sealed interface LayoutEntryKind : JsonNamed {
    data object Directory : LayoutEntryKind { override val jsonName: String get() = "Directory" }
    data object File : LayoutEntryKind { override val jsonName: String get() = "File" }
    data object AnyFile : LayoutEntryKind { override val jsonName: String get() = "AnyFile" }
    data object Ignore : LayoutEntryKind { override val jsonName: String get() = "Ignore" }
    data class Unknown(override val jsonName: String) : LayoutEntryKind

    companion object {
        val known: List<LayoutEntryKind> = listOf(Directory, File, AnyFile, Ignore)
        fun of(name: String): LayoutEntryKind = known.firstOrNull { it.jsonName == name } ?: Unknown(name)
    }
}

/** Which backend checks a constraint (design 1.7.1). */
internal sealed interface ConstraintBackend : JsonNamed {
    data object Konsist : ConstraintBackend { override val jsonName: String get() = "konsist" }
    data object KtFileContent : ConstraintBackend { override val jsonName: String get() = "ktFileContent" }
    data object Konture : ConstraintBackend { override val jsonName: String get() = "konture" }
    data object Custom : ConstraintBackend { override val jsonName: String get() = "custom" }
    data class Unknown(override val jsonName: String) : ConstraintBackend

    companion object {
        val known: List<ConstraintBackend> = listOf(Konsist, KtFileContent, Konture, Custom)
        fun of(name: String): ConstraintBackend = known.firstOrNull { it.jsonName == name } ?: Unknown(name)
    }
}

/** `FileConstraintRange`: whether a constraint applies to the whole subtree of its anchors or only to their direct files. */
internal sealed interface ConstraintRange : JsonNamed {
    data object Subtree : ConstraintRange { override val jsonName: String get() = "Subtree" }
    data object DirectOnly : ConstraintRange { override val jsonName: String get() = "DirectOnly" }
    data class Unknown(override val jsonName: String) : ConstraintRange

    companion object {
        val known: List<ConstraintRange> = listOf(Subtree, DirectOnly)
        fun of(name: String): ConstraintRange = known.firstOrNull { it.jsonName == name } ?: Unknown(name)
    }
}

/** An enum-like value of the JSON that keeps the name it was written with, so one this plugin does not know survives. */
internal interface JsonNamed {
    val jsonName: String
}
