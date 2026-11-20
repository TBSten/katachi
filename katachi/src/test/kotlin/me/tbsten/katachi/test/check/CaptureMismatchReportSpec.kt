package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.CaptureMismatch
import me.tbsten.katachi.check.CaptureOccurrence
import me.tbsten.katachi.check.Violation
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.flatCase
import me.tbsten.katachi.dsl.gradle.mainSourceSet
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

    "食い違う値がほかの水準の値の変換と一致するときは、その変換を書く案を最初に出す" - {
        "ハイフンのあるモジュールの package が flatCase なら flatCase を勧め、ハイフンのある package へ移す案は出さない" {
            val tree = repositoryOf { "feature/user-profile/api/src/main/kotlin/com/example/feature/userprofile/api" { "UserProfileApi.kt"() } }
            val violations = featureApi { capture("feature") }.validate(tree)
            val site = violations.mismatchSite()

            violations.report() shouldBe
                """
                Katachi check failed: 1 violation (Unexpected: 1)

                [CaptureMismatch] feature/user-profile/api/src/main/kotlin/com/example/feature/userprofile/api/UserProfileApi.kt
                  The file matches a pattern of role Feature.FeatureApi, but the levels named by capture("feature") do not agree.
                  Pattern: feature/${'$'}{feature}/api/src/main/kotlin/com/example/feature/${'$'}{feature}/api/<feature.pascalCase>Api.kt
                  feature: "user-profile" at level 2, "userprofile" at level 10 (expected "user-profile")
                  feature.pascalCase: "UserProfile" at level 12
                  Declared at: $site

                  How to fix:
                    - In the layout at $site, write capture("feature").flatCase at level 10
                    - Delete it if it is not needed
                    - If the two levels may differ, give them different names in the layout at $site
                """.trimIndent()
            featureApi { capture("feature").flatCase }.validate(tree) shouldBe emptyList()
        }

        "package が camelCase なら camelCase を勧める" {
            val tree = repositoryOf { "feature/user-profile/api/src/main/kotlin/com/example/feature/userProfile/api" { "UserProfileApi.kt"() } }
            val violations = featureApi { capture("feature") }.validate(tree)
            val site = violations.mismatchSite()

            violations.howToFix() shouldBe listOf(
                "- In the layout at $site, write capture(\"feature\").camelCase at level 10",
                "- Delete it if it is not needed",
                "- If the two levels may differ, give them different names in the layout at $site",
            )
            featureApi { capture("feature").camelCase }.validate(tree) shouldBe emptyList()
        }

        "いくつもの変換が同じ値になる 1 語の名前は flatCase を勧め、package になる名前へは移す案も出す" {
            val tree = repositoryOf { "feature/Home/api/src/main/kotlin/com/example/feature/home/api" { "HomeApi.kt"() } }
            val violations = featureApi { capture("feature") }.validate(tree)
            val site = violations.mismatchSite()

            violations.howToFix() shouldBe listOf(
                "- In the layout at $site, write capture(\"feature\").flatCase at level 10",
                "- Move it so that every level named feature reads \"Home\":",
                "feature/Home/api/src/main/kotlin/com/example/feature/Home/api/HomeApi.kt",
                "- Delete it if it is not needed",
                "- If the two levels may differ, give them different names in the layout at $site",
            )
        }

        "package でないディレクトリなら、変換の案に続けてハイフンのある名前へ移す案も出す" {
            val messages = architectureOf {
                "Feature".group {
                    "Messages".role {
                        layout { "feature" / capture("feature") / mainSourceSet / "resources" / capture("feature") / "*.properties".file() }
                    }
                }
            }
            val tree = repositoryOf { "feature/user-profile/src/main/resources/userprofile" { "messages.properties"() } }
            val violations = messages.validate(tree)
            val site = violations.mismatchSite()

            violations.howToFix() shouldBe listOf(
                "- In the layout at $site, write capture(\"feature\").flatCase at level 6",
                "- Move it so that every level named feature reads \"user-profile\":",
                "feature/user-profile/src/main/resources/user-profile/messages.properties",
                "- Delete it if it is not needed",
                "- If the two levels may differ, give them different names in the layout at $site",
            )
        }
    }

    "package のディレクトリへ移す案" - {
        "変換で説明できない食い違いは、今までどおり移す案になる" {
            val tree = repositoryOf { "feature/home/api/src/main/kotlin/com/example/feature/settings/api" { "HomeApi.kt"() } }
            val violations = featureApi { capture("feature") }.validate(tree)
            val site = violations.mismatchSite()

            violations.howToFix() shouldBe listOf(
                "- Move it so that every level named feature reads \"home\":",
                "feature/home/api/src/main/kotlin/com/example/feature/home/api/HomeApi.kt",
                "- Delete it if it is not needed",
                "- If the two levels may differ, give them different names in the layout at $site",
            )
        }

        "変換で説明できなくても、Kotlin の package にならない名前へは移す案を出さない" {
            val tree = repositoryOf { "feature/user-profile/api/src/main/kotlin/com/example/feature/profile/api" { "UserProfileApi.kt"() } }
            val violations = featureApi { capture("feature") }.validate(tree)
            val site = violations.mismatchSite()

            violations.howToFix() shouldBe listOf(
                "- Delete it if it is not needed",
                "- If the two levels may differ, give them different names in the layout at $site",
            )
        }

        "src/main/java の下も package のディレクトリとして見る" {
            val tree = repositoryOf { "feature/user-profile/api/src/main/java/com/example/feature/profile/api" { "UserProfileApi.kt"() } }
            val violations = featureApi(sourceRoot = "java") { capture("feature") }.validate(tree)
            val site = violations.mismatchSite()

            violations.howToFix() shouldBe listOf(
                "- Delete it if it is not needed",
                "- If the two levels may differ, give them different names in the layout at $site",
            )
        }
    }
})

/** A feature module's API, with the package level below `com/example/feature` written by [packageLevel]. */
private fun featureApi(sourceRoot: String = "kotlin", packageLevel: LayoutScope.() -> String): Architecture = architectureOf {
    "Feature".group {
        "FeatureApi".role {
            layout {
                "feature" / capture("feature") / "api" {
                    mainSourceSet / sourceRoot / "com/example/feature" / packageLevel() / "api" /
                        "${capture("feature").pascalCase}Api".ktFile()
                }
            }
        }
    }
}

private fun List<Violation>.mismatchSite(): DeclarationSite = filterIsInstance<CaptureMismatch>().single().declaredAt

/** The lines under `How to fix:` of the report, without their indent. */
private fun List<Violation>.howToFix(): List<String> =
    report().lines().dropWhile { it.trim() != "How to fix:" }.drop(1).map { it.trim() }
