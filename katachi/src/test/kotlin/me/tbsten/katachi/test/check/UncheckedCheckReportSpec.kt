package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.report
import me.tbsten.katachi.scan.UncheckedCheck
import me.tbsten.katachi.scan.UncheckedFile

/**
 * `UncheckedCheck` — a check handed to `assert(...)` / `validate(...)` that threw — and how it
 * renders alongside the trailing "could not be ..." sentences.
 */
class UncheckedCheckReportSpec : FreeSpec({
    "ブロック" - {
        "[UncheckedCheck] . + Cause: + How to fix: として出て末尾に件数の行が出る" {
            val violation = UncheckedCheck(check = "com.example.TodoCheck", cause = IllegalStateException("boom"))

            listOf(violation).report() shouldBe
                """
                Katachi check failed: 1 violation (Failed: 1)

                [UncheckedCheck] .
                  Katachi failed while running com.example.TodoCheck, so nothing it would have reported is known.
                  Cause: java.lang.IllegalStateException: boom

                  How to fix:
                    - Read the cause above and fix the check, or stop passing it to assert()
                    - Report it at https://github.com/TBSten/katachi/issues if the check is one of katachi's

                1 check could not be run.
                """.trimIndent()
        }
    }

    "末尾の件数行" - {
        "UncheckedFile 2件と UncheckedCheck 1件が混ざると末尾に2文並ぶ" {
            val violations = listOf(
                UncheckedFile(path = "src/A.kt", cause = IllegalStateException("a")),
                UncheckedFile(path = "src/B.kt", cause = IllegalStateException("b")),
                UncheckedCheck(check = "com.example.TodoCheck", cause = IllegalStateException("boom")),
            )

            val trailing = violations.report().lines().filter { it.isNotBlank() }.takeLast(2)
            trailing shouldBe listOf(
                "2 files could not be checked.",
                "1 check could not be run.",
            )
        }
    }
})
