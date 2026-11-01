@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

object RoleTable : ArchitectureProcessor<RoleTable.Args, List<String>> {
    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override fun process(context: ArchitectureProcessContext<Args>): Result<List<String>> = runCatching {
        val args = context.args
        context.log(
            "title=${args.title} groups=${args.groups} " +
                "minExamples=${args.minExamples} sortBy=${args.sortBy}",
        )

        val selected = context.roles
            .filter { args.groups.isEmpty() || it.groupPath.firstOrNull() in args.groups }
            .filter { it[Examples].orEmpty().size >= args.minExamples }
        val ordered = when (args.sortBy) {
            SortBy.Declaration -> selected
            SortBy.Name -> selected.sortedBy { it.qualifiedName }
        }

        listOf("| ${args.title} | Summary |", "|---|---|") +
            ordered.map { role -> "| ${role.qualifiedName} | ${role[Summary].orEmpty()} |" }
    }

    @Serializable
    data class Args(
        val title: String = "Role",
        val groups: List<String> = emptyList(),
        val minExamples: Int = 0,
        val sortBy: SortBy = SortBy.Declaration,
    )

    @Serializable
    enum class SortBy {
        Declaration,

        Name,
    }
}
