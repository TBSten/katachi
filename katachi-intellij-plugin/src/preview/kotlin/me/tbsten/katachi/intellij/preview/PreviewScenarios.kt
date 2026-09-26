package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.presentation.CauseState
import me.tbsten.katachi.intellij.presentation.DetailsKey
import me.tbsten.katachi.intellij.presentation.EmptyReason
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.LoadingState
import me.tbsten.katachi.intellij.presentation.OnExistingChoice
import me.tbsten.katachi.intellij.presentation.ScreenPhase
import me.tbsten.katachi.intellij.presentation.applyFormIntent
import java.nio.file.Path

/**
 * One state of the screen spec, rendered tall and narrow (docked right, 300px) and wide and short
 * (docked at the bottom), each in light and dark.
 */
internal data class Scenario(
    val name: String,
    val state: KatachiScreenState,
    val narrowHeight: Int = 640,
    val wideHeight: Int = 420,
)

internal const val NARROW_WIDTH = 300
internal const val WIDE_WIDTH = 900

internal val arch = previewModule()
internal val archA = previewModule(":arch-a")
internal val archB = previewModule(":arch-b")

/** Applies intents through the same transitions as the plugin, so the states are reachable ones. */
internal fun KatachiScreenState.then(vararg intents: KatachiIntent): KatachiScreenState =
    intents.fold(this) { state, intent -> applyFormIntent(state, intent) ?: error("not a form intent: $intent") }

internal fun ready(vararg snapshots: DescriptionSnapshot) =
    KatachiScreenState(phase = ScreenPhase.Ready, modules = snapshots.map { it.module }, snapshots = snapshots.toList())

internal fun check(template: TemplateModel, module: KatachiModule = arch) =
    KatachiIntent.ToggleCheck(idOf(module, template))

internal fun input(
    template: TemplateModel,
    parameter: String,
    value: String,
    module: KatachiModule = arch,
) = KatachiIntent.Input(FieldId(idOf(module, template), parameter), value)

/** The main list of the spec's wireframe: Repository and Service checked, `name` linked. */
internal val sampleList: KatachiScreenState =
    ready(snapshot(arch, dataSource, repository, useCase, service))
        .then(check(repository), input(repository, "name", "User"), check(service))

/** A template the last reload dropped: "Cache is gone" (E-45). */
private val previewRemoved = template("data/Cache", null, emptyList(), emptyList())

private val compileErrorDetails = listOf(
    "e: file:///Users/dev/project/architecture-test/src/test/kotlin/RepositoryRole.kt:57:13 Unresolved reference 'implSufix'.",
    "e: file:///Users/dev/project/architecture-test/src/test/kotlin/RepositoryRole.kt:61:5 Type mismatch: inferred type is Int but String was expected.",
)

internal val statusScenarios: List<Scenario> = listOf(
    Scenario("initializing", KatachiScreenState(), narrowHeight = 240, wideHeight = 200),
    Scenario(
        "loading-initial",
        KatachiScreenState(loading = LoadingState(isInitial = true, currentTask = ":architecture-test:compileTestKotlin")),
        narrowHeight = 320,
        wideHeight = 240,
    ),
    Scenario("loading-cached", sampleList.copy(loading = LoadingState(isInitial = false))),
    Scenario("empty-no-templates", KatachiScreenState(phase = ScreenPhase.Empty(EmptyReason.NoTemplates)), narrowHeight = 320, wideHeight = 240),
    Scenario("empty-not-gradle", KatachiScreenState(phase = ScreenPhase.Empty(EmptyReason.NotGradle)), narrowHeight = 320, wideHeight = 240),
    Scenario("empty-not-synced", KatachiScreenState(phase = ScreenPhase.Empty(EmptyReason.NotSynced)), narrowHeight = 320, wideHeight = 240),
    Scenario("empty-not-installed", KatachiScreenState(phase = ScreenPhase.Empty(EmptyReason.NotInstalled)), narrowHeight = 320, wideHeight = 240),
    Scenario("empty-outdated", KatachiScreenState(phase = ScreenPhase.Empty(EmptyReason.Outdated("0.2.0"))), narrowHeight = 320, wideHeight = 240),
    Scenario("empty-search", ready(snapshot(arch, dataSource, repository, useCase, service)).then(KatachiIntent.Search("repo-xyz")), narrowHeight = 400, wideHeight = 300),
    Scenario(
        "error-compile",
        KatachiScreenState(
            phase = ScreenPhase.LoadError(LoadFailure.Gradle(GradleFailure.CompilationFailed(compileErrorDetails)), emptyList()),
        ).then(KatachiIntent.ToggleDetails(DetailsKey.LoadError)),
        narrowHeight = 480,
        wideHeight = 320,
    ),
    Scenario(
        "error-stale-sync",
        KatachiScreenState(
            phase = ScreenPhase.LoadError(
                LoadFailure.Gradle(GradleFailure.TaskNotFound(":architecture-test:katachiInternalTemplatesJson", listOf("Task 'katachiInternalTemplatesJson' not found in project ':architecture-test'."))),
                emptyList(),
            ),
        ),
        narrowHeight = 360,
        wideHeight = 260,
    ),
    Scenario(
        "error-json",
        KatachiScreenState(
            phase = ScreenPhase.LoadError(
                LoadFailure.IncompatibleJson(
                    Path.of("/Users/dev/project/architecture-test/build/katachi/internalTemplatesJson/templateDescription.json"),
                    "details[0].parameters[1]",
                    "missing key 'kind'",
                ),
                emptyList(),
            ),
        ).then(KatachiIntent.ToggleDetails(DetailsKey.LoadError)),
        narrowHeight = 440,
        wideHeight = 300,
    ),
    Scenario("error-cancelled", KatachiScreenState(phase = ScreenPhase.LoadError(LoadFailure.Cancelled, emptyList())), narrowHeight = 240, wideHeight = 200),
    Scenario(
        "banners-over-cached-list",
        sampleList.copy(
            loadErrorBanner = LoadFailure.Gradle(GradleFailure.CompilationFailed(compileErrorDetails)),
            removedTemplates = listOf(idOf(arch, previewRemoved)),
            view = sampleList.view.copy(definitionChanged = true),
        ),
    ),
)


