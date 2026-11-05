package me.tbsten.katachi.intellij.ide.mcp

import kotlinx.coroutines.CompletableDeferred
import me.tbsten.katachi.intellij.ide.DefinitionReload
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId

/** [McpGradleActions] scripted by the test: [reloadWith] is what a reload returns once [reloadGate] (if any) completes. */
internal class FakeGradleActions(
    var modules: List<KatachiModule> = emptyList(),
    var reloadWith: DefinitionReload = DefinitionReload.Busy,
    var reloadGate: CompletableDeferred<Unit>? = null,
) : McpGradleActions {
    val reloads = mutableListOf<KatachiModule?>()
    var saves = 0
        private set

    override fun definitionModules(): List<KatachiModule> = modules

    override suspend fun reload(module: KatachiModule?): DefinitionReload {
        reloads += module
        reloadGate?.await()
        return reloadWith
    }

    override suspend fun saveAndPickModule(): ModuleId? {
        saves++
        return modules.singleOrNull()?.id
    }

    override suspend fun saveDocuments() {
        saves++
    }
}
