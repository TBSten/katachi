package me.tbsten.katachi.intellij.preview

import me.tbsten.katachi.intellij.presentation.KatachiIntent

/**
 * [View template] of an editor notification (C2): the highlighted row, on the wireframe's list and
 * in a module band the user had folded, which the request unfolded.
 */
internal val revealScenarios: List<Scenario> = listOf(
    Scenario("reveal-highlight", sampleList.then(KatachiIntent.RevealTemplate(idOf(arch, useCase)))),
    Scenario(
        "reveal-folded-module",
        ready(snapshot(archA, dataSource, repository, useCase), snapshot(archB, service, pagedList))
            .then(
                check(service, archB),
                KatachiIntent.ToggleModule(archA.id),
                KatachiIntent.RevealTemplate(idOf(archA, repository)),
            ),
        narrowHeight = 480,
    ),
)
