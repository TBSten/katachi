package com.example.kmp.roles

import com.example.kmp.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the baseline file that `baseline()` in `ProjectArchitecture.kt`
 * names.
 *
 * The file is a file of the project like any other, so `files = gitTracked()` hands it to the
 * check and it needs a role; without one the test fails with `[UnexpectedFile]` about it.
 */
fun DeclarationContainerScope.baselineFile() = "BaselineFile" {
    title = "Baseline (ledger of held-back violations)"
    summary = "A ledger that records violations already present when katachi was introduced and holds them back without failing the test"
    description = """
        The file that `baseline()` in `ProjectArchitecture.kt` points at. Violations recorded here
        do not fail `:architecture-test:test`; only the count is shown, as "held back N
        violations". A new violation that is not recorded fails the test as before.

        This sample deliberately leaves one entry in as a demo of baseline:
        `user/UserAgent.android.kt` in the `androidMain` of `:data`. Only the `platform` package
        may sit in `androidMain`, so the whole `user` directory becomes an `[UnexpectedDirectory]`.

        Do not write it by hand. It is updated in one of two ways.

        - `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` — rebuild it wholesale from the violations that exist now
        - `./gradlew :architecture-test:test -Dkatachi.baseline.prune=true` — remove only the entries whose violations have been fixed

        Once a violation is fixed, its entry fails the test with `[StaleBaselineEntry]`, as
        "holding back a violation that no longer exists". It keeps failing until prune removes the
        entry, so the number of held-back violations can only go down.
        On CI (environment variable `CI=true`), both update and prune are refused and only the
        comparison is made.
    """.trimIndent()
    forbiddenContents = """
        - Anything you want to allow permanently. That belongs not in the ledger but in the
          definition's `layout { }`, written as a role
        - Entries added by hand. They are overwritten by the next update
    """.trimIndent()
    example("katachi-baseline.json", "The list of held-back violations")
    layout {
        "katachi-baseline.json".file()
    }
}
