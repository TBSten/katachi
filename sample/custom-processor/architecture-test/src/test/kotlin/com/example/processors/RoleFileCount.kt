@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

object RoleFileCount : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> = runCatching {
        context.log("Counting the files covered by ${context.roles.size} roles")
        context.roles.map { role ->
            "${role.qualifiedName}: ${context.filesOf(role).size} file(s)"
        }
    }
}
