package me.tbsten.katachi.intellij.model

import java.nio.file.Path
import java.time.Instant

/**
 * Identifies a definition module. Two linked Gradle roots can both have a `:arch`, so the root is
 * part of the identity (E-32).
 */
internal data class ModuleId(val linkedRootPath: Path, val gradlePath: String)

/** Which of katachi's internal tasks a definition module has (design 4.1). */
internal enum class KatachiCapability {
    /** `katachiInternalDefinitionJson` (katachi 0.3 and later): the definition model and the templates in one JSON. */
    Definition,

    /** `katachiInternalTemplatesJson` (katachi 0.2 and later): the templates only. */
    Templates,
}

/**
 * A definition module: a Gradle module that has `katachiInternalDefinitionJson` or
 * `katachiInternalTemplatesJson`. [capabilities] says which; a module found without a task list
 * (Android Studio's sync setting, E-30) has none, and loading tries the definition task first.
 */
internal data class KatachiModule(
    /** The Gradle path, `:` for the root project. */
    val gradlePath: String,
    val directory: Path,
    /** The Gradle root linked to the IDE, where `runTask` runs. */
    val linkedRootPath: Path,
    /** The linked root's name, shown as `rootName › :path` when several roots are linked. */
    val rootName: String,
    val capabilities: Set<KatachiCapability> = setOf(KatachiCapability.Templates),
) {
    val id: ModuleId get() = ModuleId(linkedRootPath, gradlePath)

    /** Where `katachiInternalTemplatesJson` writes, by the contract. */
    val templateDescriptionJson: Path get() = directory.resolve(TEMPLATE_DESCRIPTION_JSON)

    /** Where `katachiInternalDefinitionJson` writes, by the contract (design 1.1). */
    val definitionJson: Path get() = directory.resolve(DEFINITION_JSON)

    /** Where `katachiInternalDefinitionJson` writes the definition's mistakes, next to [definitionJson] (design 2.1). */
    val definitionErrorsJson: Path get() = directory.resolve(DEFINITION_ERRORS_JSON)

    /** Whether loading reads the definition JSON: the module has the definition task, or nobody knows yet. */
    val readsDefinition: Boolean get() = KatachiCapability.Definition in capabilities || capabilities.isEmpty()

    /** `:<path>:<taskName>`, or `:<taskName>` for the root project. */
    fun taskPath(taskName: String): String = if (gradlePath == ":") ":$taskName" else "$gradlePath:$taskName"

    companion object {
        const val TEMPLATE_DESCRIPTION_JSON: String = "build/katachi/internalTemplatesJson/templateDescription.json"
        const val DEFINITION_ERRORS_JSON: String = "build/katachi/internalDefinitionJson/definition-errors.json"
        const val TEMPLATES_JSON_TASK: String = "katachiInternalTemplatesJson"
        const val DEFINITION_JSON: String = "build/katachi/internalDefinitionJson/definition.json"
        const val DEFINITION_JSON_TASK: String = "katachiInternalDefinitionJson"
        const val TEMPLATE_TASK: String = "katachiTemplate"
        const val DESCRIBE_TEMPLATES_TASK: String = "katachiTemplates"
    }
}

/** A template row, identified by its complete specifier. The same one may appear in two modules, and those are two rows (E-05). */
internal data class TemplateId(val module: ModuleId, val template: String)

/** A template together with the module it came from. */
internal data class ModuleTemplate(val module: KatachiModule, val template: TemplateModel) {
    val id: TemplateId get() = TemplateId(module.id, template.template)
}

/** What one module's JSON said, and when that JSON was written. */
internal data class DescriptionSnapshot(
    val module: KatachiModule,
    /** From the synced libraries; `null` when unknown. */
    val katachiVersion: String?,
    val templates: List<TemplateModel>,
    /** The JSON file's modification time: the "last loaded" of the screen (E-43). */
    val loadedAt: Instant,
    /**
     * The definition model, when the module was read through `katachiInternalDefinitionJson` (katachi 0.3 and later);
     * `null` for a templates-only module. `KatachiProjectService.definitions` is derived from it.
     */
    val definition: DefinitionModel? = null,
)

/**
 * Every template of [snapshots] in list order: modules as given, and in each module the templates
 * in [groupOrderOf]. Generation, "open the first file" and the keyboard all follow this order, so
 * it is the order the screen shows.
 */
internal fun templatesOf(snapshots: List<DescriptionSnapshot>): List<ModuleTemplate> =
    snapshots.flatMap { snapshot -> groupOrderOf(snapshot.templates).map { ModuleTemplate(snapshot.module, it) } }

/**
 * [templates] gathered by group, so that each group shows once under one header: the roles at the
 * root first, then every group in the order it first appears, its own roles before its child
 * groups (parents before children, as katachi lists groups). Declaration order stays within a
 * group. Provisional: the spec's "JSON order" assumed a group's roles are contiguous, which nested
 * groups break (`domain.UseCase`, `domain.model.Entity`, `domain.Service`).
 */
internal fun groupOrderOf(templates: List<TemplateModel>): List<TemplateModel> {
    val roles = LinkedHashMap<String, MutableList<TemplateModel>>()
    val children = HashMap<String, LinkedHashSet<String>>()
    for (template in templates) {
        roles.getOrPut(template.groupPath) { mutableListOf() } += template
        var path = template.groupPath
        while (path.isNotEmpty()) {
            val parent = path.substringBeforeLast('.', missingDelimiterValue = "")
            children.getOrPut(parent) { LinkedHashSet() } += path
            path = parent
        }
    }
    val ordered = ArrayList<TemplateModel>(templates.size)
    fun visit(path: String) {
        roles[path]?.let(ordered::addAll)
        children[path]?.forEach(::visit)
    }
    visit("")
    return ordered
}
