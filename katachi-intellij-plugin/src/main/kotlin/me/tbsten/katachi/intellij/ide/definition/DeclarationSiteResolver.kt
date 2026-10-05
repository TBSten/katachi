package me.tbsten.katachi.intellij.ide.definition

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleId
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a `FileName.kt:NN` of a definition among its module's test source roots (design 4.6). The roots are
 * the IDE's test source roots of the definition's own test source set, `<module>/src/<set>/` for `test`,
 * `jvmTest`, `commonTest` and the like ([TEST_SOURCE_SETS]), else `src/test/kotlin` and `src/test/java`.
 * Other test source sets (`integrationTest`, `testFixtures`) are not the definition's. Each
 * module's roots are walked once into "file name → files", without `FilenameIndex`, so it answers in dumb
 * mode too. A change under a definition's sources drops the walk [REBUILD_DELAY_MILLIS] after the last one,
 * so that a `git checkout` of hundreds of files walks once; the next question walks again. The roots themselves
 * change with the build model, so they are dropped by [invalidateRoots] (a Gradle sync), not by a change of the sources.
 *
 * ```kotlin
 * val resolver = DeclarationSiteResolver(project, scope)
 * when (val site = resolver.resolve(module, DeclarationSiteRef("UseCaseRole.kt", 16))) { ... }
 * ```
 */
internal class DeclarationSiteResolver(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    private class SiteIndex(val roots: List<VirtualFile>, val byName: Map<String, List<VirtualFile>>)

    private val indexes = ConcurrentHashMap<ModuleId, SiteIndex>()
    private val roots = ConcurrentHashMap<ModuleId, List<VirtualFile>>()
    private val pending: MutableSet<ModuleId> = ConcurrentHashMap.newKeySet()

    @Volatile private var rebuild: Job? = null

    /** Where [site] of [module]'s definition is. Zero or several files of that name are [ResolvedSite.Unresolved]. */
    fun resolve(module: KatachiModule, site: DeclarationSiteRef): ResolvedSite {
        var index = indexOf(module)
        // A file of the walk deleted since: walk again now rather than answer with it.
        if (index.byName[site.fileName].orEmpty().any { !it.isValid }) {
            indexes.remove(module.id)
            index = indexOf(module)
        }
        if (index.roots.isEmpty()) return ResolvedSite.Unresolved("no test source root in ${module.directory}")
        val found = index.byName[site.fileName].orEmpty()
        return when (found.size) {
            1 -> ResolvedSite.Found(found.single(), site.line)
            0 -> ResolvedSite.Unresolved("no ${site.fileName} under the test sources of ${module.gradlePath}")
            else -> ResolvedSite.Unresolved("${found.size} files named ${site.fileName} under the test sources of ${module.gradlePath}")
        }
    }

    /**
     * The test source roots of [module], as paths: what the file watcher counts as the definition's own sources.
     * Asked for every batch of VFS events, so even "none" is kept here until the sources change.
     */
    fun testRootsOf(module: KatachiModule): List<Path> = (roots[module.id] ?: rootsOf(module).also { roots[module.id] = it }).mapNotNull { root ->
        sdkCall("read the path of $root") { root.fileSystem.getNioPath(root) }.getOrNull()
    }

    /**
     * The build model changed (a Gradle sync): the roots are found again. The roots are not dropped when sources
     * change, as finding them walks every IDE module, in a VFS event's `prepareChange`.
     */
    fun invalidateRoots() {
        roots.clear()
        indexes.clear()
    }

    /**
     * Sources of [modules] changed: their walks are dropped once the changes stop for [REBUILD_DELAY_MILLIS],
     * and so are roots found to be none (`src/test/kotlin` may be created by this very change).
     */
    fun invalidate(modules: Set<ModuleId>) {
        if (modules.isEmpty()) return
        pending += modules
        rebuild?.cancel()
        rebuild = scope.launch {
            delay(REBUILD_DELAY_MILLIS)
            val ids = pending.toList()
            pending -= ids.toSet()
            ids.forEach { id ->
                indexes.remove(id)
                if (roots[id]?.isEmpty() == true) roots.remove(id)
            }
        }
    }

    /** The walk, kept once it found a root (see [rootsOf]). */
    private fun indexOf(module: KatachiModule): SiteIndex = indexes[module.id] ?: walk(module).also { if (it.roots.isNotEmpty()) indexes[module.id] = it }

    /**
     * The roots, kept once found. None found is looked for again: `src/test/kotlin` may be created a moment later
     * (the file watcher asks before the VFS applies the creation).
     */
    private fun rootsOf(module: KatachiModule): List<VirtualFile> = roots[module.id]?.takeIf { it.isNotEmpty() }
        ?: testRootsFromModules(module).ifEmpty { fallbackRoots(module) }.also { found -> if (found.isNotEmpty()) roots[module.id] = found }

    private fun walk(module: KatachiModule): SiteIndex {
        val roots = rootsOf(module).filter { it.isValid }
        val byName = HashMap<String, MutableList<VirtualFile>>()
        sdkCall("walk the test sources of ${module.gradlePath}") {
            ApplicationManager.getApplication().runReadAction(
                Runnable {
                    for (root in roots) {
                        VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
                            // The daemon asks from inside its own read action: a write that waits (typing) cancels it here, and no half walk is kept.
                            ProgressManager.checkCanceled()
                            if (!file.isDirectory) byName.getOrPut(file.name) { mutableListOf() } += file
                            true
                        }
                    }
                },
            )
        }
        return SiteIndex(roots, byName)
    }

    /** The test source roots the IDE knows under `<module>/src/<set>/` for the definition's own test source sets. */
    private fun testRootsFromModules(module: KatachiModule): List<VirtualFile> {
        val sources = module.directory.resolve("src")
        return sdkCall("read the test source roots of ${module.gradlePath}") {
            ApplicationManager.getApplication().runReadAction(
                Computable {
                    ModuleManager.getInstance(project).modules.flatMap { ideModule ->
                        val roots = ModuleRootManager.getInstance(ideModule)
                        roots.getSourceRoots(true).toList() - roots.getSourceRoots(false).toSet()
                    }.filter { root -> root.fileSystem.getNioPath(root)?.let { isTestSourceSetRoot(it, sources) } == true }.distinct()
                },
            )
        }.getOrNull().orEmpty()
    }

    private fun fallbackRoots(module: KatachiModule): List<VirtualFile> = FALLBACK_ROOTS.mapNotNull { relative ->
        sdkCall("find ${module.directory.resolve(relative)}") {
            LocalFileSystem.getInstance().findFileByNioFile(module.directory.resolve(relative))
        }.getOrNull()
    }

    private companion object {
        /** The source sets a definition module's tests live in (Gradle's `test`, Kotlin Multiplatform's JVM and common test). */
        val TEST_SOURCE_SETS = setOf("test", "jvmTest", "commonTest", "desktopTest")

        /** Whether [root] is `<sources>/<set>` or below it, for a set of [TEST_SOURCE_SETS]. */
        fun isTestSourceSetRoot(root: Path, sources: Path): Boolean =
            root.startsWith(sources) && root.nameCount > sources.nameCount && root.getName(sources.nameCount).toString() in TEST_SOURCE_SETS

        /** How long the sources must stay unchanged before a walk is dropped (design 4.6). */
        const val REBUILD_DELAY_MILLIS = 500L
        val FALLBACK_ROOTS = listOf("src/test/kotlin", "src/test/java")
    }
}
