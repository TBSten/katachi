package me.tbsten.katachi.intellij.ide.mcp

import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.MatchOutcome
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateRef
import me.tbsten.katachi.intellij.model.TemplateUnavailability
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.model.groupOrderOf

internal fun FileRoles.toDto(staleReason: String?): RolesOfFileResult = RolesOfFileResult(
    module = module.gradlePath,
    relativePath = relativePath,
    outcome = outcome.jsonName,
    detail = outcome.detail,
    roles = matches.map { (role, entry) ->
        FileRoleDto(role.qualifiedName, role.title, role.summary, entry.pattern, entry.description, entry.template?.toDto())
    },
    nearby = nearby.map { NearbyPlaceDto(it.role.qualifiedName, it.directory, it.filePattern) },
    stale = staleReason != null,
    staleReason = staleReason,
)

private val MatchOutcome.jsonName: String
    get() = when (this) {
        MatchOutcome.Matched -> "matched"
        is MatchOutcome.Ignored -> "ignored"
        is MatchOutcome.NotWalked -> "notWalked"
        MatchOutcome.IgnoredByVcs -> "ignoredByVcs"
        MatchOutcome.Unexpected -> "unexpected"
        is MatchOutcome.UnexpectedUnder -> "unexpectedUnder"
        is MatchOutcome.CaptureMismatch -> "captureMismatch"
    }

private val MatchOutcome.detail: String?
    get() = when (this) {
        MatchOutcome.Matched, MatchOutcome.IgnoredByVcs, MatchOutcome.Unexpected -> null
        is MatchOutcome.Ignored -> "ignored by the layout entry ${entry.pattern}"
        is MatchOutcome.NotWalked -> "katachi never walks into $directory"
        is MatchOutcome.UnexpectedUnder -> "no role declares the directory $directory"
        is MatchOutcome.CaptureMismatch ->
            "the captures of ${role.qualifiedName} (${entry.pattern}) do not agree" + (expectedPath?.let { "; expected at $it" } ?: "")
    }

internal fun DefinitionSnapshot.toRolesDto(): ModuleRolesDto =
    ModuleRolesDto(module.gradlePath, definition?.roles.orEmpty().map { it.toDto() })

private fun RoleModel.toDto(): RoleDto = RoleDto(
    qualifiedName = qualifiedName,
    groupPath = groupPath.joinToString("."),
    title = title,
    summary = summary,
    declaredAt = declaredAt?.text,
    layout = layout.map { it.toDto() },
)

private fun LayoutEntryModel.toDto(): LayoutEntryDto = LayoutEntryDto(pattern, kind.jsonName, required, description, template?.toDto())

private fun TemplateRef.toDto(): TemplateRefDto = TemplateRefDto(specifier, id, title)

private val DeclarationSiteRef.text: String get() = "$fileName:$line"

internal fun DefinitionSnapshot.toTemplatesDto(): ModuleTemplatesDto =
    ModuleTemplatesDto(module.gradlePath, groupOrderOf(templates).map { it.toDto() })

private fun TemplateModel.toDto(): TemplateDto {
    val detail = detail
    val unavailability = unavailability
    return TemplateDto(
        id = template,
        title = title,
        roleName = roleName,
        summary = summary.summary,
        setMembers = set?.members?.map { it.template },
        parameters = detail?.let(::allParametersOf).orEmpty().map { it.toDto() },
        files = set?.members?.flatMap { member -> member.files.map { it.pattern } } ?: detail?.files.orEmpty().map { it.path ?: it.pattern },
        exampleCommand = detail?.exampleCommand,
        available = unavailability == null,
        unavailableReason = when (unavailability) {
            null -> null
            TemplateUnavailability.PreviewFailed -> "the preview of this template failed; katachiTemplate may still run it"
            is TemplateUnavailability.UnknownParameterKind ->
                "a parameter has a kind this IDE plugin does not know (${unavailability.kindNames.joinToString(", ")}); update the plugin"
        },
    )
}

private fun ParameterModel.toDto(): ParameterDto = ParameterDto(
    name = name,
    kind = when (this) {
        is ParameterModel.StringParam -> "string"
        is ParameterModel.BooleanParam -> "boolean"
        is ParameterModel.IntParam -> "int"
        is ParameterModel.EnumParam -> "enum"
        is ParameterModel.CaptureParam -> "capture"
        is ParameterModel.UnknownParam -> kindName
    },
    type = typeName,
    required = isRequired,
    default = default,
    acceptedValues = when (this) {
        is ParameterModel.EnumParam -> acceptedValues
        is ParameterModel.CaptureParam -> existingModules
        else -> null
    },
)

internal fun ViolationModel.toDto(): ViolationDto = ViolationDto(
    module = module.gradlePath,
    ruleId = ruleId,
    label = label,
    level = level.jsonName,
    message = message,
    path = location?.path?.toString(),
    line = location?.startLine,
    role = properties["role"],
    constraint = properties["constraint"],
)

/** `null` for a fresh answer, else why it is not. */
internal val Freshness.staleReason: String?
    get() = when (this) {
        Freshness.Fresh -> null
        is Freshness.Stale -> when (reason) {
            StaleReason.DefinitionChanged -> "the definition changed at $since and has not been loaded again"
            // The failure itself is left out: it can hold the whole Gradle output, which the katachi tool window shows.
            is StaleReason.ReloadFailed -> "loading the changed definition failed after $since; this is the answer from before"
        }
    }
