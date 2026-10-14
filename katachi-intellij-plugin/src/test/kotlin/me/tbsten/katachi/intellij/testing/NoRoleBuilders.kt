package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.baseline.BaselineReading
import me.tbsten.katachi.intellij.data.definition.DefinitionMatcher
import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.LayoutEntryKind
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.rules.NoRoleInput
import me.tbsten.katachi.intellij.presentation.rules.ResolvedSiteView
import me.tbsten.katachi.intellij.presentation.rules.RuleSettings
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import java.nio.file.Path

/** The directory of the `UseCase` role of [noRoleSnapshot]. */
internal const val USE_CASE_DIRECTORY: String = "core/domain/src/main/kotlin/com/example/domain/useCase"

/** The directory of the `Model` role of [noRoleSnapshot]. */
internal const val MODEL_DIRECTORY: String = "core/domain/src/main/kotlin/com/example/domain/model"

/**
 * A definition with two roles, the one the no-role tests ask files of: `domain.UseCase` holds `*UseCase.kt` in
 * [USE_CASE_DIRECTORY], `domain.Model` holds `*.kt` in [MODEL_DIRECTORY], `Docs` ignores `docs`. Matching goes through the real
 * [DefinitionMatcher], so a test answers with what the plugin answers, not with a hand-made [FileRoles].
 */
internal fun noRoleSnapshot(files: String = ArchitectureModel.FILES_GIT_TRACKED, baseline: String? = null): DefinitionSnapshot = definitionSnapshot(
    definitionModel(
        roleModel("domain.UseCase", layoutEntry("$USE_CASE_DIRECTORY/*UseCase.kt")),
        roleModel("domain.Model", layoutEntry("$MODEL_DIRECTORY/*.kt")),
        roleModel("Docs", layoutEntry("docs", LayoutEntryKind.Ignore)),
        files = files,
        baseline = baseline,
    ).let { it.copy(architecture = it.architecture.copy(declaredAt = ARCHITECTURE_SITE)) },
)

/** Where `architecture { }` is written in [noRoleSnapshot]; the roles are written at `<Name>Role.kt:1` (see `roleModel`). */
internal val ARCHITECTURE_SITE: DeclarationSiteRef = DeclarationSiteRef("Architecture.kt", 3)

/** What the plugin answers for [relative] under [snapshot]'s definition: a match of the real matcher, wearing [freshness]. */
internal fun rolesFor(relative: String, snapshot: DefinitionSnapshot = noRoleSnapshot(), freshness: Freshness = Freshness.Fresh): FileRoles {
    val definition = snapshot.definition ?: throw AssertionError("a snapshot without a definition")
    return DefinitionMatcher(definition).matchFile(relative).within(snapshot.module, DEFINITION_ROOT, relative, freshness)
}

/** A file under [USE_CASE_DIRECTORY]'s `helpers`: no role holds it, and the directory is one no role declared. */
internal const val STRAY: String = "$USE_CASE_DIRECTORY/helpers/Paging.kt"

/** A file in [USE_CASE_DIRECTORY] with a name the role does not take: every directory is known, so it is plain `Unexpected`. */
internal const val MISNAMED: String = "$USE_CASE_DIRECTORY/Paging.kt"

/** A file the `UseCase` role holds. */
internal const val HELD: String = "$USE_CASE_DIRECTORY/GetUserUseCase.kt"

/** The input of a file the matcher says nobody holds, with the settings on, nothing closed, and a definition that is `Ready`. */
internal fun noRoleInput(
    relative: String = STRAY,
    snapshot: DefinitionSnapshot = noRoleSnapshot(),
    file: Path = DEFINITION_ROOT.resolve(relative),
    state: DefinitionState = DefinitionState.Ready(listOf(snapshot), Freshness.Fresh),
    roles: FileRoles? = rolesFor(relative, snapshot, (state as? DefinitionState.Ready)?.freshness ?: Freshness.Fresh),
    vcs: VcsTracking = VcsTracking.Tracked,
    settings: RuleSettings = RuleSettings(),
    memory: EditorNotificationMemory = EditorNotificationMemory.EMPTY,
    nearbySites: List<ResolvedSiteView> = roles?.nearby.orEmpty().mapIndexed { index, nearby ->
        ResolvedSiteView.Found("${nearby.role.name}Role.kt", 10 + index)
    },
    architectureSite: ResolvedSiteView? = ResolvedSiteView.Found("Architecture.kt", 3),
    baseline: BaselineReading = BaselineReading.NotConfigured,
): NoRoleInput = NoRoleInput(
    file = file,
    roles = roles,
    definition = state,
    architecture = snapshot.definition?.architecture,
    vcs = vcs,
    settings = settings,
    memory = memory,
    nearbySites = nearbySites,
    architectureSite = architectureSite,
    baseline = baseline,
)
