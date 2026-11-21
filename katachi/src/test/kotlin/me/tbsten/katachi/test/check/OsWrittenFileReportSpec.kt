package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.report.internal.sarifMessageOf

/** The `[UnexpectedFile]` block of a file the OS writes on its own, pinned line for line the way [ViolationReportSpec] pins the others. */
class OsWrittenFileReportSpec : FreeSpec({
    // The anyFile() location takes a file of any extension, so it is a nearby location even for `.DS_Store`.
    val definition = architectureOf {
        "app".group {
            "Screen".role { layout { "src/main/kotlin/com/example/app" / "*Screen".ktFile() } }
            "Resource".role { layout { "src/main/resources" { anyFile() } } }
        }
    }

    "Finder の .DS_Store は近い置き場所・移動・削除・Role の案を出さず、.gitignore に足す案を出す" {
        definition.validate(repositoryOf { "src/main/kotlin/com/example/app" { "HomeScreen.kt"(); ".DS_Store"() } })
            .report(projectRoot = FsPath.of("/repo")) shouldBe
            """
            Katachi check failed: 1 violation (Unexpected: 1)

            [UnexpectedFile] file:///repo/src/main/kotlin/com/example/app/.DS_Store
              No role is defined for this file.
              Finder on macOS writes it on its own: deleting it does not last, and a role for it fails the check with [MissingFile] on a checkout without it, such as on CI.

              How to fix:
                - Add `.DS_Store` to .gitignore: files = gitTracked() (the default) does not look at a file git ignores
                - If it was committed, also untrack it with `git rm --cached`: .gitignore does not apply to a file git already tracks
                - If files = wholeTree(), which does not read .gitignore, also switch to files = gitTracked()
            """.trimIndent()
    }

    "Windows の Thumbs.db・desktop.ini とルート直下の .DS_Store も同じ案になり、名前の大文字小文字は問わない" {
        val report = layoutArchitecture { "docs" / "*.md".file() }
            .validate(repositoryOf { ".DS_Store"(); "docs" { "index.md"(); "Thumbs.db"(); "Desktop.ini"() } })
            .report()

        report.lines().filter { it.startsWith("[") || it.endsWith("such as on CI.") || it.startsWith("    - Add") } shouldBe listOf(
            "[UnexpectedFile] .DS_Store",
            "  Finder on macOS writes it on its own: deleting it does not last, and a role for it fails the check with [MissingFile] on a checkout without it, such as on CI.",
            "    - Add `.DS_Store` to .gitignore: files = gitTracked() (the default) does not look at a file git ignores",
            "[UnexpectedFile] docs/Desktop.ini",
            "  File Explorer on Windows writes it on its own: deleting it does not last, and a role for it fails the check with [MissingFile] on a checkout without it, such as on CI.",
            "    - Add `Desktop.ini` to .gitignore: files = gitTracked() (the default) does not look at a file git ignores",
            "[UnexpectedFile] docs/Thumbs.db",
            "  File Explorer on Windows writes it on its own: deleting it does not last, and a role for it fails the check with [MissingFile] on a checkout without it, such as on CI.",
            "    - Add `Thumbs.db` to .gitignore: files = gitTracked() (the default) does not look at a file git ignores",
        )
        report shouldNotContain "Add a new role"
        report shouldNotContain "Delete it"
    }

    "OS が作る名前でなければ、同じディレクトリでも今までどおり移動・削除・Role の案が出る" {
        val report = definition
            .validate(repositoryOf { "src/main/kotlin/com/example/app" { "HomeScreen.kt"(); ".DS_Store"(); "Helper.kt"() } })
            .report()

        report shouldStartWith "Katachi check failed: 2 violations (Unexpected: 2)\n"
        report shouldContain "    - Add `.DS_Store` to .gitignore"
        report.lines().dropWhile { it != "[UnexpectedFile] src/main/kotlin/com/example/app/Helper.kt" } shouldBe listOf(
            "[UnexpectedFile] src/main/kotlin/com/example/app/Helper.kt",
            "  No role is defined for this file.",
            "",
            "  Nearby locations:",
            "    app.Screen   src/main/kotlin/com/example/app/ (*Screen.kt)",
            "    app.Resource src/main/resources/",
            "",
            "  How to fix:",
            "    - Move it to one of the locations above, with a name matching the pattern in parentheses",
            "    - Delete it if it is not needed",
            "    - Add a new role for it:",
            "        \"Helper\".role {",
            "          layout {",
            "            \"src/main/kotlin/com/example/app\" / \"Helper\".ktFile()",
            "          }",
            "        }",
        )
    }

    "SARIF の message も同じブロックから作られ、.gitignore に足す案になる" {
        val violation = definition
            .validate(repositoryOf { "src/main/kotlin/com/example/app" { "HomeScreen.kt"(); ".DS_Store"() } })
            .single()

        sarifMessageOf(violation) shouldBe
            """
            No role is defined for this file.
            Finder on macOS writes it on its own: deleting it does not last, and a role for it fails the check with [MissingFile] on a checkout without it, such as on CI.

            How to fix:
              - Add `.DS_Store` to .gitignore: files = gitTracked() (the default) does not look at a file git ignores
              - If it was committed, also untrack it with `git rm --cached`: .gitignore does not apply to a file git already tracks
              - If files = wholeTree(), which does not read .gitignore, also switch to files = gitTracked()
            """.trimIndent()
    }
})
