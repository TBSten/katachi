package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.ViolationModel
import me.tbsten.katachi.intellij.model.ViolationsState
import me.tbsten.katachi.intellij.model.loadFailed
import me.tbsten.katachi.intellij.model.loaded
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.model.startLoading
import me.tbsten.katachi.intellij.model.unavailable
import java.nio.file.Path
import java.time.Duration
import kotlin.random.Random

/**
 * Sequences of [DefinitionState] for tests that drive a feature through the states a real session
 * goes through. UI faults showed up in sequences, not in single states, so a feature's test should
 * run a sequence, not one state.
 *
 * ```kotlin
 * fake.play(DefinitionStateScripts.standardCycle(snapshot)) { state -> assertRendered(state) }
 * ```
 */
internal object DefinitionStateScripts {
    /**
     * The path of one edit: `NotLoaded`, `Loading`, `Ready`, a source edit (`Stale`), `Loading(previous)`, `Ready`
     * again, then a failed reload (`Stale(ReloadFailed)`) and a recovery. Each state is the one the transition
     * functions give, so the sequence is what the real service goes through.
     */
    fun standardCycle(snapshot: DefinitionSnapshot, reloaded: DefinitionSnapshot = snapshot): List<DefinitionState> {
        val start = DefinitionState.NotLoaded
        val loading = start.startLoading()
        val ready = loading.loaded(listOf(snapshot))
        val stale = ready.markStale(DEFINITION_INSTANT.plusSeconds(10), StaleReason.DefinitionChanged)
        val reloading = stale.startLoading()
        val again = reloading.loaded(listOf(reloaded))
        val staleAgain = again.markStale(DEFINITION_INSTANT.plusSeconds(20), StaleReason.DefinitionChanged)
        val failing = staleAgain.startLoading()
        val failed = failing.loadFailed(DEFINITION_INSTANT.plusSeconds(30), LoadFailure.Gradle(GradleFailure.Other(listOf("boom"))))
        val recovered = failed.startLoading().loaded(listOf(reloaded))
        return listOf(start, loading, ready, stale, reloading, again, staleAgain, failing, failed, recovered)
    }

    /**
     * A random walk over the transitions, [length] states long (the first is `NotLoaded`), drawn from [snapshots]. Equal
     * neighbours are dropped, as a `StateFlow` would, unless [dropEqual] is `false`: then every transition adds a state,
     * the repeated ones too, which is what a test of the transitions themselves needs. The same [random] gives the same walk.
     */
    fun random(random: Random, length: Int, snapshots: List<DefinitionSnapshot>, dropEqual: Boolean = true): List<DefinitionState> {
        var now = DEFINITION_INSTANT
        var state: DefinitionState = DefinitionState.NotLoaded
        val walk = mutableListOf(state)
        repeat(length - 1) {
            now = now.plus(Duration.ofSeconds(1 + random.nextLong(5)))
            state = when (random.nextInt(6)) {
                0 -> state.startLoading()
                1, 2 -> state.loaded(listOf(snapshots[random.nextInt(snapshots.size)]))
                3 -> state.markStale(now, StaleReason.DefinitionChanged)
                4 -> state.loadFailed(now, LoadFailure.Gradle(GradleFailure.Other(listOf("failure at $now"))))
                else -> state.unavailable(DefinitionUnavailableReason.entries[random.nextInt(DefinitionUnavailableReason.entries.size)])
            }
            if (!dropEqual || state != walk.last()) walk += state
        }
        return walk
    }
}

/** Sequences of [ViolationsState], for the same purpose as [DefinitionStateScripts]. */
internal object ViolationsStateScripts {
    /** A violation of [module] at [path] line 1. */
    fun violation(module: ModuleId, path: Path, ruleId: String = "katachi.UnexpectedFile"): ViolationModel = ViolationModel(
        module = module,
        ruleId = ruleId,
        label = ruleId.substringAfterLast('.'),
        level = SarifLevel.Error,
        message = "No role is defined for this file.",
        location = SarifLocation(path, 1),
        related = emptyList(),
        properties = emptyMap(),
    )

    /**
     * `NotRun`, a check (`Ready`), an edit after it (`stale`), a half-written file (`Unreadable(previous)`), the
     * check again, and the report deleted by the test task's start (`NotRun`).
     */
    fun standardCycle(violations: List<ViolationModel>, sarif: Path): List<ViolationsState> {
        val first = ViolationsState.Ready(violations, DEFINITION_INSTANT, stale = false, paths = listOf(sarif))
        val stale = first.copy(stale = true)
        val unreadable = ViolationsState.Unreadable(sarif, "cut off", previous = stale)
        val second = ViolationsState.Ready(violations.drop(1), DEFINITION_INSTANT.plusSeconds(60), stale = false, paths = listOf(sarif))
        return listOf(ViolationsState.NotRun, first, stale, unreadable, second, ViolationsState.NotRun)
    }
}
