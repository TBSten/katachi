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
    title = "Baseline (ledger of shelved violations)"
    summary = "A ledger that records violations already present when katachi was introduced, shelving them without failing the tests"
    description = """
        The file that `baseline()` in `ProjectArchitecture.kt` points to. Violations recorded here
        do not fail `:architecture-test:test`; only a count such as "held back N violations" is
        printed. A new violation that is not in the record fails the test as before.

        As a baseline example, this sample deliberately leaves one entry: `user/UserAgent.android.kt`
        in the `androidMain` of `:data`. Only the `platform` package may sit in `androidMain`, so
        the whole `user` directory becomes an `[UnexpectedDirectory]`.

        Do not write it by hand. Update it in one of two ways.

        - `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` -- rebuild it from scratch with the current violations
        - `./gradlew :architecture-test:test -Dkatachi.baseline.prune=true` -- remove only the entries for violations that were fixed

        Once a violation is fixed, its entry fails the test as `[StaleBaselineEntry]`
        ("shelving a violation that no longer exists"). It keeps failing until prune removes the
        entry, so the number of shelved violations only goes down. On CI (`CI=true`) both update
        and prune are refused and only the comparison runs.
    """.trimIndent()
    forbiddenContents = """
        - Things you want to allow permanently. Those are written as roles in the definition's `layout { }`, not in the ledger
        - Entries added by hand. The next update overwrites them
    """.trimIndent()
    example("katachi-baseline.json", "The list of shelved violations")
    layout {
        "katachi-baseline.json".file()
    }
}
