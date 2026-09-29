package me.tbsten.katachi.template.internal

import kotlinx.serialization.Serializable
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.ModuleCapture
import me.tbsten.katachi.dsl.internal.ModuleIndex
import me.tbsten.katachi.dsl.internal.ModuleMiss
import me.tbsten.katachi.dsl.internal.ModulePattern
import me.tbsten.katachi.dsl.internal.ResolvedModule
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.internal.catching

/**
 * One template whose file sits below a module capture (`":feature:${capture("feature")}".module { }`),
 * with every module that exists and that capture can pick: `modulePlacements[]` of
 * `templateDescription.json`.
 *
 * [me.tbsten.katachi.template.TemplateFilePreview.path] cannot say where such a file lands, since
 * which module a run picks is up to the run's values. This says it for each value a run can give:
 * the IDE plugin looks the typed values up in [modules] instead of guessing the directory from the
 * module's name, which a `ModuleResolver` may place anywhere, and which `wildcard("...")`'s naming
 * conversions (`pascalCase`, ...) may spell differently further down the path.
 */
@Serializable
internal class TemplateModulePlacement(
    /** The complete specifier of the template, as `details[].template` spells it. */
    val template: String,
    /** The module key, every capture written `*`: `":feature:*"`. */
    val modulePattern: String,
    /** The names the key gives its `*`s, in order: what `--arg` takes. */
    val captureNames: List<String>,
    /** Every existing module the key matches, in the order the project lists them. Empty when none does. */
    val modules: List<TemplateModuleChoice>,
)

/** One module a [TemplateModulePlacement] can pick, and where the template's file lands in it. */
@Serializable
internal class TemplateModuleChoice(
    /** The values of [TemplateModulePlacement.captureNames] that pick this module, in the same order. */
    val values: List<String>,
    /** The module, `":feature:home"`. */
    val modulePath: String,
    /** The module's directory relative to the project root, `/` separated, as the `ModuleResolver` answers it. */
    val directory: String,
    /**
     * Where the file lands in this module, relative to the project root: the path `katachiTemplate`
     * writes with [values], every other capture still written `${name}`.
     */
    val path: String,
)

/**
 * The [TemplateModulePlacement] of each of [templates] that sits below a module capture, in order.
 *
 * [modules] is asked once, and only when one of [templates] needs it: a definition without a
 * module capture reads nothing but its declarations, as it did before. A template this cannot
 * place -- a key with an unnamed `*`, or a failure while flattening -- is left out, which the IDE
 * reads as "not decided" rather than as "no module exists"; so is every template when the modules
 * cannot be listed at all.
 */
internal fun modulePlacementsOf(templates: List<DeclaredTemplate>, modules: () -> ModuleIndex): List<TemplateModulePlacement> {
    val below = templates.filter { moduleCaptureOf(it.entries.first())?.let(::isFullyNamed) == true }
    if (below.isEmpty()) return emptyList()
    val index = catching { modules() }.getOrNull() ?: return emptyList()
    return below.mapNotNull { template -> catching { placementOf(template, index) }.getOrNull() }
}

/** The module capture of [entry]'s own variant, read as generation (`templateFileFor`) reads it. */
private fun moduleCaptureOf(entry: LayoutEntry): ModuleCapture? =
    (entry.templateCaptures ?: entry.captureVariants.firstOrNull() ?: LayoutCaptures.NONE).moduleCapture

/**
 * Whether every single `*` of [capture]'s key has a name: only then can a run's values pick one
 * module (`ModuleIndex.boundTo` keeps a key with an unnamed `*` as a pattern).
 */
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

/**
 * Where [template]'s file lands when [module] is picked: the entry generation re-flattens the role
 * for (see `resolvedEntryFor`), with every capture but the module's written `${name}`. `null` when
 * the role places no file there -- which a run with these values would report as well.
 */
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
        path = fillCapturedPath(entry.path, variant, variant.names.associateWith(::placeholderOf)),
    )
}
