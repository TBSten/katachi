package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.model.ConflictQuestion
import me.tbsten.katachi.intellij.model.GeneratedFile
import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemReport
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GenerationReport
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.model.WrittenKind
import me.tbsten.katachi.intellij.presentation.DetailsKey
import me.tbsten.katachi.intellij.presentation.GenerationRowStatus
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.ViewState
import java.nio.file.Path

/** Repository, UseCase and Service checked with `name = User`: the three rows of spec 03. */
private val threeChecked: KatachiScreenState = ready(snapshot(arch, dataSource, repository, useCase, service))
    .then(check(repository), input(repository, "name", "User"), check(useCase), check(service))

private val repositoryId = idOf(arch, repository)
private val useCaseId = idOf(arch, useCase)
private val serviceId = idOf(arch, service)
private val order = listOf(repositoryId, useCaseId, serviceId)
private const val DATA = "/Users/dev/project/data/src/main/kotlin/com/example/data/user"
private const val DOMAIN = "/Users/dev/project/domain/src/main/kotlin/com/example/domain/user"
private const val LABEL = "katachi: 生成前（Repository, UseCase, Service）"

private fun written(dir: String, name: String, kind: WrittenKind = WrittenKind.New) = GeneratedFile(Path.of(dir, name), kind)

private val repositoryDone = GenerationItemResult.Generated(listOf(written(DATA, "UserRepository.kt"), written(DATA, "UserRepositoryImpl.kt", WrittenKind.Overwritten)))
private val useCaseDone = GenerationItemResult.Generated(listOf(written(DOMAIN, "UserUseCase.kt"), written(DOMAIN, "UserUseCaseImpl.kt")))
private val serviceDone = GenerationItemResult.Generated(listOf(written(DOMAIN, "UserService.kt")))

private val slashFailure = GenerationItemResult.Failed(
    GenerationFailure.Katachi(listOf("ファイル名に使えない文字「/」が含まれています: User/Admin", "name の値を見直してください")),
    output = listOf(
        "> Task :architecture-test:katachiTemplate",
        "[1/3] Loading architecture...",
        "[2/3] Running template data/Repository...",
        "[FAILED] template",
        "  ファイル名に使えない文字「/」が含まれています: User/Admin",
        "BUILD FAILED in 3s",
    ),
)

private fun running(statuses: Map<TemplateId, GenerationRowStatus>, conflict: ConflictQuestion? = null) =
    threeChecked.copy(generation = GenerationState.Running(order, statuses, conflict = conflict))

/** The default "open the first file" setting: the first written file carries "← opened". */
private fun finished(vararg results: GenerationItemResult, view: ViewState = threeChecked.view): KatachiScreenState {
    val report = GenerationReport(order.zip(results.toList()) { id, r -> GenerationItemReport(id, r) })
    return threeChecked.copy(
        generation = GenerationState.Finished(report, LABEL, openedFiles = report.writtenFiles.take(1).map { it.path }),
        view = view,
    )
}

internal val generationScenarios: List<Scenario> = listOf(
    Scenario(
        "generating",
        running(
            mapOf(
                repositoryId to GenerationRowStatus.Finished(repositoryDone),
                useCaseId to GenerationRowStatus.Running(":architecture-test:katachiTemplate"),
                serviceId to GenerationRowStatus.Waiting,
            ),
        ),
    ),
    Scenario(
        "generating-conflict",
        running(
            mapOf(
                repositoryId to GenerationRowStatus.Finished(repositoryDone),
                useCaseId to GenerationRowStatus.AwaitingConflict,
                serviceId to GenerationRowStatus.Waiting,
            ),
            conflict = ConflictQuestion(useCaseId, 2, 3, listOf(Path.of(DOMAIN, "UserUseCase.kt"))),
        ),
    ),
    Scenario("result-success", finished(repositoryDone, useCaseDone, serviceDone)),
    Scenario(
        "result-partial",
        finished(
            repositoryDone,
            slashFailure,
            GenerationItemResult.NotRun,
            view = ViewState(openDetails = setOf(DetailsKey.ResultRow(useCaseId))),
        ),
    ),
    Scenario("result-first-failed", finished(slashFailure, GenerationItemResult.NotRun, GenerationItemResult.NotRun)),
    // A second run with the same name, after "generate more": the form says the files are there,
    // and "stop here" in the conflict dialog leaves nothing written and nothing opened.
    Scenario(
        "second-run-form",
        threeChecked.then(KatachiIntent.ToggleFileList(repositoryId)).let {
            it.copy(
                view = it.view.copy(
                    existingPaths = setOf(
                        "data/src/main/kotlin/com/example/data/user/UserRepository.kt",
                        "data/src/main/kotlin/com/example/data/user/UserRepositoryImpl.kt",
                    ),
                ),
            )
        },
    ),
    Scenario(
        "second-run-stopped",
        finished(
            GenerationItemResult.StoppedAtConflict(listOf(Path.of(DATA, "UserRepository.kt"), Path.of(DATA, "UserRepositoryImpl.kt"))),
            GenerationItemResult.NotRun,
            GenerationItemResult.NotRun,
        ),
    ),
    Scenario(
        "result-interrupted",
        finished(
            GenerationItemResult.Skipped(listOf(Path.of(DATA, "UserRepository.kt"))),
            GenerationItemResult.Interrupted(listOf(Path.of(DOMAIN, "UserUseCase.kt"))),
            GenerationItemResult.NotRun,
        ),
    ),
)

/** Long role names, titles and paths, at the head of 200 templates (E-29). */
private val longTemplates = buildList {
    add(
        template(
            "feature/authentication/presentation/TwoFactorAuthenticationVerificationScreenStateHolder",
            "二要素認証の確認画面の状態を保持するクラスとその初期化処理（長いタイトル）",
            listOf(str("featureNameUsedInEveryGeneratedFileName"), bool("generateAccompanyingComposePreviewFunctions")),
            listOf(
                kt(
                    "feature/authentication/presentation/src/commonMain/kotlin/com/example/feature/authentication/presentation/twofactor/verification",
                    "\${featureNameUsedInEveryGeneratedFileName}TwoFactorVerificationStateHolder.kt",
                ),
            ),
            summary = "長い説明文。".repeat(6),
        ),
    )
    repeat(199) { index -> add(template("group${index / 10}/Role$index", "テンプレート $index", listOf(str("name")), listOf(kt("module/src", "\${name}$index.kt")))) }
}

private val longFirst = longTemplates.first()

internal val longScenario = Scenario(
    "long-names",
    ready(snapshot(arch, *longTemplates.toTypedArray())).then(
        check(longFirst),
        input(longFirst, "featureNameUsedInEveryGeneratedFileName", "AccountSecuritySettings"),
        KatachiIntent.ToggleFileList(idOf(arch, longFirst)),
    ),
)
