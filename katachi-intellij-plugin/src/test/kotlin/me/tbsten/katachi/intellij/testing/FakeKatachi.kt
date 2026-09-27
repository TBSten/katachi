package me.tbsten.katachi.intellij.testing

import me.tbsten.katachi.intellij.data.gradle.GradleRunOutcome
import me.tbsten.katachi.intellij.data.gradle.GradleRunRequest
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ModuleTemplate
import me.tbsten.katachi.intellij.presentation.ExpectedLocation
import me.tbsten.katachi.intellij.presentation.expectedFilesOf
import me.tbsten.katachi.intellij.presentation.resolveExpectedPath
import java.nio.file.Path
import java.util.Collections

/**
 * Plays `katachiTemplate` against a [FakeFileSystem]: the files a run writes are the template's
 * expected files for the `--arg`s it got, and `onExisting` decides what an existing one does, with
 * the same output lines katachi prints. So a second generation of the same name meets the files
 * the first one wrote, as it would on disk.
 *
 * ```kotlin
 * val katachi = FakeKatachi(fs, ROOT) { viewModel.state.value.rows }
 * val runner = FakeGradleTaskRunner(fs) { request, _ -> katachi.answer(request) }
 * ```
 */
internal class FakeKatachi(
    private val fs: FakeFileSystem,
    private val root: Path,
    private val rows: () -> List<ModuleTemplate>,
) {
    /** The `--arg`s of every run, in order. */
    val runs: MutableList<Map<String, String>> = Collections.synchronizedList(mutableListOf())

    /**
     * Replaces the emulation for a run when it returns non-null: a failure, or a gate to cancel on.
     * Gets the run's `--arg`s and its 0-based index among all runs.
     */
    var override: (args: Map<String, String>, index: Int) -> FakeRun? = { _, _ -> null }

    /** The task path of the run being answered, for an [override] that tells modules apart. */
    var currentTaskPath: String = ""
        private set

    fun answer(request: GradleRunRequest): FakeRun {
        val args = request.tasks.single().args.toMap()
        val index = runs.size
        runs += args
        currentTaskPath = request.tasks.single().taskPath
        override(args, index)?.let { return it }
        val roleName = args.getValue("roleName")
        val taskPath = request.tasks.single().taskPath
        val row = rows().firstOrNull { it.template.roleName == roleName && it.module.taskPath(KatachiModule.TEMPLATE_TASK) == taskPath }
            ?: return FakeRun(listOf("> Task ${request.tasks.single().taskPath} FAILED", "No template $roleName"), GradleRunOutcome.Failed)
        val detail = row.template.detail ?: return FakeRun(emptyList(), GradleRunOutcome.Failed)
        val paths = expectedFilesOf(detail, args - "roleName" - "onExisting")
            .mapNotNull { (it.location as? ExpectedLocation.Known)?.path }
            .mapNotNull { resolveExpectedPath(root, it) }
        val existing = paths.filter(fs::exists)
        return when {
            existing.isEmpty() -> written(paths, emptyList(), roleName)
            args["onExisting"] == "skip" -> FakeRun(
                head(paths) + log("Wrote nothing: ${existing.size} of ${paths.size} files are already there (${existing.joinToString(", ") { uri(it) }}).") + ok(),
            )
            args["onExisting"] == "overwrite" -> written(paths, existing, roleName)
            else -> FakeRun(head(paths) + conflict(existing, paths.size), GradleRunOutcome.Failed)
        }
    }

    private fun written(paths: List<Path>, overwritten: List<Path>, roleName: String): FakeRun {
        val overwriting = if (overwritten.isEmpty()) {
            emptyList()
        } else {
            log("Overwriting ${overwritten.size} of ${paths.size} files: ${overwritten.joinToString(", ") { uri(it) }}")
        }
        val lines = head(paths) + overwriting + paths.flatMap { log("Wrote ${uri(it)}") } + ok()
        return FakeRun(lines, writes = paths.associateWith { "// $roleName" })
    }

    private fun head(paths: List<Path>) =
        listOf("> Task :arch-a:katachiTemplate") + log("Generating ${paths.size} files under ${uri(root).removeSuffix("/")}")

    private fun conflict(existing: List<Path>, total: Int) = listOf(
        "",
        "[3/3] Katachi processor run: 0 succeeded, 1 failed",
        "",
        "[FAILED] template",
        "  ${existing.size} of $total generated files already exist under ${uri(root).removeSuffix("/")}:",
    ) + existing.map { "    ${uri(it)}" } + listOf("  Nothing was written.", "", "BUILD FAILED in 2s")

    private fun ok() = listOf("", "[3/3] Katachi processor run: 1 succeeded, 0 failed", "", "[OK] template", "", "BUILD SUCCESSFUL in 3s")

    private fun log(message: String) = listOf("  [template] $message")

    private fun uri(path: Path) = path.toUri().toString()
}
