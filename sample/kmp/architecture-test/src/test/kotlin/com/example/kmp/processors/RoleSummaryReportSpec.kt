package com.example.kmp.processors

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.process

/**
 * [RoleSummaryReportProcessor] run twice with two different `write` lambdas: once collecting, once
 * comparing. Nothing in [RoleSummaryReportProcessor] branches on which mode is running -- the mode is
 * entirely the lambda's business: for an `ArchitectureProcessorNoArg<Unit>`, writing and checking
 * differ only in the lambda that is injected.
 *
 * This file's own `@OptIn(ExperimentalKatachiApi::class)` is needed for calling
 * [me.tbsten.katachi.processor.process] below, which is itself `@ExperimentalKatachiApi`.
 * [RoleSummaryReportProcessor] opts in separately, because implementing
 * [me.tbsten.katachi.processor.ArchitectureProcessorNoArg] is a second place the experimental
 * marker has to be crossed -- both files doing it independently is the point.
 */
@OptIn(ExperimentalKatachiApi::class)
class RoleSummaryReportSpec : FreeSpec({
    "write mode: passing a collecting lambda gathers one MD per role" {
        val written = mutableMapOf<String, String>()

        twoRoleArchitecture.process(RoleSummaryReportProcessor(write = { path, content -> written[path] = content }))
            .getOrThrow()

        written shouldBe mapOf(
            "First.md" to "# First\n",
            "Second.md" to "# Second\n",
        )
    }

    "check mode: dropping one role lets the comparing lambda detect the vanished path (like --check)" {
        // Get what twoRoleArchitecture should have written first, using the collecting lambda.
        val expected = mutableMapOf<String, String>()
        twoRoleArchitecture.process(RoleSummaryReportProcessor(write = { path, content -> expected[path] = content }))
            .getOrThrow()

        // Run the same processor with the comparing lambda against the definition without Second. If an
        // implementation that does not know about Second yet reaches this lambda, "Second.md" stays in seen
        // and the assertion below fails -- so it cannot pass vacuously.
        val seen = mutableSetOf<String>()
        val mismatched = mutableSetOf<String>()
        oneRoleArchitecture.process(
            RoleSummaryReportProcessor(
                write = { path, content ->
                    seen += path
                    if (expected[path] != content) mismatched += path
                },
            ),
        ).getOrThrow()

        (expected.keys - seen) shouldContainExactly setOf("Second.md")
        mismatched.shouldBeEmpty()
    }
})

/** Two roles at the root, standing in for a report generated before a role was removed. */
private val twoRoleArchitecture: Architecture = architecture {
    "First" { }
    "Second" { }
}

/** [twoRoleArchitecture] with `Second` removed, standing in for the definition after it moved
 * on without regenerating the report. */
private val oneRoleArchitecture: Architecture = architecture {
    "First" { }
}
