package com.example.kmp.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

/**
 * Files of every role tagged with [owner] under [Owner], directly or through a group that is.
 *
 * Everything this class touches is something `:katachi` already publishes --
 * [ArchitectureProcessorNoArg] and [ArchitectureProcessContext] are the processor API, [Owner]
 * is this sample's own metadata key. Writing it here is the point: it demonstrates that a user
 * of katachi can add a processor of their own alongside the check katachi ships
 * ([me.tbsten.katachi.check.LayoutCheck]), without touching katachi itself.
 *
 * Looks at [ArchitectureProcessContext.groups] as well as [ArchitectureProcessContext.roles]:
 * `tool/Git` still tags itself directly, the way every role in this sample used to, but the
 * roles `me.tbsten.katachi.dsl.gradle.gradle` declares under `"Gradle"` are katachi's own and
 * cannot be reopened one by one, so `groups/GradleGroup.kt` tags the whole group instead. A
 * role counts as platform-owned when it carries the tag itself, or when its
 * [me.tbsten.katachi.dsl.Role.groupPath] sits inside a group that does.
 *
 * [owner] is a constructor parameter rather than a hardcoded `"platform"` so the same class can
 * answer "which files does platform own" for any owner, [PlatformOwnedFilesSpec]'s anti-vacuous
 * check included: it asks for an owner that tags nothing at all. A processor that takes no
 * `--arg` values is still free to be a class with settings of its own.
 */
@OptIn(ExperimentalKatachiApi::class)
class PlatformOwnedFilesProcessor(private val owner: String) :
    ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> = runCatching {
        val ownedGroupPaths = context.groups.filter { it[Owner] == owner }.map { it.path }
        context.roles
            .filter { role -> role[Owner] == owner || ownedGroupPaths.any { role.groupPath.startsWith(it) } }
            .flatMap { context.filesOf(it) }
    }
}

/** Whether [this] is [prefix] or a path nested under it. */
private fun List<String>.startsWith(prefix: List<String>): Boolean =
    size >= prefix.size && subList(0, prefix.size) == prefix
