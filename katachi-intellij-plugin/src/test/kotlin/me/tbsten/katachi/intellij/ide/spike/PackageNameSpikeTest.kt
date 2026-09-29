package me.tbsten.katachi.intellij.ide.spike

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PsiTestUtil
import me.tbsten.katachi.intellij.ide.KatachiIdeTestBase
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files
import java.nio.file.Path

/** S2 (f): the package of a directory without PSI, also for directories that do not exist yet. */
internal class PackageNameSpikeTest : KatachiIdeTestBase() {
    private val sourceRoot get() = root.resolve("feature/src/main/kotlin")

    private fun vfs(path: Path): VirtualFile {
        Files.createDirectories(path)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: throw AssertionError("not in VFS: $path")
    }

    private fun contentRootUrls(): List<String> = ModuleRootManager.getInstance(module).contentRootUrls.toList()

    /** Adds [dir] as a source root (and content root) for [block], then removes the whole content entry. */
    private fun <T> withSourceRoot(dir: VirtualFile, packagePrefix: String = "", block: () -> T): T {
        PsiTestUtil.addSourceRoot(module, dir, JavaSourceRootType.SOURCE, JpsJavaExtensionService.getInstance().createSourceRootProperties(packagePrefix))
        try {
            return block()
        } finally {
            PsiTestUtil.removeContentEntry(module, dir)
        }
    }

    // covers: 論点16
    fun `test f ソースルートの中の実在するディレクトリのパッケージ名を PSI なしで引ける`() {
        val src = vfs(sourceRoot)
        vfs(sourceRoot.resolve("com/example/feature"))

        val (atRoot, inside) = withSourceRoot(src) {
            onBgtWithReadLock { packageNameOf(project, sourceRoot) to packageNameOf(project, sourceRoot.resolve("com/example/feature")) }
        }

        assertEquals("", atRoot)
        assertEquals("com.example.feature", inside)
    }

    // covers: 論点16
    fun `test f まだ無いディレクトリは実在する一番近い祖先のパッケージ名に残りのセグメントを足す`() {
        val src = vfs(sourceRoot)
        vfs(sourceRoot.resolve("com/example"))

        val name = withSourceRoot(src) { onBgtWithReadLock { packageNameOf(project, sourceRoot.resolve("com/example/feature/ui/detail")) } }

        assertEquals("com.example.feature.ui.detail", name)
    }

    // covers: 論点16
    fun `test f パッケージの接頭辞があるソースルートでは接頭辞から始まる`() {
        val src = vfs(sourceRoot)

        val name = withSourceRoot(src, packagePrefix = "com.example") { onBgtWithReadLock { packageNameOf(project, sourceRoot.resolve("feature")) } }

        assertEquals("com.example.feature", name)
    }

    // covers: 論点16
    fun `test f ソースルートの外とまだ無いソースルートの中は null`() {
        val src = vfs(sourceRoot)
        vfs(root.resolve("feature/build"))

        val (outside, missingRoot) = withSourceRoot(src) {
            onBgtWithReadLock {
                packageNameOf(project, root.resolve("feature/build/generated")) to
                    packageNameOf(project, root.resolve("other/src/main/kotlin/com/example"))
            }
        }

        assertNull(outside)
        assertNull(missingRoot)
    }

    // covers: 論点16
    fun `test f 識別子にならないセグメントを含むと null`() {
        val src = vfs(sourceRoot)

        val name = withSourceRoot(src) { onBgtWithReadLock { packageNameOf(project, sourceRoot.resolve("com/my-app/ui")) } }

        assertNull(name)
    }

    // covers: 論点16
    fun `test f removeContentEntry で外すとモジュールのコンテンツルートが元に戻る`() {
        val before = contentRootUrls()
        val src = vfs(sourceRoot)

        withSourceRoot(src) { spike("f.contentRoots.during", contentRootUrls()) }

        spike("f.contentRoots.before", before)
        spike("f.contentRoots.after", contentRootUrls())
        assertEquals(before, contentRootUrls())
    }

