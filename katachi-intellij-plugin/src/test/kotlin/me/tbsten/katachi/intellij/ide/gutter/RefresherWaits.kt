package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.sarif.ViolationsService
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.presentation.gutter.GutterFreshness

/** The number of the last draw the real refresher handed to the daemon; read it before the operation that should cause a new one. */
internal fun redrawSeq(project: Project, refresher: HighlightingRefresher = HighlightingRefresher.getInstance(project)): Long = refresher.lastRedraw.value?.seq ?: 0

/**
 * Waits until the real [HighlightingRefresher] drew for [definitions] in a state [condition] accepts, after [since] (any draw in
 * [HighlightingRefresher.redraws]), and is idle. The wait ends on the refresher's own record, never on a clock; the 10 seconds are only the limit of a refresher that did not draw.
 */
internal fun awaitRedraw(
    since: Long,
    definitions: DefinitionModelService,
    refresher: HighlightingRefresher,
    condition: (DefinitionState?) -> Boolean = { true },
) {
    PlatformTestUtil.waitWithEventsDispatching(
        {
            "the refresher did not draw for the service after #$since in a state the test wants: " +
                "lastRedraw=${refresher.lastRedraw.value} isBusy=${refresher.isBusy} state=${definitions.state.value}"
        },
        {
            // Any draw after [since]: a slow restart may record after a newer draw, so the last record alone can name an older state.
            refresher.redraws.any { it.seq > since && it.definitions === definitions && condition(it.definition) } && !refresher.isBusy
        },
        10,
    )
}

/** Waits until the refresher drew anything after [since] and is idle: for a draw of every open file ([HighlightingRefresher.redrawNow]), which belongs to no followed service. */
internal fun awaitAnyRedraw(project: Project, since: Long) {
    val refresher = HighlightingRefresher.getInstance(project)
    PlatformTestUtil.waitWithEventsDispatching(
        { "the refresher did not draw after #$since: lastRedraw=${refresher.lastRedraw.value} isBusy=${refresher.isBusy}" },
        { (refresher.lastRedraw.value?.seq ?: 0) > since && !refresher.isBusy },
        10,
    )
}

/**
 * Knows what the refresher owes the test: it remembers the two services' states at the previous [settle], and the next
 * [settle] waits for a draw only if the change since is one the refresher draws for (going to `Loading` is not). A
 * change nobody drew for is not waited on, so a test that expects "no draw" never waits for a time to pass.
 */
internal class RefresherWatch(
    private val project: Project,
    private val definitions: () -> DefinitionModelService,
    private val violations: () -> ViolationsService?,
) {
    private var seenSeq = 0L
    private var seenDefinition: DefinitionState? = null
    private var seenViolations: Any? = null
    private var seenFollow: HighlightingRefresher.FollowStart? = null

    private val refresher get() = HighlightingRefresher.getInstance(project)

    /** Takes the current states and draw number as the baseline. */
    fun reset() {
        seenSeq = redrawSeq(project)
        seenDefinition = definitions().state.value
        seenViolations = violations()?.state?.value
        seenFollow = refresher.followStartOf(definitions())
    }

    /** Waits for the draw the changes since the previous call asked for, if any. */
    fun settle() {
        val service = definitions()
        val now = service.state.value
        val nowViolations = violations()?.state?.value
        val follow = refresher.followStartOf(service)
        // A refresher that does not follow yet owes nothing: the first draw reads the current state. One that started following
        // after the baseline counts only the changes after it started.
        val fromDefinition = if (follow == null || follow === seenFollow) seenDefinition else follow.definition
        val fromViolations = if (follow == null || follow === seenFollow) seenViolations else follow.violations
        val owed = follow != null && (GutterFreshness.shouldRedraw(fromDefinition, now) || nowViolations != fromViolations)
        if (owed) awaitRedraw(seenSeq, service, refresher) { it == service.state.value || it == now }
        // Whatever owed or not, a draw of every file ([HighlightingRefresher.redrawNow], a setting) may still be on its way: it is
        // over when the refresher is idle. redrawNow registers its job before it returns, so no clock is needed.
        PlatformTestUtil.waitWithEventsDispatching({ "the refresher stays busy: lastRedraw=${refresher.lastRedraw.value}" }, { !refresher.isBusy }, 10)
        reset()
    }
}
