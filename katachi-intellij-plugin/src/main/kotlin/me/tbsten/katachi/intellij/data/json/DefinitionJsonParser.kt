package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.ConstraintBackend
import me.tbsten.katachi.intellij.model.ConstraintCoverage
import me.tbsten.katachi.intellij.model.ConstraintModel
import me.tbsten.katachi.intellij.model.ConstraintRange
import me.tbsten.katachi.intellij.model.DeclarationModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DeclarationVerdict
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.DefinitionProblem
import me.tbsten.katachi.intellij.model.DefinitionProblemKind
import me.tbsten.katachi.intellij.model.FileDeclarationsModel
import me.tbsten.katachi.intellij.model.GroupModel
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.ModuleCaptureRef
import me.tbsten.katachi.intellij.model.RoleModel
import me.tbsten.katachi.intellij.model.RuleRef
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateRef
import java.nio.file.Path
import java.nio.file.Paths

internal val SUPPORTED_DEFINITION_SCHEMA_VERSIONS: List<Int> = listOf(1)

internal data class ParsedDefinitionJson(val definition: DefinitionModel, val templates: List<TemplateModel>)

// Keys an older katachi does not write (`fileDeclarations`, `synthetic`, `place`, `wildcardIndex`, `transform`) are optional;
// unknown kinds are kept as unknown values instead of failing the file.
internal fun parseDefinitionJson(text: String, file: Path): ParsedDefinitionJson {
    val document = try {
        parseJsonText(text, file)
    } catch (failure: KatachiMalformedTemplateJsonException) {
        throw KatachiMalformedDefinitionJsonException(file, failure.problem)
    }
    val root = DefinitionJsonReader(document, "$", file)
    val schemaVersion = root.int("schemaVersion")
    if (schemaVersion !in SUPPORTED_DEFINITION_SCHEMA_VERSIONS) {
        throw KatachiUnsupportedDefinitionSchemaException(file, schemaVersion, SUPPORTED_DEFINITION_SCHEMA_VERSIONS)
    }
    val definition = DefinitionModel(
        projectRoot = root.nullableString("projectRoot")?.let { Paths.get(it) },
        architecture = root.obj("architecture").architecture(),
        groups = root.array("groups").map { it.group() },
        roles = root.array("roles").map { it.role() },
        problems = root.array("problems").map { it.problem() },
        katachiVersion = root.string("katachiVersion"),
        generatedAt = root.instant("generatedAt"),
    )
    return ParsedDefinitionJson(definition, templatesOf(root.obj("templates"), file))
}

private fun templatesOf(templates: DefinitionJsonReader, file: Path): List<TemplateModel> = try {
    parseTemplateDescriptionJson(templates.raw, file)
} catch (failure: KatachiIncompatibleTemplateJsonException) {
    throw KatachiIncompatibleDefinitionJsonException(file, "templates" + failure.location.removePrefix("$"), failure.expected, failure.actual)
}

private fun DefinitionJsonReader.site(key: String): DeclarationSiteRef? =
    nullableObj(key)?.let { DeclarationSiteRef(it.string("fileName"), it.int("line")) }

private fun DefinitionJsonReader.architecture() = ArchitectureModel(
    declaredAt = site("declaredAt"),
    files = string("files"),
    baseline = nullableString("baseline"),
)

private fun DefinitionJsonReader.group() = GroupModel(
    qualifiedName = string("qualifiedName"),
    name = string("name"),
    path = strings("path"),
    title = nullableString("title"),
    summary = nullableString("summary"),
    documented = nullableBoolean("documented"),
    declaredAt = site("declaredAt"),
)

private fun DefinitionJsonReader.role() = RoleModel(
    qualifiedName = string("qualifiedName"),
    name = string("name"),
    groupPath = strings("groupPath"),
    title = nullableString("title"),
    summary = nullableString("summary"),
    documented = nullableBoolean("documented"),
    declaredAt = site("declaredAt"),
    layout = array("layout").map { it.entry() },
    constraints = array("constraints").map { it.constraint() },
)

private fun DefinitionJsonReader.entry() = LayoutEntryModel(
    index = int("index"),
    pattern = string("path"),
    kind = LayoutEntryKind.of(string("kind")),
    required = boolean("required"),
    description = nullableString("description"),
    declaredAt = site("declaredAt"),
    modulePath = nullableString("modulePath"),
    pathInModule = string("pathInModule"),
    captures = array("captures").map {
        CaptureRef(it.string("name"), it.int("segmentIndex"), it.optionalInt("wildcardIndex", default = 0), it.optionalNullableString("transform"))
    },
    moduleCapture = nullableObj("moduleCapture")?.let { ModuleCaptureRef(it.string("pattern"), it.strings("names")) },
    template = nullableObj("template")?.let {
        TemplateRef(it.string("specifier"), it.nullableString("id"), it.nullableString("title"), it.site("declaredAt"))
    },
    matchedFiles = strings("matchedFiles"),
    matchedCount = nullableInt("matchedCount"),
    truncated = boolean("matchedFilesTruncated"),
    synthetic = optionalBoolean("synthetic", default = false),
    place = optionalBoolean("place", default = false),
)

private fun DefinitionJsonReader.constraint(): ConstraintModel {
    val coverage = obj("coverage")
    return ConstraintModel(
        name = nullableString("name"),
        backend = ConstraintBackend.of(string("backend")),
        checkClass = string("checkClass"),
        range = ConstraintRange.of(string("range")),
        declaredAt = site("declaredAt"),
        layoutPath = nullableString("layoutPath"),
        anchors = strings("anchors"),
        entryIndices = ints("entries"),
        coverage = ConstraintCoverage(coverage.strings("files"), coverage.strings("anyFileDirectories")),
        fileDeclarations = optionalNullableArray("fileDeclarations")?.map { it.fileDeclarations() },
    )
}

private fun DefinitionJsonReader.fileDeclarations() = FileDeclarationsModel(
    file = string("file"),
    declarationsChecked = boolean("declarationsChecked"),
    declarations = array("declarations").map { it.declaration() },
    missing = array("missing").map { it.rule() },
    unexplainedReason = nullableString("unexplainedReason"),
)

private fun DefinitionJsonReader.declaration() = DeclarationModel(
    kind = SourceDeclarationKind.of(string("kind")),
    name = string("name"),
    line = int("line"),
    verdict = DeclarationVerdict.of(string("verdict")),
    rules = array("rules").map { it.rule() },
    difference = nullableString("difference"),
)

private fun DefinitionJsonReader.rule() = RuleRef(string("text"), site("declaredAt"))

private fun DefinitionJsonReader.problem(): DefinitionProblem {
    val kindName = string("kind")
    return DefinitionProblem(
        kind = DefinitionProblemKind.entries.firstOrNull { it != DefinitionProblemKind.Unknown && it.name == kindName }
            ?: DefinitionProblemKind.Unknown,
        kindName = kindName,
        message = string("message"),
        detail = nullableString("detail"),
        // Written since katachi 0.3 for the three kinds that name a template or a file; an older JSON has neither key.
        declaredAt = optionalNullableObj("declaredAt")?.let { DeclarationSiteRef(it.string("fileName"), it.int("line")) },
        exception = optionalNullableString("exception"),
    )
}
