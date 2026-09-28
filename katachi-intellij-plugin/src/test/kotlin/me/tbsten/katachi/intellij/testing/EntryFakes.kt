package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.generate.EntryGenerationFailure
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogPort
import me.tbsten.katachi.intellij.data.generate.GenerationCatalogReload
import me.tbsten.katachi.intellij.data.placement.TemplatePlacementIndex
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.TemplateId
import me.tbsten.katachi.intellij.presentation.IdeEffects
import me.tbsten.katachi.intellij.presentation.dialog.CaptureSeedPort
import me.tbsten.katachi.intellij.presentation.entry.EntryEffects
import me.tbsten.katachi.intellij.presentation.entry.EntryOrigin
import me.tbsten.katachi.intellij.presentation.entry.GenerateDialogRequest
import java.nio.file.Path
import java.util.Collections

// Fakes for the placement, dialog and entry ports (K1). Platform-free: compiled into uiTest too.

/** Records what the notification and the New menu asked of the IDE, in order. */
internal class FakeEntryEffects : EntryEffects {
    val log: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val dialogs: MutableList<GenerateDialogRequest> = Collections.synchronizedList(mutableListOf())
    val revealed: MutableList<TemplateId> = Collections.synchronizedList(mutableListOf())

    override fun openGenerateDialog(request: GenerateDialogRequest) {
        log += "dialog"
        dialogs += request
    }

    override fun openSettings() {
        log += "settings"
    }

    override fun revealTemplateInToolWindow(template: TemplateId) {
        log += "reveal"
        revealed += template
    }
}

/**
 * [IdeEffects] for the generation from an entry, over [fileSystem] with open Documents of its own:
 * [documents] holds the text an open editor shows, [unsaved] the files whose Document differs from
 * the disk. Everything else goes to [base], whose `log` this one appends to as well.
 * [failing] makes the named effects fail, as an SDK call that threw would.
 */
internal class FakeGenerationIdeEffects(
    val fileSystem: FakeFileSystem = FakeFileSystem(),
    val base: FakeIdeEffects = FakeIdeEffects(),
    /** Package names by directory; a directory not in it is outside source roots. */
    var packages: Map<Path, String> = emptyMap(),
    var failing: Set<String> = emptySet(),
) : IdeEffects by base {
    val log: MutableList<String> get() = base.log
    val documents: MutableMap<Path, String> = Collections.synchronizedMap(mutableMapOf())
    val unsaved: MutableSet<Path> = Collections.synchronizedSet(mutableSetOf())
    val directories: MutableSet<Path> = Collections.synchronizedSet(mutableSetOf())
    val failures: MutableList<EntryGenerationFailure> = Collections.synchronizedList(mutableListOf())

    /** An edit the user typed and did not save. */
    fun typeUnsaved(path: Path, text: String) {
        documents[path] = text
        unsaved.add(path) // add, not +=: a Path is an Iterable<Path> too
    }

    override suspend fun createDirectories(directory: Path): Boolean = effect("mkdirs") { directories.add(directory) }

    override suspend fun writeProvisionalFile(path: Path, text: String): Boolean = effect("provisional") {
        fileSystem.write(path, text)
        documents[path] = text
        unsaved.remove(path)
    }

    override suspend fun currentText(path: Path): CharSequence? = documents[path] ?: fileSystem.readText(path)

    override suspend fun hasUnsavedChanges(path: Path): Boolean = path in unsaved

    override suspend fun saveDocument(path: Path): Boolean = effect("saveDocument") {
        documents[path]?.let { fileSystem.write(path, it) }
        unsaved.remove(path)
    }

    override suspend fun packageNameOf(directory: Path): String? = packages[directory]

    override suspend fun reloadFromDisk(paths: List<Path>) {
        log += "reload"
        for (path in paths) fileSystem.readText(path)?.let { documents[path] = it }
        unsaved -= paths.toSet()
    }

    override fun notifyEntryGenerationFailed(failure: EntryGenerationFailure) {
        log += "notifyEntryFailed"
        failures += failure
    }

    private inline fun effect(name: String, block: () -> Unit): Boolean {
        log += name
        if (name in failing) return false
        block()
        return true
    }
}

/** Seeds by template, whatever the origin; records the questions. */
internal class FakeCaptureSeedPort(var seeds: Map<TemplateId, Map<String, String>> = emptyMap()) : CaptureSeedPort {
    val asked: MutableList<Pair<EntryOrigin, TemplateId>> = Collections.synchronizedList(mutableListOf())

    override fun seedsFor(origin: EntryOrigin, template: TemplateId): Map<String, String> {
        asked += origin to template
        return seeds[template].orEmpty()
    }
}

/** Answers each reload with the next of [answer] (by call count) and records the own writes. */
internal class FakeGenerationCatalog(
    var answer: (KatachiModule, Int) -> GenerationCatalogReload = { _, _ -> GenerationCatalogReload.Reloaded(emptyList(), TemplatePlacementIndex.EMPTY) },
) : GenerationCatalogPort {
    val reloads: MutableList<KatachiModule> = Collections.synchronizedList(mutableListOf())
    val ownWrites: MutableList<Path> = Collections.synchronizedList(mutableListOf())

    override suspend fun reloadForGeneration(module: KatachiModule): GenerationCatalogReload {
        val reload = answer(module, reloads.size)
        reloads += module
        return reload
    }

    override fun registerOwnWrite(path: Path) {
        ownWrites.add(path)
    }
}
