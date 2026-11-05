package me.tbsten.katachi.intellij.ide.mcp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The results of the katachi MCP tools. Public only because the MCP Server reads them by reflection from the public
// functions of KatachiMcpToolset; the field names are the JSON an AI agent sees, so renaming one changes the schema golden.

@Serializable
data class RolesOfFileResult(
    val module: String,
    val relativePath: String,
    /** matched / ignored / notWalked / ignoredByVcs / unexpected / unexpectedUnder / captureMismatch */
    val outcome: String,
    val detail: String? = null,
    val roles: List<FileRoleDto>,
    val nearby: List<NearbyPlaceDto>,
    val stale: Boolean,
    val staleReason: String? = null,
)

@Serializable
data class FileRoleDto(
    val qualifiedName: String,
    val title: String? = null,
    val summary: String? = null,
    val pattern: String,
    val entryDescription: String? = null,
    val template: TemplateRefDto? = null,
)

@Serializable
data class TemplateRefDto(val specifier: String, val id: String? = null, val title: String? = null)

@Serializable
data class NearbyPlaceDto(val role: String, val directory: String, val filePattern: String? = null)

@Serializable
data class ListRolesResult(val modules: List<ModuleRolesDto>, val stale: Boolean, val staleReason: String? = null)

@Serializable
data class ModuleRolesDto(val module: String, val roles: List<RoleDto>)

@Serializable
data class RoleDto(
    val qualifiedName: String,
    val groupPath: String,
    val title: String? = null,
    val summary: String? = null,
    val declaredAt: String? = null,
    val layout: List<LayoutEntryDto>,
)

@Serializable
data class LayoutEntryDto(
    val pattern: String,
    /** Directory / File / AnyFile / Ignore, or a kind a newer katachi added. */
    val kind: String,
    val required: Boolean,
    val description: String? = null,
    val template: TemplateRefDto? = null,
)

@Serializable
data class ListTemplatesResult(val modules: List<ModuleTemplatesDto>, val stale: Boolean, val staleReason: String? = null)

@Serializable
data class ModuleTemplatesDto(val module: String, val templates: List<TemplateDto>)

@Serializable
data class TemplateDto(
    /** The complete specifier `katachiTemplate --arg template=` takes. */
    val id: String,
    val title: String,
    val roleName: String,
    val summary: String? = null,
    /** The specifiers of the templates a templateSet generates; `null` for a plain template. */
    val setMembers: List<String>? = null,
    val parameters: List<ParameterDto>,
    val files: List<String>,
    val exampleCommand: String? = null,
    val available: Boolean,
    val unavailableReason: String? = null,
)

@Serializable
data class ParameterDto(
    val name: String,
    /** string / boolean / int / enum / capture, or the JSON kind of one this plugin does not know. */
    val kind: String,
    val type: String,
    val required: Boolean,
    val default: String? = null,
    val acceptedValues: List<String>? = null,
)

@Serializable
data class ViolationsResult(
    /** notRun / ready / unreadable */
    val state: String,
    val readAt: String? = null,
    val stale: Boolean,
    val unreadableReason: String? = null,
    val violations: List<ViolationDto>,
)

@Serializable
data class ViolationDto(
    val module: String,
    val ruleId: String,
    val label: String,
    /** error / warning / note / none */
    val level: String,
    val message: String,
    val path: String? = null,
    val line: Int? = null,
    val role: String? = null,
    val constraint: String? = null,
)

@Serializable
data class RefreshDefinitionResult(
    /** ready / unavailable / loading / notLoaded */
    val state: String,
    val unavailableReason: String? = null,
    val modules: List<String>,
    val stale: Boolean,
    val staleReason: String? = null,
    /** Why the reload failed, when it did; the definition answered is then the one from before (or none). */
    val loadFailure: LoadFailureDto? = null,
    val definitionErrors: List<DefinitionErrorDto>,
)

@Serializable
data class LoadFailureDto(
    /** compilationFailed / taskNotFound / architectureNotSet / processorRejected / definitionRejected / gradle / jsonMissing / malformedJson / incompatibleJson / cancelled */
    val kind: String,
    val details: List<String>,
)

@Serializable
data class DefinitionErrorDto(
    val module: String,
    /** entryPoint / processor */
    val phase: String,
    val processor: String? = null,
    val exception: String,
    val message: String,
    /** `Architecture.kt:12`, when katachi could tell where. */
    val declaredAt: String? = null,
)

@Serializable
data class RunCheckResult(
    val module: String,
    /** succeeded / failed / timedOut. failed is also what a check that found violations gives. */
    val outcome: String,
    val violationCount: Int,
    /** The first violations of [module]; katachi_violations lists every one. */
    val violations: List<ViolationDto>,
    val truncated: Boolean,
)

/** What katachi_generate does with a file that already exists. Overwriting is left to a person in the IDE. */
@Serializable
enum class McpOnExisting {
    @SerialName("fail")
    Fail,

    @SerialName("skip")
    Skip,
}

@Serializable
data class GenerateResult(
    val module: String,
    /** One per katachiTemplate run: the templates of one module run together, a templateSet alone. */
    val items: List<GenerateItemDto>,
    /** Every file written, absolute. */
    val writtenFiles: List<String>,
    /** The run was cancelled at the time limit; the items it did not finish are interrupted or notRun. */
    val timedOut: Boolean,
)

@Serializable
data class GenerateItemDto(
    val templates: List<String>,
    /** generated / skipped / failed / stoppedAtConflict / interrupted / notRun */
    val result: String,
    /** For generated: the files katachi wrote. */
    val files: List<GeneratedFileDto>? = null,
    /** For skipped and stoppedAtConflict: the files that already existed. For interrupted: the expected files that exist now. */
    val existingPaths: List<String>? = null,
    /** For failed: what katachi or Gradle said. */
    val message: String? = null,
)

@Serializable
data class GeneratedFileDto(
    val path: String,
    /** new / overwritten */
    val kind: String,
)
