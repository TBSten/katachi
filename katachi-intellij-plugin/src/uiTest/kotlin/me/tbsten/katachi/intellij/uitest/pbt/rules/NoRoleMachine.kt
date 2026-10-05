package me.tbsten.katachi.intellij.uitest.pbt.rules

import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.DefinitionUnavailableReason
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.StaleReason
import me.tbsten.katachi.intellij.model.answer
import me.tbsten.katachi.intellij.model.loadFailed
import me.tbsten.katachi.intellij.model.loaded
import me.tbsten.katachi.intellij.model.markStale
import me.tbsten.katachi.intellij.model.startLoading
import me.tbsten.katachi.intellij.model.unavailable
import me.tbsten.katachi.intellij.presentation.entry.EditorNotificationMemory
import me.tbsten.katachi.intellij.presentation.rules.NoRoleDecision
import me.tbsten.katachi.intellij.presentation.rules.RuleSettings
import me.tbsten.katachi.intellij.presentation.rules.VcsTracking
import me.tbsten.katachi.intellij.presentation.rules.decideNoRoleNotification
import me.tbsten.katachi.intellij.testing.DEFINITION_INSTANT
import me.tbsten.katachi.intellij.testing.DEFINITION_ROOT
import me.tbsten.katachi.intellij.testing.HELD
import me.tbsten.katachi.intellij.testing.MISNAMED
import me.tbsten.katachi.intellij.testing.STRAY
import me.tbsten.katachi.intellij.testing.noRoleInput
import me.tbsten.katachi.intellij.testing.noRoleSnapshot
import me.tbsten.katachi.intellij.testing.rolesFor
import java.nio.file.Path
import java.time.Duration

/** One thing the user or the IDE does to the no-role notification of three files. */
internal sealed interface NoRoleOp {
    data class Dismiss(val file: Int) : NoRoleOp
    data class SetVcs(val file: Int, val vcs: VcsTracking) : NoRoleOp
    data class Toggle(val setting: Int) : NoRoleOp

    /** A step of the definition's life: 0 loading, 1 loaded, 2 an edit (stale), 3 a failed read, 4 nothing to read. */
    data class Definition(val step: Int) : NoRoleOp
    data object NewSession : NoRoleOp
}

/**
 * The three files of the machine: one nobody holds under a directory no role knows, one nobody holds in a known directory
 * (its name is not taken), one a role holds. Played two ways at once: the real [EditorNotificationMemory], the real
 * [DefinitionState] transitions and [decideNoRoleNotification], and a plain model that knows only the rules of design 2.1
 * (settings, the file's × , whether an answer was ever read) from the operations alone, never from the state's shape.
 */
internal class NoRoleMachine {
    val log = mutableListOf<String>()
    private val files = listOf(STRAY, MISNAMED, HELD)

    private var state: DefinitionState = DefinitionState.NotLoaded
    private var memory = EditorNotificationMemory.EMPTY
    private var settings = RuleSettings()
    private val vcs = MutableList(files.size) { VcsTracking.Tracked }
    private var clock = DEFINITION_INSTANT

    // The plain model
    private var everRead = false
    private var staleNow = false
    private var gone = false
    private val closed = MutableList(files.size) { false }

    fun apply(op: NoRoleOp) {
        log += op.toString()
        when (op) {
            is NoRoleOp.Dismiss -> {
                memory = memory.dismissNoRole(path(op.file))
                closed[op.file] = true
            }
            is NoRoleOp.SetVcs -> vcs[op.file] = op.vcs
            is NoRoleOp.Toggle -> settings = if (op.setting == 0) settings.copy(notifyOnNoRole = !settings.notifyOnNoRole) else settings.copy(notificationsEnabled = !settings.notificationsEnabled)
            is NoRoleOp.Definition -> definitionStep(op.step)
            NoRoleOp.NewSession -> {
                memory = EditorNotificationMemory.EMPTY
                closed.fill(false)
            }
        }
        check()
    }

    private fun definitionStep(step: Int) {
        clock = clock.plus(Duration.ofSeconds(1))
        when (step) {
            0 -> {
                state = state.startLoading()
                gone = false
            }
            1 -> {
                state = state.loaded(listOf(noRoleSnapshot()))
                everRead = true
                staleNow = false
                gone = false
            }
            2 -> {
                state = state.markStale(clock, StaleReason.DefinitionChanged)
                // An unavailable module has nothing to mark.
                if (everRead && !gone) staleNow = true
            }
            3 -> {
                state = state.loadFailed(clock, LoadFailure.Gradle(GradleFailure.Other(listOf("failure"))))
                // With an answer to keep, a failed read marks it stale; without one the module is unavailable.
                if (everRead) {
                    staleNow = true
                    gone = false
                } else {
                    gone = true
                }
            }
            else -> {
                state = state.unavailable(DefinitionUnavailableReason.NoDefinition)
                gone = true
            }
        }
    }

    private fun path(file: Int): Path = DEFINITION_ROOT.resolve(files[file])

    /** The decision for file [index] as the plugin would draw it now: the roles wear the state's freshness, as the service's answers do. */
    private fun decisionOf(index: Int): NoRoleDecision {
        val relative = files[index]
        return decideNoRoleNotification(
            noRoleInput(
                relative = relative,
                state = state,
                roles = rolesFor(relative, freshness = state.answer?.freshness ?: Freshness.Fresh),
                vcs = vcs[index],
                settings = settings,
                memory = memory,
            ),
        )
    }

    /** Whether file [index] shows the notification now. */
    fun shows(index: Int): Boolean = decisionOf(index) is NoRoleDecision.NoRole

    /** After every step each file's decision must be the model's, and the answer-less states must show nothing. */
    private fun check() {
        files.forEachIndexed { index, relative ->
            val decision = decisionOf(index)
            val held = relative == HELD
            val expectShown = !held && everRead && settings.notifyOnNoRole && settings.notificationsEnabled && !closed[index]
            if (decision is NoRoleDecision.NoRole != expectShown) {
                throw IllegalStateException("file $relative: model says shown=$expectShown, decision is $decision\nstate: $state")
            }
            if (decision is NoRoleDecision.NoRole) {
                // `unavailable` after a read keeps the answer but is not fresh; a failed read and an edit are stale; a clean read is neither.
                val expectStale = staleNow || gone
                if (decision.stale != expectStale) throw IllegalStateException("file $relative: stale=${decision.stale}, model says $expectStale\nstate: $state")
                val expectUntracked = vcs[index] == VcsTracking.Untracked
                if (decision.untracked != expectUntracked) throw IllegalStateException("file $relative: untracked=${decision.untracked}, model says $expectUntracked")
            }
        }
    }
}
