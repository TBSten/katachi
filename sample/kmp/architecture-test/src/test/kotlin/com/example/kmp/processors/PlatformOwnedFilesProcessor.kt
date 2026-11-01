package com.example.kmp.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

@OptIn(ExperimentalKatachiApi::class)
class PlatformOwnedFilesProcessor(private val owner: String) :
    ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> = runCatching {
        val ownedGroupPaths = context.groups.filter { it[Owner] == owner }.map { it.path }
        context.roles
            .filter { role -> role[Owner] == owner || ownedGroupPaths.any { role.groupPath.startsWith(it) } }
            .flatMap { context.filesOf(it) }
    }
}

private fun List<String>.startsWith(prefix: List<String>): Boolean =
    size >= prefix.size && subList(0, prefix.size) == prefix
