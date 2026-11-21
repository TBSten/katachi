package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.Severity
import me.tbsten.katachi.check.internal.IgnoredLayout
import me.tbsten.katachi.check.internal.assert
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * [IgnoredLayout] through the real check: a line of a role's layout that an `ignore()` hides
 * entirely, whoever wrote the `ignore()`, against a fake file system.
 */
class IgnoredLayoutSpec : FreeSpec({
    "報告が勧めた ignore() の下に後から書いた Role の行" - {
        val definition = architectureOf {
            "Legacy".role { layout { "src/main/kotlin/com/example/legacy".ignore() } }
            "LegacyService".role { layout { "src/main/kotlin/com/example/legacy" / "*Service".ktFile() } }
        }
        val tree = repositoryOf { "src/main/kotlin/com/example/legacy" { "OldThing.kt"(); "UserService.kt"() } }

        "警告が 1 件出て、隠している ignore() を指す" {
            val warning = definition.validate(tree).filterIsInstance<IgnoredLayout>().single()

            warning.severity shouldBe Severity.Warning
            warning.path shouldBe "src/main/kotlin/com/example/legacy/*Service.kt"
            warning.role.qualifiedName shouldBe "LegacyService"
            warning.hiddenBy.map { "${it.role.qualifiedName} ${it.path} ${it.gradleModule}" } shouldBe
                listOf("Legacy src/main/kotlin/com/example/legacy null")
        }

        "報告は Role の行と ignore() の宣言位置を出し、直し方を 2 つ勧める" {
            val warning = definition.validate(tree).filterIsInstance<IgnoredLayout>().single()
            val ignoredAt = warning.hiddenBy.single().declaredAt

            warning.declaredAt.fileName shouldBe "IgnoredLayoutSpec.kt"
            ignoredAt.fileName shouldBe "IgnoredLayoutSpec.kt"
            listOf(warning).report() shouldBe
                """
                Katachi check found 1 warning. Warnings never fail the check.

                [IgnoredLayout] src/main/kotlin/com/example/legacy/*Service.kt
                  Role LegacyService declares this path, but every file it allows is in a directory that is ignored, so none of them is checked.
                  Declared at: ${warning.declaredAt}

                  Hidden by:
                    src/main/kotlin/com/example/legacy (ignore() of Legacy at $ignoredAt)

                  How to fix:
                    - If these files are to be checked, remove the ignore(), or narrow it so that it leaves this path out
                    - Otherwise remove this path from LegacyService: no file it allows is ever checked
                """.trimIndent()
        }

        "assert() は落ちず、警告を出す" {
            val printed = capturingStandardError { shouldNotThrowAny { definition.assert(tree) } }

            printed shouldContain "[IgnoredLayout] src/main/kotlin/com/example/legacy/*Service.kt"
        }
    }

    "\"**/build\".ignore() がソースのパッケージ build を隠すと警告になる" {
        val warnings = architectureOf {
            "BuildOutput".role { layout { "**/build".ignore() } }
            "BuildInfo".role { layout { "src/main/kotlin/com/example/build" / "*".ktFile() } }
        }.validate(repositoryOf { "src/main/kotlin/com/example/build" { "BuildInfo.kt"() } })
            .filterIsInstance<IgnoredLayout>()

        warnings.map { "${it.path} <- ${it.hiddenBy.single().path}" } shouldBe
            listOf("src/main/kotlin/com/example/build/*.kt <- **/build")
    }

    "gradle() がモジュールごとに外す build の下の Role の行は、gradle() が隠していると言う" {
        val definition = architectureOf {
            gradle(requireWrapper = false)
            "GeneratedEntryPoint".role { layout { "architecture-test/build/generated" / "**" / "*".ktFile() } }
        }
        val tree = repositoryOf {
            "settings.gradle.kts"()
            "build.gradle.kts"()
            "architecture-test" {
                "build.gradle.kts"()
                "build/generated/ksp/test/kotlin" { "GeneratedKatachiEntryPoint.kt"() }
            }
        }

        val violations = definition.validate(tree)
        val warning = violations.filterIsInstance<IgnoredLayout>().single()
        val gradleAt = warning.hiddenBy.single().declaredAt

        violations.filter { it.severity == Severity.Error }.shouldBeEmpty()
        warning.path shouldBe "architecture-test/build/generated/**/*.kt"
        warning.hiddenBy.map { "${it.path} ${it.gradleModule}" } shouldBe listOf("architecture-test/build :architecture-test")
        gradleAt.fileName shouldBe "IgnoredLayoutSpec.kt"
        listOf(warning).report().lines().drop(7) shouldBe listOf(
            "    architecture-test/build (the build directory of module :architecture-test, which gradle() at $gradleAt ignores)",
            "",
            "  How to fix:",
            "    - If these files are to be checked, declare the Gradle files without gradle(), which always ignores the build directory of every module",
            "    - Otherwise remove this path from GeneratedEntryPoint: no file it allows is ever checked",
        )
    }

    "gradle() が宣言した行を利用者の ignore() が隠すと、ignore() を外すよう勧める" {
        val warnings = architectureOf {
            gradle(requireWrapper = false)
            "GradleDirectory".role { layout { "gradle".ignore() } }
        }.validate(repositoryOf { "gradle" { "libs.versions.toml"() } }).filterIsInstance<IgnoredLayout>()

        warnings.map { "${it.role.qualifiedName} ${it.path}" } shouldBe listOf(
            "Gradle.VersionCatalog gradle/*.versions.toml",
            "Gradle.DaemonJvmProperties gradle/gradle-daemon-jvm.properties",
            "Gradle.GradleWrapper.WrapperJar gradle/wrapper/gradle-wrapper.jar",
            "Gradle.GradleWrapper.WrapperProperties gradle/wrapper/gradle-wrapper.properties",
        )
        listOf(warnings.first()).report().lines().takeLast(2) shouldBe listOf(
            "  How to fix:",
            "    - Remove the ignore(), or narrow it so that it leaves this path out: gradle() declares this path itself",
        )
    }

    "同じ Role の中で anyFile() の場所を ignore() しても警告になる" {
        val warnings = layoutArchitecture(group = "Tool", role = "Scripts") {
            "scripts".ignore()
            "scripts" { anyFile() }
        }.validate(repositoryOf { "scripts" { "run.sh"() } }).filterIsInstance<IgnoredLayout>()

        warnings.map { "${it.role.qualifiedName} ${it.path} <- ${it.hiddenBy.single().role.qualifiedName}" } shouldBe
            listOf("Tool.Scripts scripts <- Tool.Scripts")
    }

    "警告を出さない重なり" - {
        "一部の場所だけを ignore() で外した Role の行" {
            architectureOf {
                "Source".role { layout { "src" / "**" / "*".ktFile() } }
                "Legacy".role { layout { "src/legacy".ignore() } }
            }.validate(repositoryOf { "src" { "Main.kt"(); "legacy" { "Old.kt"() } } })
                .filterIsInstance<IgnoredLayout>().shouldBeEmpty()
        }

        "ワイルドカードのディレクトリが ignore() の外にも届く行" {
            architectureOf {
                "Screen".role { layout { "feature" / "*" / "*Screen".ktFile() } }
                "Legacy".role { layout { "feature/legacy".ignore() } }
            }.validate(repositoryOf { "feature" { "home" { "HomeScreen.kt"() } } })
                .filterIsInstance<IgnoredLayout>().shouldBeEmpty()
        }

        "名前の前方が同じだけの別のディレクトリ（model と model2）" {
            architectureOf {
                "Model".role { layout { "model".ignore() } }
                "Model2".role { layout { "model2" / "*".ktFile() } }
            }.validate(repositoryOf { "model2" { "A.kt"() } })
                .filterIsInstance<IgnoredLayout>().shouldBeEmpty()
        }

        "ignore() したディレクトリそのものと同じ名前のファイル" {
            architectureOf {
                "Output".role { layout { "out".ignore() } }
                "OutFile".role { layout { "out".file() } }
            }.validate(repositoryOf { })
                .filterIsInstance<IgnoredLayout>().shouldBeEmpty()
        }
    }
    "\"**/build\".ignore() が src/**/*.kt の下の build を隠しうるときは、一部だけ隠すと警告する" {
        val warnings = architectureOf {
            "BuildOutput".role { layout { "**/build".ignore() } }
            "Source".role { layout { "src" / "**" / "*".ktFile() } }
        }.validate(repositoryOf { "src/main" { "App.kt"() } }).filterIsInstance<IgnoredLayout>()

        warnings.map { "${it.path} <- ${it.hiddenBy.single().path} ${it.partly}" } shouldBe
            listOf("src/**/*.kt <- **/build true")
    }

    "固定の部分が無い行と、名前で書いた ignore() は、一部だけ隠していても警告しない" {
        architectureOf {
            "BuildOutput".role { layout { "**/build".ignore() } }
            "Any".role { layout { "**" / "*".ktFile() } }
        }.validate(repositoryOf { "App.kt"() }).filterIsInstance<IgnoredLayout>().shouldBeEmpty()

        architectureOf {
            "Legacy".role { layout { "src/legacy".ignore() } }
            "Source".role { layout { "src" / "**" / "*".ktFile() } }
        }.validate(repositoryOf { "src/main" { "App.kt"() } }).filterIsInstance<IgnoredLayout>().shouldBeEmpty()
    }

})
