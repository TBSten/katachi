package com.example.kmp.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessorUnit
import me.tbsten.katachi.processor.ProjectModel

/**
 * One short `<qualifiedName>.md` per role, handed to [write].
 *
 * This is this sample's only [ArchitectureProcessorUnit] -- [PlatformOwnedFilesProcessor] next
 * to it is `ArchitectureProcessor<List<String>>` and has a value to return, so it cannot use the
 * alias. This one exists only for its effect: calling [write] once per role, exactly the shape
 * `ArchitectureProcessorUnit` is named for.
 *
 * Where [write] sends that text is deliberately not this class's business -- see
 * [ArchitectureProcessorUnit]'s own KDoc. That is what [RoleSummaryReportSpec] demonstrates: the
 * very same processor, run once with a lambda that collects into a map and once with a lambda
 * that compares against one, with no mode flag anywhere in this file.
 *
 * ## Example 1: write one `.md` file per role
 * ```kt
 * projectArchitecture.process(
 *     RoleSummaryReport(write = { path, content -> File(outDir, path).writeText(content) }),
 * )
 * ```
 *
 * ## Example 2: the same processor, comparing instead of writing
 * ```kt
 * val stale = mutableListOf<String>()
 * projectArchitecture.process(
 *     RoleSummaryReport(
 *         write = { path, content -> if (File(outDir, path).readText() != content) stale += path },
 *     ),
 * )
 * stale.shouldBeEmpty()
 * ```
 */
// This `@OptIn` is where the experimental wall around `ArchitectureProcessorUnit` (a typealias,
// not `ArchitectureProcessor` directly) actually bites: `: ArchitectureProcessorUnit` below does
// not compile without it, from `com.example.kmp`, outside `:katachi`. That only works because
// `ExperimentalKatachiApi`'s `@Target` was extended with `AnnotationTarget.TYPEALIAS` -- without
// that, the typealias could not carry the annotation and this line would not need opt-in at all,
// quietly widening the experimental surface. Do not remove this `@OptIn`.
@OptIn(ExperimentalKatachiApi::class)
class RoleSummaryReport(
    private val write: (path: String, content: String) -> Unit,
) : ArchitectureProcessorUnit {
    override fun process(model: ProjectModel) {
        for (role in model.roles) {
            write("${role.qualifiedName}.md", "# ${role.qualifiedName}\n")
        }
    }
}
