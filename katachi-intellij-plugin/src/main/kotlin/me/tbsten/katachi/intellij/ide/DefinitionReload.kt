package me.tbsten.katachi.intellij.ide

import me.tbsten.katachi.intellij.model.DefinitionErrors
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.LoadFailure

/** What [KatachiProjectService.reloadAndAwait] left. */
internal sealed interface DefinitionReload {
    /** The tool window is generating; nothing was read. */
    data object Busy : DefinitionReload

    /** [failure] is set only for a one-module reload that failed: a reload of every module shows its failure in [state]. */
    data class Done(val state: DefinitionState, val errors: DefinitionErrors, val failure: LoadFailure?) : DefinitionReload
}
