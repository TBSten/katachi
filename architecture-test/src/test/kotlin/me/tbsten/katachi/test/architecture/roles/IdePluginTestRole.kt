package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role of the IDE plugin's tests: plain JUnit for pure logic, BasePlatformTestCase with the
 * Analysis API for what needs the platform, and property-based and UI tests on standalone Compose.
 */
fun DeclarationContainerScope.idePluginTest() = "IdePluginTest" {
    title = "IDE プラグインのテスト"
    summary = "IDE を起動しない JUnit 4 のテスト。純ロジックは素の JUnit、プラットフォームが要るものは BasePlatformTestCase + Analysis API"
    example("KatachiToolWindowRegistrationTest.kt", "plugin.xml に Tool Window が登録されていることを確かめる")
    example("AnalysisTestBase.kt", "プラットフォームのテストの土台")
    layout {
        "katachi-intellij-plugin" / "src" / "test" {
            "kotlin" / "me/tbsten/katachi/intellij" / "**" {
                "*Test".ktFile()
                "*TestBase".ktFile()
                // What the tests share within a folder: their world, fixtures, and helpers.
                "*Support".ktFile()
                "*Fixtures".ktFile()
                "DiskGradleRunner".ktFile()
            }
            // Fakes of the ports and builders of test data, shared by the tests.
            "kotlin" / "me/tbsten/katachi/intellij" / "testing" / "*".ktFile()
            // The contract goldens: katachi's JSON and `katachiTemplate` outputs the parsers read.
            "resources" / "contract" / "json" / "*.json".file()
            "resources" / "contract" / "output" / "*.log".file()
            "resources" / "contract" / "output" / "*.exit".file()
        }
        // The Driver smoke: a real IDE started by Starter (JUnit 5), run on demand by `integrationTest`.
        "katachi-intellij-plugin" / "src" / "integrationTest" / "kotlin" / "me/tbsten/katachi/intellij" / "**" / "*Test".ktFile()
        // The property-based tests and the UI tests on standalone Compose, run by `uiTest` (which
        // `test` runs too), with what they build on: generators, operations, invariants, the renderer.
        "katachi-intellij-plugin" / "src" / "uiTest" / "kotlin" / "me/tbsten/katachi/intellij/uitest" / "**" / "*".ktFile()
    }
}
