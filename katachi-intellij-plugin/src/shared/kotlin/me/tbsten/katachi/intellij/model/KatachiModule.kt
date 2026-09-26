package me.tbsten.katachi.intellij.model

import java.nio.file.Path
import java.time.Instant

/**
 * Identifies a definition module. Two linked Gradle roots can both have a `:arch`, so the root is
 * part of the identity (E-32).
 */
internal data class ModuleId(val linkedRootPath: Path, val gradlePath: String)

/** A definition module: a Gradle module that has the `katachiInternalTemplatesJson` task. */
internal data class KatachiModule(
    /** The Gradle path, `:` for the root project. */
    val gradlePath: String,
    val directory: Path,
    /** The Gradle root linked to the IDE, where `runTask` runs. */
    val linkedRootPath: Path,
    /** The linked root's name, shown as `rootName › :path` when several roots are linked. */
    val rootName: String,
) {
    val id: ModuleId get() = ModuleId(linkedRootPath, gradlePath)

    /** Where `katachiInternalTemplatesJson` writes, by the contract. */
    val templateDescriptionJson: Path get() = directory.resolve(TEMPLATE_DESCRIPTION_JSON)

    /** `:<path>:<taskName>`, or `:<taskName>` for the root project. */
    fun taskPath(taskName: String): String = if (gradlePath == ":") ":$taskName" else "$gradlePath:$taskName"

    companion object {
        const val TEMPLATE_DESCRIPTION_JSON: String = "build/katachi/internalTemplatesJson/templateDescription.json"
        const val TEMPLATES_JSON_TASK: String = "katachiInternalTemplatesJson"
        const val TEMPLATE_TASK: String = "katachiTemplate"
        const val DESCRIBE_TEMPLATES_TASK: String = "katachiTemplates"
    }
}

/** A template row. The same role name may appear in two modules, and those are two rows (E-05). */
internal data class TemplateId(val module: ModuleId, val roleName: String)

/** A template together with the module it came from. */
internal data class ModuleTemplate(val module: KatachiModule, val template: TemplateModel) {
    val id: TemplateId get() = TemplateId(module.id, template.roleName)
}

/** What one module's JSON said, and when that JSON was written. */
internal data class DescriptionSnapshot(
    val module: KatachiModule,
    /** From the synced libraries; `null` when unknown. */
    val katachiVersion: String?,
    val templates: List<TemplateModel>,
    /** The JSON file's modification time: the "last loaded" of the screen (E-43). */
    val loadedAt: Instant,
)

/** Every template of [snapshots] in list order: modules as given, templates in JSON order. */
internal fun templatesOf(snapshots: List<DescriptionSnapshot>): List<ModuleTemplate> =
    snapshots.flatMap { snapshot -> snapshot.templates.map { ModuleTemplate(snapshot.module, it) } }
