package me.tbsten.katachi.intellij.uitest.pbt

import me.tbsten.katachi.intellij.model.GenerationFailure
import me.tbsten.katachi.intellij.model.GenerationItemResult
import me.tbsten.katachi.intellij.model.GradleFailure
import me.tbsten.katachi.intellij.model.LoadFailure
import me.tbsten.katachi.intellij.model.allParametersOf
import me.tbsten.katachi.intellij.presentation.BodyUi
import me.tbsten.katachi.intellij.presentation.FieldId
import me.tbsten.katachi.intellij.presentation.FooterUi
import me.tbsten.katachi.intellij.presentation.GenerationState
import me.tbsten.katachi.intellij.presentation.KatachiIntent
import me.tbsten.katachi.intellij.presentation.KatachiScreenState
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.presentation.ScreenPhase

/*
 * What must hold after every step, whatever came before. Each check returns what it found wrong,
 * worded for the failure message.
 */

/** The combinations of state the transitions of spec 02 / 04 can reach. */
internal fun stateViolationsOf(state: KatachiScreenState): List<String> = buildList {
    val generation = state.generation
    val rowIds = state.rows.map { it.id }.toSet()
    if (generation != null && state.phase != ScreenPhase.Ready) add("a generation (${generation::class.simpleName}) outside the list: ${state.phase}")
    if (state.loading?.isInitial == true && state.snapshots.isNotEmpty()) add("the first-load progress over a list that is there")
    if (state.phase == ScreenPhase.Ready && state.snapshots.isEmpty()) add("the list is ready with nothing loaded")
    if (generation is GenerationState.Running) {
        if (state.loading != null && !generation.waitingForLoad) add("a load runs while generating, and the generation does not wait for it")
        val gone = generation.rows.filter { it !in rowIds }
        if (gone.isNotEmpty()) add("generating rows that are no longer in the list: $gone")
    }
    val notListed = state.form.selected.filter { it !in rowIds }
    if (notListed.isNotEmpty()) add("checked rows that are not in the list: $notListed")
    val unavailable = state.rows.filter { state.form.isSelected(it.id) && !it.template.isAvailable }.map { it.id }
    if (unavailable.isNotEmpty()) add("checked rows that cannot be checked: $unavailable")
    val openUnchecked = state.form.expanded.filter { !state.form.isSelected(it) }
    if (openUnchecked.isNotEmpty()) add("open forms of unchecked rows: $openUnchecked")
    unexpectedFailureOf(state)?.let { add(it) }
}

/** What the screen shows must agree with the state it came from. */
internal fun uiViolationsOf(state: KatachiScreenState, ui: KatachiUiState): List<String> = buildList {
    val list = (ui.body as? BodyUi.Listing)?.list ?: return@buildList
    val expected = when (state.generation) {
        null -> FooterUi.Form::class
        is GenerationState.Running -> FooterUi.Generating::class
        is GenerationState.Finished -> FooterUi.Result::class
    }
    if (list.footer::class != expected) add("footer ${list.footer::class.simpleName} for generation ${state.generation?.let { it::class.simpleName }}")
    if (list.search.enabled != (state.generation == null)) add("search enabled=${list.search.enabled} with generation ${state.generation}")
}

/**
 * Checks and inputs only go away when the user took them away (unchecking, the result buttons,
 * typing into the same-named field) or when a load removed their template or parameter (E-45).
 */
internal fun keptViolationsOf(before: KatachiScreenState, intents: List<KatachiIntent>, after: KatachiScreenState): List<String> = buildList {
    val resultButton = intents.any {
        it == KatachiIntent.RetryRemaining || it == KatachiIntent.ContinueGenerating || it == KatachiIntent.UncheckAll
    }
    if (resultButton) return@buildList
    val afterRows = after.rows.associateBy { it.id }
    for (id in before.form.selected) {
        if (after.form.isSelected(id)) continue
        val row = afterRows[id]
        val explained = KatachiIntent.ToggleCheck(id) in intents || row == null || !row.template.isAvailable
        if (!explained) add("the check of ${id.roleName} (${id.module.gradlePath}) went away")
    }
    val typedNames = intents.mapNotNull {
        when (it) {
            is KatachiIntent.Input -> it.field.parameterName
            is KatachiIntent.Relink -> it.field.parameterName
            else -> null
        }
    }.toSet()
    for ((id, inputs) in before.form.inputs) {
        val detail = afterRows[id]?.template?.detail
        val names = detail?.let { allParametersOf(it).map { parameter -> parameter.name }.toSet() }.orEmpty()
        for ((name, value) in inputs) {
            if (value.isBlank() || after.form.inputOf(FieldId(id, name)) == value) continue
            val explained = name in typedNames || name !in names || KatachiIntent.ToggleCheck(id) in intents
            if (!explained) add("the input $name=\"$value\" of ${id.roleName} changed to \"${after.form.inputOf(FieldId(id, name))}\"")
        }
    }
}

/**
 * A failure that says an exception escaped the plugin's code: the fakes never answer with one, so
 * an exception name in a failure is the ViewModel's catch-all at work. And a generated definition
 * always loads, so a JSON error is the encoder's or the parser's.
 */
private fun unexpectedFailureOf(state: KatachiScreenState): String? {
    fun thrown(lines: List<String>) = lines.firstOrNull { it.contains("Exception") || it.contains("Error:") }
    fun ofLoad(failure: LoadFailure?): String? = when (failure) {
        is LoadFailure.Gradle -> (failure.failure as? GradleFailure.Other)?.let { thrown(it.details) }
        is LoadFailure.MalformedJson -> "malformed JSON: ${failure.message}"
        is LoadFailure.IncompatibleJson -> "incompatible JSON at ${failure.location}: ${failure.message}"
        else -> null
    }
    ofLoad(state.loadErrorBanner)?.let { return "load failed unexpectedly: $it" }
    ofLoad((state.phase as? ScreenPhase.LoadError)?.failure)?.let { return "load failed unexpectedly: $it" }
    val report = (state.generation as? GenerationState.Finished)?.report ?: return null
    for (item in report.items) {
        val failure = (item.result as? GenerationItemResult.Failed)?.failure as? GenerationFailure.NotReached ?: continue
        val other = failure.failure as? GradleFailure.Other ?: continue
        thrown(other.details)?.let { return "generating ${item.templateId.roleName} failed unexpectedly: $it" }
    }
    return null
}
