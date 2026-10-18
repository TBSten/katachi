package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSetMemberModel
import me.tbsten.katachi.intellij.model.TemplateSetModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.CandidateList
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import java.nio.file.Path

/**
 * The scenes of a template set's row (template-set plan 3.6, design 8.1 and 8.2): closed beside templates, open with the
 * set's parameters and the directory candidates of the first, found by one of its members' titles, one that cannot be
 * checked, and the result of generating it. Only the scenes are here: no golden PNG is made from them until the lane
 * that owns the pictures runs `updatePreview`.
 */

private fun setFile(dir: String, name: String) = FilePreviewModel("$dir/$name", name, "$dir/$name", emptyList(), emptyList(), "")

private fun setRow(
    specifier: String,
    title: String,
    summary: String?,
    parameters: List<ParameterModel>,
    members: List<TemplateSetMemberModel>,
    unavailable: Boolean = false,
): TemplateModel {
    val files = members.flatMap { it.files }
    return TemplateModel(
        TemplateSummaryModel(specifier, null, title, specifier, summary, parameters.map { it.name }, conflict = unavailable),
        TemplateDetailModel(specifier, null, title, specifier, summary, parameters, files, emptyList(), "./gradlew katachiTemplate --arg template=$specifier")
            .takeUnless { unavailable },
        TemplateSetModel(specifier.substringAfterLast('.'), members),
    )
}

private const val UI = "app/src/main/kotlin/com/example/feature/\${feature}/ui"

private val newFeature = setRow(
    specifier = "feature.NewFeature",
    title = "新しい機能",
    summary = "画面・ViewModel・Repository を一度に作る",
    parameters = listOf(str("feature"), str("name"), bool("withState")),
    members = listOf(
        TemplateSetMemberModel("feature.Screen", "画面", emptyList(), listOf(setFile(UI, "\${name}Screen.kt"))),
        TemplateSetMemberModel("feature.ViewModel", "ViewModel", emptyList(), listOf(setFile(UI, "\${name}ViewModel.kt"))),
        TemplateSetMemberModel("data.Repository", "Repository", emptyList(), listOf(setFile("data/src/main/kotlin/com/example/data/\${feature}", "\${name}Repository.kt"))),
    ),
)

private val brokenSet = setRow(
    specifier = "feature.Broken",
    title = "壊れた組",
    summary = null,
    parameters = emptyList(),
    members = listOf(TemplateSetMemberModel("feature.Screen", "画面", emptyList(), emptyList())),
    unavailable = true,
)

private val newFeatureId = idOf(arch, newFeature)
private val featureField = FieldId(newFeatureId, "feature")

private val setList = ready(snapshot(arch, repository, useCase, newFeature, brokenSet))

/** The set checked and its parameters filled, the directories of `feature` read from the disk. */
private val setOpen = setList
    .then(check(newFeature), input(newFeature, "feature", "home"), input(newFeature, "name", "Home"))
    .let { it.copy(captureCandidates = mapOf(featureField to CandidateList(listOf("home", "profile", "settings"), directoryPattern = "app/src/main/kotlin/com/example/feature/{value}"))) }

private const val APP = "/Users/dev/project/app/src/main/kotlin/com/example/feature/home/ui"
private val setReport = GenerationReport(
    listOf(
        GenerationItemReport(
            listOf(newFeatureId),
            GenerationItemResult.Generated(
                listOf(
                    GeneratedFile(Path.of(APP, "HomeScreen.kt"), WrittenKind.New),
                    GeneratedFile(Path.of(APP, "HomeViewModel.kt"), WrittenKind.New),
                    GeneratedFile(Path.of("/Users/dev/project/data/src/main/kotlin/com/example/data/home", "HomeRepository.kt"), WrittenKind.New),
                ),
            ),
        ),
    ),
)

internal val setScenarios: List<Scenario> = listOf(
    Scenario("set-row-closed", setList, narrowHeight = 520),
    Scenario("set-row-open", setOpen, narrowHeight = 560),
    Scenario("set-row-open-candidates", setOpen, narrowHeight = 560, open = ScenarioOpen.Candidates(featureField)),
    Scenario("set-search-by-member", setList.then(KatachiIntent.Search("Repository")), narrowHeight = 420),
    Scenario("set-row-menu", setOpen, narrowHeight = 560, open = ScenarioOpen.RowMenu(newFeatureId)),
    Scenario(
        "set-result-success",
        setOpen.copy(generation = GenerationState.Finished(setReport, "katachi: 生成前（NewFeature）", openedFiles = listOf(Path.of(APP, "HomeScreen.kt")))),
        narrowHeight = 520,
    ),
    Scenario(
        "set-result-failed",
        setOpen.copy(
            generation = GenerationState.Finished(
                GenerationReport(
                    listOf(
                        GenerationItemReport(
                            listOf(newFeatureId),
                            GenerationItemResult.Failed(
                                GenerationFailure.Katachi(listOf("Failed to generate set member feature.Screen", "Nothing was written")),
                                output = listOf("> Task :architecture-test:katachiTemplate", "[FAILED] template", "  Failed to generate set member feature.Screen", "BUILD FAILED in 3s"),
                            ),
                        ),
                    ),
                ),
                "katachi: 生成前（NewFeature）",
            ),
        ),
        narrowHeight = 520,
    ),
)