internal val listScenarios: List<Scenario> = listOf(
    Scenario("list-basic", sampleList),
    Scenario(
        "list-multi-module",
        ready(snapshot(archA, dataSource, repository, useCase), snapshot(archB, service, pagedList))
            .then(check(service, archB), input(service, "name", "User", archB), KatachiIntent.ToggleModule(archA.id)),
    ),
    Scenario(
        "form-all-types",
        ready(snapshot(arch, component, service)).then(check(component), input(component, "name", "Profile")),
        narrowHeight = 720,
    ),
    Scenario(
        "form-errors",
        ready(snapshot(arch, component, service)).then(
            check(component),
            input(component, "name", "Profile"),
            input(component, "name", ""),
            input(component, "columns", "abc"),
        ),
        narrowHeight = 720,
    ),
    Scenario(
        "form-conditional",
        ready(snapshot(arch, repository, component)).then(
            check(repository),
            input(repository, "name", "User"),
            input(repository, "withImpl", "false"),
            check(component),
            input(component, "kind", "Internal"),
        ),
        narrowHeight = 760,
    ),
    Scenario(
        "form-branch-counts",
        ready(snapshot(arch, repository, screen, navigation)).then(
            check(repository),
            input(repository, "name", "User"),
            input(repository, "withImpl", "false"),
            check(screen),
            input(screen, "withViewModel", "false"),
            input(screen, "withPreview", "false"),
            check(navigation),
            KatachiIntent.SetExpanded(idOf(arch, repository), false),
            KatachiIntent.SetExpanded(idOf(arch, navigation), false),
        ),
        narrowHeight = 560,
    ),
    Scenario(
        "linked-fields",
        ready(snapshot(archA, repository, useCase, service), snapshot(archB, pagedList)).then(
            check(repository, archA),
            input(repository, "name", "User", archA),
            check(service, archA),
            check(useCase, archA),
            input(useCase, "name", "Account", archA),
            check(pagedList, archB),
        ),
        narrowHeight = 760,
    ),
    Scenario(
        "search-outside-selected",
        sampleList.then(KatachiIntent.Search("Service")),
        narrowHeight = 480,
    ),
    Scenario(
        "file-list-open",
        ready(snapshot(arch, repository, mapper)).then(
            check(repository),
            input(repository, "name", "User"),
            KatachiIntent.ToggleFileList(idOf(arch, repository)),
            check(mapper),
            input(mapper, "name", "User"),
            KatachiIntent.ToggleFileList(idOf(arch, mapper)),
        ).let { it.copy(view = it.view.copy(existingPaths = setOf("data/src/main/kotlin/com/example/data/user/UserRepositoryImpl.kt"))) },
        narrowHeight = 720,
    ),
    Scenario(
        "preview-failed-cause",
        ready(snapshot(arch, dataSource, brokenTemplate, futureTemplate, service)).let {
            it.copy(
                view = it.view.copy(
                    causes = mapOf(
                        idOf(arch, brokenTemplate) to CauseState.Loaded(
                            listOf("[FAILED] data/Cache", "  Unresolved placeholder \${entity} in path", "  at CacheRole.kt:22"),
                        ),
                    ),
                ),
            )
        },
        narrowHeight = 400,
    ),
    Scenario(
        "overwrite-footer",
        sampleList.then(input(service, "name", "User"), KatachiIntent.SetOnExisting(OnExistingChoice.Overwrite), KatachiIntent.ToggleFileCountPopup),
    ),
)
