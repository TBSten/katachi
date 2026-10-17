package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.model.ArchitectureModel
import me.tbsten.katachi.intellij.model.DefinitionModel
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiCapability
import me.tbsten.katachi.intellij.presentation.CandidateList
import me.tbsten.katachi.intellij.presentation.FieldId

/**
 * The screens the review of the IDE screens added (design ide-review-screens 3, plan 4.4): the candidates of a capture, the
 * katachi 0.2.x band and the row menu's "Open Definition". The result screen's `1 ファイル` and `← 開いた` are in
 * `result-success` (PreviewGenerationScenarios.kt).
 */

private val featureId = FieldId(idOf(arch, featureViewModel), "feature")

private val homeProfileSettings = CandidateList(listOf("home", "profile", "settings"), directoryPattern = "feature/{value}")

/** `feature.ViewModel` checked, `name` filled and the candidates of `feature` read from the disk. */
private val candidatesState = ready(snapshot(arch, featureViewModel, service))
    .then(check(featureViewModel), input(featureViewModel, "name", "Home"))
    .let { it.copy(captureCandidates = mapOf(featureId to homeProfileSettings)) }

/** A definition the module read: only its being there matters to the menu. */
private val readDefinition = DefinitionModel(
    projectRoot = PREVIEW_ROOT,
    architecture = ArchitectureModel(declaredAt = null, files = ArchitectureModel.FILES_GIT_TRACKED, baseline = null),
    groups = emptyList(),
    roles = emptyList(),
    problems = emptyList(),
    katachiVersion = "0.3.0",
    generatedAt = PREVIEW_NOW,
)

private val oldModule = previewModule().copy(capabilities = setOf(KatachiCapability.Templates))

internal val reviewScenarios: List<Scenario> = listOf(
    Scenario("form-capture-candidates", candidatesState, narrowHeight = 520),
    Scenario("form-capture-candidates-open", candidatesState, narrowHeight = 520, open = ScenarioOpen.Candidates(featureId)),
    Scenario(
        "form-capture-new-directory",
        candidatesState.then(input(featureViewModel, "feature", "profile-new")),
        narrowHeight = 520,
    ),
    Scenario(
        "banner-katachi-too-old",
        ready(DescriptionSnapshot(oldModule, "0.2.5", listOf(repository, service), PREVIEW_NOW.minusSeconds(600))),
        narrowHeight = 420,
    ),
    Scenario(
        "row-menu-open-definition",
        ready(DescriptionSnapshot(arch, "0.3.0", listOf(repository, service), PREVIEW_NOW.minusSeconds(600), readDefinition))
            .then(check(repository), input(repository, "name", "User")),
        narrowHeight = 520,
        open = ScenarioOpen.RowMenu(idOf(arch, repository)),
    ),
)
