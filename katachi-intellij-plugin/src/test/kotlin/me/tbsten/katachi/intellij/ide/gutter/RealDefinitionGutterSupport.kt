package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tbsten.katachi.intellij.data.definition.DefinitionLookup
import me.tbsten.katachi.intellij.ide.definition.DefinitionModelService
import me.tbsten.katachi.intellij.ide.definition.RefreshReason
import me.tbsten.katachi.intellij.ide.definition.ResolvedSite
import me.tbsten.katachi.intellij.model.DeclarationSiteRef
import me.tbsten.katachi.intellij.model.DefinitionSnapshot
import me.tbsten.katachi.intellij.model.DefinitionState
import me.tbsten.katachi.intellij.model.FileRoles
import me.tbsten.katachi.intellij.model.Freshness
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.RoleFileDeclarations
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.relativeTo

/**
 * A [DefinitionModelService] over one definition read from a real `definition.json`: the real [DefinitionLookup] answers
 * "which roles", "which declarations", and [resolve] finds a `FileName.kt:NN` among the files of the source tree under
 * [treeRoot] the way the real resolver does among the test source roots (`src/test`, exactly one file of that name).
 * Only the state is fixed (`Ready`, fresh). Nothing here knows what the definition says.
 *
 * ```kotlin
 * val service = LookupDefinitionService(snapshot, root)
 * service.forPath(root.resolve("src/main/kotlin/com/example/service/HealthService.kt"))?.matches
 * ```
 */
internal class LookupDefinitionService(private val snapshot: DefinitionSnapshot, private val treeRoot: Path) : DefinitionModelService {
    private val lookup = DefinitionLookup(listOf(snapshot))
    private val mutableState = MutableStateFlow<DefinitionState>(DefinitionState.Ready(listOf(snapshot), Freshness.Fresh))

    /** The files under `src/test` by name, as the real resolver's index of the test source roots has them. */
    private val testFilesByName: Map<String, List<Path>> by lazy {
        Files.walk(treeRoot).use { paths ->
            paths.filter { Files.isRegularFile(it) && "/src/test/" in "/" + it.relativeTo(treeRoot).toString().replace(File.separatorChar, '/') }
                .toList().groupBy { it.fileName.toString() }
        }
    }

    override val state: StateFlow<DefinitionState> = mutableState.asStateFlow()

    override val definitions: List<DefinitionSnapshot> get() = listOf(snapshot)

    override fun ensureLoaded() = Unit

    override fun refresh(reason: RefreshReason) = Unit

    override fun forFile(file: VirtualFile): FileRoles? = forPath(Paths.get(file.path))

    override fun forPath(path: Path): FileRoles? = lookup.forPath(path, Freshness.Fresh)

    override fun definitionOf(module: ModuleId): DefinitionSnapshot? = snapshot.takeIf { it.module.id == module }

    override fun fileDeclarationsOf(file: VirtualFile): List<RoleFileDeclarations> =
        if (file.extension == "kt") lookup.fileDeclarationsOf(Paths.get(file.path), Freshness.Fresh) else emptyList()

    override fun resolve(module: ModuleId, site: DeclarationSiteRef): ResolvedSite {
        val found = testFilesByName[site.fileName].orEmpty()
        return when (found.size) {
            1 -> ResolvedSite.Found(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(found.single()) ?: return ResolvedSite.Unresolved("${found.single()} is not in the VFS"), site.line)
            0 -> ResolvedSite.Unresolved("no ${site.fileName} under the test sources")
            else -> ResolvedSite.Unresolved("${found.size} files named ${site.fileName} under the test sources")
        }
    }

    /** The one file [site] names among the test sources, or `null` when there is none or several. */
    fun fileOf(site: DeclarationSiteRef): Path? = testFilesByName[site.fileName]?.singleOrNull()
}

/** The copies of the samples' source files under `src/test/resources/contract/gutter/sources/<sample>/`, and the goldens beside them. */
internal object GutterSources {
    private const val SOURCES = "/contract/gutter/sources"

    /** Where the goldens are written to (the test's working directory is the plugin's project directory). */
    fun goldenFile(sample: String): File = File("src/test/resources/contract/gutter/$sample.txt")

    /** The directory of [sample]'s copied sources on the class path. */
    private fun directory(sample: String): Path {
        val url = GutterSources::class.java.getResource("$SOURCES/$sample") ?: throw IllegalArgumentException("No test resource $SOURCES/$sample")
        return Paths.get(url.toURI())
    }

    /** The relative path (`/` separated) of every copied file of [sample], sorted. */
    fun files(sample: String): List<String> {
        val directory = directory(sample)
        return Files.walk(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) }.map { it.relativeTo(directory).toString().replace(File.separatorChar, '/') }.sorted().toList()
        }
    }

    /** Copies [sample]'s sources under [target], keeping their relative paths. */
    fun copyTo(sample: String, target: Path) {
        val directory = directory(sample)
        for (relative in files(sample)) {
            val to = target.resolve(relative)
            Files.createDirectories(to.parent)
            Files.copy(directory.resolve(relative), to)
        }
    }
}
