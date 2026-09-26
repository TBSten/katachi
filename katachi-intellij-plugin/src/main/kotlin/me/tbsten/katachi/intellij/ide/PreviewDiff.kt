package me.tbsten.katachi.intellij.ide

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightVirtualFile
import java.nio.file.Path

/*
 * The expected contents are the JSON's preview filled with the inputs: katachi only knows the real
 * ones when it writes. Every view of them says "expected" (spec 06).
 */

/** The standard diff viewer: the existing file on the left, the expected contents on the right. Call on the EDT. */
internal fun showExpectedDiff(project: Project, existing: Path, expectedContents: String) {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(existing) ?: return
    val factory = DiffContentFactory.getInstance()
    val request = SimpleDiffRequest(
        KatachiBundle.message("diff.title", existing.fileName.toString()),
        factory.create(project, file),
        factory.create(project, expectedContents, file.fileType),
        KatachiBundle.message("diff.existing"),
        KatachiBundle.message("diff.expected"),
    )
    DiffManager.getInstance().showDiff(project, request)
}

/** Opens [contents] as a read-only editor tab named "<fileName> (expected)". Call on the EDT. */
internal fun openExpectedContents(project: Project, fileName: String, contents: String) {
    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
    val file = LightVirtualFile(KatachiBundle.message("expected.fileName", fileName), fileType, contents).apply { isWritable = false }
    FileEditorManager.getInstance(project).openFile(file, true)
}
