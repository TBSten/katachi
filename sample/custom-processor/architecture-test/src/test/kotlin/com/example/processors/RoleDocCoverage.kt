@file:OptIn(ExperimentalKatachiApi::class)

package com.example.processors

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.util.runCatchingScoped

/**
 * A check of the definition itself: every role that reaches the generated documentation has to
 * carry a `summary` and at least one `example`.
 *
 * The third of the three shapes, and the one that is worth reading twice. It is a check, but it
 * does not answer with `List<Violation>` -- its answer is a [Report], a type of its own, because
 * what it found is a count rather than a list of file paths.
 *
 * ## Why it uses `runCatchingScoped` rather than answering a [Report] that says "missing"
 *
 * A `katachi<Key>` task never learns the shape of a result. What it reads is the [Result]:
 * `success` is `[OK]`, `failure` is `[FAILED]` and a non-zero exit. So a gap has to be a
 * `failure`, and a gap should not stop the walk at the first role that has one.
 * `runCatchingScoped` does both: `failure(...)` records a gap and carries on, and when the block
 * ends the run is a `failure` holding every gap as a suppressed exception of a
 * `KatachiMultipleFailuresException`. `throw(...)` is for the other kind of problem, the one that
 * makes the rest meaningless -- a definition with no documented role at all.
 *
 * ## Which roles it looks at
 *
 * Only the ones a reader will meet. A role that wrote `documented = false`, and a role inside a
 * group that wrote it, are skipped -- the `Gradle` roles `gradle()` declares and the `tool`
 * roles of this sample are exactly that. Metadata is not inherited, so the walk up [me.tbsten.katachi.dsl.Role.groupPath]
 * is this processor's own: katachi keeps what was written, and what combining two values means
 * is a decision only the reader can make.
 *
 * ## Example 1: read every gap at once, not only the first
 * ```kt
 * val failure = projectArchitecture.process(RoleDocCoverage).exceptionOrNull()
 * failure?.suppressed?.map { it.message } shouldBe emptyList()
 * ```
 */
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

    /**
     * What [RoleDocCoverage] found when nothing was missing: how many roles it looked at.
     *
     * `toString()` is written out because `katachiRoleDocCoverage` prints it through the result on
     * `[OK]`, and the generated `toString()` of a data class is the one line a reader least wants
     * at the end of a run.
     */
    data class Report(val checked: Int) {
        override fun toString(): String = "All $checked roles have a summary and an example"
    }
}
