@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.util.runCatchingScoped

object RoleDocCoverage : ArchitectureProcessorNoArg<RoleDocCoverage.Report> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Report> = runCatchingScoped {
        // A group that opted out takes its roles with it, so the silent group names are
        // collected first and every prefix of a role's group path is checked against them.
        val silentGroups = context.groups
            .filterNot { it[Documented] ?: true }
            .map { it.qualifiedName }
            .toSet()

        val documented = context.roles.filter { role ->
            (role[Documented] ?: true) &&
                role.groupPath.indices.none { depth ->
                    role.groupPath.take(depth + 1).joinToString(".") in silentGroups
                }
        }
        context.log(
            "Checking ${documented.size} roles " +
                "(${context.roles.size - documented.size} roles that do not reach the documentation are skipped)",
        )

        if (documented.isEmpty()) `throw`(IllegalStateException("no documented roles"))

        for (role in documented) {
            if (role[Summary].isNullOrBlank()) {
                failure(IllegalStateException("${role.qualifiedName}: no summary"))
            }
            if (role[Examples].orEmpty().isEmpty()) {
                failure(IllegalStateException("${role.qualifiedName}: no example"))
            }
        }

        Report(checked = documented.size)
    }

    data class Report(val checked: Int) {
        override fun toString(): String = "All $checked roles have a summary and an example"
    }
}
