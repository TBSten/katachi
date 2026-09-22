package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.report
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.scan.MissingFile
import me.tbsten.katachi.scan.UncheckedFile
import me.tbsten.katachi.scan.UnexpectedFile

/** A [Role] good enough to hang a [MissingFile] off of; nothing here reads its layout. */
private val role: Role = architectureOf { "app".group { "Role" {} } }.allRoles.single()

private fun unexpectedFile(path: String): UnexpectedFile = UnexpectedFile(path, nearby = emptyList())

private fun missingFile(path: String): MissingFile =
    MissingFile(path, role = role, declaredAt = DeclarationSite("ReportTruncationSpec.kt", 0))

/**
 * `report()`'s truncation: the budget is split fairly across the kinds that showed up, not
 * spent on the first kind in the list, and the trailing "could not be ..." sentences never
 * depend on what made it into the shown blocks.
 */
class ReportTruncationSpec : FreeSpec({
    "Unexpected 12件 + Missing 1件、maxViolations = 2" - {
        val violations = (1..12).map { unexpectedFile("note-$it.md") } + listOf(missingFile("missing.txt"))

        "Missing のブロックが1件出る" {
            val report = violations.report(maxViolations = 2)
            report shouldContain "[MissingFile] missing.txt"
        }

        "打ち切り行は隠れた種別が1つなので内訳なしの Showing first 2 (11 more)" {
            violations.report(maxViolations = 2).lines().last() shouldBe "Showing first 2 (11 more)"
        }
    }

    "Unexpected 12件 + Missing 3件、maxViolations = 2" - {
        val violations = (1..12).map { unexpectedFile("note-$it.md") } +
            (1..3).map { missingFile("missing-$it.txt") }

        "打ち切り行は隠れた種別が2つなので内訳つきの Showing first 2 (13 more: Unexpected 11, Missing 2)" {
            violations.report(maxViolations = 2).lines().last() shouldBe
                "Showing first 2 (13 more: Unexpected 11, Missing 2)"
        }
    }

    "打ち切りが起きないときブロックの並びは渡された順のまま" {
        val violations = listOf(
            missingFile("m1"),
            unexpectedFile("u1"),
            missingFile("m2"),
            unexpectedFile("u2"),
        )

        violations.report(maxViolations = 10).lines().filter { it.startsWith("[") } shouldBe listOf(
            "[MissingFile] m1",
            "[UnexpectedFile] u1",
            "[MissingFile] m2",
            "[UnexpectedFile] u2",
        )
    }

    "maxViolations = 0 だと1ブロックも出ずサマリ行と打ち切り行だけになる" {
        val violations = (1..3).map { unexpectedFile("u$it") } + (1..2).map { missingFile("m$it") }

        val report = violations.report(maxViolations = 0)

        report.lines().count { it.startsWith("[") } shouldBe 0
        report.lines().last() shouldBe "Showing first 0 (5 more: Unexpected 3, Missing 2)"
    }

    "打ち切られて隠れた種別でも末尾の件数行は消えない" - {
        "N paths could not be checked." {
            val violations = (1..12).map { unexpectedFile("note-$it.md") } +
                listOf(UncheckedFile(path = "src/A.kt", cause = IllegalStateException("boom")))

            // budget = 1 all goes to Unexpected (first in ViolationKind order), so the sole
            // UncheckedFile never gets a block of its own — yet the trailing sentence still
            // has to say a file could not be checked.
            val report = violations.report(maxViolations = 1)

            report.lines().count { it.startsWith("[") } shouldBe 1
            report shouldContain "1 file could not be checked."
        }
    }
})
