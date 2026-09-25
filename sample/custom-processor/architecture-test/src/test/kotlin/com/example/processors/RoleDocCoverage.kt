@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg

/**
 * A check of the definition itself: every role that reaches the generated documentation has to
 * carry a `summary` and at least one `example`.
 *
 * The third of the three shapes, and the one that is worth reading twice. It is a check, but it
 * does not return `List<Violation>` -- it returns [Report], a type of its own, because what it
 * found is two numbers and a list of reasons rather than a list of file paths.
 *
 * ## Why `isFailure` has to be written here
 *
 * `runKatachiProcessor` never learns the shape of a result. A `List<Violation>` that is not
 * empty and a `List<String>` that is not empty look the same to it, so it cannot decide on its
 * own whether a run passed. [isFailure] is where that is said, and the default is `false`:
 * "producing a result is the whole job", which is right for a generator and wrong for a check.
 * Leaving it out would make this processor report `[OK]` and exit zero while holding the
 * problems it had just found.
 *
 * ## Which roles it looks at
 *
 * Only the ones a reader will meet. A role that wrote `documented = false`, and a role inside a
 * group that wrote it, are skipped -- `build/Gradle` and the `tool` roles of this sample are
 * exactly that. Metadata is not inherited, so the walk up [me.tbsten.katachi.dsl.Role.groupPath]
 * is this processor's own: katachi keeps what was written, and what combining two values means
 * is a decision only the reader can make.
 *
 * ## Example 1: run it from a test and read both halves of the answer
 * ```kt
 * val report = projectArchitecture.process(RoleDocCoverage)
 * report.missing shouldBe emptyList()
 * RoleDocCoverage.isFailure(report) shouldBe false
 * ```
 */
object RoleDocCoverage : ArchitectureProcessorNoArg<RoleDocCoverage.Report> {
    override fun process(context: ArchitectureProcessContext<Unit>): Report {
        // A group that opted out takes its roles with it, so the silent group names are
        // collected first and every prefix of a role's group path is checked against them.
        val silentGroups = context.groups
            .filterNot { it[Documented] ?: true }
            .map { it.qualifiedName }
            .toSet()

        val documented = context.roles.filter { role ->
            (role[Documented] ?: true) &&
                role.groupPath.indices.none { depth ->
                    role.groupPath.take(depth + 1).joinToString("/") in silentGroups
                }
        }
        context.log(
            "${documented.size} 個の役割を見ます" +
                "（ドキュメントに出ない ${context.roles.size - documented.size} 個は対象外）",
        )

        val missing = documented.flatMap { role ->
            buildList {
                if (role[Summary].isNullOrBlank()) {
                    add(Missing(role = role.qualifiedName, reason = "summary が無い"))
                }
                if (role[Examples].orEmpty().isEmpty()) {
                    add(Missing(role = role.qualifiedName, reason = "example が1つも無い"))
                }
            }
        }

        return Report(checked = documented.size, missing = missing)
    }

    override fun isFailure(result: Report): Boolean = result.missing.isNotEmpty()

    /**
     * What [RoleDocCoverage] found: how many roles it looked at, and everything that was missing.
     *
     * `toString()` is written out because `runKatachiProcessor` prints a result that is not a
     * `Collection` through it, and the generated `toString()` of a data class is the one line a
     * reader least wants at the end of a run.
     */
    data class Report(
        val checked: Int,
        val missing: List<Missing>,
    ) {
        override fun toString(): String = buildString {
            if (missing.isEmpty()) {
                append("$checked 件すべてに summary と example がある")
                return@buildString
            }
            append("$checked 件のうち、以下が足りない")
            missing.forEach { append("\n  - ${it.role}: ${it.reason}") }
        }
    }

    /** One thing a role did not write, and which role did not write it. */
    data class Missing(
        val role: String,
        val reason: String,
    )
}
