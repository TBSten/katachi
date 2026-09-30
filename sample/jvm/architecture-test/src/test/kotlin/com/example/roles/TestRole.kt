package com.example.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of code that asserts behaviour, mirroring the main source set. */
fun DeclarationContainerScope.test() = "Test" {
    title = "Test code"
    summary = "Tests placed in src/test/kotlin, keeping the same package structure as the main code"
    description = """
        The code that checks the application's behaviour. It sits in the same package as its
        target, so that what is under `src/test/kotlin` mirrors `src/main/kotlin`.

        `HealthRouteTest` is written with kotest's `FreeSpec`, starts Ktor's `testApplication`
        and sends a real request to `GET /health`. A test name is a single sentence that says
        exactly what is being checked. It is a safeguard against a state where the layout check
        passes but the contents are dead.

        What does not belong here:

        - Tests of the architecture definition itself. Those live in `:architecture-test` and are
          covered by the architecture definition role. This role only looks at the test source
          set of the root project (`:`)

        The file name is `*Test.kt`, so a helper or a fixture with another name is reported
        rather than let in silently. `**` is the package hierarchy, which mirrors the main
        source set, and a directory left under the test source set without a single test is
        reported.
    """.trimIndent()
    example("HealthRouteTest", "The test for GET /health")
    layout {
        // `**` stands for the package levels, which mirror the main source set and are
        // not worth writing twice — so `modulePackage` is deliberately not used here.
        // `*Test` is the name of one test file, so a directory holding none is still reported.
        ":".module {
            testSourceSet / kotlin / "**" / "*Test".ktFile()
        }
    }
}
