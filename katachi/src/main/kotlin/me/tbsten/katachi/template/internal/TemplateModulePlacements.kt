package me.tbsten.katachi.template.internal

import kotlinx.serialization.Serializable
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.fillCapturedPath
import me.tbsten.katachi.dsl.internal.ModuleCapture
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModuleMiss
import me.tbsten.katachi.dsl.internal.ModulePattern
import me.tbsten.katachi.dsl.internal.ResolvedModule
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.internal.catching

@Serializable
internal class TemplateModulePlacement(
    val template: String,
    val modulePattern: String,
    val captureNames: List<String>,
    val modules: List<TemplateModuleChoice>,
)

@Serializable
internal class TemplateModuleChoice(
    val values: List<String>,
    val modulePath: String,
    val directory: String,
    val path: String,
)

internal fun modulePlacementsOf(templates: List<DeclaredTemplate>, modules: () -> ModuleIndex): List<TemplateModulePlacement> {
    val below = templates.filter { moduleCaptureOf(it.entries.first())?.let(::isFullyNamed) == true }
    if (below.isEmpty()) return emptyList()
    val index = catching { modules() }.getOrNull() ?: return emptyList()
    return below.mapNotNull { template -> catching { placementOf(template, index) }.getOrNull() }
}

private fun moduleCaptureOf(entry: LayoutEntry): ModuleCapture? =
    (entry.templateCaptures ?: entry.captureVariants.firstOrNull() ?: LayoutCaptures.NONE).moduleCapture

// Only a fully named key can pick one module (ModuleIndex.boundTo keeps a key with an unnamed * as a pattern).
private fun isFullyNamed(capture: ModuleCapture): Boolean =
    capture.names.size == compiled(capture.modulePattern).singleWildcardCount

private fun compiled(modulePattern: String): ModulePattern =
    // An already-flattened key (`*`, no capture token): nothing here can raise
    // KatachiAdjacentCaptureException, so which declaration site is blamed does not matter.
    ModulePattern.compile(modulePattern, DeclarationSite.Unknown)

private fun placementOf(template: DeclaredTemplate, index: ModuleIndex): TemplateModulePlacement? {
    val capture = moduleCaptureOf(template.entries.first()) ?: return null
    val names = capture.names
    val matching = index.matchingModules(compiled(capture.modulePattern))
        // Two modules can share the values only below a trailing `**`; a run picks the first, as here.
        .distinctBy { it.wildcards.take(names.size) }
    return TemplateModulePlacement(
        template = template.specifier,
        modulePattern = capture.modulePattern,
        captureNames = names,
        modules = matching.mapNotNull { module -> choiceOf(template, index, names, module) },
    )
}

private fun choiceOf(
    template: DeclaredTemplate,
    index: ModuleIndex,
    names: List<String>,
    module: ResolvedModule,
): TemplateModuleChoice? {
    val values = module.wildcards.take(names.size)
    val key = template.template.declarationKey
    val entry = template.role.flattenLayout(index.boundTo(names.zip(values).toMap(), mutableListOf<ModuleMiss>()))
        .firstOrNull { it.role === template.role && it[Template]?.declarationKey == key }
        ?: return null
    val variant = entry.templateCaptures ?: entry.captureVariants.firstOrNull() ?: LayoutCaptures.NONE
    return TemplateModuleChoice(
        values = values,
        modulePath = module.path.value,
        directory = module.directory,
        path = fillCapturedPath(entry.path, variant, variant.names.associateWith(::placeholderOf), ::placeholderOf),
    )
}
