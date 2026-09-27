package com.example.kmp.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

/**
 * One short `<qualifiedName>.md` per role, handed to [write].
 *
 * This is this sample's only [ArchitectureProcessorNoArg]`<Unit>` -- [PlatformOwnedFilesProcessor]
 * next to it is `ArchitectureProcessorNoArg<List<String>>` and has a value to return. This one
 * exists only for its effect: calling [write] once per role.
 *
 * Where [write] sends that text is deliberately not this class's business -- see
 * [ArchitectureProcessorNoArg]'s own KDoc. That is what [RoleSummaryReportSpec] demonstrates: the
 * very same processor, run once with a lambda that collects into a map and once with a lambda
 * that compares against one, with no mode flag anywhere in this file.
 *
 * ## Example 1: write one `.md` file per role
 * ```kt
 * projectArchitecture.process(
 *     RoleSummaryReport(write = { path, content -> File(outDir, path).writeText(content) }),
 * ).getOrThrow()
 * ```
 *
 * ## Example 2: the same processor, comparing instead of writing
 * ```kt
 * val stale = mutableListOf<String>()
 * projectArchitecture.process(
 *     RoleSummaryReport(
 *         write = { path, content -> if (File(outDir, path).readText() != content) stale += path },
 *     ),
 * ).getOrThrow()
 * stale.shouldBeEmpty()
 * ```
 */
// The `@OptIn` is what a real consumer of the published artifact writes: implementing
// `ArchitectureProcessorNoArg` outside `:katachi` does not compile without it. v0.1 made the
// same point through the `ArchitectureProcessorUnit` typealias, which v0.2 dropped -- two type
// parameters left nothing for an alias named after one of them to say.
@OptIn(ExperimentalKatachiApi::class)
class RoleSummaryReport(
    private val write: (path: String, content: String) -> Unit,
) : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
        for (role in context.roles) {
            write("${role.qualifiedName}.md", "# ${role.qualifiedName}\n")
        }
    }
}
