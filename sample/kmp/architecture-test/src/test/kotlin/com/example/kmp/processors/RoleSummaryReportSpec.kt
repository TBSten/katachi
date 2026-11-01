package com.example.kmp.processors

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.process

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

private val twoRoleArchitecture: Architecture = architecture {
    "First".role { }
    "Second".role { }
}

private val oneRoleArchitecture: Architecture = architecture {
    "First".role { }
}
