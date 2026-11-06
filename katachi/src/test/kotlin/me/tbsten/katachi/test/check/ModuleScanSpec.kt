package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.test.dsl.files.FakeFileSystemScope

/**
 * The modules seen from the check, not from the flattening.
 *
 * `gradle()` declares the build script of every module katachi finds, through the one module key
 * left (`":**"`). Every other spec about it evaluates the layout directly and hands it a module
 * index it built itself. That leaves one thing unsaid: `validate()` has to build that index from
 * the tree it was given, or the key quietly expands to nothing and every module's build script
 * comes back as unexpected. These run the real entry point instead.
 */
class ModuleScanSpec : FreeSpec({
    /** Two feature modules and one that is only a directory, under `/repo`. */
    fun repository(block: FakeFileSystemScope.() -> Unit = {}) = repositoryOf {
        "build.gradle.kts"()
        "settings.gradle.kts"()
        "feature" {
            "home" { "build.gradle.kts"() }
            "settings" { "build.gradle.kts"() }
        }
        "core" { "data" { "build.gradle.kts"() } }
        block()
    }

    "gradle() の build script は実在するモジュールに展開されて検査される" - {
        // The regression guard: with an empty module index `":**"` expands to nothing and every
        // module's `build.gradle.kts` comes back as an [UnexpectedFile].
        "モジュールごとの build.gradle.kts が許可される" {
            architectureOf {
                gradle(requireWrapper = false)
                "app".group {
                    "Screen".role {
                        layout {
                            "feature" / capture("feature") / mainSourceSet / kotlin /
                                "${capture("feature").pascalCase}Screen".ktFile()
                        }
                    }
                    "Repository".role { layout { "core/data" / mainSourceSet / kotlin / "Repository".ktFile() } }
                }
            }
                .validate(
                    repository {
                        "feature" {
                            "home" { "src/main/kotlin" { "HomeScreen.kt"() } }
                            "settings" { "src/main/kotlin" { "SettingsScreen.kt"() } }
                        }
                        "core" { "data" { "src/main/kotlin" { "Repository.kt"() } } }
                    },
                )
                .labels() shouldBe emptyList()
        }

        "捕捉値と噛み合わない名前のファイルは CaptureMismatch になる" {
            architectureOf {
                gradle(requireWrapper = false)
                "app".group {
                    "Screen".role {
                        layout {
                            "feature" / capture("feature") / mainSourceSet / kotlin /
                                "${capture("feature").pascalCase}Screen".ktFile()
                        }
                    }
                    "Repository".role { layout { "core/data" / mainSourceSet / kotlin / "Repository".ktFile() } }
                }
            }
                .validate(
                    repository {
                        "feature" {
                            "home" { "src/main/kotlin" { "HomeScreen.kt"(); "ProfileScreen.kt"() } }
                            "settings" { "src/main/kotlin" { "SettingsScreen.kt"() } }
                        }
                        "core" { "data" { "src/main/kotlin" { "Repository.kt"() } } }
                    },
                )
                .labels() shouldBe
                listOf("[CaptureMismatch] feature/home/src/main/kotlin/ProfileScreen.kt")
        }

        "モジュールが1つも無くても Missing にならない" {
            architectureOf { gradle(requireWrapper = false) }
                .validate(repositoryOf { "build.gradle.kts"(); "settings.gradle.kts"() })
                .labels() shouldBe emptyList()
        }
    }
})
