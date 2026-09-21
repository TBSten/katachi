package com.example.kmp

import me.tbsten.katachi.dsl.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel

/**
 * Files of every role tagged with [owner] under [Owner].
 *
 * Everything this class touches is something `:katachi` already publishes --
 * [ArchitectureProcessor] and [ProjectModel] are the processor API, [Owner] is this sample's
 * own metadata key. Writing it here is the point: it demonstrates that a user of katachi can
 * add a processor of their own alongside the check katachi ships
 * ([me.tbsten.katachi.check.LayoutCheck]), without touching katachi itself.
 *
 * [owner] is a constructor parameter rather than a hardcoded `"platform"` so the same class
 * can answer "which files does platform own" for any owner, [PlatformOwnedFilesSpec]'s
 * anti-vacuous check included: it asks for an owner that tags nothing at all.
 */
@OptIn(ExperimentalKatachiApi::class)
class PlatformOwnedFilesProcessor(private val owner: String) : ArchitectureProcessor<List<String>> {
    override fun process(model: ProjectModel): List<String> =
        model.roles.filter { it[Owner] == owner }.flatMap { model.filesOf(it) }
}