    // covers: 論点16
    fun `test f removeSourceRoot だけではコンテンツルートが残って次のテストに漏れる`() {
        val before = contentRootUrls()
        val src = vfs(sourceRoot)
        PsiTestUtil.addSourceRoot(module, src)

        PsiTestUtil.removeSourceRoot(module, src)
        val leaked = contentRootUrls()
        PsiTestUtil.removeContentEntry(module, src)

        spike("f.removeSourceRootOnly", leaked)
        assertEquals(before + src.url, leaked)
        assertEquals(before, contentRootUrls())
    }

    // covers: 論点16
    fun `test f 非推奨の API を使わずソースルートと接頭辞からでも同じパッケージ名になる`() {
        val src = vfs(sourceRoot)
        vfs(sourceRoot.resolve("com/example"))
        vfs(root.resolve("feature/build"))
        val paths = listOf(
            sourceRoot,
            sourceRoot.resolve("com/example"),
            sourceRoot.resolve("com/example/feature/ui"),
            sourceRoot.resolve("com/my-app/ui"),
            root.resolve("feature/build/generated"),
            root.resolve("other/src/main/kotlin/com/example"),
        )

        for (prefix in listOf("", "org.sample")) {
            val (deprecated, viaRoot) = withSourceRoot(src, packagePrefix = prefix) {
                onBgtWithReadLock {
                    paths.map { packageNameOf(project, it) } to paths.map { packageNameOf(project, it, ::packageOfDirectoryViaSourceRoot) }
                }
            }
            spike("f.viaSourceRoot[$prefix]", viaRoot)
            assertEquals(deprecated, viaRoot)
        }
    }
}

/**
 * The package of [dir], which may not exist yet: the package of its nearest ancestor the VFS knows,
 * plus the remaining segments. `null` outside a source root or when a segment is not an identifier.
 * Call in a read action; it only uses the VFS and ProjectFileIndex (no PSI, no refresh).
 */
internal fun packageNameOf(
    project: Project,
    dir: Path,
    packageOfDirectory: (Project, VirtualFile) -> String? = { p, f -> @Suppress("DEPRECATION") ProjectFileIndex.getInstance(p).getPackageNameByDirectory(f) },
): String? {
    val fileSystem = LocalFileSystem.getInstance()
    val remaining = ArrayDeque<String>()
    var current: Path? = dir
    while (current != null) {
        val file = fileSystem.findFileByNioFile(current)
        if (file != null && file.isDirectory) {
            val base = packageOfDirectory(project, file) ?: return null
            if (!remaining.all(::isIdentifier)) return null
            return (listOf(base).filter { it.isNotEmpty() } + remaining).joinToString(".")
        }
        remaining.addFirst(current.fileName?.toString() ?: return null)
        current = current.parent
    }
    return null
}

/**
 * The same without the deprecated ProjectFileIndex.getPackageNameByDirectory (PackageIndex has a live one, but it
 * lives in the Java plugin): the source root, its package prefix and the relative path.
 */
internal fun packageOfDirectoryViaSourceRoot(project: Project, dir: VirtualFile): String? {
    val index = ProjectFileIndex.getInstance(project)
    val sourceRoot = index.getSourceRootForFile(dir) ?: return null
    val module = index.getModuleForFile(dir) ?: return null
    val prefix = ModuleRootManager.getInstance(module).contentEntries.asSequence()
        .flatMap { it.sourceFolders.asSequence() }
        .firstOrNull { it.file == sourceRoot }?.packagePrefix.orEmpty()
    val relative = VfsUtilCore.getRelativePath(dir, sourceRoot, '.') ?: return null
    return listOf(prefix, relative).filter { it.isNotEmpty() }.joinToString(".")
}

private fun isIdentifier(segment: String): Boolean =
    segment.isNotEmpty() && Character.isJavaIdentifierStart(segment[0]) && segment.all(Character::isJavaIdentifierPart)
