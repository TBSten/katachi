package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.check.CaptureOccurrence
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase

/** The `[CaptureMismatch]` block, pinned line for line the way [ViolationReportSpec] pins the others. */
class CaptureMismatchReportSpec : FreeSpec({
    val definition = architectureOf {
        "feature".group {
            "Component".role {
                layout {
                    "feature" / capture("feature") / "src" / capture("feature") / "component" / capture("name").ktFile()
                }
            }
        }
    }

    "ブロックに Pattern と出現ごとの値と期待値が出る" {
        val tree = repositoryOf { "feature/home/src/settings/component" { "HomeUserCard.kt"() } }
        val site = definition.allRoles.single().layouts.single().declaredAt

        definition.validate(tree).report() shouldBe
            """
            Katachi check failed: 1 violation (Unexpected: 1)

            [CaptureMismatch] feature/home/src/settings/component/HomeUserCard.kt
              The file matches a pattern of role feature.Component, but the levels named by capture("feature") do not agree.
              Pattern: feature/${'$'}{feature}/src/${'$'}{feature}/component/${'$'}{name}.kt
              feature: "home" at level 2, "settings" at level 4 (expected "home")
              Declared at: ${site.fileName}:${site.lineNumber + 1}

              How to fix:
                - Move it so that every level named feature reads "home":
                    feature/home/src/home/component/HomeUserCard.kt
                - Delete it if it is not needed
                - If the two levels may differ, give them different names in the layout at ${site.fileName}:${site.lineNumber + 1}
            """.trimIndent()
    }

    "How to fix に元の値で埋め直したパスが file:// で出る" {
        val tree = repositoryOf { "feature/home/src/settings/component" { "HomeUserCard.kt"() } }
        val report = definition.validate(tree).report(projectRoot = FsPath.of("/repo"))

        report.lines().filter { it.contains("file://") } shouldBe listOf(
            "[CaptureMismatch] file:///repo/feature/home/src/settings/component/HomeUserCard.kt",
            "        file:///repo/feature/home/src/home/component/HomeUserCard.kt",
        )
    }

    "派生の行は元の値から導いた期待値を出し、派生だけが落ちた違反の How to fix は変換を外す案になる" {
        val derived = architectureOf {
            "feature".group {
                "Screen".role {
                    layout { "feature" / capture("feature") / "${capture("feature").pascalCase}Screen".ktFile() }
                }
            }
        }
        val tree = repositoryOf { "feature/home" { "ProfileScreen.kt"() } }
        val site = derived.allRoles.single().layouts.single().declaredAt

        derived.validate(tree).report() shouldBe
            """
            Katachi check failed: 1 violation (Unexpected: 1)

            [CaptureMismatch] feature/home/ProfileScreen.kt
              The file matches a pattern of role feature.Screen, but the levels named by capture("feature") do not agree.
              Pattern: feature/${'$'}{feature}/<feature.pascalCase>Screen.kt
              feature: "home" at level 2
              feature.pascalCase: "Profile" at level 3 (expected "Home", from feature = "home")
              Declared at: ${site.fileName}:${site.lineNumber}

              How to fix:
                - Move it so that every level named feature reads "home":
                    feature/home/HomeScreen.kt
                - Delete it if it is not needed
                - If the file name need not follow the directory, drop .pascalCase at ${site.fileName}:${site.lineNumber}
            """.trimIndent()
    }

    "期待値が決まらないときは移動先を書かない" {
        val violation = CaptureMismatch(
            path = "feature/home/src/settings/A.kt",
            role = definition.allRoles.single(),
            declaredAt = DeclarationSite("ComponentRole.kt", 12),
            pattern = "feature/\${feature}/src/\${feature}/*.kt",
            occurrences = listOf(
                CaptureOccurrence(name = "feature", transform = null, level = 1, value = "home", expected = null),
                CaptureOccurrence(name = "feature", transform = null, level = 3, value = "settings", expected = "home"),
            ),
            expectedPath = null,
        )

        listOf(violation).report() shouldBe
            """
            Katachi check failed: 1 violation (Unexpected: 1)

            [CaptureMismatch] feature/home/src/settings/A.kt
              The file matches a pattern of role feature.Component, but the levels named by capture("feature") do not agree.
              Pattern: feature/${'$'}{feature}/src/${'$'}{feature}/*.kt
              feature: "home" at level 2, "settings" at level 4 (expected "home")
              Declared at: ComponentRole.kt:12

              How to fix:
                - Move it so that every level named feature reads "home"
                - Delete it if it is not needed
                - If the two levels may differ, give them different names in the layout at ComponentRole.kt:12
            """.trimIndent()
    }
})
