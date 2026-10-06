package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.CaptureRef
import me.tbsten.katachi.intellij.model.ConstraintBackend
import me.tbsten.katachi.intellij.model.ConstraintCoverage
import me.tbsten.katachi.intellij.model.ConstraintModel
import me.tbsten.katachi.intellij.model.ConstraintRange
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.FileDeclarationsModel
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.model.LayoutEntryModel
import me.tbsten.katachi.intellij.model.ModuleCaptureRef
import me.tbsten.katachi.intellij.model.RoleModel
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant

/** A time every builder uses, so models built apart are equal. */
internal val DEFINITION_INSTANT: Instant = Instant.parse("2026-10-05T00:00:00Z")

/** The project root a built [definitionModel] has. */
internal val DEFINITION_ROOT: Path = Paths.get("/w/repo")

/**
 * A layout entry of a role. [index] is set by [roleModel] from the entry's place when left at `-1`.
 *
 * ```kotlin
 * val role = roleModel("domain.UseCase", layoutEntry("core/domain/GetUser.kt"), layoutEntry("core/domain", LayoutEntryKind.Directory))
 * ```
 */
internal fun layoutEntry(
    pattern: String,
    kind: LayoutEntryKind = LayoutEntryKind.File,
    index: Int = -1,
    required: Boolean = false,
    matchedFiles: List<String> = emptyList(),
    synthetic: Boolean = false,
    place: Boolean = false,
    captures: List<CaptureRef> = emptyList(),
    modulePath: String? = null,
    pathInModule: String = pattern,
    moduleCapture: ModuleCaptureRef? = null,
): LayoutEntryModel = LayoutEntryModel(
    index = index,
    pattern = pattern,
    kind = kind,
    required = required,
    description = null,
    declaredAt = null,
    modulePath = modulePath,
    pathInModule = pathInModule,
    captures = captures,
    moduleCapture = moduleCapture,
    template = null,
    matchedFiles = matchedFiles,
    matchedCount = matchedFiles.size.takeIf { kind == LayoutEntryKind.File || kind == LayoutEntryKind.AnyFile },
    truncated = false,
    synthetic = synthetic,
    place = place,
)

internal fun constraintModel(
    name: String? = null,
    backend: ConstraintBackend = ConstraintBackend.Konsist,
    range: ConstraintRange = ConstraintRange.Subtree,
    files: List<String> = emptyList(),
    anyFileDirectories: List<String> = emptyList(),
    entryIndices: List<Int> = emptyList(),
    fileDeclarations: List<FileDeclarationsModel>? = null,
): ConstraintModel = ConstraintModel(
    name = name,
    backend = backend,
    checkClass = "test.Check",
    range = range,
    declaredAt = null,
    layoutPath = null,
    anchors = emptyList(),
    entryIndices = entryIndices,
    coverage = ConstraintCoverage(files, anyFileDirectories),
    fileDeclarations = fileDeclarations,
)

internal fun roleModel(
    qualifiedName: String,
    vararg entries: LayoutEntryModel,
    constraints: List<ConstraintModel> = emptyList(),
): RoleModel = RoleModel(
    qualifiedName = qualifiedName,
    name = qualifiedName.substringAfterLast('.'),
    groupPath = qualifiedName.split('.').dropLast(1),
    title = null,
    summary = null,
    documented = null,
    declaredAt = DeclarationSiteRef("${qualifiedName.substringAfterLast('.')}Role.kt", 1),
    layout = entries.mapIndexed { place, entry -> if (entry.index >= 0) entry else entry.copy(index = place) },
    constraints = constraints,
)

internal fun definitionModel(
    vararg roles: RoleModel,
    files: String = ArchitectureModel.FILES_GIT_TRACKED,
    projectRoot: Path = DEFINITION_ROOT,
    generatedAt: Instant = DEFINITION_INSTANT,
): DefinitionModel = DefinitionModel(
    projectRoot = projectRoot,
    architecture = ArchitectureModel(declaredAt = null, files = files, baseline = null),
    groups = emptyList(),
    roles = roles.toList(),
    problems = emptyList(),
    katachiVersion = "0.3.0",
    generatedAt = generatedAt,
)

internal fun definitionSnapshot(
    definition: DefinitionModel? = definitionModel(),
    module: KatachiModule = module(":arch"),
): DefinitionSnapshot = DefinitionSnapshot(module, definition, emptyList(), definition?.katachiVersion, DEFINITION_INSTANT)
