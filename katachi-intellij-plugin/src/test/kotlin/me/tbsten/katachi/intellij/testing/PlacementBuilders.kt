package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.DescriptionSnapshot
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateModel
import java.nio.file.Path
import java.time.Instant

// Builders for the placement index (A1). Platform-free: compiled into uiTest too.

internal fun snapshotOf(module: KatachiModule, templates: List<TemplateModel>): DescriptionSnapshot =
    DescriptionSnapshot(module, katachiVersion = null, templates = templates, loadedAt = Instant.parse("2026-09-27T00:00:00Z"))

/** The index of [snapshots], every definition's pattern root being its `linkedRootPath` (the ordinary case). */
internal fun indexOf(vararg snapshots: DescriptionSnapshot): TemplatePlacementIndex =
    TemplatePlacementIndex.build(snapshots.toList()) { it.linkedRootPath }

/** A template of [roleName] whose one file has [pattern], its captures ([captureNames]) and no other parameter. */
internal fun patternTemplate(
    roleName: String,
    pattern: String,
    captureNames: List<String> = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""").findAll(pattern).map { it.groupValues[1] }.distinct().toList(),
    moduleCaptures: Set<String> = emptySet(),
    id: String? = null,
): TemplateModel = template(
    roleName = roleName,
    id = id,
    parameters = emptyList(),
    files = listOf(file(pattern.substringAfterLast('/'), path = null, pattern = pattern, captures = captureNames)),
    captures = captureNames.map { capture(it, module = it in moduleCaptures) },
)

internal fun Path.resolveAll(relative: String): Path = if (relative.isEmpty()) this else resolve(relative)
