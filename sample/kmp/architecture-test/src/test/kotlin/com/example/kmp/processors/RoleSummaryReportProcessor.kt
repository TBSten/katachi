package com.example.kmp.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

// The `@OptIn` is what a real consumer of the published artifact writes: implementing
// `ArchitectureProcessorNoArg` outside `:katachi` does not compile without it.
@OptIn(ExperimentalKatachiApi::class)
class RoleSummaryReportProcessor(
    private val write: (path: String, content: String) -> Unit,
) : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
        for (role in context.roles) {
            write("${role.qualifiedName}.md", "# ${role.qualifiedName}\n")
        }
    }
}
