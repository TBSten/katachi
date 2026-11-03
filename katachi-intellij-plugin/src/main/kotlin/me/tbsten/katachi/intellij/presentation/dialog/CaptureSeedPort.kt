package me.tbsten.katachi.intellij.presentation.dialog

import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin

internal fun interface CaptureSeedPort {
    fun seedsFor(origin: EntryOrigin, template: TemplateId): Map<String, String>
}
