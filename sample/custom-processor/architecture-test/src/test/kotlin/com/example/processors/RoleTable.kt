@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor

/**
 * A Markdown table of the roles, narrowed and ordered by its arguments.
 *
 * The second of the three shapes: a processor with arguments. It implements
 * [ArchitectureProcessor] with an `Args` type of its own and hands over the generated
 * serializer, which is the whole of what `--arg` needs to become typed values. That
 * `@Serializable` is also the only reason this module applies the serialization compiler
 * plugin -- a module registering only argument-free processors applies none.
 *
 * ## How a `--arg` string becomes a value
 *
 * [Args] deliberately holds one field of each interesting kind, because the rule that catches
 * people out is visible only when they sit next to each other:
 *
 * - `--arg title=a,b` reaches [Args.title] as the single string `"a,b"`. **A comma splits only
 *   when the receiving field is a `List` or a `Set`**, and there is no escape syntax, so a
 *   `String` field is also the way out when a value has to contain a comma.
 *   Read [me.tbsten.katachi.processor.decodeFromStringMap] for the rule itself.
 * - `--arg groups=core,testing` reaches [Args.groups] as two elements.
 * - `--arg minExamples=2` reaches [Args.minExamples] as an `Int`; `minExamples=x` fails the run
 *   rather than quietly becoming zero.
 * - `--arg sortBy=Name` reaches [Args.sortBy] as [SortBy.Name]. The value is the enum entry's
 *   own name, spelled exactly.
 *
 * `architecture-test/build.gradle.kts` registers this one with `arg("sortBy", "Name")`, which
 * is this module's default rather than a lock: a `--arg sortBy=...` on the command line wins.
 *
 * ## Example 1: run it from a test, with arguments built in code
 * ```kt
 * val table = projectArchitecture.process(RoleTable, RoleTable.Args(groups = listOf("core")))
 *     .getOrThrow()
 * table.first() shouldBe "| 役割 | 概要 |"
 * ```
 *
 * @see RoleFileCount for the argument-free shape.
 */
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

        listOf("| ${args.title} | 概要 |", "|---|---|") +
            ordered.map { role -> "| ${role.qualifiedName} | ${role[Summary].orEmpty()} |" }
    }

    /** What one run of [RoleTable] was asked for. Every field has a default, so every one is optional. */
    @Serializable
    data class Args(
        /** The first column's heading. A `String`, so a comma in it stays one value. */
        val title: String = "役割",
        /** Top level groups to include, or every group when empty. A `List`, so a comma splits. */
        val groups: List<String> = emptyList(),
        /** Drop roles with fewer `example()` entries than this. */
        val minExamples: Int = 0,
        /** The order of the rows. */
        val sortBy: SortBy = SortBy.Declaration,
    )

    /** The orders [RoleTable] can put its rows in. Written on the command line as the entry's own name. */
    @Serializable
    enum class SortBy {
        /** The order the definition declares the roles in. */
        Declaration,

        /** Alphabetical by qualified name. */
        Name,
    }
}
