package com.example.kmp.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

/**
 * Files of every role tagged with [owner] under [Owner].
 *
 * Everything this class touches is something `:katachi` already publishes --
 * [ArchitectureProcessorNoArg] and [ArchitectureProcessContext] are the processor API, [Owner]
 * is this sample's own metadata key. Writing it here is the point: it demonstrates that a user
 * of katachi can add a processor of their own alongside the check katachi ships
 * ([me.tbsten.katachi.check.LayoutCheck]), without touching katachi itself.
 *
 * [owner] is a constructor parameter rather than a hardcoded `"platform"` so the same class can
 * answer "which files does platform own" for any owner, [PlatformOwnedFilesSpec]'s anti-vacuous
 * check included: it asks for an owner that tags nothing at all. A processor that takes no
 * `--arg` values is still free to be a class with settings of its own.
 */
@OptIn(ExperimentalKatachiApi::class)
class PlatformOwnedFilesProcessor(private val owner: String) :
    ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
        runCatching { context.roles.filter { it[Owner] == owner }.flatMap { context.filesOf(it) } }
}
