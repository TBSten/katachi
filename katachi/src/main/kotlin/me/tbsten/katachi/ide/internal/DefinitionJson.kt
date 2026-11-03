package me.tbsten.katachi.ide.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.template.internal.TemplateDescriptionJson

// Adding a key keeps DEFINITION_SCHEMA_VERSION; removing one or changing its meaning raises it.
// Property order is key order in the file.

internal const val DEFINITION_SCHEMA_VERSION: Int = 1

@Serializable
internal class DefinitionJson(
    val schemaVersion: Int,
    val katachiVersion: String,
    val generatedAt: String,
    val projectRoot: String?,
    val architecture: ArchitectureJson,
    val groups: List<GroupJson>,
    val roles: List<RoleJson>,
    val templates: TemplateDescriptionJson,
    val problems: List<DefinitionProblemJson>,
)

@Serializable
internal class DeclarationSiteJson(val fileName: String, val line: Int)

@Serializable
internal class ArchitectureJson(
    val declaredAt: DeclarationSiteJson?,
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
    // One of the two lines a module { } block injects, not user-written; still listed because the check allows
    // files by it.
    val synthetic: Boolean,
    // The directory a module key opened; a plain Directory entry to the check.
    val place: Boolean,
)

// A name on two occurrences, or a non-null transform, means the levels are held to one value.
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
    val backend: String,
    val checkClass: String,
    val range: String,
    val declaredAt: DeclarationSiteJson?,
    val layoutPath: String?,
    val anchors: List<String>,
    val entries: List<Int>,
    val coverage: CoverageJson,
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
    val kind: String,
    val name: String,
    val line: Int,
    val verdict: String,
    val rules: List<RuleJson>,
    val difference: String?,
)

@Serializable
internal class RuleJson(val text: String, val declaredAt: DeclarationSiteJson?)

@Serializable
internal class DefinitionProblemJson(
    val kind: DefinitionProblemKind,
    val message: String,
    val detail: String?,
    val declaredAt: DeclarationSiteJson? = null,
    val exception: String? = null,
)

@Serializable
internal enum class DefinitionProblemKind {
    WalkFailed,

    TemplatePreviewFailed,

    TemplateSetPreviewFailed,

    ModulesNotListed,

    ExplanationTruncated,

    ExplanationFailed,
}
