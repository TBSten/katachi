package me.tbsten.katachi.ide.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.template.internal.TemplateDescriptionJson

/*
 * The shape of `definition.json`, the file the katachi IDE plugin reads to know a definition:
 * its roles, their layout entries and constraints, the files each entry matched when the task ran,
 * and the templates. Every key is written, in declaration order (`encodeToJson`'s rules), so the
 * order of the properties below is the order of the keys in the file.
 *
 * The contract with the plugin: adding a key keeps `schemaVersion`; removing one or changing what
 * one means raises it. The plugin ignores the keys it does not know.
 */

/** The version of the shape below. See the note at the top of this file. */
internal const val DEFINITION_SCHEMA_VERSION: Int = 1

/** The whole of `definition.json`. */
@Serializable
internal class DefinitionJson(
    val schemaVersion: Int,
    val katachiVersion: String,
    /** ISO-8601, UTC, to the second. */
    val generatedAt: String,
    /** Where the walk started, absolute and `/` separated; `null` when it could not run. */
    val projectRoot: String?,
    val architecture: ArchitectureJson,
    val groups: List<GroupJson>,
    val roles: List<RoleJson>,
    val templates: TemplateDescriptionJson,
    val problems: List<DefinitionProblemJson>,
)

/** A `DeclarationSite`, or `null` in its place for `DeclarationSite.Unknown`. */
@Serializable
internal class DeclarationSiteJson(val fileName: String, val line: Int)

@Serializable
internal class ArchitectureJson(
    val declaredAt: DeclarationSiteJson?,
    /** `gitTracked`, `wholeTree`, or the class name of any other `FileSelection`. */
    val files: String,
    val baseline: String?,
)

@Serializable
internal class GroupJson(
    val qualifiedName: String,
    val name: String,
    val path: List<String>,
    val title: String?,
    val summary: String?,
    val documented: Boolean?,
    val declaredAt: DeclarationSiteJson?,
)

@Serializable
internal class RoleJson(
    val qualifiedName: String,
    val name: String,
    val groupPath: List<String>,
    val title: String?,
    val summary: String?,
    val documented: Boolean?,
    val declaredAt: DeclarationSiteJson?,
    val layout: List<LayoutEntryJson>,
    val constraints: List<ConstraintJson>,
)

@Serializable
internal class LayoutEntryJson(
    val index: Int,
    val path: String,
    /** A `LayoutEntryKind`, by name. */
    val kind: String,
    val required: Boolean,
    val description: String?,
    val declaredAt: DeclarationSiteJson?,
    val modulePath: String?,
    val pathInModule: String,
    val captures: List<PathCaptureJson>,
    val moduleCapture: ModuleCaptureJson?,
    val template: TemplateRefJson?,
    val matchedFiles: List<String>,
    val matchedCount: Int?,
    val matchedFilesTruncated: Boolean,
    /**
     * One of the two lines a `module { }` block injects (`build`, `build.gradle.kts`), not something the user wrote. The
     * entry is listed like any other, since the check allows files by it; the flag is for a reader that wants to say so.
     */
    val synthetic: Boolean,
    /** The directory a module key opened, one of the places its role may live in. A `Directory` entry like any other to the check. */
    val place: Boolean,
)

/**
 * One `capture("...")` occurrence of an entry's path. [wildcardIndex] is which `*` of that level it
 * is (0-based, every unescaped `*` counted); [transform] is the naming conversion of a derived
 * occurrence (`"pascalCase"`), `null` for a plain one. A name on two occurrences, or a non-null
 * [transform], means the check holds the levels to one value -- see `LayoutCaptures.isBound`.
 */
@Serializable
internal class PathCaptureJson(
    val name: String,
    val segmentIndex: Int,
    val wildcardIndex: Int,
    val transform: String?,
)

@Serializable
internal class ModuleCaptureJson(val pattern: String, val names: List<String>)

@Serializable
internal class TemplateRefJson(
    val specifier: String,
    val id: String?,
    val title: String?,
    val declaredAt: DeclarationSiteJson?,
)

@Serializable
internal class ConstraintJson(
    val name: String?,
    /** `konsist`, `ktFileContent`, `konture` or `custom`. See [backendOf]. */
    val backend: String,
    val checkClass: String,
    /** A `FileConstraintRange`, by name. */
    val range: String,
    val declaredAt: DeclarationSiteJson?,
    val layoutPath: String?,
    val anchors: List<String>,
    val entries: List<Int>,
    val coverage: CoverageJson,
    /** `null` unless the constraint explains its declarations and the run asked it to. */
    val fileDeclarations: List<FileDeclarationsJson>?,
)

@Serializable
internal class CoverageJson(val files: List<String>, val anyFileDirectories: List<String>)

@Serializable
internal class FileDeclarationsJson(
    val file: String,
    val declarationsChecked: Boolean,
    val declarations: List<DeclarationJson>,
    val missing: List<RuleJson>,
    val unexplainedReason: String?,
)

@Serializable
internal class DeclarationJson(
    /** `class`, `interface`, `object`, `fun`, `val`, `var` or `typealias`. */
    val kind: String,
    val name: String,
    /** The line it starts at; a declaration the backend could not place is left out of the list. */
    val line: Int,
    /** A `DeclarationVerdict`, by name. */
    val verdict: String,
    val rules: List<RuleJson>,
    val difference: String?,
)

@Serializable
internal class RuleJson(val text: String, val declaredAt: DeclarationSiteJson?)

@Serializable
internal class DefinitionProblemJson(
    val kind: DefinitionProblemKind,
    /** One English sentence. */
    val message: String,
    val detail: String?,
    /**
     * Where the thing that failed was written, when the exception says (or its stack trace does):
     * set on [DefinitionProblemKind.TemplatePreviewFailed], [DefinitionProblemKind.TemplateSetPreviewFailed]
     * and [DefinitionProblemKind.ExplanationFailed], `null` on the rest.
     */
    val declaredAt: DeclarationSiteJson? = null,
    /** The class name of the exception that made it a problem, on the same three kinds. */
    val exception: String? = null,
)

/** What a [DefinitionProblemJson] is about. Written by its name. */
@Serializable
internal enum class DefinitionProblemKind {
    /** The walk could not run, so no entry knows its files. */
    WalkFailed,

    /** A template could not be previewed and is missing from `templates.details`. */
    TemplatePreviewFailed,

    /** A template set could not be previewed and is missing from `templates.templateSetDetails`. */
    TemplateSetPreviewFailed,

    /** The modules could not be listed: a wildcard module key stayed unexpanded, or a template has no placement. */
    ModulesNotListed,

    /** `fileDeclarations` stopped at `maxExplainedFiles`. */
    ExplanationTruncated,

    /** A constraint threw while it explained its declarations. */
    ExplanationFailed,
}
